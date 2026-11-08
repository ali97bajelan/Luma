package com.luma.lifeos.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.luma.lifeos.data.LumaStorage
import com.luma.lifeos.domain.focus.FocusInvitationConfig
import com.luma.lifeos.domain.focus.FocusInvitationSlot
import com.luma.lifeos.domain.focus.focusInvitationDeadline
import com.luma.lifeos.domain.focus.focusInvitationDueAt
import com.luma.lifeos.domain.focus.nextFocusInvitation
import com.luma.lifeos.domain.habit.isWithinQuietHours
import com.luma.lifeos.domain.focus.FocusInvitationRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

private const val FOCUS_INVITATION_RECONCILE_WORK = "focus-invitation-reconcile"

fun enqueueFocusInvitationReconciliation(context: Context) {
    val request = OneTimeWorkRequestBuilder<FocusInvitationWorker>()
        .setInputData(workDataOf())
        .build()
    WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
        FOCUS_INVITATION_RECONCILE_WORK,
        ExistingWorkPolicy.REPLACE,
        request,
    )
}

internal fun enqueueFocusInvitationEvent(context: Context, request: FocusInvitationRequest) {
    val work = OneTimeWorkRequestBuilder<FocusInvitationWorker>()
        .setInputData(request.toData())
        .build()
    val slot = request.slot
    WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
        "focus-invitation-${request.action}-${slot.generation}-${slot.scheduleRevision}-${slot.date}",
        ExistingWorkPolicy.KEEP,
        work,
    )
}

class FocusInvitationWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        val storage = LumaStorage(applicationContext)
        val repository = storage.focusInvitations
        val platform = FocusInvitationPlatform(applicationContext)
        val action = inputData.getString(EXTRA_FOCUS_INVITATION_ACTION)
        if (action == null) {
            reconcile(repository, platform)
        } else {
            val request = focusInvitationRequestFrom(inputData)
            if (request != null) {
                when (request.action) {
                    "DELIVER" -> deliver(repository, platform, request)
                    "SNOOZE" -> snooze(repository, platform, request)
                    "DISABLE" -> disable(repository, platform, request)
                }
            }
        }
        Result.success()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: SecurityException) {
        Result.retry()
    } catch (_: IllegalStateException) {
        Result.retry()
    } catch (_: Exception) {
        Result.retry()
    }

    private suspend fun reconcile(
        repository: FocusInvitationRepository,
        platform: FocusInvitationPlatform,
    ) {
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val config = repository.observeConfig().first()
        val generation = repository.currentGeneration()
        if (!config.enabled) {
            platform.cancelAll()
            return
        }

        var nextSlot: FocusInvitationSlot? = null
        val today = now.atZone(zone).toLocalDate()
        for (daysAgo in 7 downTo 0) {
            val date = today.minusDays(daysAgo.toLong())
            val slot = date.toSlot(config, generation, zone) ?: continue
            val pending = repository.ensureScheduled(slot)
            val candidate = if (pending) slot else repository.snoozedSlot(slot)
            if (candidate == null) continue
            if (now.isAfter(focusInvitationDeadline(candidate.dueAt))) {
                repository.skip(candidate, now)
            } else if (nextSlot == null) {
                nextSlot = candidate
            }
        }

        if (nextSlot == null) {
            val next = nextFocusInvitation(now, zone, config.weekdays, config.time)
            if (next != null) {
                val slot = FocusInvitationSlot(generation, config.revision, next.atZone(zone).toLocalDate(), next)
                if (repository.ensureScheduled(slot)) nextSlot = slot
            }
        }

        val desired = nextSlot?.let { setOf(it.identity()) } ?: emptySet()
        nextSlot?.let(platform::schedule)
        platform.cancelAlarmsExcept(desired)
        platform.cancelStaleNotifications(generation, config.revision, now)
    }

    private suspend fun deliver(
        repository: FocusInvitationRepository,
        platform: FocusInvitationPlatform,
        request: FocusInvitationRequest,
    ) {
        val slot = request.slot
        val now = Instant.now()
        if (now.isBefore(slot.dueAt)) {
            platform.schedule(slot)
            return
        }

        val quietHours = ReminderPreferences(applicationContext).quietHours()
        val minute = now.atZone(ZoneId.systemDefault()).toLocalTime().toSecondOfDay() / 60
        if (!notificationPermissionGranted(applicationContext) ||
            isWithinQuietHours(minute, quietHours)
        ) {
            repository.skip(slot, now)
            platform.cancelNotification(slot)
            enqueueFocusInvitationReconciliation(applicationContext)
            return
        }

        if (repository.consume(slot, now)) {
            repository.actionToken(slot)?.let { platform.show(slot, it) }
        } else {
            platform.cancelNotification(slot)
        }
        enqueueFocusInvitationReconciliation(applicationContext)
    }

    private suspend fun snooze(
        repository: FocusInvitationRepository,
        platform: FocusInvitationPlatform,
        request: FocusInvitationRequest,
    ) {
        val token = request.token ?: return
        if (repository.snooze(request.slot, Instant.now(), token)) {
            platform.cancelNotification(request.slot)
        }
        enqueueFocusInvitationReconciliation(applicationContext)
    }

    private suspend fun disable(
        repository: FocusInvitationRepository,
        platform: FocusInvitationPlatform,
        request: FocusInvitationRequest,
    ) {
        val token = request.token ?: return
        if (repository.disable(request.slot, token)) {
            platform.cancelAll()
        } else {
            platform.cancelNotification(request.slot)
        }
        enqueueFocusInvitationReconciliation(applicationContext)
    }
}

class FocusInvitationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val request = focusInvitationRequestFrom(
            intent,
            expectedComponent = FocusInvitationAlarmReceiver::class.java.name,
        ) ?: return
        if (request.action != "DELIVER") return
        enqueueFocusInvitationEvent(context, request)
    }
}

class FocusInvitationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val request = focusInvitationRequestFrom(
            intent,
            expectedComponent = FocusInvitationActionReceiver::class.java.name,
        ) ?: return
        if (request.action !in setOf("SNOOZE", "DISABLE")) return
        enqueueFocusInvitationEvent(context, request)
    }
}

private fun LocalDate.toSlot(
    config: FocusInvitationConfig,
    generation: java.util.UUID,
    zone: ZoneId,
): FocusInvitationSlot? {
    if (dayOfWeek !in config.weekdays) return null
    return FocusInvitationSlot(
        generation = generation,
        scheduleRevision = config.revision,
        date = this,
        dueAt = focusInvitationDueAt(this, zone, config.time),
    )
}

private fun FocusInvitationSlot.identity(): String =
    "$generation:$scheduleRevision:$date:${dueAt.toEpochMilli()}"
