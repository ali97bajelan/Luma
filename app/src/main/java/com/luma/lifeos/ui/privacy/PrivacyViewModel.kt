package com.luma.lifeos.ui.privacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PrivacyUiState(val confirming: Boolean = false, val busy: Boolean = false,
    val requestAccepted: Boolean = false, val actionError: Boolean = false)

class PrivacyViewModel(private val reset: AppDataReset, private val io: CoroutineDispatcher = Dispatchers.IO) : ViewModel() {
    private val mutable = MutableStateFlow(PrivacyUiState())
    val state = mutable.asStateFlow()
    fun requestDelete() {
        if (!state.value.busy && !state.value.requestAccepted) mutable.value = PrivacyUiState(confirming = true)
    }
    fun cancelDelete() { if (!state.value.busy) mutable.value = PrivacyUiState() }
    fun confirmDelete(resetOperation: suspend () -> Boolean = { reset.requestReset() }) {
        if (!state.value.confirming || state.value.busy || state.value.requestAccepted) return
        mutable.value = PrivacyUiState(busy = true)
        // Start the confirmed OS request even if navigation clears this ViewModel immediately.
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val accepted = withContext(io + NonCancellable) { resetOperation() }
                mutable.value = if (accepted) PrivacyUiState(busy = true, requestAccepted = true)
                    else PrivacyUiState(actionError = true)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutable.value = PrivacyUiState(actionError = true)
            }
        }
    }
}
