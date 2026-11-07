package com.luma.lifeos.domain.focus

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Returns the next selected local weekday at [time], including a slot due exactly at [now].
 *
 * For a DST gap, the slot is moved to the first valid local time after the gap. For an overlap,
 * the earlier valid offset is used so a local date can produce only one invitation.
 */
fun nextFocusInvitation(
    now: Instant,
    zone: ZoneId,
    weekdays: Set<DayOfWeek>,
    time: LocalTime,
): Instant? {
    require((time.second == 0) && (time.nano == 0)) { "Invitation time must use whole minutes" }
    if (weekdays.isEmpty()) return null

    val today = now.atZone(zone).toLocalDate()
    return (0L..7L)
        .asSequence()
        .map(today::plusDays)
        .filter { it.dayOfWeek in weekdays }
        .map { focusInvitationDueAt(it, zone, time) }
        .firstOrNull { !it.isBefore(now) }
}

/** Resolves one local civil-time slot using the invitation DST policy. */
fun focusInvitationDueAt(date: LocalDate, zone: ZoneId, time: LocalTime): Instant {
    require((time.second == 0) && (time.nano == 0)) { "Invitation time must use whole minutes" }
    val localDateTime = LocalDateTime.of(date, time)
    val rules = zone.rules
    val offsets = rules.getValidOffsets(localDateTime)

    return when (offsets.size) {
        1 -> localDateTime.toInstant(offsets.single())
        2 -> localDateTime.toInstant(offsets.first())
        0 -> {
            val transition = requireNotNull(rules.getTransition(localDateTime)) {
                "A missing local time must have a zone transition"
            }
            transition.dateTimeAfter.toInstant(transition.offsetAfter)
        }
        else -> error("Unexpected number of offsets for $localDateTime in $zone")
    }
}

/** The delivery deadline is inclusive: a slot remains eligible through exactly one hour late. */
fun focusInvitationDeadline(dueAt: Instant): Instant = dueAt.plusSeconds(60L * 60L)
