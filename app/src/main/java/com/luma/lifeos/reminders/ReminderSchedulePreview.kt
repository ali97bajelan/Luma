package com.luma.lifeos.reminders

import com.luma.lifeos.domain.focus.FocusInvitationConfig
import com.luma.lifeos.domain.focus.nextFocusInvitation
import com.luma.lifeos.domain.habit.ReminderPlanningInput
import com.luma.lifeos.domain.habit.ReminderQuietHours
import com.luma.lifeos.domain.habit.isWithinQuietHours
import com.luma.lifeos.domain.habit.nextReminderSlot
import com.luma.lifeos.domain.todo.TaskReminderConfig
import com.luma.lifeos.domain.todo.TodoTask
import com.luma.lifeos.domain.todo.nextTaskReminder
import java.time.Instant
import java.time.ZoneId

sealed interface ReminderSchedulePreview {
    data class NextEligible(val at: Instant, val zone: ZoneId) : ReminderSchedulePreview
    data class QuietHoursSuppressed(val at: Instant, val zone: ZoneId) : ReminderSchedulePreview
    data object MissingDueDate : ReminderSchedulePreview
    data object MissingDueTime : ReminderSchedulePreview
    data object NoSchedule : ReminderSchedulePreview
    data object NoEligibleSlot : ReminderSchedulePreview
}

internal fun taskReminderSchedulePreview(
    task: TodoTask,
    config: TaskReminderConfig,
    now: Instant,
    zone: ZoneId,
    quietHours: ReminderQuietHours,
): ReminderSchedulePreview {
    if (!config.enabled || task.isCompleted) return ReminderSchedulePreview.NoSchedule
    if (task.dueDate == null) return ReminderSchedulePreview.MissingDueDate
    if (task.dueTime == null) return ReminderSchedulePreview.MissingDueTime

    val eligible = nextTaskReminder(task, config, now, zone, quietHours)
    if (eligible != null) return ReminderSchedulePreview.NextEligible(eligible.scheduledAt, zone)

    val withoutQuietHours = nextTaskReminder(task, config, now, zone, quietHours.copy(enabled = false))
    return withoutQuietHours?.let {
        ReminderSchedulePreview.QuietHoursSuppressed(it.scheduledAt, zone)
    } ?: ReminderSchedulePreview.NoEligibleSlot
}

internal fun habitReminderSchedulePreview(
    input: ReminderPlanningInput,
    quietHours: ReminderQuietHours,
): ReminderSchedulePreview {
    if (!input.config.enabled) return ReminderSchedulePreview.NoSchedule
    val withQuietHours = input.copy(quietHours = quietHours)
    val eligible = nextReminderSlot(withQuietHours)
    if (eligible != null) return ReminderSchedulePreview.NextEligible(eligible.at, input.zone)

    val withoutQuietHours = nextReminderSlot(withQuietHours.copy(quietHours = quietHours.copy(enabled = false)))
    return withoutQuietHours?.let {
        ReminderSchedulePreview.QuietHoursSuppressed(it.at, input.zone)
    } ?: ReminderSchedulePreview.NoEligibleSlot
}

internal fun focusInvitationSchedulePreview(
    config: FocusInvitationConfig,
    now: Instant,
    zone: ZoneId,
    quietHours: ReminderQuietHours,
): ReminderSchedulePreview {
    if (!config.enabled) return ReminderSchedulePreview.NoSchedule
    val next = nextFocusInvitation(now, zone, config.weekdays, config.time)
        ?: return ReminderSchedulePreview.NoEligibleSlot
    val localMinute = next.atZone(zone).toLocalTime().toSecondOfDay() / 60
    return if (isWithinQuietHours(localMinute, quietHours)) {
        ReminderSchedulePreview.QuietHoursSuppressed(next, zone)
    } else {
        ReminderSchedulePreview.NextEligible(next, zone)
    }
}
