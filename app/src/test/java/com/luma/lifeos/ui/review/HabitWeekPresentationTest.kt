package com.luma.lifeos.ui.review

import com.luma.lifeos.domain.habit.HabitDay
import com.luma.lifeos.domain.habit.HabitDefinition
import com.luma.lifeos.domain.habit.HabitLifecycle
import com.luma.lifeos.domain.habit.HabitSchedule
import com.luma.lifeos.domain.habit.HabitWeekComparisonRow
import com.luma.lifeos.domain.habit.HabitWeekRow
import com.luma.lifeos.domain.habit.HabitDayState
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitWeekPresentationTest {
    private val start = LocalDate.parse("2026-10-12") // Monday
    private val zone = ZoneOffset.UTC
    private val habit = HabitDefinition(
        id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        name = "Water",
        unitLabel = "glasses",
        start = start.minusWeeks(1),
        createdAt = Instant.parse("2026-10-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-10-01T00:00:00Z"),
    )
    private val mondayOnlySchedule = HabitSchedule(
        id = UUID.fromString("00000000-0000-0000-0000-000000000002"),
        habitId = habit.id,
        effectiveFrom = start.minusWeeks(1),
        target = 8,
        weekdays = setOf(DayOfWeek.MONDAY),
        lifecycle = HabitLifecycle.ACTIVE,
    )

    @Test fun partialWeekHasSevenChronologicalSlotsAndLeavesFutureDaysUpcoming() {
        val through = start.plusDays(2)
        val current = HabitWeekRow(habit, (0L..2L).map { offset ->
            val date = start.plusDays(offset)
            HabitDay(habit, date, zone, mondayOnlySchedule, count = if (offset == 0L) 8 else 0, skipRemaining = false)
        })

        val slots = habitWeekSlots(start, through, HabitWeekComparisonRow(habit, current, previous = null))

        assertEquals((0L..6L).map(start::plusDays), slots.map { it.date })
        assertEquals(listOf(false, false, false, true, true, true, true), slots.map { it.upcoming })
        assertEquals(HabitDayState.NOT_SCHEDULED, slots[1].day?.state)
        assertTrue(slots.drop(3).all { it.day == null })
    }

    @Test fun previousOnlyHabitDoesNotInventCurrentWeekDaysOrTargets() {
        val previousStart = start.minusWeeks(1)
        val previous = HabitWeekRow(habit, (0L..6L).map { offset ->
            val date = previousStart.plusDays(offset)
            HabitDay(habit, date, zone, mondayOnlySchedule, count = 0, skipRemaining = false)
        })

        val slots = habitWeekSlots(
            start,
            start.plusDays(2),
            HabitWeekComparisonRow(habit, current = null, previous = previous),
        )

        assertTrue(slots.all { it.day == null })
        assertFalse(slots.take(3).any { it.upcoming })
        assertTrue(slots.take(3).all { it.day == null })
    }
}
