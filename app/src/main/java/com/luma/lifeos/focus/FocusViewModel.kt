package com.luma.lifeos.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.luma.lifeos.domain.focus.FocusAction
import com.luma.lifeos.domain.focus.FocusInvalidReason
import com.luma.lifeos.domain.focus.FocusRepository
import com.luma.lifeos.domain.focus.FocusRecoveryChoice
import com.luma.lifeos.domain.focus.FocusSession
import com.luma.lifeos.domain.focus.FocusStartInput
import com.luma.lifeos.domain.focus.FocusTag
import com.luma.lifeos.domain.focus.FocusWriteResult
import com.luma.lifeos.domain.focus.normalizeFocusStartInput
import com.luma.lifeos.ui.focus.FocusInsightsState
import com.luma.lifeos.ui.focus.FocusPeriod
import com.luma.lifeos.ui.focus.FocusSetupEvent
import com.luma.lifeos.ui.focus.FocusSetupState
import com.luma.lifeos.ui.focus.encodeFocusSetupState
import com.luma.lifeos.ui.focus.focusInsightsQueryFor
import com.luma.lifeos.ui.focus.observeFocusInsights
import com.luma.lifeos.ui.focus.reduceFocusSetupState
import com.luma.lifeos.ui.focus.restoreFocusSetupState
import com.luma.lifeos.ui.focus.visibleFocusInsightsState
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FocusScreenState(
    val runtime: FocusRuntimeState,
    val busy: Boolean = false,
    val operationFailed: Boolean = false,
    val backPromptVisible: Boolean = false,
    val emergencyPromptVisible: Boolean = false,
    val shortcutUnavailable: Boolean = false,
    val setup: FocusSetupState = FocusSetupState(),
    val insights: FocusInsightsState = FocusInsightsState.Unselected,
    val tagSuggestions: List<FocusTag> = emptyList(),
)

