package com.luma.lifeos.ui.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luma.lifeos.domain.backup.*
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BackupMessage { EXPORTED, RESTORED, EXPORT_FAILED, INVALID_BACKUP, RESTORE_FAILED, PICKER_UNAVAILABLE }
data class BackupUiState(val busy: Boolean = false, val preview: BackupPreview? = null, val message: BackupMessage? = null)

class BackupViewModel(private val service: BackupService, private val io: CoroutineDispatcher = Dispatchers.IO) : ViewModel() {
    private val mutable = MutableStateFlow(BackupUiState())
    val state = mutable.asStateFlow()
    private var prepared: PreparedBackup? = null
    fun pickerUnavailable() { if (!state.value.busy) mutable.value = BackupUiState(message = BackupMessage.PICKER_UNAVAILABLE) }
    fun export(open: () -> OutputStream) {
        if (state.value.busy || prepared != null) return
        mutable.value = BackupUiState(busy = true)
        viewModelScope.launch {
            try {
                withContext(io) { open().use { service.export(it) } }
                mutable.value = BackupUiState(message = BackupMessage.EXPORTED)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutable.value = BackupUiState(message = BackupMessage.EXPORT_FAILED)
            }
        }
    }
    fun prepare(open: () -> InputStream) {
        if (state.value.busy || prepared != null) return
        mutable.value = BackupUiState(busy = true)
        viewModelScope.launch {
            var candidate: PreparedBackup? = null
            try {
                withContext(io) { candidate = open().use { service.prepare(it) } }
                prepared = requireNotNull(candidate)
                mutable.value = BackupUiState(preview = prepared!!.preview)
            } catch (failure: Exception) {
                withContext(io + NonCancellable) { candidate?.close() }
                if (failure is CancellationException) throw failure
                mutable.value = BackupUiState(message = BackupMessage.INVALID_BACKUP)
            }
        }
    }
    fun cancelRestore() {
        if (state.value.busy) return
        val candidate = prepared ?: return
        prepared = null; mutable.value = BackupUiState(busy = true)
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(io + NonCancellable) { candidate.close() }
            mutable.value = BackupUiState()
        }
    }
    fun confirmRestore() {
        if (state.value.busy) return
        val candidate = prepared ?: return
        prepared = null; mutable.value = BackupUiState(busy = true)
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                withContext(io + NonCancellable) { candidate.use { it.restore() } }
                mutable.value = BackupUiState(message = BackupMessage.RESTORED)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutable.value = BackupUiState(message = BackupMessage.RESTORE_FAILED)
            }
        }
    }
    override fun onCleared() {
        val candidate = prepared; prepared = null
        if (candidate != null) CoroutineScope(io).launch { candidate.close() }
        super.onCleared()
    }
}
