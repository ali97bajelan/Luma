package com.luma.lifeos.focus

import android.app.Activity
import com.luma.lifeos.domain.focus.FocusAction
import com.luma.lifeos.domain.focus.FocusRecoveryChoice
import com.luma.lifeos.domain.focus.FocusSession
import com.luma.lifeos.domain.focus.FocusState
import com.luma.lifeos.domain.focus.FocusTerminalReason
import com.luma.lifeos.domain.focus.FocusWriteResult
import java.util.IdentityHashMap
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class FocusRuntimeState(
    val initialized: Boolean = false,
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val session: FocusSession? = null,
    val recoveryRequired: Boolean = false,
    val writeFailed: Boolean = false,
    val checkpointFailed: Boolean = false,
    val shortcutFailed: Boolean = false,
    val displayedMillis: Long = 0,
    val emergencyExitSessionId: UUID? = null,
    val emergencyMarkerSaved: Boolean? = null,
    val emergencyExitConfirmed: Boolean = false,
) {
    val guardReleased: Boolean
        get() = session?.let { emergencyExitSessionId == it.id }
            ?: emergencyExitConfirmed

    val navigationGuarded: Boolean
        get() = session?.state == FocusState.RUNNING && !guardReleased
}

enum class FocusShortcutResult { LAUNCHED, NOT_SELECTED, UNAVAILABLE, CHECKPOINT_FAILED }

