package com.luma.lifeos.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.luma.lifeos.R
import com.luma.lifeos.domain.focus.FocusInvitationSlot
import java.time.Instant
import java.util.UUID

/** Platform delivery dedicated to Focus invitations; it never touches task or habit reminder identities. */
class FocusInvitationPlatform(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = NotificationManagerCompat.from(context)
    private val registry = context.getSharedPreferences("focus_invitation_platform", Context.MODE_PRIVATE)

    fun schedule(slot: FocusInvitationSlot) {
        val intent = FocusInvitationRequest(slot, "DELIVER").toIntent(context)
        synchronized(REGISTRY_LOCK) {
            if (quiesced) return@synchronized
            val slots = registry.getStringSet(KEY_ALARMS, emptySet()).orEmpty().toMutableSet()
            slots += slotIdentity(slot)
            registry.edit().putStringSet(KEY_ALARMS, slots).commit()
            alarms.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                slot.dueAt.toEpochMilli(),
                PendingIntent.getBroadcast(
                    context,
                    pendingRequestCode(slot, "DELIVER"),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
    }

    fun cancelAlarmsExcept(desired: Set<String>) {
        synchronized(REGISTRY_LOCK) {
            val registered = registry.getStringSet(KEY_ALARMS, emptySet()).orEmpty().toSet()
            val keep = desired.takeUnless { quiesced }.orEmpty()
            registered.filterNot(keep::contains).forEach { identity ->
                parseSlotIdentity(identity)?.let { slot ->
                    alarms.cancel(alarmPendingIntent(slot))
                }
            }
            registry.edit().putStringSet(KEY_ALARMS, keep).commit()
        }
    }

    fun quiesceForReplacement() {
        synchronized(REGISTRY_LOCK) { quiesced = true }
        cancelAll()
    }

    fun resumeAfterQuiescence() {
        cancelAll()
        synchronized(REGISTRY_LOCK) { quiesced = false }
        enqueueFocusInvitationReconciliation(context)
    }

    fun isQuiesced(): Boolean = synchronized(REGISTRY_LOCK) { quiesced }

    fun cancelAll() {
        cancelAlarmsExcept(emptySet())
        synchronized(REGISTRY_LOCK) {
            notificationEntries().forEach { entry ->
                entry.substringBefore('|').let { notifications.cancel(notificationTag(it), 0) }
            }
            registry.edit().putStringSet(KEY_NOTIFICATIONS, emptySet()).commit()
        }
    }

    fun cancelNotification(slot: FocusInvitationSlot) {
        val identity = slotIdentity(slot)
        notifications.cancel(notificationTag(identity), 0)
        synchronized(REGISTRY_LOCK) {
            val entries = notificationEntries().filterNot { it.substringBefore('|') == identity }.toSet()
            registry.edit().putStringSet(KEY_NOTIFICATIONS, entries).commit()
        }
    }

    fun cancelStaleNotifications(generation: UUID, revision: Long, now: Instant) {
        synchronized(REGISTRY_LOCK) {
            val keep = mutableSetOf<String>()
            notificationEntries().forEach { entry ->
                val identity = entry.substringBefore('|')
                val dueAt = entry.substringAfter('|', "").toLongOrNull()
                val parts = identity.split(':', limit = 3)
                val isCurrent = parts.size == 3 &&
                    parts[0] == generation.toString() &&
                    parts[1].toLongOrNull() == revision
                val expired = dueAt == null || now.isAfter(Instant.ofEpochMilli(dueAt).plusSeconds(60 * 60))
                if (isCurrent && !expired) keep += entry
                else notifications.cancel(notificationTag(identity), 0)
            }
            registry.edit().putStringSet(KEY_NOTIFICATIONS, keep).commit()
        }
    }

    fun show(slot: FocusInvitationSlot, token: String) {
        if (!notificationPermissionGranted(context)) return
        ReminderPlatform(context).ensureChannel()
        val request = FocusInvitationRequest(slot, "OPEN", token)
        val generic = context.getString(R.string.focus_invitation_notification_title)
        val body = context.getString(R.string.focus_invitation_notification_body)
        val builder = NotificationCompat.Builder(context, ReminderPlatform.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(generic)
            .setContentText(body)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, ReminderPlatform.CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_popup_reminder)
                    .setContentTitle(generic)
                    .setContentText(body)
                    .build(),
            )
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    pendingRequestCode(slot, "OPEN"),
                    request.toIntent(context),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .addAction(
                android.R.drawable.ic_lock_idle_alarm,
                context.getString(R.string.focus_invitation_snooze),
                PendingIntent.getBroadcast(
                    context,
                    pendingRequestCode(slot, "SNOOZE"),
                    request.copy(action = "SNOOZE").toIntent(context),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.focus_invitation_disable),
                PendingIntent.getBroadcast(
                    context,
                    pendingRequestCode(slot, "DISABLE"),
                    request.copy(action = "DISABLE").toIntent(context),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        val identity = slotIdentity(slot)
        synchronized(REGISTRY_LOCK) {
            if (quiesced) return
            val entries = notificationEntries().toMutableSet()
            entries.removeAll { it.substringBefore('|') == identity }
            entries += "$identity|${slot.dueAt.toEpochMilli()}"
            registry.edit().putStringSet(KEY_NOTIFICATIONS, entries).commit()
            try {
                notifications.notify(notificationTag(identity), 0, builder.build())
            } catch (_: SecurityException) {
                // Permission or channel access may be revoked after the preflight check.
            }
        }
    }

    private fun alarmPendingIntent(slot: FocusInvitationSlot): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            pendingRequestCode(slot, "DELIVER"),
            FocusInvitationRequest(slot, "DELIVER").toIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun notificationEntries(): Set<String> =
        registry.getStringSet(KEY_NOTIFICATIONS, emptySet()).orEmpty().toSet()

    private fun notificationTag(identity: String) = "luma-focus-invitation-$identity"

    private fun parseSlotIdentity(value: String): FocusInvitationSlot? = runCatching {
        val parts = value.split(':', limit = 4)
        require(parts.size == 4)
        FocusInvitationSlot(
            generation = UUID.fromString(parts[0]),
            scheduleRevision = parts[1].toLong(),
            date = java.time.LocalDate.parse(parts[2]),
            dueAt = Instant.ofEpochMilli(parts[3].toLong()),
        )
    }.getOrNull()

    private fun slotIdentity(slot: FocusInvitationSlot) =
        "${slot.generation}:${slot.scheduleRevision}:${slot.date}:${slot.dueAt.toEpochMilli()}"

    private fun pendingRequestCode(slot: FocusInvitationSlot, action: String) =
        "focus-invitation:${slotIdentity(slot)}:$action".hashCode()

    private companion object {
        const val KEY_ALARMS = "scheduled_slots"
        const val KEY_NOTIFICATIONS = "notification_slots"
        val REGISTRY_LOCK = Any()
        @Volatile var quiesced = false
    }
}
