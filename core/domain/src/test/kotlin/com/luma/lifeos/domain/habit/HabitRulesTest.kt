package com.luma.lifeos.domain.habit

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitRulesTest {
    private val today = LocalDate.of(2026, 10, 17)
    private val zone = ZoneId.of("Asia/Tehran")
    private val now = Instant.parse("2026-10-17T09:00:00Z")

    @Test fun scheduleForUsesTheLatestEffectiveVersionAndKeepsHistory() {
        val habitId = id(1)
        val todaySchedule = schedule(habitId, today, target = 8)
        val tomorrowSchedule = schedule(habitId, today.plusDays(1), target = 10)

        assertEquals(8, scheduleFor(today, listOf(todaySchedule, tomorrowSchedule))!!.target)
        assertEquals(10, scheduleFor(today.plusDays(1), listOf(todaySchedule, tomorrowSchedule))!!.target)
    }

    @Test fun scheduleForReturnsInactiveAndUnselectedLatestVersions() {
        val habitId = id(1)
        val active = schedule(habitId, today, weekdays = setOf(DayOfWeek.MONDAY))
        val paused = schedule(
            habitId,
            today.plusDays(1),
            weekdays = setOf(DayOfWeek.MONDAY),
            lifecycle = HabitLifecycle.PAUSED,
        )

        val resolved = scheduleFor(today.plusDays(1), listOf(active, paused))

        assertEquals(HabitLifecycle.PAUSED, resolved!!.lifecycle)
        assertFalse(resolved.weekdays.contains(today.plusDays(1).dayOfWeek))
    }

    @Test fun targetProgressKeepsTheActualCountAndSkipIsIndependent() {
        val habit = habit(id(1))
        val schedule = schedule(habit.id, today, target = 8)
        val reached = HabitDay(habit, today, zone, schedule, count = 9, skipRemaining = false)
        val partialAndSkipped = HabitDay(habit, today, zone, schedule, count = 3, skipRemaining = true)

        assertEquals(HabitDayState.TARGET_REACHED, reached.state)
        assertEquals(9, reached.count)
        assertEquals(HabitDayState.PARTIAL, partialAndSkipped.state)
        assertEquals(3, partialAndSkipped.count)
        assertTrue(partialAndSkipped.skipRemaining)
    }

    @Test fun dayStateSeparatesNoRecordsFromNotScheduledPausedAndArchived() {
        val habit = habit(id(1))
        val active = schedule(habit.id, today, target = 2)
        val paused = active.copy(lifecycle = HabitLifecycle.PAUSED)
        val archived = active.copy(lifecycle = HabitLifecycle.ARCHIVED)
        val wrongWeekday = active.copy(weekdays = setOf(today.plusDays(1).dayOfWeek))

        assertEquals(HabitDayState.NO_RECORDS, HabitDay(habit, today, zone, active, 0, false).state)
        assertEquals(HabitDayState.PAUSED, HabitDay(habit, today, zone, paused, 3, false).state)
        assertEquals(HabitDayState.ARCHIVED, HabitDay(habit, today, zone, archived, 3, false).state)
        assertEquals(HabitDayState.NOT_SCHEDULED, HabitDay(habit, today, zone, wrongWeekday, 0, false).state)
        assertEquals(HabitDayState.NOT_SCHEDULED, HabitDay(habit, today, zone, null, 0, false).state)
    }

    @Test fun saveInputNormalizesNamesAndBlankUnitsAndCountsUnicodeCodePoints() {
        val save = HabitSaveInput(
            name = "  Reading  ",
            unitLabel = "  ",
            start = today,
            target = 1,
            weekdays = DayOfWeek.entries.toSet(),
            zone = zone,
        )
        val explicitUnit = HabitSaveInput("  Books  ", "  bottles  ", today, 1, DayOfWeek.entries.toSet(), zone)
        val hundredCodePoints = "📚".repeat(100)
        val thirtyCodePoints = "📚".repeat(30)

        assertEquals("Reading", save.name)
        assertNull(save.unitLabel)
        assertEquals("Books", explicitUnit.name)
        assertEquals("bottles", explicitUnit.unitLabel)
        assertEquals(100, HabitTextLimits.codePointCount(hundredCodePoints))
        assertEquals(30, HabitTextLimits.codePointCount(thirtyCodePoints))
        assertEquals(hundredCodePoints, HabitSaveInput(hundredCodePoints, null, today, 1, weekdays, zone).name)
        assertEquals(thirtyCodePoints, HabitSaveInput("Name", thirtyCodePoints, today, 1, weekdays, zone).unitLabel)
    }

    @Test fun saveAndScheduleInputsRejectInvalidTargetsLabelsAndWeekdays() {
        assertThrows(IllegalArgumentException::class.java) {
            HabitSaveInput("Name", null, today, 0, weekdays, zone)
        }
        assertThrows(IllegalArgumentException::class.java) {
            HabitSaveInput("Name", null, today, 1000, weekdays, zone)
        }
        assertThrows(IllegalArgumentException::class.java) {
            HabitSaveInput("  ", null, today, 1, weekdays, zone)
        }
        assertThrows(IllegalArgumentException::class.java) {
            HabitSaveInput("📚".repeat(101), null, today, 1, weekdays, zone)
        }
        assertThrows(IllegalArgumentException::class.java) {
            HabitSaveInput("Name", "📚".repeat(31), today, 1, weekdays, zone)
        }
        assertThrows(IllegalArgumentException::class.java) {
            HabitSaveInput("Name", null, today, 1, emptySet(), zone)
        }
        assertThrows(IllegalArgumentException::class.java) {
            HabitScheduleInput(1000, weekdays, HabitLifecycle.ACTIVE)
        }
        assertThrows(IllegalArgumentException::class.java) {
            HabitScheduleInput(1, emptySet(), HabitLifecycle.ACTIVE)
        }
    }

    @Test fun dayAndWeekRejectMismatchedHabitIds() {
        val habit = habit(id(1))
        val otherHabitSchedule = schedule(id(2), today)
        val day = assertThrows(IllegalArgumentException::class.java) {
            HabitDay(habit, today, zone, otherHabitSchedule, 0, false)
        }
        assertTrue(day.message.orEmpty().contains("habit"))

        val otherHabitDay = HabitDay(habit(id(2)), today, zone, schedule(id(2), today), 0, false)
        assertThrows(IllegalArgumentException::class.java) {
            HabitWeekRow(habit, listOf(otherHabitDay))
        }
    }

    @Test fun scheduleChangesReplaceOnlyTheSameEffectiveDateAndCanBeCancelled() {
        val habitId = id(1)
        val active = schedule(habitId, today, target = 8)
        val pendingFirst = schedule(habitId, today.plusDays(1), target = 10)
        val pendingReplacement = schedule(habitId, today.plusDays(1), target = 12)
        val withPending = replaceScheduleAtEffectiveDate(listOf(active), pendingFirst, today)
        val replaced = replaceScheduleAtEffectiveDate(withPending, pendingReplacement, today)

        assertEquals(12, scheduleFor(today.plusDays(1), replaced)!!.target)
        assertEquals(2, replaced.size)

        val cancelled = cancelPendingSchedule(replaced, today.plusDays(1), today)
        assertEquals(8, scheduleFor(today.plusDays(1), cancelled)!!.target)
        assertEquals(1, cancelled.size)
        assertThrows(IllegalArgumentException::class.java) {
            cancelPendingSchedule(replaced, today.plusDays(1), today.plusDays(1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            cancelPendingSchedule(listOf(active), today, today)
        }
    }

    @Test fun startedHabitEditsTakeEffectTomorrowAndCannotRewritePastDates() {
        val habitId = id(1)
        val active = schedule(habitId, today, target = 8)
        val pending = scheduleVersion(
            id = id(2), habitId = habitId, start = today, now = now, zone = zone,
            versions = listOf(active), input = HabitScheduleInput(10, weekdays, HabitLifecycle.ACTIVE),
        )
        val versions = replaceScheduleAtEffectiveDate(listOf(active), pending, today)

        assertEquals(today.plusDays(1), pending.effectiveFrom)
        assertEquals(8, scheduleFor(today, versions)!!.target)
        assertEquals(10, scheduleFor(today.plusDays(1), versions)!!.target)
        assertThrows(IllegalArgumentException::class.java) {
            replaceScheduleAtEffectiveDate(versions, active, today)
        }
    }

    @Test fun futureStartEditsKeepInitialDateRejectPauseAndCannotUndoArchive() {
        val start = today.plusDays(20)
        val habitId = id(1)
        val initial = schedule(habitId, start, target = 3)
        val targetEdit = scheduleVersion(
            id = id(2), habitId = habitId, start = start, now = now, zone = zone,
            versions = listOf(initial), input = HabitScheduleInput(5, weekdays, HabitLifecycle.ACTIVE),
        )
        assertEquals(start, targetEdit.effectiveFrom)
        assertThrows(IllegalArgumentException::class.java) {
            scheduleVersion(
                id = id(3), habitId = habitId, start = start, now = now, zone = zone,
                versions = listOf(initial), input = HabitScheduleInput(3, weekdays, HabitLifecycle.PAUSED),
            )
        }

        val archived = scheduleVersion(
            id = id(4), habitId = habitId, start = start, now = now, zone = zone,
            versions = listOf(initial), input = HabitScheduleInput(3, weekdays, HabitLifecycle.ARCHIVED),
        )
        val archivedVersions = replaceScheduleAtEffectiveDate(listOf(initial), archived, today)
        assertEquals(start, archived.effectiveFrom)
        assertEquals(1, archivedVersions.size)
        assertEquals(HabitLifecycle.ARCHIVED, scheduleFor(start, archivedVersions)!!.lifecycle)

        assertThrows(IllegalArgumentException::class.java) {
            scheduleVersion(
                id = id(5), habitId = habitId, start = start, now = now, zone = zone,
                versions = archivedVersions, input = HabitScheduleInput(3, weekdays, HabitLifecycle.ACTIVE),
            )
        }
    }

    @Test fun weekdayMaskUsesMondayAsBitZero() {
        assertEquals(1, weekdaysToMask(setOf(DayOfWeek.MONDAY)))
        assertEquals(64, weekdaysToMask(setOf(DayOfWeek.SUNDAY)))
        assertEquals(DayOfWeek.entries.toSet(), weekdaysFromMask(127))
        assertThrows(IllegalArgumentException::class.java) { weekdaysFromMask(0) }
        assertThrows(IllegalArgumentException::class.java) { weekdaysFromMask(128) }
    }

    private fun habit(id: UUID) = HabitDefinition(
        id = id,
        name = "Reading",
        unitLabel = null,
        start = today,
        createdAt = now,
        updatedAt = now,
    )

    private fun schedule(
        habitId: UUID,
        effectiveFrom: LocalDate,
        target: Int = 1,
        weekdays: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
        lifecycle: HabitLifecycle = HabitLifecycle.ACTIVE,
    ) = HabitSchedule(id(10), habitId, effectiveFrom, target, weekdays, lifecycle)

    private fun id(number: Int) = UUID.fromString("00000000-0000-0000-0000-${number.toString().padStart(12, '0')}")

    private companion object {
        val weekdays = DayOfWeek.entries.toSet()
    }
}
