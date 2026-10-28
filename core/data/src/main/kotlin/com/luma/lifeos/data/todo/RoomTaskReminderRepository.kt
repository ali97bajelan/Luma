package com.luma.lifeos.data.todo

import androidx.room.withTransaction
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.repository.RoomTodoRepository
import com.luma.lifeos.domain.habit.ReminderQuietHours
import com.luma.lifeos.domain.todo.TaskReminderAction
import com.luma.lifeos.domain.todo.TaskReminderCommand
import com.luma.lifeos.domain.todo.TaskReminderConfig
import com.luma.lifeos.domain.todo.TaskReminderRepository
import com.luma.lifeos.domain.todo.TaskReminderSlot
import com.luma.lifeos.domain.todo.nextTaskReminder
import com.luma.lifeos.domain.todo.snoozeTaskReminder
import com.luma.lifeos.domain.todo.taskReminderActionIsCurrent
import com.luma.lifeos.domain.todo.taskReminderActionToken
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class RoomTaskReminderRepository(
    private val database: LumaDatabase,
    private val clock: Clock = Clock.systemUTC(),
) : TaskReminderRepository {
    private val reminders = database.taskReminders()
    private val tasks = database.todos()

    override suspend fun configuration(taskId: UUID): TaskReminderConfig? = reminders.reminder(taskId.toString())?.toDomain()

    override suspend fun configurations(): List<TaskReminderConfig> = database.withTransaction {
        val stored = reminders.reminders().associateBy(TaskReminderEntity::taskId)
        tasks.reminderCandidates().forEach { candidate ->
            val row = stored[candidate.taskId]
            when {
                row == null -> reminders.upsert(automaticReminder(candidate.taskId, candidate.taskRevision))
                !row.enabled || row.taskRevision != candidate.taskRevision -> reminders.upsert(
                    row.copy(
                        enabled = true,
                        taskRevision = candidate.taskRevision,
                        revisionToken = UUID.randomUUID().toString(),
                        consumedActionToken = null,
                        lastDeliveredSlot = null,
                        snoozeSlot = null,
                        snoozeUntil = null,
                        needsReconcile = true,
                    ),
                )
            }
        }
        reminders.reminders().map(TaskReminderEntity::toDomain)
    }

    override suspend fun save(taskId: UUID, enabled: Boolean): TaskReminderConfig = database.withTransaction {
        val task = requireNotNull(tasks.find(taskId.toString())) { "Task does not exist" }
        val next = TaskReminderEntity(
            taskId = taskId.toString(), enabled = enabled, taskRevision = task.revisionToken,
            revisionToken = UUID.randomUUID().toString(), consumedActionToken = null, lastDeliveredSlot = null,
            snoozeSlot = null, snoozeUntil = null, needsReconcile = true,
        )
        reminders.upsert(next)
        next.toDomain()
    }

    override suspend fun planningSlots(now: Instant, zone: ZoneId, quietHours: ReminderQuietHours): List<TaskReminderSlot> =
        reminders.reminders().mapNotNull { row ->
            val task = tasks.find(row.taskId)?.toDomain() ?: return@mapNotNull null
            val config = row.toDomain()
            if (!config.enabled || config.taskRevision != task.revision) return@mapNotNull null
            val regular = nextTaskReminder(task, config, now, zone, quietHours)
            val snoozeUntil = row.snoozeUntil?.let(Instant::ofEpochMilli)
            val snooze = if (snoozeUntil != null) {
                val pastDue = nextTaskReminder(task, config, Instant.MIN, zone, quietHours)
                if (pastDue == null || now >= pastDue.expiresAt || row.snoozeSlot == null) null
                else pastDue.copy(scheduledAt = snoozeUntil, key = row.snoozeSlot,
                    actionToken = taskReminderActionToken(pastDue.copy(key = row.snoozeSlot), TaskReminderAction.COMPLETE))
            } else null
            val next = snooze ?: regular
            next?.takeIf { row.lastDeliveredSlot != it.key }
        }

    override suspend fun markDelivered(slot: TaskReminderSlot, now: Instant, zone: ZoneId, quietHours: ReminderQuietHours): Boolean =
        database.withTransaction {
            val row = reminders.reminder(slot.taskId.toString()) ?: return@withTransaction false
            val task = tasks.find(slot.taskId.toString())?.toDomain() ?: return@withTransaction false
            val config = row.toDomain()
            val expected = expectedSlot(row, task, config, now, zone, quietHours) ?: return@withTransaction false
            if (expected.key != slot.key || expected.scheduledAt != slot.scheduledAt ||
                !taskReminderActionIsCurrent(task, config, slot, now, zone, quietHours)) return@withTransaction false
            if (row.lastDeliveredSlot == slot.key) return@withTransaction row.consumedActionToken == null
            reminders.markDelivered(slot.taskId.toString(), slot.configRevision.toString(), slot.key) == 1
        }

    override suspend fun apply(command: TaskReminderCommand, zone: ZoneId, quietHours: ReminderQuietHours): Boolean =
        database.withTransaction {
            val slot = command.slot
            val row = reminders.reminder(slot.taskId.toString()) ?: return@withTransaction false
            if (row.consumedActionToken == command.actionId.toString()) return@withTransaction true
            val taskEntity = tasks.find(slot.taskId.toString()) ?: return@withTransaction false
            val task = taskEntity.toDomain()
            val config = row.toDomain()
            val now = command.at
            val expected = expectedSlot(row, task, config, now, zone, quietHours) ?: return@withTransaction false
            if (expected.key != slot.key || expected.scheduledAt != slot.scheduledAt ||
                !taskReminderActionIsCurrent(task, config, slot, now, zone, quietHours) ||
                command.actionId != taskReminderActionToken(slot, command.action)) return@withTransaction false
            when (command.action) {
                TaskReminderAction.COMPLETE -> {
                    try {
                        RoomTodoRepository(database, clock).setCompleted(task.id, task.revision, UUID.randomUUID(), true)
                    } catch (_: RuntimeException) {
                        return@withTransaction false
                    }
                    val changed = reminders.reminder(task.id.toString()) ?: return@withTransaction false
                    reminders.consumeAction(task.id.toString(), changed.revisionToken, command.actionId.toString()) == 1
                }
                TaskReminderAction.SNOOZE -> {
                    val snoozed = snoozeTaskReminder(slot, now, zone, quietHours) ?: return@withTransaction false
                    val snoozeAction = taskReminderActionToken(slot, TaskReminderAction.SNOOZE)
                    reminders.snooze(task.id.toString(), config.revision.toString(), snoozed.slot.key,
                        snoozed.until.toEpochMilli(), snoozeAction.toString()) == 1
                }
                TaskReminderAction.OPEN -> false
            }
        }

    override suspend fun markReconciled(taskId: UUID, revision: UUID) {
        reminders.markReconciled(taskId.toString(), revision.toString())
    }

    private fun expectedSlot(
        row: TaskReminderEntity,
        task: com.luma.lifeos.domain.todo.TodoTask,
        config: TaskReminderConfig,
        now: Instant,
        zone: ZoneId,
        quietHours: ReminderQuietHours,
    ): TaskReminderSlot? {
        if (config.taskRevision != task.revision) return null
        val regular = nextTaskReminder(task, config, Instant.MIN, zone, quietHours) ?: return null
        val snoozeUntil = row.snoozeUntil?.let(Instant::ofEpochMilli)
        if (snoozeUntil != null && now < regular.expiresAt) {
            val snoozeKey = row.snoozeSlot ?: return null
            return regular.copy(scheduledAt = snoozeUntil, key = snoozeKey,
                actionToken = taskReminderActionToken(regular.copy(key = snoozeKey), TaskReminderAction.COMPLETE))
        }
        if (now >= regular.expiresAt) return null
        return regular
    }
}

private fun TaskReminderEntity.toDomain() = TaskReminderConfig(
    taskId = UUID.fromString(taskId), enabled = enabled, revision = UUID.fromString(revisionToken),
    taskRevision = UUID.fromString(taskRevision),
)

private fun automaticReminder(taskId: String, taskRevision: String) = TaskReminderEntity(
    taskId = taskId,
    enabled = true,
    taskRevision = taskRevision,
    revisionToken = UUID.randomUUID().toString(),
    consumedActionToken = null,
    lastDeliveredSlot = null,
    snoozeSlot = null,
    snoozeUntil = null,
    needsReconcile = true,
)
