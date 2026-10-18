package com.luma.lifeos.ui.usage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luma.lifeos.domain.usage.*
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class UsageSettingsState(val current: UsageImportState = UsageImportState(), val loading: Boolean = true,
    val busy: Boolean = false, val actionError: Boolean = false)
class UsageSettingsViewModel(private val integration: UsageIntegration) : ViewModel() {
    private val mutable = MutableStateFlow(UsageSettingsState())
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
    fun retryConfiguration() = observe()
    fun refresh() = action { observe(); integration.refreshAccess() }
    private fun action(allowWhileBusy: Boolean = false, block: suspend () -> Unit) {
        if (state.value.busy && !allowWhileBusy) return
        actions++
        mutable.value = state.value.copy(busy = true, actionError = false)
        viewModelScope.launch {
            try { block() }
            catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutable.value = state.value.copy(actionError = true)
            } finally { actions--; mutable.value = state.value.copy(busy = actions > 0) }
        }
    }
}
sealed interface UsageDayState {
    data object Loading : UsageDayState
    data object Empty : UsageDayState
    data object Error : UsageDayState
    data class Data(val report: DailyUsageReport) : UsageDayState
}
fun observeUsageDay(integration: UsageIntegration, date: LocalDate, zone: ZoneId): Flow<UsageDayState> = flow<UsageDayState> {
    emit(UsageDayState.Loading)
    integration.observeDaily(date, zone).collect { emit(if (it == null) UsageDayState.Empty else UsageDayState.Data(it)) }
}.catch { failure ->
    if (failure is CancellationException) throw failure
    emit(UsageDayState.Error)
}
