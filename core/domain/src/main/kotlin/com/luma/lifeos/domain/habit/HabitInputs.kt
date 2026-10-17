package com.luma.lifeos.domain.habit

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.ConsistentCopyVisibility

object HabitTextLimits {
    const val NAME = 100
    const val UNIT_LABEL = 30
    const val MIN_TARGET = 1
    const val MAX_TARGET = 999

    fun codePointCount(value: String): Int = value.codePointCount(0, value.length)
}

@ConsistentCopyVisibility
data class HabitSaveInput private constructor(
    val name: String,
    val unitLabel: String?,
    val start: LocalDate,
    val target: Int,
    val weekdays: Set<DayOfWeek>,
    val zone: ZoneId,
) {
    init {
        require(name == name.trim() && name.isNotEmpty()) { "Habit name is required" }
        require(HabitTextLimits.codePointCount(name) <= HabitTextLimits.NAME) { "Habit name is too long" }
        require(unitLabel == null || (unitLabel == unitLabel.trim() && unitLabel.isNotEmpty())) {
            "Habit unit label must be non-blank and trimmed"
        }
        require(unitLabel == null || HabitTextLimits.codePointCount(unitLabel) <= HabitTextLimits.UNIT_LABEL) {
            "Habit unit label is too long"
        }
        require(target in HabitTextLimits.MIN_TARGET..HabitTextLimits.MAX_TARGET) {
            "Habit target must be between ${HabitTextLimits.MIN_TARGET} and ${HabitTextLimits.MAX_TARGET}"
        }
        require(weekdays.isNotEmpty()) { "At least one habit weekday is required" }
    }

    companion object {
        operator fun invoke(
            name: String,
            unitLabel: String?,
            start: LocalDate,
            target: Int,
            weekdays: Set<DayOfWeek>,
            zone: ZoneId,
        ): HabitSaveInput = HabitSaveInput(
            name = name.trim(),
            unitLabel = unitLabel?.trim()?.takeUnless(String::isEmpty),
            start = start,
            target = target,
            weekdays = weekdays.toSet(),
            zone = zone,
        )
    }
}

data class HabitScheduleInput(
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

data class HabitLogInput(val habitId: UUID, val date: LocalDate, val zone: ZoneId, val occurredAt: Instant?) {
    init {
        require(occurredAt == null || occurredAt.atZone(zone).toLocalDate() == date) {
            "Habit occurrence date must match its tracking zone date"
        }
    }
}
