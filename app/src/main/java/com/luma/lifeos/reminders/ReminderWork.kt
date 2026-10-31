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
import com.luma.lifeos.domain.habit.ReminderQuietHours
import com.luma.lifeos.domain.habit.nextReminderSlot
import com.luma.lifeos.domain.todo.TaskReminderAction
import com.luma.lifeos.domain.todo.TaskReminderCommand
import com.luma.lifeos.domain.todo.nextTaskReminder
import com.luma.lifeos.domain.todo.taskReminderActionToken
import com.luma.lifeos.LumaApplication
import com.luma.lifeos.lock.ProtectedPreviewPolicy
import com.luma.lifeos.lock.LockActionDeniedException
import com.luma.lifeos.domain.lock.LockContentClassification
import com.luma.lifeos.domain.lock.LockOperation
import com.luma.lifeos.domain.lock.LockRequest
import com.luma.lifeos.domain.lock.LockSection
import java.time.Clock
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val RECONCILE_WORK = "luma-reminder-reconcile"

fun enqueueReminderReconciliation(context: Context) {
    val request = OneTimeWorkRequestBuilder<ReminderWorker>().setInputData(workDataOf("reconcile" to true)).build()
    WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(RECONCILE_WORK, ExistingWorkPolicy.REPLACE, request)
}

internal fun enqueueReminderEvent(context: Context, event: ReminderEvent) {
    val request = OneTimeWorkRequestBuilder<ReminderWorker>().setInputData(event.toData()).build()
    WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
        "reminder-event-${event.kind}-${event.id}-${event.key}-${event.action}", ExistingWorkPolicy.KEEP, request,
    )
}

class ReminderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        return try {
            val storage = LumaStorage(applicationContext)
            val preferences = ReminderPreferences(applicationContext)
            val platform = ReminderPlatform(applicationContext)
            val app = applicationContext as? LumaApplication
            val lockState = try {
                val policy = app?.let { withContext(Dispatchers.IO) { it.localLockStore.read()?.policy } }
                ReminderLockState(policy, app != null)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ReminderLockState(policy = null, readable = false)
            }
            val event = reminderEventFrom(inputData)
            if (event == null) {
                reconcile(storage, preferences, platform, lockState)
            } else if (event.action == "DELIVER") {
                handle(event, storage, preferences, platform, lockState)
            } else {
                val app = applicationContext as? LumaApplication ?: return Result.failure()
                val section = ProtectedPreviewPolicy.reminderSection(event.kind)?.let {
                    if (it == LockSection.HABITS) LockSection.HABITS else LockSection.TASKS
                } ?: return Result.success()
                app.appLockCoordinator.initialize()
                try {
                    app.lockActionGuard.execute(
                        LockRequest(
                            operation = LockOperation.UPDATE,
                            requiredSections = setOf(section),
                            contentClassification = LockContentClassification.CLASSIFIED,
                            objectId = event.id.toString(),
                        ),
                    ) {
                        handle(event, storage, preferences, platform, lockState)
                    }
                } catch (_: LockActionDeniedException) {
                    enqueueReminderReconciliation(applicationContext)
                    return Result.success()
                }
            }
            Result.success()
        } catch (_: SecurityException) {
            Result.retry()
        } catch (_: IllegalStateException) {
            Result.retry()
        }
    }

    private suspend fun reconcile(
        storage: LumaStorage,
        preferences: ReminderPreferences,
        platform: ReminderPlatform,
        lockState: ReminderLockState,
    ) {
        val now = Clock.systemUTC().instant()
        val zone = ZoneId.systemDefault()
        val quiet = preferences.quietHours()
        val status = ReminderStatusStore(applicationContext)
        val desired = linkedMapOf<String, ReminderEvent>()
        val currentRevisions = linkedMapOf<String, String>()
        val protectedIdentities = linkedSetOf<String>()

        val habitInputs = storage.habitReminders.planningInputs(now)
        habitInputs.forEach { input ->
            val identity = "$KIND_HABIT:${input.config.habitId}"
            if (!input.config.enabled) {
                status.set(KIND_HABIT, input.config.habitId.toString(), ReminderDeliveryStatus.OFF)
                return@forEach
            }
            val protected = ProtectedPreviewPolicy.redact(
                lockState.policy, com.luma.lifeos.domain.lock.LockSection.HABITS, lockState.readable,
            )
            currentRevisions[identity] = input.config.revision.toString()
            if (protected) protectedIdentities += identity
            if (platform.permissionBlocked()) {
                status.set(KIND_HABIT, input.config.habitId.toString(), ReminderDeliveryStatus.BLOCKED)
                return@forEach
            }
            val next = nextReminderSlot(input.copy(quietHours = quiet))
            if (next != null) {
                desired[identity] = next.toEvent()
                status.set(KIND_HABIT, input.config.habitId.toString(), ReminderDeliveryStatus.PENDING)
            } else {
                val withoutQuiet = nextReminderSlot(input.copy(quietHours = ReminderQuietHours(enabled = false)))
                status.set(KIND_HABIT, input.config.habitId.toString(),
                    if (withoutQuiet != null) ReminderDeliveryStatus.QUIET_HOURS else ReminderDeliveryStatus.CONFIGURED)
                storage.habitReminders.markReconciled(input.config.habitId, input.config.revision)
            }
        }

        val taskConfigs = storage.taskReminders.configurations()
        val taskSlots = storage.taskReminders.planningSlots(now, zone, quiet).associateBy { it.taskId }
        taskConfigs.forEach { config ->
            val identity = "$KIND_TASK:${config.taskId}"
            if (!config.enabled) {
                status.set(KIND_TASK, config.taskId.toString(), ReminderDeliveryStatus.OFF)
                return@forEach
            }
            val protected = ProtectedPreviewPolicy.redact(
                lockState.policy, com.luma.lifeos.domain.lock.LockSection.TASKS, lockState.readable,
            )
            currentRevisions[identity] = config.revision.toString()
            if (protected) protectedIdentities += identity
            if (platform.permissionBlocked()) {
                status.set(KIND_TASK, config.taskId.toString(), ReminderDeliveryStatus.BLOCKED)
                return@forEach
            }
            val slot = taskSlots[config.taskId]
            if (slot != null) {
                desired[identity] = slot.toEvent()
                status.set(KIND_TASK, config.taskId.toString(), ReminderDeliveryStatus.PENDING)
            } else {
                val task = storage.todos.get(config.taskId)
                val withoutQuiet = task?.let { nextTaskReminder(it, config, now, zone, ReminderQuietHours(enabled = false)) }
                val reason = when {
                    task == null || task.isCompleted || task.dueDate == null || task.dueTime == null -> ReminderDeliveryStatus.CONFIGURED
                    withoutQuiet != null -> ReminderDeliveryStatus.QUIET_HOURS
                    else -> ReminderDeliveryStatus.CONFIGURED
                }
                status.set(KIND_TASK, config.taskId.toString(), reason)
                storage.taskReminders.markReconciled(config.taskId, config.revision)
            }
        }

        if (platform.permissionBlocked()) {
            platform.cancelAlarmsExcept(emptySet())
            platform.cancelNotificationsExcept(emptyMap(), cancelAll = true)
        } else {
            platform.cancelNotificationsExcept(currentRevisions, protectedIdentities)
            platform.cancelAlarmsExcept(desired.keys)
            desired.values.forEach { event ->
                platform.schedule(event)
                status.set(event.kind, event.id.toString(), ReminderDeliveryStatus.SCHEDULED)
                when (event.kind) {
                    KIND_HABIT -> storage.habitReminders.markReconciled(event.id, event.revision)
                    KIND_TASK -> storage.taskReminders.markReconciled(event.id, event.revision)
                }
            }
        }
    }

    private suspend fun handle(
        event: ReminderEvent,
        storage: LumaStorage,
        preferences: ReminderPreferences,
        platform: ReminderPlatform,
        lockState: ReminderLockState,
    ) {
        val now = Clock.systemUTC().instant()
        val zone = ZoneId.systemDefault()
        val quiet = preferences.quietHours()
        if (event.action == "DELIVER") {
            if (platform.permissionBlocked()) {
                ReminderStatusStore(applicationContext).set(event.kind, event.id.toString(), ReminderDeliveryStatus.BLOCKED)
                enqueueReminderReconciliation(applicationContext)
                return
            }
            val recorded = if (event.kind == KIND_HABIT) storage.habitReminders.recordDelivery(event.toHabitSlot(), quiet)
                else storage.taskReminders.markDelivered(event.toTaskSlot(), now, zone, quiet)
            if (!recorded) {
                platform.cancelNotificationForEvent(event)
                enqueueReminderReconciliation(applicationContext)
                return
            }
            val section = ProtectedPreviewPolicy.reminderSection(event.kind)
            val latestLockState = currentReminderLockState()
            val redact = section == null || ProtectedPreviewPolicy.redact(
                latestLockState.policy, section, latestLockState.readable,
            )
            var privateTitle = if (!redact && preferences.privatePreviews()) {
                if (event.kind == KIND_HABIT) storage.habits.get(event.id)?.name else storage.todos.get(event.id)?.title
            } else null
            val finalLockState = currentReminderLockState()
            if (section == null || ProtectedPreviewPolicy.redact(
                    finalLockState.policy, section, finalLockState.readable,
                )
            ) privateTitle = null
            platform.show(event, privateTitle)
            enqueueReminderReconciliation(applicationContext)
            return
        }
        if (event.kind == KIND_HABIT) {
            when (event.action) {
                "LOG_ONE" -> storage.habitReminders.consumeLogAction(event.toHabitSlot(), event.token, now, quiet) != null
                "SNOOZE" -> storage.habitReminders.snooze(event.toHabitSlot(), now, quiet)
                else -> false
            }
        } else {
            val action = when (event.action) {
                "COMPLETE" -> TaskReminderAction.COMPLETE
                "SNOOZE" -> TaskReminderAction.SNOOZE
                else -> return
            }
            val slot = event.toTaskSlot()
            storage.taskReminders.apply(TaskReminderCommand(
                slot = slot, action = action, at = now, actionId = taskReminderActionToken(slot, action),
            ), zone, quiet)
        }
        platform.cancelNotificationForEvent(event)
        enqueueReminderReconciliation(applicationContext)
    }

    private suspend fun currentReminderLockState(): ReminderLockState {
        val app = applicationContext as? LumaApplication ?: return ReminderLockState(null, readable = false)
        return try {
            val policy = withContext(kotlinx.coroutines.Dispatchers.IO) { app.localLockStore.read()?.policy }
            ReminderLockState(policy, readable = true)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ReminderLockState(policy = null, readable = false)
        }
    }
}

private data class ReminderLockState(
    val policy: com.luma.lifeos.domain.lock.LockPolicy?,
    val readable: Boolean,
)

class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        reminderEventFrom(intent)?.let { event -> enqueueReminderEvent(context, event) }
    }
}

class ReminderSystemReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)) enqueueReminderReconciliation(context)
    }
}
