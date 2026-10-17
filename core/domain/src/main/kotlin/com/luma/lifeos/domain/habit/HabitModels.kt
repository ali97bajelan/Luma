package com.luma.lifeos.domain.habit

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

enum class HabitLifecycle { ACTIVE, PAUSED, ARCHIVED }

enum class HabitDayState {
    NOT_SCHEDULED,
    PAUSED,
    ARCHIVED,
    NO_RECORDS,
    PARTIAL,
    TARGET_REACHED,
}

data class HabitDefinition(
    val id: UUID,
    val name: String,
    val unitLabel: String?,
    val start: LocalDate,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(name == name.trim() && name.isNotEmpty()) { "Habit name is required and must be trimmed" }
        require(HabitTextLimits.codePointCount(name) <= HabitTextLimits.NAME) { "Habit name is too long" }
        require(unitLabel == null || (unitLabel == unitLabel.trim() && unitLabel.isNotEmpty())) {
            "Habit unit label must be non-blank and trimmed"
        }
        require(unitLabel == null || HabitTextLimits.codePointCount(unitLabel) <= HabitTextLimits.UNIT_LABEL) {
            "Habit unit label is too long"
        }
        require(updatedAt >= createdAt) { "Habit updated-at cannot precede created-at" }
    }
}

data class HabitSchedule(
    val id: UUID,
    val habitId: UUID,
    val effectiveFrom: LocalDate,
    val target: Int,
    val weekdays: Set<DayOfWeek>,
    val lifecycle: HabitLifecycle,
) {
    init {
        require(target in HabitTextLimits.MIN_TARGET..HabitTextLimits.MAX_TARGET) {
            "Habit target must be between ${HabitTextLimits.MIN_TARGET} and ${HabitTextLimits.MAX_TARGET}"
        }
        require(weekdays.isNotEmpty()) { "At least one habit weekday is required" }
    }
}

data class HabitLog(
    val id: UUID,
    val habitId: UUID,
    val date: LocalDate,
    val zone: ZoneId,
    val occurredAt: Instant?,
    val recordedAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(occurredAt == null || occurredAt.atZone(zone).toLocalDate() == date) {
            "Habit occurrence date must match its tracking zone date"
        }
        require(updatedAt >= recordedAt) { "Habit updated-at cannot precede recorded-at" }
    }
}

data class HabitDay(
    val habit: HabitDefinition,
    val date: LocalDate,
    val zone: ZoneId,
    val schedule: HabitSchedule?,
    val count: Int,
    val skipRemaining: Boolean,
) {
    init {
        require(count >= 0) { "Habit count cannot be negative" }
        require(schedule == null || schedule.habitId == habit.id) { "Habit day schedule belongs to another habit" }
        require(schedule == null || schedule.effectiveFrom <= date) { "Habit day schedule is not effective yet" }
    }

    val state: HabitDayState
        get() = when {
            schedule == null -> HabitDayState.NOT_SCHEDULED
            schedule.lifecycle == HabitLifecycle.PAUSED -> HabitDayState.PAUSED
            schedule.lifecycle == HabitLifecycle.ARCHIVED -> HabitDayState.ARCHIVED
            date.dayOfWeek !in schedule.weekdays -> HabitDayState.NOT_SCHEDULED
            count == 0 -> HabitDayState.NO_RECORDS
            count < schedule.target -> HabitDayState.PARTIAL
            else -> HabitDayState.TARGET_REACHED
        }
}

data class HabitHistoryPage(val logs: List<HabitLog>, val offset: Int, val total: Int) {
    init {
        require(offset >= 0) { "Habit history offset cannot be negative" }
        require(total >= 0) { "Habit history total cannot be negative" }
        require(logs.map { it.id }.distinct().size == logs.size) { "Habit history contains duplicate logs" }
        require(logs.size <= total) { "Habit history page cannot exceed its total" }
    }
}
