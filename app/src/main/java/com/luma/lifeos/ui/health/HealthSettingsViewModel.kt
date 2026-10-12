package com.luma.lifeos.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luma.lifeos.domain.health.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HealthSettingsState(val current: HealthState = HealthState(), val loading: Boolean = true,
    val busy: Boolean = false, val actionError: Boolean = false)

class HealthSettingsViewModel(private val integration: HealthIntegration) : ViewModel() {
    private val mutable = MutableStateFlow(HealthSettingsState())
    val state = mutable.asStateFlow()
    private var observer: Job? = null
    private var actions = 0
    init { observe() }
    private fun observe() {
        if (observer?.isActive == true) return
        observer = viewModelScope.launch {
            try { integration.observeState().collect { mutable.value = state.value.copy(current = it, loading = false) } }
            catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutable.value = state.value.copy(loading = false, actionError = true)
            }
        }
    }
    fun setEnabled(enabled: Boolean) = action(allowWhileBusy = !enabled) { integration.setEnabled(enabled) }
    fun importNow() = action { integration.importNow() }
    fun refresh() = action { observe(); integration.refreshAccess() }
    private fun action(allowWhileBusy: Boolean = false, block: suspend () -> Unit) {
        if (state.value.busy && !allowWhileBusy) return
        actions++; mutable.value = state.value.copy(busy = true, actionError = false)
        viewModelScope.launch {
            try { block() }
            catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutable.value = state.value.copy(actionError = true)
            } finally { actions--; mutable.value = state.value.copy(busy = actions > 0) }
        }
    }
}
