package com.luma.lifeos.data.habit

import com.luma.lifeos.domain.habit.HabitDay
import com.luma.lifeos.domain.habit.HabitDefinition
import com.luma.lifeos.domain.habit.HabitLifecycle
import com.luma.lifeos.domain.habit.HabitLog
import com.luma.lifeos.domain.habit.HabitSchedule
import com.luma.lifeos.domain.habit.weekdaysFromMask
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

internal fun HabitEntity.toDomain(): HabitDefinition = HabitDefinition(
    id = UUID.fromString(id),
    name = name,
    unitLabel = unitLabel,
    start = LocalDate.parse(startDate),
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

internal fun HabitScheduleEntity.toDomain(): HabitSchedule = HabitSchedule(
    id = UUID.fromString(id),
    habitId = UUID.fromString(habitId),
    effectiveFrom = LocalDate.parse(effectiveFrom),
    target = target,
    weekdays = weekdaysFromMask(weekdayMask),
    lifecycle = HabitLifecycle.valueOf(state),
)

internal fun HabitLogEntity.toDomain(): HabitLog = HabitLog(
    id = UUID.fromString(id),
    habitId = UUID.fromString(habitId),
    date = LocalDate.parse(habitDate),
    zone = ZoneId.of(zoneId),
    occurredAt = occurredAt?.let(Instant::ofEpochMilli),
    recordedAt = Instant.ofEpochMilli(recordedAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

internal fun HabitDayRow.toDomain(date: LocalDate): HabitDay {
    val definition = HabitDefinition(
        id = UUID.fromString(habitId),
        name = name,
        unitLabel = unitLabel,
        start = LocalDate.parse(startDate),
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt),
    )
    val trackingZone = ZoneId.of(trackingZoneId)
    val version = scheduleId?.let { id ->
        HabitSchedule(
            id = UUID.fromString(id),
            habitId = definition.id,
            effectiveFrom = LocalDate.parse(requireNotNull(effectiveFrom)),
            target = requireNotNull(target),
            weekdays = weekdaysFromMask(requireNotNull(weekdayMask)),
            lifecycle = HabitLifecycle.valueOf(requireNotNull(scheduleState)),
        )
    }
    return HabitDay(definition, date, trackingZone, version, count, skipRemaining)
}
