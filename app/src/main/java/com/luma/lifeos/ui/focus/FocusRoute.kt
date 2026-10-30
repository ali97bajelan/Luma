package com.luma.lifeos.ui.focus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.luma.lifeos.focus.AllowedFocusApps
import com.luma.lifeos.focus.FocusPreferences
import com.luma.lifeos.focus.FocusViewModel
import com.luma.lifeos.domain.focus.FocusAction
import com.luma.lifeos.domain.focus.FocusRecoveryChoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

@Composable
internal fun FocusRoute(
    viewModel: FocusViewModel,
    preferences: FocusPreferences?,
    allowedFocusApps: AllowedFocusApps?,
    onOpenHistory: () -> Unit,
    hasPendingDestination: Boolean,
    onBack: () -> Unit,
    onOpenPendingDestination: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val resolvedPreferences = remember(context, preferences) { preferences ?: FocusPreferences(context) }
    val resolvedAllowedFocusApps = remember(context, allowedFocusApps) {
        allowedFocusApps ?: AllowedFocusApps(context)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedPackages by resolvedPreferences.allowedPackages.collectAsStateWithLifecycle(
        initialValue = resolvedPreferences.selectedPackages(),
    )
    var discoveredApps by remember { mutableStateOf(emptyList<AllowedFocusApps.App>()) }
    var refreshKey by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey += 1
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(resolvedAllowedFocusApps, refreshKey) {
        discoveredApps = try {
            withContext(Dispatchers.IO) { resolvedAllowedFocusApps.discover() }
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            emptyList()
        }
    }

    FocusScreen(
        state = state,
        allowedApps = discoveredApps.filter { it.packageName in selectedPackages },
        hasPendingDestination = hasPendingDestination,
        onBack = onBack,
        onShowBackPrompt = viewModel::showBackPrompt,
        onDismissBackPrompt = viewModel::dismissBackPrompt,
        onStart = viewModel::start,
        onAction = { action -> viewModel.act(action) },
        onLeaveAfterAction = { action -> viewModel.act(action, onSuccess = onBack) },
        onResolveRecovery = viewModel::resolveRecovery,
        onRetry = viewModel::retry,
        onRetryInitialization = viewModel::retryInitialization,
        onEmergencyExit = viewModel::requestEmergencyExit,
        onDismissEmergencyExit = viewModel::dismissEmergencyExit,
        onConfirmEmergencyExit = viewModel::confirmEmergencyExit,
        onLaunchApp = viewModel::launchSelectedApp,
        onOpenPendingDestination = onOpenPendingDestination,
        onOpenHistory = onOpenHistory,
    )
}
