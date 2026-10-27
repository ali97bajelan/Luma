package com.luma.lifeos.reminders

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.luma.lifeos.R

internal data class ReminderNotificationAccess(val granted: Boolean, val request: () -> Unit)

@Composable
internal fun rememberReminderNotificationAccess(onRequestComplete: () -> Unit = {}): ReminderNotificationAccess {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val preferences = remember(context) { ReminderPreferences(context) }
    val completeRequest by rememberUpdatedState(onRequestComplete)
    // This is a display snapshot; every permission-sensitive action checks Android again.
    var granted by remember(context) { mutableStateOf(notificationPermissionGranted(context)) }
    var showRationale by rememberSaveable { mutableStateOf(false) }
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = notificationPermissionGranted(context)
        completeRequest()
        enqueueReminderReconciliation(context)
    }
    val launchPermission = {
        preferences.markNotificationPermissionRequested()
        permissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    DisposableEffect(owner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val current = notificationPermissionGranted(context)
                if (current != granted) enqueueReminderReconciliation(context)
                granted = current
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false; completeRequest() },
            title = { Text(stringResource(R.string.reminder_notification_access)) },
            text = { Text(stringResource(R.string.reminder_notification_access_hint)) },
            confirmButton = {
                TextButton(onClick = { showRationale = false; launchPermission() }) {
                    Text(stringResource(R.string.reminder_allow_notifications))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false; completeRequest() }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    return ReminderNotificationAccess(granted) {
        granted = notificationPermissionGranted(context)
        val activity = context.reminderActivity()
        val needsRuntimePermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        when {
            !needsRuntimePermission || activity == null -> {
                completeRequest()
                openReminderNotificationSettings(context)
            }
            ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS) -> showRationale = true
            preferences.notificationPermissionRequested() -> {
                completeRequest()
                openReminderNotificationSettings(context)
            }
            else -> launchPermission()
        }
    }
}

private fun openReminderNotificationSettings(context: Context) {
    // A blocked app needs its app-level switch; otherwise expose the reminder channel.
    val intent = if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
        ReminderPlatform(context).ensureChannel()
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, ReminderPlatform.CHANNEL_ID)
    } else {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private tailrec fun Context.reminderActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.reminderActivity()
    else -> null
}
