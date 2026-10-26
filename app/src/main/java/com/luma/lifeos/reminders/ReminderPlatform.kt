package com.luma.lifeos.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.luma.lifeos.R
import com.luma.lifeos.domain.todo.TaskReminderAction

enum class ReminderDeliveryStatus { OFF, CONFIGURED, PENDING, BLOCKED, SCHEDULED, QUIET_HOURS }

class ReminderStatusStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("reminder_status", Context.MODE_PRIVATE)
    fun get(kind: String, id: String): ReminderDeliveryStatus = runCatching {
        ReminderDeliveryStatus.valueOf(prefs.getString("$kind:$id", ReminderDeliveryStatus.CONFIGURED.name).orEmpty())
    }.getOrDefault(ReminderDeliveryStatus.CONFIGURED)
    fun set(kind: String, id: String, status: ReminderDeliveryStatus) {
        prefs.edit().putString("$kind:$id", status.name).apply()
    }
}

internal class ReminderPlatform(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = NotificationManagerCompat.from(context)
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val registry = context.getSharedPreferences("reminder_runtime", Context.MODE_PRIVATE)

    fun permissionBlocked(): Boolean = !notificationPermissionGranted(context) ||
        (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE)

    fun schedule(event: ReminderEvent) {
        ensureChannel()
        val ids = registry.getStringSet(KEY_ALARMS, emptySet()).orEmpty().toMutableSet()
        ids += reminderIdentity(event)
        registry.edit().putStringSet(KEY_ALARMS, ids).apply()
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, event.scheduledAt.toEpochMilli(),
            broadcastPendingIntent(context, event.copy(action = "DELIVER"), "DELIVER"))
    }

    fun cancel(kind: String, id: String) {
        val event = emptyReminderEvent(kind, id)
        alarms.cancel(broadcastPendingIntent(context, event, "DELIVER"))
        val ids = registry.getStringSet(KEY_ALARMS, emptySet()).orEmpty().toMutableSet()
        ids.remove("$kind:$id")
        registry.edit().putStringSet(KEY_ALARMS, ids).apply()
        cancelNotification(kind, id)
    }

    fun cancelAlarmsExcept(desired: Set<String>) {
        val registered = registry.getStringSet(KEY_ALARMS, emptySet()).orEmpty().toSet()
        registered.filterNot(desired::contains).forEach { identity ->
            val (kind, id) = identity.split(':', limit = 2)
            runCatching { alarms.cancel(broadcastPendingIntent(context, emptyReminderEvent(kind, id), "DELIVER")) }
        }
        registry.edit().putStringSet(KEY_ALARMS, desired).apply()
    }

    fun cancelAll() {
        cancelAlarmsExcept(emptySet())
        notifications.cancelAll()
        registry.edit().putStringSet(KEY_NOTIFICATIONS, emptySet()).apply()
    }

    fun cancelNotificationsExcept(currentRevisions: Map<String, String>, cancelAll: Boolean = false) {
        val recorded = registry.getStringSet(KEY_NOTIFICATIONS, emptySet()).orEmpty().toSet()
        val keep = mutableSetOf<String>()
        recorded.forEach { entry ->
            val parts = entry.split(':', limit = 3)
            if (parts.size == 3) {
                val identity = "${parts[0]}:${parts[1]}"
                if (!cancelAll && currentRevisions[identity] == parts[2]) keep += entry
                else cancelNotification(parts[0], parts[1])
            }
        }
        registry.edit().putStringSet(KEY_NOTIFICATIONS, keep).apply()
    }

    fun show(event: ReminderEvent, privateTitle: String?) {
        ensureChannel()
        if (permissionBlocked()) return
        val title = privateTitle?.takeIf { it.isNotBlank() } ?: context.getString(R.string.reminder_notification_generic)
        val generic = context.getString(R.string.reminder_notification_generic)
        val body = when (event.kind) {
            KIND_TASK -> context.getString(R.string.reminder_task_notification)
            else -> context.getString(R.string.reminder_habit_notification)
        }
        val openIntent = openReminderPendingIntent(context, event)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(openIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(generic).setContentText(generic).build())
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
        if (event.kind == KIND_TASK) {
            builder.addAction(android.R.drawable.ic_menu_save, context.getString(R.string.reminder_complete),
                broadcastPendingIntent(context, event.copy(action = "COMPLETE"), "COMPLETE"))
        } else {
            builder.addAction(android.R.drawable.ic_menu_add, context.getString(R.string.reminder_log_one),
                broadcastPendingIntent(context, event.copy(action = "LOG_ONE"), "LOG_ONE"))
        }
        builder.addAction(android.R.drawable.ic_lock_idle_alarm, context.getString(R.string.reminder_snooze),
            broadcastPendingIntent(context, event.copy(action = "SNOOZE"), "SNOOZE"))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ReminderStatusStore(context).set(event.kind, event.id.toString(), ReminderDeliveryStatus.BLOCKED)
            return
        }
        try {
            notifications.notify(notificationTag(event.kind, event.id.toString()), 0, builder.build())
            val entries = registry.getStringSet(KEY_NOTIFICATIONS, emptySet()).orEmpty().toMutableSet()
            entries.removeAll { it.startsWith("${event.kind}:${event.id}:") }
            entries += "${event.kind}:${event.id}:${event.revision}"
            registry.edit().putStringSet(KEY_NOTIFICATIONS, entries).apply()
        } catch (_: SecurityException) {
            ReminderStatusStore(context).set(event.kind, event.id.toString(), ReminderDeliveryStatus.BLOCKED)
        }
    }

    fun cancelNotification(kind: String, id: String) {
        notifications.cancel(notificationTag(kind, id), 0)
        val entries = registry.getStringSet(KEY_NOTIFICATIONS, emptySet()).orEmpty().toMutableSet()
        entries.removeAll { it.startsWith("$kind:$id:") }
        registry.edit().putStringSet(KEY_NOTIFICATIONS, entries).apply()
    }

    fun cancelNotificationForEvent(event: ReminderEvent) {
        val identity = "${event.kind}:${event.id}:${event.revision}"
        if (registry.getStringSet(KEY_NOTIFICATIONS, emptySet()).orEmpty().contains(identity)) {
            cancelNotification(event.kind, event.id.toString())
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID,
                context.getString(R.string.reminder_channel_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.reminder_channel_description)
            })
        }
    }

    private fun notificationTag(kind: String, id: String) = "luma-reminder-$kind-$id"

    companion object {
        const val CHANNEL_ID = "local_reminders"
        private const val KEY_ALARMS = "scheduled_ids"
        private const val KEY_NOTIFICATIONS = "notification_revisions"
    }
}

internal fun taskActionName(action: String): TaskReminderAction? = when (action) {
    "COMPLETE" -> TaskReminderAction.COMPLETE
    "SNOOZE" -> TaskReminderAction.SNOOZE
    "OPEN" -> TaskReminderAction.OPEN
    else -> null
}

private fun reminderIdentity(event: ReminderEvent) = "${event.kind}:${event.id}"

private fun emptyReminderEvent(kind: String, id: String): ReminderEvent {
    val parsed = java.util.UUID.fromString(id)
    val date = java.time.LocalDate.of(1970, 1, 1)
    val at = date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant()
    return ReminderEvent(kind, "DELIVER", parsed, java.util.UUID(0, 0),
        if (kind == KIND_TASK) java.util.UUID(0, 0) else null,
        date, "", at, at.plusSeconds(1), 0, java.util.UUID(0, 0))
}
