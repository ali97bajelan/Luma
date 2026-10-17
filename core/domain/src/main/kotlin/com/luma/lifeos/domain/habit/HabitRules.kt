package com.luma.lifeos.domain.habit

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

/** Returns the latest stored schedule effective on [date], including inactive versions. */
fun scheduleFor(date: LocalDate, versions: List<HabitSchedule>): HabitSchedule? {
    validateScheduleVersions(versions)
    return versions.asSequence()
        .filter { it.effectiveFrom <= date }
        .maxByOrNull { it.effectiveFrom }
}

/** The next schedule date for an already-started habit, in its saved tracking zone. */
fun nextHabitEffectiveDate(now: Instant, zone: ZoneId): LocalDate = now.atZone(zone).toLocalDate().plusDays(1)

/**
 * Builds a version for an edit. Initial and pre-start edits keep the original start date;
 * an already archived future-start habit cannot be reactivated by a stale ACTIVE input.
 */
fun scheduleVersion(
    id: UUID,
    habitId: UUID,
    start: LocalDate,
    now: Instant,
    zone: ZoneId,
    versions: List<HabitSchedule>,
    input: HabitScheduleInput,
): HabitSchedule {
    validateScheduleVersions(versions)
    require(versions.all { it.habitId == habitId }) { "Schedule versions belong to another habit" }
    require(versions.isEmpty() || versions.minOf { it.effectiveFrom } == start) {
        "Initial habit schedule must use the habit start date"
    }
    val today = now.atZone(zone).toLocalDate()
    val effectiveFrom = when {
        versions.isEmpty() -> start
        today.isBefore(start) -> {
            require(input.lifecycle != HabitLifecycle.PAUSED) { "Pause and resume are unavailable before habit start" }
            val initial = scheduleFor(start, versions)
            require(initial?.lifecycle != HabitLifecycle.ARCHIVED || input.lifecycle == HabitLifecycle.ARCHIVED) {
                "An archived future-start habit cannot be reactivated"
            }
            start
        }
        else -> nextHabitEffectiveDate(now, zone)
    }
    return HabitSchedule(id, habitId, effectiveFrom, input.target, input.weekdays, input.lifecycle)
}

/** Replaces a pending edit on the same date while preserving all other history. */
fun replaceScheduleAtEffectiveDate(
    versions: List<HabitSchedule>,
    replacement: HabitSchedule,
    today: LocalDate,
): List<HabitSchedule> {
    validateScheduleVersions(versions)
    require(versions.all { it.habitId == replacement.habitId }) { "Schedule versions belong to another habit" }
    if (versions.isNotEmpty()) {
        val start = versions.minOf { it.effectiveFrom }
        val validEffectiveDate = if (today.isBefore(start)) {
            replacement.effectiveFrom == start
        } else {
            replacement.effectiveFrom.isAfter(today)
        }
        require(validEffectiveDate) { "A schedule version cannot rewrite a past or current date" }
        val replaced = versions.singleOrNull { it.effectiveFrom == replacement.effectiveFrom }
        require(replaced?.lifecycle != HabitLifecycle.ARCHIVED || replacement.lifecycle == HabitLifecycle.ARCHIVED) {
            "An archived habit schedule cannot be reactivated"
        }
    }
    return (versions.filterNot { it.effectiveFrom == replacement.effectiveFrom } + replacement)
        .sortedBy { it.effectiveFrom }
}

/** Removes a pending version only; the initial schedule remains the historical fallback. */
fun cancelPendingSchedule(versions: List<HabitSchedule>, effectiveDate: LocalDate, today: LocalDate): List<HabitSchedule> {
    validateScheduleVersions(versions)
    val pending = versions.singleOrNull { it.effectiveFrom == effectiveDate }
        ?: throw IllegalArgumentException("No schedule exists for the requested effective date")
    require(effectiveDate > versions.minOf { it.effectiveFrom }) { "The initial habit schedule cannot be cancelled" }
    require(effectiveDate.isAfter(today)) { "Only a future pending schedule can be cancelled" }
    require(versions.any { it.effectiveFrom < pending.effectiveFrom }) { "Pending schedule has no earlier version" }
    return versions.filterNot { it.effectiveFrom == effectiveDate }
}

/** Monday is bit zero; Sunday is bit six. */
fun weekdaysToMask(weekdays: Set<DayOfWeek>): Int {
    require(weekdays.isNotEmpty()) { "At least one habit weekday is required" }
    return weekdays.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }
}

fun weekdaysFromMask(mask: Int): Set<DayOfWeek> {
    require(mask in 1..127) { "Habit weekday mask must contain only Monday-through-Sunday bits" }
    return DayOfWeek.entries.filterTo(linkedSetOf()) { mask and (1 shl (it.value - 1)) != 0 }
}

/** A date-only log stays null. A supplied wall time rejects gaps and future instants. */
fun habitLogTime(
    date: LocalDate,
    time: LocalTime?,
    zone: ZoneId,
    now: Instant,
    preferredOffset: ZoneOffset? = null,
): Instant? {
    require(!date.isAfter(now.atZone(zone).toLocalDate())) { "Habit log date cannot be in the future" }
    if (time == null) return null

    val localDateTime = date.atTime(time)
    val offsets = zone.rules.getValidOffsets(localDateTime)
    require(offsets.isNotEmpty()) { "This local time does not exist in the selected timezone" }
    require(preferredOffset == null || preferredOffset in offsets) {
        "Selected timezone offset is not valid for this local time"
    }
    val occurredAt = localDateTime.toInstant(preferredOffset ?: offsets.first())
    require(!occurredAt.isAfter(now)) { "Habit log time cannot be in the future" }
    return occurredAt
}

private fun validateScheduleVersions(versions: List<HabitSchedule>) {
    require(versions.map { it.habitId }.distinct().size <= 1) { "Schedule versions belong to multiple habits" }
    require(versions.map { it.effectiveFrom }.distinct().size == versions.size) {
        "Schedule versions must have unique effective dates"
    }
}
