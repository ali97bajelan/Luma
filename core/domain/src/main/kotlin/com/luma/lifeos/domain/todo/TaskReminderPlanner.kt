package com.luma.lifeos.domain.todo

import com.luma.lifeos.domain.habit.ReminderQuietHours
import com.luma.lifeos.domain.habit.isWithinQuietHours
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class TaskReminderConfig(val taskId: UUID, val enabled: Boolean, val revision: UUID, val taskRevision: UUID)

data class TaskReminderSlot(
    val taskId: UUID,
    val taskRevision: UUID,
    val configRevision: UUID,
    val dueDate: LocalDate,
    val dueMinute: Int,
    val scheduledAt: Instant,
    val expiresAt: Instant,
    val actionToken: UUID,
    val key: String,
) {
    init { require(dueMinute in 0..1439 && expiresAt > scheduledAt) }
}

enum class TaskReminderAction { COMPLETE, SNOOZE, OPEN }

data class TaskReminderCommand(
    val slot: TaskReminderSlot,
    val action: TaskReminderAction,
    val at: Instant,
    val actionId: UUID,
)

interface TaskReminderRepository {
    suspend fun configuration(taskId: UUID): TaskReminderConfig?
    suspend fun configurations(): List<TaskReminderConfig>
    suspend fun save(taskId: UUID, enabled: Boolean): TaskReminderConfig
    suspend fun planningSlots(now: Instant, zone: ZoneId, quietHours: ReminderQuietHours = ReminderQuietHours()): List<TaskReminderSlot>
    suspend fun markDelivered(slot: TaskReminderSlot, now: Instant, zone: ZoneId, quietHours: ReminderQuietHours = ReminderQuietHours()): Boolean
    suspend fun apply(command: TaskReminderCommand, zone: ZoneId, quietHours: ReminderQuietHours = ReminderQuietHours()): Boolean
    suspend fun markReconciled(taskId: UUID, revision: UUID)
}

data class TaskReminderSnooze(val until: Instant, val slot: TaskReminderSlot)

fun nextTaskReminder(
    task: TodoTask,
    config: TaskReminderConfig,
    now: Instant,
    zone: ZoneId,
    quietHours: ReminderQuietHours = ReminderQuietHours(),
): TaskReminderSlot? {
    if (!config.enabled || task.isCompleted || config.taskId != task.id || config.taskRevision != task.revision) return null
    val date = task.dueDate ?: return null
    val time = task.dueTime ?: return null
    val local = date.atTime(time)
    val rules = zone.rules
    val offsets = rules.getValidOffsets(local)
    val at = when {
        offsets.isNotEmpty() -> local.toInstant(offsets.first())
        else -> rules.getTransition(local)?.let { it.dateTimeAfter.toInstant(it.offsetAfter) } ?: return null
    }
    if (at < now) return null
    if (isWithinQuietHours(at.atZone(zone).toLocalTime().toSecondOfDay() / 60, quietHours)) return null
    val expiry = date.plusDays(1).atStartOfDay(zone).toInstant()
    if (expiry <= at) return null
    val key = "$date:${time.hour * 60 + time.minute}:regular"
    val provisional = TaskReminderSlot(task.id, task.revision, config.revision, date, time.hour * 60 + time.minute, at, expiry,
        UUID.nameUUIDFromBytes("${task.id}:${config.revision}:$key:COMPLETE".toByteArray()), key)
    return provisional
}

fun snoozeTaskReminder(slot: TaskReminderSlot, at: Instant, zone: ZoneId, quietHours: ReminderQuietHours = ReminderQuietHours()): TaskReminderSnooze? {
    val until = at.plusSeconds(10 * 60L)
    val dayEnd = slot.dueDate.plusDays(1).atStartOfDay(zone).toInstant()
    if (until <= at || until >= dayEnd || until >= slot.expiresAt) return null
    if (isWithinQuietHours(until.atZone(zone).toLocalTime().toSecondOfDay() / 60, quietHours)) return null
    val key = "${slot.dueDate}:${slot.dueMinute}:snooze:${until.toEpochMilli()}"
    val snoozed = slot.copy(
        scheduledAt = until,
        key = key,
        actionToken = taskReminderActionToken(slot.copy(key = key), TaskReminderAction.COMPLETE),
    )
    return TaskReminderSnooze(until, snoozed)
}

fun taskReminderActionToken(slot: TaskReminderSlot, action: TaskReminderAction): UUID =
    UUID.nameUUIDFromBytes("${slot.taskId}:${slot.configRevision}:${slot.key}:${action.name}".toByteArray())

fun taskReminderActionIsCurrent(
    task: TodoTask,
    config: TaskReminderConfig,
    slot: TaskReminderSlot,
    now: Instant,
    zone: ZoneId,
    quietHours: ReminderQuietHours = ReminderQuietHours(),
): Boolean {
    if (!config.enabled || task.isCompleted || task.id != slot.taskId || task.revision != slot.taskRevision ||
        config.taskId != slot.taskId || config.revision != slot.configRevision || config.taskRevision != task.revision ||
        task.dueDate != slot.dueDate || task.dueTime?.let { it.hour * 60 + it.minute } != slot.dueMinute ||
        now < slot.scheduledAt || now >= slot.expiresAt || now.atZone(zone).toLocalDate() != slot.dueDate ||
        slot.actionToken != taskReminderActionToken(slot, TaskReminderAction.COMPLETE)) return false
    val base = nextTaskReminder(task, config, Instant.MIN, zone, quietHours) ?: return false
    if (slot.key.endsWith(":regular")) return base.key == slot.key && base.scheduledAt == slot.scheduledAt
    return slot.key.startsWith("${slot.dueDate}:${slot.dueMinute}:snooze:") && slot.scheduledAt > base.scheduledAt
}