class FocusViewModel(
    private val runtime: FocusRuntime,
    private val repository: FocusRepository?,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private sealed interface PendingOperation {
        data class Start(
            val commandId: UUID,
            val sessionId: UUID,
            val input: FocusStartInput,
            val attempt: FocusCoordinator.OperationAttempt,
        ) : PendingOperation
        data class Action(
            val commandId: UUID,
            val session: FocusSession,
            val action: FocusAction,
            val attempt: FocusCoordinator.OperationAttempt,
        ) : PendingOperation
        data class Recovery(
            val session: FocusSession,
            val choice: FocusRecoveryChoice,
            val attempt: FocusCoordinator.OperationAttempt,
        ) : PendingOperation
    }

    private data class LocalState(
        val busy: Boolean = false,
        val operationFailed: Boolean = false,
        val backPromptVisible: Boolean = false,
        val emergencyPromptVisible: Boolean = false,
        val emergencyMarkerSaved: Boolean? = null,
        val shortcutUnavailable: Boolean = false,
        val setup: FocusSetupState,
    )

    private val local = MutableStateFlow(LocalState(setup = restoreSetupState(savedStateHandle)))
    private val insightState = MutableStateFlow<FocusInsightsState>(FocusInsightsState.Unselected)
    private val tagSuggestions = MutableStateFlow<List<FocusTag>>(emptyList())
    private val insightRetry = MutableStateFlow(0)
    private var pendingOperation: PendingOperation? = null
    private var pendingShortcutPackage: String? = null

    val state: StateFlow<FocusScreenState> = combine(
        runtime.state, local, insightState, tagSuggestions,
    ) { runtimeState, localState, insights, suggestions ->
        val selectedQuery = focusInsightsQueryFor(
            localState.setup,
            runtimeState.initialized,
            runtimeState.databaseGeneration,
        )
        FocusScreenState(
            runtime = runtimeState.copy(
                emergencyMarkerSaved = localState.emergencyMarkerSaved ?: runtimeState.emergencyMarkerSaved,
            ),
            busy = localState.busy,
            operationFailed = localState.operationFailed,
            backPromptVisible = localState.backPromptVisible,
            emergencyPromptVisible = localState.emergencyPromptVisible,
            shortcutUnavailable = localState.shortcutUnavailable || runtimeState.shortcutFailed,
            setup = localState.setup,
            insights = visibleFocusInsightsState(selectedQuery, insights),
            tagSuggestions = suggestions,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        FocusScreenState(runtime.state.value, setup = local.value.setup),
    )

    init {
        viewModelScope.launch {
            runtime.state.map { it.databaseGeneration }.filterNotNull().distinctUntilChanged().collect { generation ->
                val previous = local.value.setup.databaseGeneration
                if (previous != null && previous != generation) {
                    pendingOperation = null
                    local.update { it.copy(operationFailed = false) }
                }
                updateSetup(FocusSetupEvent.GenerationChanged(generation))
            }
        }
        repository?.let { focusRepository ->
            val queries = combine(runtime.state, local) { runtimeState, localState ->
                focusInsightsQueryFor(
                    localState.setup,
                    runtimeState.initialized,
                    runtimeState.databaseGeneration,
                )
            }.distinctUntilChanged()
            val requests = combine(queries, insightRetry) { query, _ -> query }
            viewModelScope.launch {
                observeFocusInsights(focusRepository, requests).collect { insightState.value = it }
            }
            viewModelScope.launch {
                focusRepository.observeTagSuggestions()
                    .catch { failure ->
                        if (failure is CancellationException) throw failure
                        emit(emptyList())
                    }
                    .collect { tagSuggestions.value = it }
            }
        }
    }

    fun changeName(value: String) = updateSetup(FocusSetupEvent.NameChanged(value))
    fun changeTag(value: String) = updateSetup(FocusSetupEvent.TagChanged(value))
    fun changePeriod(value: FocusPeriod) = updateSetup(FocusSetupEvent.PeriodChanged(value))
    fun updateViewingContext(date: LocalDate, zone: ZoneId) = updateSetup(
        FocusSetupEvent.ViewingContextChanged(date, zone.id),
    )
    fun retryInsights() { insightRetry.update { it + 1 } }

    private fun updateSetup(event: FocusSetupEvent) {
        val next = reduceFocusSetupState(local.value.setup, event)
        local.update { it.copy(setup = next) }
        encodeFocusSetupState(next).forEach { (key, value) -> savedStateHandle[key] = value }
    }

    fun start(input: FocusStartInput) {
        val normalized = runCatching { normalizeFocusStartInput(input) }.getOrElse {
            updateSetup(FocusSetupEvent.StartValidationRejected)
            return
        }
        if (!local.value.setup.canStart) return
        if (local.value.setup.normalizedInput != normalized) return
        val attempt = runtime.captureUserOperationAttempt() ?: return reportUnavailableAttempt()
        updateSetup(FocusSetupEvent.StartCaptured(normalized))
        if (local.value.setup.capturedStart != normalized) return
        submit(PendingOperation.Start(UUID.randomUUID(), UUID.randomUUID(), normalized, attempt))
    }

    fun act(action: FocusAction, onSuccess: (() -> Unit)? = null) {
        val session = runtime.state.value.session ?: return
        val attempt = runtime.captureUserOperationAttempt() ?: return reportUnavailableAttempt()
        submit(PendingOperation.Action(UUID.randomUUID(), session, action, attempt), onSuccess)
    }

    fun resolveRecovery(choice: FocusRecoveryChoice) {
        val session = runtime.state.value.session ?: return
        val attempt = runtime.captureUserOperationAttempt() ?: return reportUnavailableAttempt()
        submit(PendingOperation.Recovery(session, choice, attempt))
    }

    fun retry() {
        val pending = pendingOperation
        if (pending != null) {
            submit(pending)
            return
        }
        val shortcutPackage = pendingShortcutPackage
        if (shortcutPackage != null) {
            if (runtime.state.value.session?.state == com.luma.lifeos.domain.focus.FocusState.RUNNING) {
                launchSelectedApp(shortcutPackage)
            } else {
                pendingShortcutPackage = null
                local.update { it.copy(shortcutUnavailable = false) }
            }
            return
        }
        viewModelScope.launch {
            local.update { it.copy(busy = true, operationFailed = false) }
            try {
                runtime.checkpointNow()
                local.update { it.copy(busy = false, operationFailed = runtime.state.value.checkpointFailed) }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                local.update { it.copy(busy = false, operationFailed = true) }
            }
        }
    }

    fun retryInitialization() = runtime.retryInitialization()

    private fun submit(operation: PendingOperation, onSuccess: (() -> Unit)? = null) {
        pendingOperation = operation
        local.update { it.copy(busy = true, operationFailed = false) }
        viewModelScope.launch {
            try {
                val result = when (operation) {
                    is PendingOperation.Start -> runtime.start(
                        operation.commandId, operation.sessionId, operation.input, operation.attempt,
                    )
                    is PendingOperation.Action -> runtime.act(
                        operation.commandId, operation.session, operation.action, operation.attempt,
                    )
                    is PendingOperation.Recovery -> runtime.resolveRecovery(
                        operation.session, operation.choice, operation.attempt,
                    )
                }
                when (result) {
                    is FocusWriteResult.Applied,
                    is FocusWriteResult.AlreadyApplied,
                    is FocusWriteResult.Conflict,
                    is FocusWriteResult.RecoveryRequired -> {
                        pendingOperation = null
                        if (operation is PendingOperation.Start) {
                            updateSetup(
                                if (result is FocusWriteResult.Applied || result is FocusWriteResult.AlreadyApplied) {
                                    FocusSetupEvent.StartSucceeded
                                } else {
                                    FocusSetupEvent.StartOutcomeReconciled
                                },
                            )
                        }
                        local.update {
                            it.copy(
                                busy = false,
                                operationFailed = false,
                                shortcutUnavailable = if (operation is PendingOperation.Action &&
                                    operation.action in setOf(FocusAction.PAUSE, FocusAction.STOP)
                                ) false else it.shortcutUnavailable,
                            )
                        }
                        if (operation is PendingOperation.Action &&
                            operation.action in setOf(FocusAction.PAUSE, FocusAction.STOP)
                        ) pendingShortcutPackage = null
                        if (result is FocusWriteResult.Applied || result is FocusWriteResult.AlreadyApplied) {
                            onSuccess?.invoke()
                        }
                    }
                    is FocusWriteResult.Invalid -> {
                        pendingOperation = null
                        if (operation is PendingOperation.Start) {
                            val metadataInvalid = result.reason == FocusInvalidReason.INVALID_METADATA
                            updateSetup(
                                if (metadataInvalid) FocusSetupEvent.StartValidationRejected
                                else FocusSetupEvent.StartOutcomeReconciled,
                            )
                            local.update { it.copy(busy = false, operationFailed = false) }
                        } else {
                            local.update { it.copy(busy = false, operationFailed = true) }
                        }
                    }
                    is FocusWriteResult.Unavailable -> {
                        if (operation is PendingOperation.Start) updateSetup(FocusSetupEvent.StartIoFailed)
                        local.update { it.copy(busy = false, operationFailed = true) }
                    }
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                if (operation is PendingOperation.Start) updateSetup(FocusSetupEvent.StartIoFailed)
                local.update { it.copy(busy = false, operationFailed = true) }
            }
        }
    }

    private fun reportUnavailableAttempt() {
        local.update { it.copy(operationFailed = true) }
    }

    fun showBackPrompt() = local.update { it.copy(backPromptVisible = true) }
    fun dismissBackPrompt() = local.update { it.copy(backPromptVisible = false) }

    fun requestEmergencyExit() {
        viewModelScope.launch {
            val session = runtime.state.value.session
            val saved = session?.let { runtime.markEmergencyExit(it.id) } ?: false
            local.update {
                it.copy(emergencyPromptVisible = true, emergencyMarkerSaved = saved)
            }
        }
    }

    fun dismissEmergencyExit() {
        viewModelScope.launch {
            val runtimeState = runtime.state.value
            val sessionId = runtimeState.session?.id
            if (runtimeState.emergencyMarkerSaved == true && sessionId != null) {
                val cleared = runtime.cancelEmergencyExit(sessionId)
                if (!cleared) return@launch
            }
            local.update { it.copy(emergencyPromptVisible = false, emergencyMarkerSaved = null) }
        }
    }

    fun confirmEmergencyExit() {
        val sessionId = runtime.state.value.session?.id
        if (sessionId == null) runtime.releaseEmergencyExitWithoutSession()
        else runtime.releaseEmergencyExit(sessionId)
        local.update { it.copy(emergencyPromptVisible = false) }
    }

    fun launchSelectedApp(packageName: String) {
        pendingShortcutPackage = packageName
        viewModelScope.launch {
            local.update { it.copy(busy = true, shortcutUnavailable = false) }
            try {
                when (runtime.launchSelectedApp(packageName)) {
                    FocusShortcutResult.LAUNCHED -> {
                        pendingShortcutPackage = null
                        local.update { it.copy(shortcutUnavailable = false) }
                    }
                    FocusShortcutResult.NOT_SELECTED,
                    FocusShortcutResult.UNAVAILABLE,
                    FocusShortcutResult.CHECKPOINT_FAILED -> local.update { it.copy(shortcutUnavailable = true) }
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                local.update { it.copy(shortcutUnavailable = true) }
            } finally {
                local.update { it.copy(busy = false) }
            }
        }
    }

    private companion object {
        fun restoreSetupState(savedStateHandle: SavedStateHandle): FocusSetupState = restoreFocusSetupState(
            saved = savedStateHandle.keys().associateWith { key -> savedStateHandle.get<Any?>(key) },
            defaultZoneId = ZoneId.systemDefault().id,
            today = LocalDate.now(ZoneId.systemDefault()),
        )
    }
}
