package com.luma.lifeos.ui.health

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.luma.lifeos.domain.health.HealthIntegration
import com.luma.lifeos.domain.health.HealthStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first

@Composable
fun HealthForegroundRefresh(integration: HealthIntegration) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(integration, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try { refreshHealthOnOpen(integration) } catch (failure: Exception) { if (failure is CancellationException) throw failure }
            awaitCancellation()
        }
    }
}

internal suspend fun refreshHealthOnOpen(integration: HealthIntegration) {
    integration.refreshAccess()
    val state = integration.observeState().first()
    if (state.enabled && state.status in setOf(HealthStatus.READY, HealthStatus.NO_DATA, HealthStatus.ERROR)) {
        integration.importNow()
    }
}
