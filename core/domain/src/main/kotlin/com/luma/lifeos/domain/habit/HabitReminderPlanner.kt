package com.luma.lifeos.domain.habit

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

private const val MAX_SCAN_DAYS = 366 * 100

fun nextReminderSlot(input: ReminderPlanningInput): ReminderSlot? {
    val config = input.config
    if (!config.enabled) return null
    val today = input.now.atZone(input.zone).toLocalDate()
    val firstDate = maxOf(today, input.schedules.minOfOrNull(HabitSchedule::effectiveFrom) ?: return null)
    for (days in 0..MAX_SCAN_DAYS) {
        val date = firstDate.plusDays(days.toLong())
        val schedule = input.schedules.lastOrNull { it.effectiveFrom <= date } ?: continue
        if (schedule.lifecycle != HabitLifecycle.ACTIVE || date.dayOfWeek !in schedule.weekdays) continue
        if (date == today && (input.todaySkip || input.todayCount >= schedule.target)) continue
        val grid = gridMinutes(config.schedule)
        val candidates = grid.mapNotNull { minute ->
            val resolved = resolve(date, minute, input.zone) ?: return@mapNotNull null
            val localMinute = resolved.atZone(input.zone).toLocalTime().toSecondOfDay() / 60
            if (insideQuietHours(localMinute, input.quietHours)) return@mapNotNull null
            resolved.takeIf { it >= input.now }
        }.sorted()
        val regularSlots = candidates.mapNotNull { at ->
            val minute = at.atZone(input.zone).toLocalTime().toSecondOfDay() / 60
            val offset = at.atZone(input.zone).offset
            val key = "$date:$minute:${offset.id}:regular"
            val dayEnd = date.plusDays(1).atStartOfDay(input.zone).toInstant()
            val nextGridInstant = candidates.firstOrNull { it > at } ?: dayEnd
            val expiry = minOf(dayEnd, nextGridInstant)
            if (input.runtime?.lastDeliveredSlot == key || input.runtime?.snoozeSlot == key) return@mapNotNull null
            val token = actionToken(config.revision, key)
            ReminderSlot(config.habitId, date, at, expiry, key, config.revision, token)
        }
        val runtime = input.runtime
        val snoozed = if (date == today && runtime?.snoozeUntil != null && runtime.revision == config.revision &&
            runtime.snoozeUntil < date.plusDays(1).atStartOfDay(input.zone).toInstant() &&
            !insideQuietHours(runtime.snoozeUntil.atZone(input.zone).toLocalTime().toSecondOfDay() / 60, input.quietHours)) {
            val storedKey = runtime.snoozeSlot
            val snoozeKey = storedKey?.substringBeforeLast('|')
            val persistedExpiry = storedKey?.substringAfterLast('|')?.toLongOrNull()?.let(Instant::ofEpochMilli)
            if (snoozeKey != null && runtime.lastDeliveredSlot != storedKey) {
                val expiry = persistedExpiry ?: date.plusDays(1).atStartOfDay(input.zone).toInstant()
                if (runtime.snoozeUntil < expiry && input.now < expiry) ReminderSlot(config.habitId, date, runtime.snoozeUntil,
                    expiry, storedKey, config.revision, actionToken(config.revision, storedKey)) else null
            } else null
        } else null
        val next = (regularSlots + listOfNotNull(snoozed)).minByOrNull(ReminderSlot::at)
        if (next != null) return next
    }
    return null
}

