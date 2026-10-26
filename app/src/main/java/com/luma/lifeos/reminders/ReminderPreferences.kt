package com.luma.lifeos.reminders

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.app.NotificationManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.luma.lifeos.domain.habit.ReminderQuietHours
import com.luma.lifeos.R

class ReminderPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("reminder_preferences", Context.MODE_PRIVATE)

    fun quietHours(): ReminderQuietHours = ReminderQuietHours(
        enabled = preferences.getBoolean(KEY_QUIET_ENABLED, true),
        startMinute = preferences.getInt(KEY_QUIET_START, 22 * 60),
        endMinute = preferences.getInt(KEY_QUIET_END, 8 * 60),
    )

    fun setQuietHours(value: ReminderQuietHours) {
        preferences.edit().putBoolean(KEY_QUIET_ENABLED, value.enabled)
            .putInt(KEY_QUIET_START, value.startMinute).putInt(KEY_QUIET_END, value.endMinute).apply()
    }

    fun privatePreviews(): Boolean = preferences.getBoolean(KEY_PRIVATE_PREVIEWS, false)

    fun setPrivatePreviews(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_PRIVATE_PREVIEWS, enabled).apply()
    }

    companion object {
        private const val KEY_QUIET_ENABLED = "quiet_enabled"
        private const val KEY_QUIET_START = "quiet_start"
        private const val KEY_QUIET_END = "quiet_end"
        private const val KEY_PRIVATE_PREVIEWS = "private_previews"
    }
}

fun notificationPermissionGranted(context: Context): Boolean =
    (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 26 || context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(ReminderPlatform.CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE)

fun reminderStatusText(status: ReminderDeliveryStatus): Int = when (status) {
    ReminderDeliveryStatus.BLOCKED -> R.string.reminder_status_blocked
    ReminderDeliveryStatus.SCHEDULED -> R.string.reminder_status_scheduled
    ReminderDeliveryStatus.QUIET_HOURS -> R.string.reminder_status_quiet
    ReminderDeliveryStatus.CONFIGURED, ReminderDeliveryStatus.PENDING -> R.string.reminder_status_pending
    ReminderDeliveryStatus.OFF -> R.string.reminder_status_configured
}
