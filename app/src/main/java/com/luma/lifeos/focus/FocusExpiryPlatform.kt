package com.luma.lifeos.focus

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.luma.lifeos.MainActivity
import com.luma.lifeos.R
import java.util.UUID

internal const val FOCUS_EXPIRY_ACTION = "com.luma.lifeos.focus.EXPIRE"
internal const val FOCUS_EXPIRY_KIND_FOCUS = "focus"
internal const val FOCUS_EXPIRY_KIND_BREAK = "break"

data class FocusExpiryEvent(
    val kind: String,
    val generation: UUID,
    val subjectId: String,
    val revision: Long,
    val triggerElapsedMillis: Long,
)

/** Device-local, best-effort elapsed alarms. PendingIntent identity excludes revision on purpose. */
class FocusExpiryPlatform(context: Context) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)
    private val notifications = appContext.getSystemService(NotificationManager::class.java)

    fun scheduleFocus(
        sessionId: UUID,
        generation: UUID,
        revision: Long,
        triggerElapsedMillis: Long,
    ) = schedule(FOCUS_EXPIRY_KIND_FOCUS, generation, sessionId.toString(), revision, triggerElapsedMillis)

    fun cancelFocus(sessionId: UUID, generation: UUID) =
        cancel(FOCUS_EXPIRY_KIND_FOCUS, generation, sessionId.toString())

    fun scheduleBreak(record: com.luma.lifeos.domain.focus.FocusBreakRecord) {
        val trigger = runCatching {
            Math.addExact(record.startedElapsedMillis, record.durationMillis)
        }.getOrNull() ?: return
        schedule(FOCUS_EXPIRY_KIND_BREAK, record.databaseGeneration, "break", record.revision, trigger)
    }

    fun cancelBreak(generation: UUID) = cancel(FOCUS_EXPIRY_KIND_BREAK, generation, "break")

    private fun schedule(kind: String, generation: UUID, subjectId: String, revision: Long, triggerElapsedMillis: Long) {
        if (triggerElapsedMillis < 0L || revision < 0L) return
        val pendingIntent = pendingIntent(kind, generation, subjectId, revision, triggerElapsedMillis, PendingIntent.FLAG_UPDATE_CURRENT)
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            triggerElapsedMillis,
            pendingIntent,
        )
    }

    private fun cancel(kind: String, generation: UUID, subjectId: String) {
        val identity = alarmIntent(kind, generation, subjectId, 0L, 0L)
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            requestCode(kind),
            identity,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun pendingIntent(
        kind: String,
        generation: UUID,
        subjectId: String,
        revision: Long,
        triggerElapsedMillis: Long,
        flags: Int,
    ) = PendingIntent.getBroadcast(
        appContext,
        requestCode(kind),
        alarmIntent(kind, generation, subjectId, revision, triggerElapsedMillis),
        flags or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun alarmIntent(
        kind: String,
        generation: UUID,
        subjectId: String,
        revision: Long,
        triggerElapsedMillis: Long,
    ) = Intent(appContext, FocusExpiryReceiver::class.java)
        .setAction(FOCUS_EXPIRY_ACTION)
        .setData(Uri.parse("luma-focus://expiry/$kind/$generation/$subjectId"))
        .putExtra(EXTRA_REVISION, revision)
        .putExtra(EXTRA_TRIGGER, triggerElapsedMillis)

    fun postExpiryNotification(breakExpired: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val channelId = "focus_timer_expiry"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notifications.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    appContext.getString(R.string.focus_expiry_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
        val title = appContext.getString(
            if (breakExpired) R.string.focus_break_expired_notification_title
            else R.string.focus_expired_notification_title,
        )
        val message = appContext.getString(
            if (breakExpired) R.string.focus_break_expired_notification_body
            else R.string.focus_expired_notification_body,
        )
        val openApp = PendingIntent.getActivity(
            appContext,
            if (breakExpired) BREAK_NOTIFICATION_ID else FOCUS_NOTIFICATION_ID,
            Intent(appContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(appContext, channelId)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle(appContext.getString(R.string.focus_expiry_public_title))
                    .setContentText(appContext.getString(R.string.focus_expiry_public_title))
                    .build(),
            )
            .build()
        runCatching {
            notifications.notify(if (breakExpired) BREAK_NOTIFICATION_ID else FOCUS_NOTIFICATION_ID, notification)
        }
    }

    fun postRecoveryNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val channelId = "focus_timer_expiry"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notifications.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    appContext.getString(R.string.focus_expiry_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
        val openApp = PendingIntent.getActivity(
            appContext,
            RECOVERY_NOTIFICATION_ID,
            Intent(appContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(appContext.getString(R.string.focus_recovery_notification_title))
            .setContentText(appContext.getString(R.string.focus_recovery_notification_body))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(appContext, channelId)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle(appContext.getString(R.string.focus_expiry_public_title))
                    .setContentText(appContext.getString(R.string.focus_expiry_public_title))
                    .build(),
            )
            .build()
        runCatching { notifications.notify(RECOVERY_NOTIFICATION_ID, notification) }
    }

    private fun requestCode(kind: String): Int = if (kind == FOCUS_EXPIRY_KIND_FOCUS) 7301 else 7302

    companion object {
        const val EXTRA_REVISION = "focus_expiry_revision"
        const val EXTRA_TRIGGER = "focus_expiry_trigger"
        const val EXTRA_SUBJECT = "focus_expiry_subject"
        const val EXTRA_KIND = "focus_expiry_kind"
        const val EXTRA_GENERATION = "focus_expiry_generation"
        private const val FOCUS_NOTIFICATION_ID = 7311
        private const val BREAK_NOTIFICATION_ID = 7312
        private const val RECOVERY_NOTIFICATION_ID = 7313
    }
}

class FocusExpiryReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != FOCUS_EXPIRY_ACTION) return
        val data = intent.data ?: return
        val parts = data.pathSegments
        if (parts.size != 3 || parts[0] !in setOf(FOCUS_EXPIRY_KIND_FOCUS, FOCUS_EXPIRY_KIND_BREAK)) return
        val generation = runCatching { UUID.fromString(parts[1]) }.getOrNull() ?: return
        val subjectId = parts[2]
        if (parts[0] == FOCUS_EXPIRY_KIND_FOCUS && runCatching { UUID.fromString(subjectId) }.isFailure) return
        if (parts[0] == FOCUS_EXPIRY_KIND_BREAK && subjectId != "break") return
        val revision = intent.getLongExtra(FocusExpiryPlatform.EXTRA_REVISION, -1L)
        val trigger = intent.getLongExtra(FocusExpiryPlatform.EXTRA_TRIGGER, -1L)
        if (revision < 0L || trigger < 0L) return

        val work = OneTimeWorkRequestBuilder<FocusExpiryWorker>()
            .setInputData(
                workDataOf(
                    FocusExpiryPlatform.EXTRA_KIND to parts[0],
                    FocusExpiryPlatform.EXTRA_GENERATION to generation.toString(),
                    FocusExpiryPlatform.EXTRA_SUBJECT to subjectId,
                    FocusExpiryPlatform.EXTRA_REVISION to revision,
                    FocusExpiryPlatform.EXTRA_TRIGGER to trigger,
                ),
            )
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            "focus-expiry-${parts[0]}-$generation-$subjectId-$revision",
            ExistingWorkPolicy.KEEP,
            work,
        )
    }
}

