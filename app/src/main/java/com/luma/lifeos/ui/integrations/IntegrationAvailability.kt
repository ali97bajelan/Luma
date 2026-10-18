package com.luma.lifeos.ui.integrations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luma.lifeos.domain.health.HealthAccess
import com.luma.lifeos.domain.health.HealthIntegration
import com.luma.lifeos.domain.health.HealthStatus
import com.luma.lifeos.domain.usage.UsageAccess
import com.luma.lifeos.domain.usage.UsageIntegration
import com.luma.lifeos.domain.usage.UsageSyncStatus
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class IntegrationAccessState { CHECKING, ALLOWED, REQUIRED, LOCKED, UPDATE_REQUIRED, UNAVAILABLE, ERROR }

data class IntegrationAvailability(
    val enabled: Boolean? = null,
    val access: IntegrationAccessState = IntegrationAccessState.CHECKING,
    val lastSync: Instant? = null,
    val importFailed: Boolean = false,
    val configurationFailed: Boolean = false,
)

data class IntegrationAvailabilityState(
    val usage: IntegrationAvailability = IntegrationAvailability(),
    val health: IntegrationAvailability = IntegrationAvailability(),
)

class IntegrationAvailabilityViewModel(
    private val usage: UsageIntegration?,
    private val health: HealthIntegration?,
) : ViewModel() {
    private val mutable = MutableStateFlow(IntegrationAvailabilityState(
        usage = IntegrationAvailability(access = if (usage == null) IntegrationAccessState.UNAVAILABLE else IntegrationAccessState.CHECKING),
        health = IntegrationAvailability(access = if (health == null) IntegrationAccessState.UNAVAILABLE else IntegrationAccessState.CHECKING),
    ))
    val state = mutable.asStateFlow()
    private val probes = Mutex()
    private var generation = 0L
    private var usageObserver: Job? = null
    private var healthObserver: Job? = null

    init {
        observeUsageConfiguration()
        observeHealthConfiguration()
    }

    private fun observeUsageConfiguration() {
        val integration = usage ?: return
        if (usageObserver?.isActive == true) return
        usageObserver = viewModelScope.launch {
            try {
                integration.observeState().collect { saved ->
                    mutable.update { current -> current.copy(usage = current.usage.copy(
                        enabled = saved.enabled,
                        lastSync = saved.lastSync,
                        importFailed = saved.status == UsageSyncStatus.ERROR,
                        configurationFailed = false,
                    )) }
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutable.update { current -> current.copy(usage = current.usage.copy(
                    enabled = null, configurationFailed = true,
                )) }
            } finally {
                usageObserver = null
            }
        }
    }

    private fun observeHealthConfiguration() {
        val integration = health ?: return
        if (healthObserver?.isActive == true) return
        healthObserver = viewModelScope.launch {
            try {
                integration.observeState().collect { saved ->
                    mutable.update { current -> current.copy(health = current.health.copy(
                        enabled = saved.enabled,
                        lastSync = saved.lastSync,
                        importFailed = saved.status == HealthStatus.ERROR,
                        configurationFailed = false,
                    )) }
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutable.update { current -> current.copy(health = current.health.copy(
                    enabled = null, configurationFailed = true,
                )) }
            } finally {
                healthObserver = null
            }
        }
    }

    /** Coalesces obsolete queued refreshes and lets the newest probe own the displayed result. */
    fun refreshAccess() {
        observeUsageConfiguration()
        observeHealthConfiguration()
        val request = synchronized(this) { ++generation }
        viewModelScope.launch {
            probes.withLock {
                if (!isCurrent(request)) return@withLock
                mutable.update { current -> current.copy(
                    usage = current.usage.copy(access = if (usage == null) IntegrationAccessState.UNAVAILABLE else IntegrationAccessState.CHECKING),
                    health = current.health.copy(access = if (health == null) IntegrationAccessState.UNAVAILABLE else IntegrationAccessState.CHECKING),
                ) }
                val usageResult = usage?.let { integration ->
                    async { runProbe { integration.access().toAvailability() } }
                }
                val healthResult = health?.let { integration ->
                    async { runProbe { integration.access().toAvailability() } }
                }
                val usageAccess = usageResult?.await() ?: IntegrationAccessState.UNAVAILABLE
                val healthAccess = healthResult?.await() ?: IntegrationAccessState.UNAVAILABLE
                if (isCurrent(request)) mutable.update { current -> current.copy(
                    usage = current.usage.copy(access = usageAccess),
                    health = current.health.copy(access = healthAccess),
                ) }
            }
        }
    }

    private fun isCurrent(request: Long): Boolean = synchronized(this) { generation == request }

    private suspend fun runProbe(block: suspend () -> IntegrationAccessState): IntegrationAccessState = try {
        block()
    } catch (failure: Exception) {
        if (failure is CancellationException) throw failure
        IntegrationAccessState.ERROR
    }
}

private fun UsageAccess.toAvailability(): IntegrationAccessState = when (this) {
    UsageAccess.GRANTED -> IntegrationAccessState.ALLOWED
    UsageAccess.REQUIRED -> IntegrationAccessState.REQUIRED
    UsageAccess.LOCKED -> IntegrationAccessState.LOCKED
    UsageAccess.UNAVAILABLE -> IntegrationAccessState.UNAVAILABLE
}

private fun HealthAccess.toAvailability(): IntegrationAccessState = when (this) {
    HealthAccess.GRANTED -> IntegrationAccessState.ALLOWED
    HealthAccess.REQUIRED -> IntegrationAccessState.REQUIRED
    HealthAccess.UPDATE_REQUIRED -> IntegrationAccessState.UPDATE_REQUIRED
    HealthAccess.UNAVAILABLE -> IntegrationAccessState.UNAVAILABLE
}
