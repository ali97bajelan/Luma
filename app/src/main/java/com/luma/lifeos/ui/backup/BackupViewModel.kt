package com.luma.lifeos.ui.backup

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luma.lifeos.domain.backup.*
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BackupMessage { EXPORTED, RESTORED, EXPORT_FAILED, INVALID_BACKUP, RESTORE_FAILED, PICKER_UNAVAILABLE }
enum class BackupOperation { IDLE, EXPORTING, PREPARING, RESTORING, CANCELLING }
enum class BackupLeaveResult {
    CANCELLED_CLEAN,
    CANCELLED_OUTPUT_MAY_REMAIN,
    COMPLETED,
    FAILED_CLEAN,
    FAILED_OUTPUT_MAY_REMAIN,
    RESTORE_IN_PROGRESS,
}

data class BackupDeparture(val operation: BackupOperation, val result: BackupLeaveResult) {
    fun encode(): String = "${operation.name}:${result.name}"

    companion object {
        fun decode(value: String?): BackupDeparture? {
            val parts = value?.split(':', limit = 2) ?: return null
            if (parts.size != 2) return null
            return runCatching { BackupDeparture(BackupOperation.valueOf(parts[0]), BackupLeaveResult.valueOf(parts[1])) }
                .getOrNull()
        }
    }
}

data class BackupUiState(
    val busy: Boolean = false,
    val preview: BackupPreview? = null,
    val message: BackupMessage? = null,
    val operation: BackupOperation = BackupOperation.IDLE,
)

class BackupViewModel(private val service: BackupService, private val io: CoroutineDispatcher = Dispatchers.IO) : ViewModel() {
    private val mutable = MutableStateFlow(BackupUiState())
    val state = mutable.asStateFlow()
    private var prepared: PreparedBackup? = null
    private var activeOperationJob: Job? = null
    private var discardOutput: (suspend () -> Boolean)? = null
    private var lastOperation = BackupOperation.IDLE
    @Volatile private var exportCompletedSuccessfully = false

    fun pickerUnavailable() {
        if (!state.value.busy) mutable.value = BackupUiState(message = BackupMessage.PICKER_UNAVAILABLE)
    }

    fun export(open: () -> OutputStream) = export(open, discardOutput = { false }, focusChoice = null)

    fun export(
        open: () -> OutputStream,
        discardOutput: suspend () -> Boolean,
        focusChoice: BackupFocusExportChoice? = null,
    ) {
        if (state.value.busy || prepared != null) return
        this.discardOutput = discardOutput
        exportCompletedSuccessfully = false
        launchOperation(BackupOperation.EXPORTING) {
            try {
                withContext(io) {
                    open().use { service.export(it, focusChoice) }
                    exportCompletedSuccessfully = true
                }
                mutable.value = BackupUiState(busy = true, message = BackupMessage.EXPORTED,
                    operation = BackupOperation.EXPORTING)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutable.value = BackupUiState(busy = true, message = BackupMessage.EXPORT_FAILED,
                    operation = BackupOperation.EXPORTING)
            }
        }
    }

    fun prepare(open: () -> InputStream) {
        if (state.value.busy || prepared != null) return
        launchOperation(BackupOperation.PREPARING) {
            var candidate: PreparedBackup? = null
            try {
                withContext(io) { candidate = open().use { service.prepare(it) } }
                prepared = requireNotNull(candidate)
                mutable.value = BackupUiState(busy = true, preview = prepared!!.preview,
                    operation = BackupOperation.PREPARING)
            } catch (failure: Exception) {
                withContext(io + NonCancellable) { candidate?.close() }
                if (failure is CancellationException) throw failure
                mutable.value = BackupUiState(busy = true, message = BackupMessage.INVALID_BACKUP,
                    operation = BackupOperation.PREPARING)
            }
        }
    }