/** Application-owned focus lifecycle, durable reconciliation and foreground checkpoint loop. */
class FocusRuntime(
    private val coordinator: FocusCoordinator,
    private val preferences: FocusPreferences,
    private val allowedApps: AllowedFocusApps,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(FocusRuntimeState())
    val state: StateFlow<FocusRuntimeState> = mutableState.asStateFlow()

    private val operationMutex = Mutex()
    private val activityLock = Any()
    private val resumedActivities = IdentityHashMap<Activity, Unit>()
    private var initializeJob: Job? = null
    private var observerJob: Job? = null
    private var checkpointJob: Job? = null
    private var lastCheckpointElapsedMillis: Long? = null

    fun initialize() {
        if (initializeJob?.isActive == true || mutableState.value.initialized) return
        initializeJob = scope.launch { initializeState() }
    }

    fun retryInitialization() {
        mutableState.update {
            it.copy(
                initialized = false,
                loading = true,
                loadFailed = false,
                emergencyExitConfirmed = false,
            )
        }
        initializeJob = scope.launch { initializeState() }
    }

    private suspend fun initializeState() {
        mutableState.update { it.copy(loading = true, loadFailed = false) }
        try {
            val reconciliation = coordinator.reconcile()
            if (reconciliation is FocusWriteResult.Invalid || reconciliation is FocusWriteResult.Unavailable) {
                throw IllegalStateException("Focus session could not be reconciled")
            }
            var open = coordinator.observeOpen().first()
            var session = when (reconciliation) {
                is FocusWriteResult.Applied -> reconciliation.snapshot
                is FocusWriteResult.AlreadyApplied -> reconciliation.snapshot
                is FocusWriteResult.RecoveryRequired -> reconciliation.current
                is FocusWriteResult.Conflict -> reconciliation.current ?: open
                else -> open
            }
            var recoveryRequired = reconciliation is FocusWriteResult.RecoveryRequired

            val emergencySessionId = preferences.emergencyExitSessionId()
            if (session != null && recoveryRequired && emergencySessionId == session.id) {
                when (
                    val ended = coordinator.resolveRecovery(
                        session,
                        FocusRecoveryChoice.END_AT_CHECKPOINT,
                        terminalReason = FocusTerminalReason.EMERGENCY_EXIT,
                    )
                ) {
                    is FocusWriteResult.Applied -> {
                        session = ended.snapshot
                        recoveryRequired = false
                        ended.snapshot?.let { preferences.clearEmergencyExit(it.id) }
                    }
                    is FocusWriteResult.AlreadyApplied -> {
                        session = ended.snapshot
                        recoveryRequired = false
                        ended.snapshot?.let { preferences.clearEmergencyExit(it.id) }
                    }
                    else -> Unit
                }
                open = coordinator.observeOpen().first()
                if (session?.isTerminal != true) session = open ?: session
            } else if (emergencySessionId != null &&
                (session == null || session.id != emergencySessionId || session.state != FocusState.RUNNING)
            ) {
                preferences.clearEmergencyExit(emergencySessionId)
            }

            val anchor = if (session?.state == FocusState.RUNNING && !recoveryRequired) {
                coordinator.sample().elapsedMillis
            } else {
                null
            }
            val emergencyExitConfirmed = mutableState.value.emergencyExitConfirmed
            lastCheckpointElapsedMillis = anchor
            mutableState.value = FocusRuntimeState(
                initialized = true,
                loading = false,
                session = session,
                recoveryRequired = recoveryRequired,
                displayedMillis = session?.creditedMillis ?: 0,
                emergencyExitSessionId = if (emergencyExitConfirmed && session?.state == FocusState.RUNNING) {
                    session.id
                } else {
                    null
                },
                emergencyExitConfirmed = emergencyExitConfirmed,
            )
            observeOpenSessions()
            ensureCheckpointLoopIfResumed()
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            mutableState.update { it.copy(initialized = false, loading = false, loadFailed = true) }
        }
    }

    private fun observeOpenSessions() {
        observerJob?.cancel()
        observerJob = scope.launch {
            try {
                coordinator.observeOpen().collect { open ->
                    val previousSession = mutableState.value.session
                    if (open?.state == FocusState.RUNNING && open != previousSession &&
                        !mutableState.value.recoveryRequired
                    ) {
                        lastCheckpointElapsedMillis = coordinator.sample().elapsedMillis
                    }
                    mutableState.update { current ->
                        if (open != null) {
                            current.copy(
                                session = open,
                                displayedMillis = open.creditedMillis,
                                recoveryRequired = current.recoveryRequired && open.state == FocusState.RUNNING,
                            )
                        } else if (current.session?.isTerminal == true) {
                            current.copy(recoveryRequired = false)
                        } else {
                            current.copy(session = null, recoveryRequired = false, displayedMillis = 0)
                        }
                    }
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutableState.update { it.copy(loadFailed = true) }
            }
        }
    }

    fun onActivityResumed(activity: Activity) {
        synchronized(activityLock) {
            resumedActivities[activity] = Unit
            ensureCheckpointLoopLocked()
        }
    }

    fun onActivityPaused(activity: Activity) {
        val backgrounded = synchronized(activityLock) {
            resumedActivities.remove(activity)
            if (resumedActivities.isEmpty()) {
                checkpointJob?.cancel()
                checkpointJob = null
                true
            } else false
        }
        if (backgrounded) scope.launch { checkpointNow() }
    }

    private fun ensureCheckpointLoopIfResumed() {
        synchronized(activityLock) { ensureCheckpointLoopLocked() }
    }

    private fun ensureCheckpointLoopLocked() {
        if (resumedActivities.isEmpty() || checkpointJob?.isActive == true) return
        checkpointJob = scope.launch {
            while (true) {
                delay(1_000)
                try {
                    updateDisplayedTime()
                    val anchor = lastCheckpointElapsedMillis ?: continue
                    val sample = coordinator.sample()
                    if (sample.elapsedMillis - anchor >= CHECKPOINT_INTERVAL_MILLIS) {
                        checkpointNow(sample)
                    }
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    mutableState.update { it.copy(checkpointFailed = true) }
                }
            }
        }
    }

    private suspend fun updateDisplayedTime() {
        val current = mutableState.value
        val session = current.session ?: return
        if (session.state != FocusState.RUNNING || current.recoveryRequired) return
        val anchor = lastCheckpointElapsedMillis ?: return
        val elapsed = (coordinator.sample().elapsedMillis - anchor).coerceAtLeast(0)
        val displayed = runCatching { Math.addExact(session.creditedMillis, elapsed) }
            .getOrDefault(session.creditedMillis)
        mutableState.update { state ->
            if (state.session?.id == session.id && state.session.state == FocusState.RUNNING) {
                state.copy(displayedMillis = displayed)
            } else state
        }
    }

    suspend fun start(commandId: UUID, sessionId: UUID): FocusWriteResult = operationMutex.withLock {
        val sample = coordinator.sample()
        applyResult(coordinator.start(commandId, sessionId, sample), sample)
    }

    suspend fun act(commandId: UUID, session: FocusSession, action: FocusAction): FocusWriteResult =
        operationMutex.withLock {
            val sample = coordinator.sample()
            applyResult(coordinator.act(commandId, session, action, sample), sample)
        }

    suspend fun resolveRecovery(session: FocusSession, choice: FocusRecoveryChoice): FocusWriteResult =
        operationMutex.withLock {
            val sample = coordinator.sample()
            applyResult(coordinator.resolveRecovery(session, choice, sample = sample), sample)
        }

    suspend fun checkpointNow(sample: com.luma.lifeos.domain.focus.FocusTimeSample? = null): FocusWriteResult? =
        operationMutex.withLock {
            val current = mutableState.value
            val session = current.session ?: return@withLock null
            if (session.state != FocusState.RUNNING || current.recoveryRequired) {
                return@withLock null
            }
            val checkpointSample = sample ?: coordinator.sample()
            val result = coordinator.checkpoint(session, checkpointSample)
            applyResult(result, checkpointSample, checkpoint = true)
        }

    private fun applyResult(
        result: FocusWriteResult,
        sample: com.luma.lifeos.domain.focus.FocusTimeSample,
        checkpoint: Boolean = false,
    ): FocusWriteResult {
        when (result) {
            is FocusWriteResult.Applied -> publishSnapshot(result.snapshot, sample)
            is FocusWriteResult.AlreadyApplied -> publishSnapshot(result.snapshot, sample)
            is FocusWriteResult.RecoveryRequired -> mutableState.update {
                it.copy(session = result.current, recoveryRequired = true, writeFailed = false, checkpointFailed = false)
            }
            is FocusWriteResult.Conflict -> mutableState.update { current ->
                current.copy(
                    session = result.current ?: current.session?.takeIf(FocusSession::isTerminal),
                    recoveryRequired = false,
                    writeFailed = false,
                    checkpointFailed = false,
                )
            }
            is FocusWriteResult.Invalid,
            is FocusWriteResult.Unavailable -> mutableState.update {
                it.copy(
                    writeFailed = !checkpoint,
                    checkpointFailed = checkpoint,
                )
            }
        }
        return result
    }

    private fun publishSnapshot(snapshot: FocusSession?, sample: com.luma.lifeos.domain.focus.FocusTimeSample) {
        val session = snapshot ?: return
        if (session.state == FocusState.RUNNING) lastCheckpointElapsedMillis = sample.elapsedMillis
        else lastCheckpointElapsedMillis = null
        if (session.state != FocusState.RUNNING || session.isTerminal) {
            scope.launch { preferences.clearEmergencyExit(session.id) }
        }
        mutableState.update {
            val releasedSessionId = it.emergencyExitSessionId
                ?.takeIf { released -> released == session.id && session.state == FocusState.RUNNING }
            val canKeepEmergencyRelease = releasedSessionId == session.id
            it.copy(
                initialized = true,
                loading = false,
                loadFailed = false,
                session = session,
                recoveryRequired = false,
                writeFailed = false,
                checkpointFailed = false,
                shortcutFailed = false,
                displayedMillis = session.creditedMillis,
                emergencyExitSessionId = releasedSessionId,
                emergencyMarkerSaved = if (canKeepEmergencyRelease) it.emergencyMarkerSaved else null,
                emergencyExitConfirmed = canKeepEmergencyRelease,
            )
        }
    }

    suspend fun markEmergencyExit(sessionId: UUID): Boolean = operationMutex.withLock {
        val saved = withContext(Dispatchers.IO) { preferences.markEmergencyExit(sessionId) }
        mutableState.update {
            it.copy(emergencyMarkerSaved = saved)
        }
        saved
    }

    suspend fun cancelEmergencyExit(sessionId: UUID): Boolean = operationMutex.withLock {
        val cleared = withContext(Dispatchers.IO) { preferences.clearEmergencyExit(sessionId) }
        if (cleared) mutableState.update { it.copy(emergencyMarkerSaved = null) }
        cleared
    }

    fun releaseEmergencyExit(sessionId: UUID) {
        mutableState.update {
            it.copy(emergencyExitSessionId = sessionId, emergencyExitConfirmed = true)
        }
    }

    fun releaseEmergencyExitWithoutSession() {
        mutableState.update {
            it.copy(emergencyExitSessionId = null, emergencyExitConfirmed = true)
        }
    }

    suspend fun launchSelectedApp(packageName: String): FocusShortcutResult = operationMutex.withLock {
        val session = mutableState.value.session
        if (session?.state != FocusState.RUNNING) return@withLock FocusShortcutResult.UNAVAILABLE
        val sample = coordinator.sample()
        val checkpoint = applyResult(coordinator.checkpoint(session, sample), sample, checkpoint = true)
        val durable = when (checkpoint) {
            is FocusWriteResult.Applied -> checkpoint.snapshot
            is FocusWriteResult.AlreadyApplied -> checkpoint.snapshot
            else -> null
        }
        if (durable?.state != FocusState.RUNNING) return@withLock FocusShortcutResult.CHECKPOINT_FAILED
        when (allowedApps.launch(packageName, preferences.selectedPackages())) {
            AllowedFocusApps.LaunchResult.LAUNCHED -> {
                mutableState.update { it.copy(shortcutFailed = false) }
                FocusShortcutResult.LAUNCHED
            }
            AllowedFocusApps.LaunchResult.NOT_SELECTED -> FocusShortcutResult.NOT_SELECTED
            AllowedFocusApps.LaunchResult.UNAVAILABLE -> {
                mutableState.update { it.copy(shortcutFailed = true) }
                FocusShortcutResult.UNAVAILABLE
            }
        }
    }

    private companion object {
        const val CHECKPOINT_INTERVAL_MILLIS = 5_000L
    }
}
