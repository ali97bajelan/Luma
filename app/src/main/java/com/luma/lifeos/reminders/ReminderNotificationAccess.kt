package com.luma.lifeos.reminders

import com.luma.lifeos.ui.common.LumaAlertDialog

import android.Manifest
import android.annotation.SuppressLint
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.luma.lifeos.R

internal enum class ReminderAccessBlockReason { NONE, APP, CHANNEL }

internal data class ReminderNotificationAccess(
    val granted: Boolean,
    val blockReason: ReminderAccessBlockReason = if (granted) ReminderAccessBlockReason.NONE else ReminderAccessBlockReason.APP,
    val refresh: () -> Unit = {},
    val request: () -> Unit,
)

@SuppressLint("InlinedApi")
@Composable
internal fun rememberReminderNotificationAccess(onRequestComplete: () -> Unit = {}): ReminderNotificationAccess {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val preferences = remember(context) { ReminderPreferences(context) }
    val completeRequest by rememberUpdatedState(onRequestComplete)
    // This is a display snapshot; every permission-sensitive action checks Android again.
    var blockReason by remember(context) { mutableStateOf(reminderNotificationBlockReason(context)) }
    val latestBlockReason = rememberUpdatedState(blockReason)
    var showRationale by rememberSaveable { mutableStateOf(value = false) }
    val refresh = { blockReason = reminderNotificationBlockReason(context) }
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refresh()
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
                val current = reminderNotificationBlockReason(context)
                if (current != latestBlockReason.value) enqueueReminderReconciliation(context)
                blockReason = current
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    if (showRationale) {
        LumaAlertDialog(
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

    val request = {
        refresh()
        val activity = context.reminderActivity()
        val needsRuntimePermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
        when {
            (!needsRuntimePermission || activity == null) -> {
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
    return ReminderNotificationAccess(
        granted = blockReason == ReminderAccessBlockReason.NONE,
        blockReason = blockReason,
        refresh = refresh,
        request = request,
    )
}

private fun openReminderNotificationSettings(context: Context) {
    // Repair the same layer reported to the user; available access opens the channel settings.
    val reason = reminderNotificationBlockReason(context)
    val intent = if (reason != ReminderAccessBlockReason.APP) {
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
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private tailrec fun Context.reminderActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.reminderActivity()
    else -> null
}
