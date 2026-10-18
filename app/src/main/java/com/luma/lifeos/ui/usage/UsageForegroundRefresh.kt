package com.luma.lifeos.ui.usage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.luma.lifeos.domain.usage.UsageIntegration
import com.luma.lifeos.domain.usage.UsageSyncStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first

@Composable
fun UsageForegroundRefresh(integration: UsageIntegration) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(integration, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try { refreshUsageOnOpen(integration) } catch (failure: Exception) { if (failure is CancellationException) throw failure }
            awaitCancellation()
        }
    }
}

internal suspend fun refreshUsageOnOpen(integration: UsageIntegration) {
    integration.refreshAccess()
    val state = integration.observeState().first()
    if (state.enabled && state.status in setOf(UsageSyncStatus.READY, UsageSyncStatus.NO_DATA,
            UsageSyncStatus.ERROR, UsageSyncStatus.INTERVAL_CHANGED)) {
        integration.importNow()
    }
}