fun dueReminderSlot(input: ReminderPlanningInput, requested: ReminderSlot, now: Instant): ReminderSlot? {
    if (!input.config.enabled || input.config.revision != requested.revision || input.config.habitId != requested.habitId) return null
    if (requested.at > now || now >= requested.expiresAt || requested.date != now.atZone(input.zone).toLocalDate()) return null
    val daySchedule = input.schedules.lastOrNull { it.effectiveFrom <= requested.date } ?: return null
    if (daySchedule.lifecycle != HabitLifecycle.ACTIVE || requested.date.dayOfWeek !in daySchedule.weekdays ||
        input.todaySkip || input.todayCount >= daySchedule.target) return null
    if (requested.key == input.runtime?.snoozeSlot) {
        val snoozeUntil = input.runtime.snoozeUntil ?: return null
        if (requested.at != snoozeUntil ||
            requested.actionToken != actionToken(input.config.revision, requested.key) ||
            insideQuietHours(snoozeUntil.atZone(input.zone).toLocalTime().toSecondOfDay() / 60, input.quietHours)) return null
        return requested
    }
    val generated = gridMinutes(input.config.schedule).mapNotNull { minute ->
        val at = resolve(requested.date, minute, input.zone) ?: return@mapNotNull null
        val localMinute = at.atZone(input.zone).toLocalTime().toSecondOfDay() / 60
        if (insideQuietHours(localMinute, input.quietHours)) null else at
    }
    val actual = generated.firstOrNull { at ->
        val local = at.atZone(input.zone)
        "${requested.date}:${local.toLocalTime().toSecondOfDay() / 60}:${local.offset.id}:regular" == requested.key
    } ?: return null
    if (actual > requested.expiresAt || requested.actionToken != actionToken(input.config.revision, requested.key)) return null
    return requested
}

fun reminderActionToken(revision: UUID, key: String): UUID = actionToken(revision, key)

fun reminderSnoozeActionToken(revision: UUID, key: String): UUID = UUID.nameUUIDFromBytes("$revision:$key:SNOOZE".toByteArray())

fun snoozedReminder(slot: ReminderSlot, at: Instant, zone: ZoneId, quietHours: ReminderQuietHours = ReminderQuietHours()): ReminderSlot? {
    val until = at.plus(10, ChronoUnit.MINUTES)
    val dayEnd = slot.date.plusDays(1).atStartOfDay(zone).toInstant()
    if (until >= dayEnd || until >= slot.expiresAt || until <= at) return null
    val local = until.atZone(zone)
    if (insideQuietHours(local.toLocalTime().toSecondOfDay() / 60, quietHours)) return null
    val key = "${slot.date}:${local.toLocalTime().toSecondOfDay() / 60}:${local.offset.id}:snooze:${slot.key}|${slot.expiresAt.toEpochMilli()}"
    return slot.copy(at = until, expiresAt = minOf(slot.expiresAt, dayEnd), key = key, actionToken = actionToken(slot.revision, key))
}

fun isWithinQuietHours(minute: Int, quietHours: ReminderQuietHours): Boolean = insideQuietHours(minute, quietHours)

private fun insideQuietHours(minute: Int, quiet: ReminderQuietHours): Boolean {
    if (!quiet.enabled || quiet.startMinute == quiet.endMinute) return false
    return if (quiet.startMinute < quiet.endMinute) minute >= quiet.startMinute && minute < quiet.endMinute
    else minute >= quiet.startMinute || minute < quiet.endMinute
}

private fun gridMinutes(schedule: ReminderSchedule): List<Int> = when (schedule) {
    is ReminderSchedule.Times -> schedule.minutes
    is ReminderSchedule.Interval -> generateSequence(schedule.startMinute) { it + schedule.minutes }
        .takeWhile { it < schedule.endMinute }.toList()
}

private fun resolve(date: LocalDate, minute: Int, zone: ZoneId): Instant? {
    val local = date.atTime(LocalTime.of(minute / 60, minute % 60))
    val rules = zone.rules
    val offsets = rules.getValidOffsets(local)
    return when {
        offsets.size == 1 -> local.toInstant(offsets[0])
        offsets.size > 1 -> local.toInstant(offsets.first())
        else -> rules.getTransition(local)?.let { transition -> transition.dateTimeAfter.toInstant(transition.offsetAfter) }
    }
}

private fun actionToken(revision: UUID, key: String): UUID = UUID.nameUUIDFromBytes("$revision:$key:LOG_ONE".toByteArray())
