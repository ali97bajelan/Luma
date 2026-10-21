package com.luma.lifeos.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luma.lifeos.domain.health.*
import com.luma.lifeos.ui.integrations.IntegrationSettingsError
import com.luma.lifeos.ui.integrations.IntegrationSettingsOperation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HealthSettingsState(
    val current: HealthState = HealthState(),
    val loading: Boolean = true,
    val busy: Boolean = false,
    val errors: Map<IntegrationSettingsOperation, IntegrationSettingsError> = emptyMap(),
)

class HealthSettingsViewModel(private val integration: HealthIntegration) : ViewModel() {
    private val mutable = MutableStateFlow(HealthSettingsState())
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
            HealthStatus.READY, HealthStatus.NO_DATA -> ActionOutcome.RECOVERED
            HealthStatus.DISABLED -> ActionOutcome.UNCHANGED
            HealthStatus.ACCESS_REQUIRED, HealthStatus.UPDATE_REQUIRED, HealthStatus.UNAVAILABLE,
            HealthStatus.ERROR -> ActionOutcome.FAILED
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
