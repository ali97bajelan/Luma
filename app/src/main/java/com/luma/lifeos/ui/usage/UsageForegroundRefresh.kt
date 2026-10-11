package com.luma.lifeos.ui.usage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.luma.lifeos.domain.usage.UsageIntegration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation

@Composable
fun UsageForegroundRefresh(integration: UsageIntegration) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(integration, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try { integration.refreshAccess() } catch (failure: Exception) { if (failure is CancellationException) throw failure }
            awaitCancellation()
        }
    }
}
