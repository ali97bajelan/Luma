package com.luma.lifeos.ui.usage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luma.lifeos.domain.usage.*
import com.luma.lifeos.ui.integrations.IntegrationSettingsError
import com.luma.lifeos.ui.integrations.IntegrationSettingsOperation
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class UsageSettingsState(
    val current: UsageImportState = UsageImportState(),
    val loading: Boolean = true,
    val busy: Boolean = false,
    val errors: Map<IntegrationSettingsOperation, IntegrationSettingsError> = emptyMap(),
)

class UsageSettingsViewModel(private val integration: UsageIntegration) : ViewModel() {
    private val mutable = MutableStateFlow(UsageSettingsState())
    val state = mutable.asStateFlow()
    private var observer: Job? = null
    private var actions = 0
    private var nextRequestId = 0L
    private val latestRequest = mutableMapOf<IntegrationSettingsOperation, Long>()
    private var lastPreferenceValue: Boolean? = null

    private enum class ActionOutcome { RECOVERED, FAILED, UNCHANGED }

    init { observe() }

    private fun observe() {
        if (observer?.isActive == true) return
        val requestId = begin(IntegrationSettingsOperation.OBSERVE)
        observer = viewModelScope.launch {
            try {
                integration.observeState().collect { current ->
                    if (isLatest(IntegrationSettingsOperation.OBSERVE, requestId)) {
                        clear(IntegrationSettingsOperation.OBSERVE, requestId)
                        mutable.value = state.value.copy(current = current, loading = false)
                    }
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                fail(IntegrationSettingsOperation.OBSERVE, requestId)
                mutable.value = state.value.copy(loading = false)
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        lastPreferenceValue = enabled
        action(IntegrationSettingsOperation.PREFERENCE, allowWhileBusy = !enabled) {
            integration.setEnabled(enabled)
            ActionOutcome.RECOVERED
        }
    }

    fun retryPreference() { lastPreferenceValue?.let(::setEnabled) }
    fun importNow() = action(IntegrationSettingsOperation.IMPORT) {
        when (integration.importNow()) {
            UsageSyncOutcome.SUCCESS, UsageSyncOutcome.NO_DATA -> ActionOutcome.RECOVERED
            UsageSyncOutcome.DISABLED -> ActionOutcome.UNCHANGED
            UsageSyncOutcome.ACCESS_REQUIRED, UsageSyncOutcome.LOCKED, UsageSyncOutcome.UNAVAILABLE,
            UsageSyncOutcome.ERROR, UsageSyncOutcome.INTERVAL_CHANGED -> ActionOutcome.FAILED
        }
    }
    fun retryConfiguration() = observe()
    fun refresh() = action(IntegrationSettingsOperation.ACCESS_REFRESH) {
        observe()
        integration.refreshAccess()
        // Some integrations persist provider errors and return normally from refreshAccess().
        // A direct access probe distinguishes that case from a successful refresh.
        integration.access()
        ActionOutcome.RECOVERED
    }

    private fun action(
        operation: IntegrationSettingsOperation,
        allowWhileBusy: Boolean = false,
        block: suspend () -> ActionOutcome,
    ) {
        if (state.value.busy && !allowWhileBusy) return
        val requestId = begin(operation)
        actions++
        mutable.value = state.value.copy(busy = true)
        viewModelScope.launch {
            try {
                when (block()) {
                    ActionOutcome.RECOVERED -> clear(operation, requestId)
                    ActionOutcome.FAILED -> fail(operation, requestId)
                    ActionOutcome.UNCHANGED -> Unit
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                fail(operation, requestId)
            } finally {
                actions--
                mutable.value = state.value.copy(busy = actions > 0)
            }
        }
    }

    private fun begin(operation: IntegrationSettingsOperation): Long = (++nextRequestId).also {
        latestRequest[operation] = it
    }

    private fun isLatest(operation: IntegrationSettingsOperation, requestId: Long): Boolean =
        latestRequest[operation] == requestId

    private fun clear(operation: IntegrationSettingsOperation, requestId: Long) {
        if (isLatest(operation, requestId) && state.value.errors.containsKey(operation)) {
            mutable.value = state.value.copy(errors = state.value.errors - operation)
        }
    }

    private fun fail(operation: IntegrationSettingsOperation, requestId: Long) {
        if (isLatest(operation, requestId)) {
            mutable.value = state.value.copy(errors = state.value.errors +
                (operation to IntegrationSettingsError(operation, requestId)))
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
