package com.luma.lifeos.ui.focus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luma.lifeos.focus.AllowedFocusApps
import com.luma.lifeos.focus.FocusPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Composable
internal fun FocusSettingsRoute(
    preferences: FocusPreferences,
    allowedFocusApps: AllowedFocusApps,
    isRunning: Boolean,
    onBack: () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val selectedPackages by preferences.allowedPackages.collectAsStateWithLifecycle(
        initialValue = preferences.selectedPackages(),
    )
    val writeMutex = remember(preferences) { Mutex() }
    var refreshKey by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var discoveryFailed by remember { mutableStateOf(false) }
    var writeFailed by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf(emptyList<AllowedFocusApps.App>()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey += 1
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(allowedFocusApps, refreshKey) {
        loading = true
        discoveryFailed = false
        try {
            apps = withContext(Dispatchers.IO) { allowedFocusApps.discover() }
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            apps = emptyList()
            discoveryFailed = true
        } finally {
            loading = false
        }
    }

    FocusSettingsScreen(
        apps = apps,
        selectedPackages = selectedPackages,
        isRunning = isRunning,
        loading = loading,
        discoveryFailed = discoveryFailed,
        writeFailed = writeFailed,
        onRefresh = { refreshKey += 1 },
        onToggle = { app, allowed ->
            if (!isRunning && (allowed.not() || apps.any { it.packageName == app.packageName })) {
                scope.launch(Dispatchers.IO) {
                    val saved = try {
                        writeMutex.withLock { preferences.setPackageAllowed(app.packageName, allowed) }
                    } catch (failure: Exception) {
                        if (failure is CancellationException) throw failure
                        false
                    }
                    withContext(Dispatchers.Main.immediate) { writeFailed = !saved }
                }
            }
        },
        onBack = onBack,
    )
}