    fun cancelRestore() {
        if (state.value.busy) return
        val candidate = prepared ?: return
        prepared = null
        mutable.value = BackupUiState(busy = true, operation = BackupOperation.CANCELLING)
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(io + NonCancellable) { candidate.close() }
            mutable.value = BackupUiState()
        }
    }

    fun confirmRestore(onRestored: () -> Unit = {}, onFinished: () -> Unit = {}): Boolean {
        if (state.value.busy) return false
        val candidate = prepared ?: return false
        prepared = null
        launchOperation(BackupOperation.RESTORING) {
            try {
                withContext(io + NonCancellable) {
                    candidate.use { it.restore() }
                    try {
                        withContext(Dispatchers.Main.immediate) { onRestored() }
                    } catch (failure: Exception) {
                        Log.e("BackupViewModel", "Restore committed, but its success callback failed", failure)
                    }
                }
                mutable.value = BackupUiState(busy = true, message = BackupMessage.RESTORED,
                    operation = BackupOperation.RESTORING)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutable.value = BackupUiState(busy = true, message = BackupMessage.RESTORE_FAILED,
                    operation = BackupOperation.RESTORING)
            } finally {
                withContext(Dispatchers.Main.immediate + NonCancellable) {
                    try { onFinished() } catch (failure: Exception) {
                        Log.e("BackupViewModel", "Restore completion callback failed", failure)
                    }
                }
            }
        }
        return true
    }

    /** Cancels route-owned preparation/export and waits for streams and provider cleanup before leaving. */
    suspend fun cancelAndAwait(): BackupDeparture {
        val operation = when (state.value.operation) {
            BackupOperation.EXPORTING, BackupOperation.PREPARING -> state.value.operation
            BackupOperation.RESTORING -> return BackupDeparture(BackupOperation.RESTORING, BackupLeaveResult.RESTORE_IN_PROGRESS)
            BackupOperation.CANCELLING -> lastOperation
            BackupOperation.IDLE -> {
                val departure = leaveAfterIdle()
                return departure ?: BackupDeparture(lastOperation, BackupLeaveResult.CANCELLED_CLEAN)
            }
        }
        val job = activeOperationJob

        if (operation == BackupOperation.EXPORTING && exportCompletedSuccessfully) {
            job?.join()
            return BackupDeparture(BackupOperation.EXPORTING, BackupLeaveResult.COMPLETED)
        }

        mutable.value = state.value.copy(busy = true, operation = BackupOperation.CANCELLING)
        job?.cancelAndJoin()

        if (operation == BackupOperation.EXPORTING) {
            if (exportCompletedSuccessfully || state.value.message == BackupMessage.EXPORTED) {
                return BackupDeparture(BackupOperation.EXPORTING, BackupLeaveResult.COMPLETED)
            }
            val failed = state.value.message == BackupMessage.EXPORT_FAILED
            val removed = discardIncompleteOutput()
            mutable.value = BackupUiState()
            val result = when {
                failed && removed -> BackupLeaveResult.FAILED_CLEAN
                failed -> BackupLeaveResult.FAILED_OUTPUT_MAY_REMAIN
                removed -> BackupLeaveResult.CANCELLED_CLEAN
                else -> BackupLeaveResult.CANCELLED_OUTPUT_MAY_REMAIN
            }
            return BackupDeparture(BackupOperation.EXPORTING, result)
        }

        val failed = state.value.message == BackupMessage.INVALID_BACKUP
        closePreparedBackup()
        mutable.value = BackupUiState()
        return BackupDeparture(BackupOperation.PREPARING,
            if (failed) BackupLeaveResult.FAILED_CLEAN else BackupLeaveResult.CANCELLED_CLEAN)
    }

    /** Returns feedback for a finished operation, and cleans a failed export before the route leaves. */
    suspend fun leaveAfterIdle(): BackupDeparture? {
        if (state.value.operation == BackupOperation.RESTORING) {
            return BackupDeparture(BackupOperation.RESTORING, BackupLeaveResult.RESTORE_IN_PROGRESS)
        }
        if (state.value.operation == BackupOperation.EXPORTING || state.value.operation == BackupOperation.PREPARING) {
            return cancelAndAwait()
        }
        if (state.value.operation == BackupOperation.CANCELLING) return null

        if (prepared != null) {
            closePreparedBackup()
            mutable.value = BackupUiState()
            return BackupDeparture(BackupOperation.PREPARING, BackupLeaveResult.CANCELLED_CLEAN)
        }
        return when (state.value.message) {
            BackupMessage.EXPORTED -> BackupDeparture(BackupOperation.EXPORTING, BackupLeaveResult.COMPLETED)
            BackupMessage.RESTORED -> BackupDeparture(BackupOperation.RESTORING, BackupLeaveResult.COMPLETED)
            BackupMessage.EXPORT_FAILED -> {
                val removed = discardIncompleteOutput()
                BackupDeparture(BackupOperation.EXPORTING,
                    if (removed) BackupLeaveResult.FAILED_CLEAN else BackupLeaveResult.FAILED_OUTPUT_MAY_REMAIN)
            }
            BackupMessage.INVALID_BACKUP -> BackupDeparture(BackupOperation.PREPARING, BackupLeaveResult.FAILED_CLEAN)
            BackupMessage.RESTORE_FAILED -> BackupDeparture(BackupOperation.RESTORING, BackupLeaveResult.FAILED_CLEAN)
            else -> null
        }
    }

    private fun launchOperation(operation: BackupOperation, action: suspend () -> Unit) {
        mutable.value = BackupUiState(busy = true, operation = operation)
        lastOperation = operation
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            val currentJob = currentCoroutineContext()[Job]
            try {
                action()
            } finally {
                if (activeOperationJob === currentJob) {
                    activeOperationJob = null
                    val latest = state.value
                    if (latest.operation != BackupOperation.CANCELLING) {
                        mutable.value = latest.copy(busy = false, operation = BackupOperation.IDLE)
                    }
                }
            }
        }
        activeOperationJob = job
        job.start()
    }

    private suspend fun discardIncompleteOutput(): Boolean {
        val discard = discardOutput ?: return false
        return try {
            withContext(io + NonCancellable) { discard() }
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun closePreparedBackup() {
        val candidate = prepared ?: return
        prepared = null
        withContext(io + NonCancellable) { candidate.close() }
    }

    override fun onCleared() {
        val candidate = prepared
        prepared = null
        if (candidate != null) CoroutineScope(io).launch { candidate.close() }
        super.onCleared()
    }
}
