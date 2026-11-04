package com.luma.lifeos.focus

import com.luma.lifeos.domain.focus.FocusBreakRecord
import com.luma.lifeos.domain.focus.FocusBreakWriteResult
import com.luma.lifeos.domain.focus.FocusRepository
import com.luma.lifeos.domain.focus.MILLIS_PER_MINUTE
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface FocusBreakState {
    data object Idle : FocusBreakState
    data class Running(val durationMillis: Long, val remainingMillis: Long, val revision: Long) : FocusBreakState
    data class Expired(val durationMillis: Long, val revision: Long) : FocusBreakState
}

/** Owns an explicitly started, uncredited and navigation-independent break countdown. */
class FocusBreakRuntime(
    private val repository: FocusRepository,
    private val clock: FocusClock,
    private val expiryPlatform: FocusExpiryPlatform,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow<FocusBreakState>(FocusBreakState.Idle)
    val state: StateFlow<FocusBreakState> = mutableState.asStateFlow()
    private val updateMutex = Mutex()
    private var currentRecord: FocusBreakRecord? = null
    private var scheduledIdentity: Pair<UUID, Long>? = null

    init {
        scope.launch {
            try {
                repository.observeBreak().collect { updateRecord(it) }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutableState.value = FocusBreakState.Idle
            }
        }
        scope.launch {
            while (true) {
                delay(1_000)
                val record = updateMutex.withLock { currentRecord }
                record?.let { updateRecord(it) }
            }
        }
    }

    suspend fun start(durationMinutes: Int = 5): Boolean {
        if (durationMinutes !in 1..60) return false
        return try {
            updateMutex.withLock {
                val sample = clock.sample()
                val generation = repository.currentGeneration()
                when (
                    val result = repository.startBreak(
                        durationMillis = durationMinutes * MILLIS_PER_MINUTE,
                        startedElapsedMillis = sample.elapsedMillis,
                        bootId = sample.bootId,
                        expectedGeneration = generation,
                    )
                ) {
                    is FocusBreakWriteResult.Applied -> {
                        updateRecordLocked(result.record)
                        true
                    }
                    is FocusBreakWriteResult.AlreadyApplied -> {
                        updateRecordLocked(result.record)
                        true
                    }
                    is FocusBreakWriteResult.Conflict -> {
                        updateRecordLocked(result.current)
                        false
                    }
                    FocusBreakWriteResult.Invalid, FocusBreakWriteResult.Unavailable -> false
                }
            }
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            false
        }
    }

    suspend fun end(): Boolean {
        return try {
            updateMutex.withLock {
                val record = currentRecord ?: return@withLock true
                when (val result = repository.endBreak(record.revision, record.databaseGeneration)) {
                    is FocusBreakWriteResult.Applied -> {
                        updateRecordLocked(result.record)
                        true
                    }
                    is FocusBreakWriteResult.AlreadyApplied -> {
                        updateRecordLocked(result.record)
                        true
                    }
                    is FocusBreakWriteResult.Conflict -> {
                        updateRecordLocked(result.current)
                        false
                    }
                    FocusBreakWriteResult.Invalid, FocusBreakWriteResult.Unavailable -> false
                }
            }
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            false
        }
    }

    suspend fun expireFromAlarm(
        generation: UUID,
        revision: Long,
        triggerElapsedMillis: Long,
    ): FocusExpiryOutcome = updateMutex.withLock {
        val record = try {
            repository.currentBreak()
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            return@withLock FocusExpiryOutcome.RETRY
        } ?: return@withLock FocusExpiryOutcome.IGNORED
        if (record.databaseGeneration != generation || record.revision != revision) {
            return@withLock FocusExpiryOutcome.IGNORED
        }
        val sample = try {
            clock.sample()
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            return@withLock FocusExpiryOutcome.RETRY
        }
        if (record.bootId == null || sample.bootId == null || record.bootId != sample.bootId ||
            sample.elapsedMillis < record.startedElapsedMillis
        ) {
            try {
                repository.endBreak(record.revision, record.databaseGeneration)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                return@withLock FocusExpiryOutcome.RETRY
            }
            updateRecordLocked(null)
            return@withLock FocusExpiryOutcome.IGNORED
        }
        val expectedTrigger = runCatching { Math.addExact(record.startedElapsedMillis, record.durationMillis) }
            .getOrNull() ?: return@withLock FocusExpiryOutcome.IGNORED
        if (expectedTrigger != triggerElapsedMillis || sample.elapsedMillis < expectedTrigger) {
            return@withLock FocusExpiryOutcome.IGNORED
        }
        updateRecordLocked(record)
        FocusExpiryOutcome.BREAK_EXPIRED
    }

    private suspend fun updateRecord(record: FocusBreakRecord?) {
        try {
            updateMutex.withLock { updateRecordLocked(record) }
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            // Retain the last visible state; the observer or periodic refresh can retry later.
        }
    }

    private suspend fun updateRecordLocked(record: FocusBreakRecord?) {
        if (record == null) {
            currentRecord?.let { runCatching { expiryPlatform.cancelBreak(it.databaseGeneration) } }
            currentRecord = null
            scheduledIdentity = null
            mutableState.value = FocusBreakState.Idle
            // Also clear a stale alarm after process recreation when the database has no break row.
            runCatching { repository.currentGeneration() }.getOrNull()?.let { generation ->
                runCatching { expiryPlatform.cancelBreak(generation) }
            }
            return
        }

        val generation = runCatching { repository.currentGeneration() }.getOrNull()
        if (generation != record.databaseGeneration) {
            runCatching { repository.endBreak(record.revision, record.databaseGeneration) }
            runCatching { expiryPlatform.cancelBreak(record.databaseGeneration) }
            currentRecord = null
            scheduledIdentity = null
            mutableState.value = FocusBreakState.Idle
            return
        }
        val sample = clock.sample()
        if (record.bootId == null || sample.bootId == null || sample.bootId != record.bootId ||
            sample.elapsedMillis < record.startedElapsedMillis
        ) {
            runCatching { repository.endBreak(record.revision, record.databaseGeneration) }
            runCatching { expiryPlatform.cancelBreak(record.databaseGeneration) }
            currentRecord = null
            scheduledIdentity = null
            mutableState.value = FocusBreakState.Idle
            return
        }

        val identity = record.databaseGeneration to record.revision
        if (scheduledIdentity != identity) {
            if (runCatching { expiryPlatform.scheduleBreak(record) }.isSuccess) {
                scheduledIdentity = identity
            }
        }
        currentRecord = record
        val elapsed = sample.elapsedMillis - record.startedElapsedMillis
        val remaining = (record.durationMillis - elapsed).coerceAtLeast(0L)
        mutableState.value = if (remaining == 0L) {
            FocusBreakState.Expired(record.durationMillis, record.revision)
        } else {
            FocusBreakState.Running(record.durationMillis, remaining, record.revision)
        }
    }
}