class FocusExpiryWorker(context: Context, parameters: androidx.work.WorkerParameters) :
    androidx.work.CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val kind = inputData.getString(FocusExpiryPlatform.EXTRA_KIND) ?: return Result.failure()
        val generation = runCatching {
            UUID.fromString(inputData.getString(FocusExpiryPlatform.EXTRA_GENERATION))
        }.getOrNull() ?: return Result.failure()
        val subject = inputData.getString(FocusExpiryPlatform.EXTRA_SUBJECT) ?: return Result.failure()
        val revision = inputData.getLong(FocusExpiryPlatform.EXTRA_REVISION, -1L)
        val trigger = inputData.getLong(FocusExpiryPlatform.EXTRA_TRIGGER, -1L)
        if (revision < 0L || trigger < 0L) return Result.failure()

        val app = applicationContext as? com.luma.lifeos.LumaApplication ?: return Result.failure()
        val result = when (kind) {
            FOCUS_EXPIRY_KIND_FOCUS -> {
                val sessionId = runCatching { UUID.fromString(subject) }.getOrNull() ?: return Result.failure()
                app.focusRuntime.expireFromAlarm(sessionId, generation, revision, trigger)
            }
            FOCUS_EXPIRY_KIND_BREAK -> {
                if (subject != "break") return Result.failure()
                app.focusBreakRuntime.expireFromAlarm(generation, revision, trigger)
            }
            else -> return Result.failure()
        }
        when (result) {
            FocusExpiryOutcome.TARGET_REACHED -> FocusExpiryPlatform(applicationContext).postExpiryNotification(false)
            FocusExpiryOutcome.BREAK_EXPIRED -> FocusExpiryPlatform(applicationContext).postExpiryNotification(true)
            FocusExpiryOutcome.RECOVERY_REQUIRED -> FocusExpiryPlatform(applicationContext).postRecoveryNotification()
            FocusExpiryOutcome.RETRY -> return Result.retry()
            FocusExpiryOutcome.IGNORED -> Unit
        }
        return Result.success()
    }
}

enum class FocusExpiryOutcome { TARGET_REACHED, BREAK_EXPIRED, RECOVERY_REQUIRED, IGNORED, RETRY }
