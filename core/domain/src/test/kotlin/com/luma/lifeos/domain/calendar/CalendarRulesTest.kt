package com.luma.lifeos.domain.calendar

import com.luma.lifeos.domain.todo.TodoTask
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarRulesTest {
    @Test fun monthRangesContainFourFiveAndSixCompleteRows() {
        assertEquals(
            CalendarRange(LocalDate.parse("2021-02-01"), LocalDate.parse("2021-02-28")),
            calendarRange(LocalDate.parse("2021-02-22"), CalendarMode.MONTH, DayOfWeek.MONDAY),
        )
        assertEquals(
            CalendarRange(LocalDate.parse("2026-09-28"), LocalDate.parse("2026-11-01")),
            calendarRange(LocalDate.parse("2026-10-22"), CalendarMode.MONTH, DayOfWeek.MONDAY),
        )
        assertEquals(
            CalendarRange(LocalDate.parse("2020-07-27"), LocalDate.parse("2020-09-06")),
            calendarRange(LocalDate.parse("2020-08-12"), CalendarMode.MONTH, DayOfWeek.MONDAY),
        )
    }

    @Test fun monthNavigationClampsAtFebruaryAndKeepsTheClampedDay() {
        val jan2027 = LocalDate.parse("2027-01-31")
        assertEquals(LocalDate.parse("2027-02-28"), moveCalendarSelection(jan2027, CalendarMode.MONTH, 1))
        assertEquals(LocalDate.parse("2027-03-28"), moveCalendarSelection(
            moveCalendarSelection(jan2027, CalendarMode.MONTH, 1), CalendarMode.MONTH, 1,
        ))
        assertEquals(LocalDate.parse("2027-02-28"), moveCalendarSelection(
            LocalDate.parse("2027-03-28"), CalendarMode.MONTH, -1,
        ))

        val jan2028 = LocalDate.parse("2028-01-31")
        assertEquals(LocalDate.parse("2028-02-29"), moveCalendarSelection(jan2028, CalendarMode.MONTH, 1))
        assertEquals(LocalDate.parse("2028-03-29"), moveCalendarSelection(
            moveCalendarSelection(jan2028, CalendarMode.MONTH, 1), CalendarMode.MONTH, 1,
        ))
        val leapRange = calendarRange(
            LocalDate.parse("2000-02-15"), CalendarMode.MONTH, DayOfWeek.MONDAY,
        )
        assertTrue(datesIn(leapRange).contains(LocalDate.parse("2000-02-29")))
        val nonLeapRange = calendarRange(
            LocalDate.parse("2100-02-15"), CalendarMode.MONTH, DayOfWeek.MONDAY,
        )
        assertEquals(28, datesIn(nonLeapRange).count { it.year == 2100 && it.monthValue == 2 })
    }

    @Test fun weekRangeAndMovementUseSevenCivilDatesAcrossYearBoundary() {
        val selected = LocalDate.parse("2026-12-31")
        assertEquals(
            CalendarRange(LocalDate.parse("2026-12-28"), LocalDate.parse("2027-01-03")),
            calendarRange(selected, CalendarMode.WEEK, DayOfWeek.MONDAY),
        )
        assertEquals(LocalDate.parse("2027-01-07"), moveCalendarSelection(selected, CalendarMode.WEEK, 1))
        assertEquals(selected, moveCalendarSelection(
            moveCalendarSelection(selected, CalendarMode.WEEK, 1), CalendarMode.WEEK, -1,
        ))
    }

    @Test fun weekStartsFollowEnglishFrenchAndPersianLocaleFields() {
        val selected = LocalDate.parse("2026-10-22")
        val cases = listOf(
            Locale.US to CalendarRange(LocalDate.parse("2026-10-18"), LocalDate.parse("2026-10-24")),
            Locale.FRANCE to CalendarRange(LocalDate.parse("2026-10-19"), LocalDate.parse("2026-10-25")),
            Locale.forLanguageTag("fa") to CalendarRange(LocalDate.parse("2026-10-18"), LocalDate.parse("2026-10-24")),
        )
        cases.forEach { (locale, expected) ->
            val firstDay = WeekFields.of(locale).firstDayOfWeek
            assertEquals(expected, calendarRange(selected, CalendarMode.WEEK, firstDay))
        }
    }

    @Test fun rangesAndMovementClipAtSupportedYearBounds() {
        val first = LocalDate.of(1, 1, 1)
        val last = LocalDate.of(9999, 12, 31)
        assertEquals(CalendarRange(first, LocalDate.of(1, 2, 4)),
            calendarRange(first, CalendarMode.MONTH, DayOfWeek.MONDAY))
        assertEquals(CalendarRange(LocalDate.of(9999, 11, 29), last),
            calendarRange(last, CalendarMode.MONTH, DayOfWeek.MONDAY))
        assertFalse(canMoveCalendarSelection(first, CalendarMode.MONTH, -1))
        assertFalse(canMoveCalendarSelection(last, CalendarMode.MONTH, 1))
        assertEquals(first, moveCalendarSelection(first, CalendarMode.MONTH, -1))
        assertEquals(last, moveCalendarSelection(last, CalendarMode.MONTH, 1))
        assertThrows(IllegalArgumentException::class.java) {
            calendarRange(LocalDate.of(10000, 1, 1), CalendarMode.MONTH, DayOfWeek.MONDAY)
        }
    }

    @Test fun taskMembershipUsesCivilDatesAndGlobalOverdueScope() {
        val today = LocalDate.parse("2026-10-22")
        val yesterday = task("00000000-0000-0000-0000-000000000001", today.minusDays(1))
        val older = task("00000000-0000-0000-0000-000000000006", today.minusDays(3))
        val todayMorning = task("00000000-0000-0000-0000-000000000002", today, LocalTime.of(9, 0))
        val tomorrow = task("00000000-0000-0000-0000-000000000003", today.plusDays(1), LocalTime.MIDNIGHT)
        val completedPast = task(
            "00000000-0000-0000-0000-000000000004", today.minusDays(2), completedAt = Instant.parse("2026-10-20T08:00:00Z"),
        )
        val undated = task("00000000-0000-0000-0000-000000000005", null)

        assertTrue(calendarTaskEligible(yesterday, today, today, overdueOnly = false))
        assertTrue(calendarTaskEligible(yesterday, today.plusDays(1), today, overdueOnly = true))
        assertFalse(calendarTaskEligible(older, today.minusDays(1), today, overdueOnly = false))
        assertTrue(calendarTaskEligible(todayMorning, today, today, overdueOnly = false))
        assertFalse(calendarTaskEligible(todayMorning, today, today, overdueOnly = true))
        assertFalse(calendarTaskEligible(tomorrow, today, today, overdueOnly = false))
        assertTrue(calendarTaskEligible(tomorrow, today.plusDays(1), today, overdueOnly = false))
        assertFalse(calendarTaskEligible(completedPast, today, today, overdueOnly = true))
        assertFalse(calendarTaskEligible(undated, today, today, overdueOnly = false))
    }

    @Test fun taskOrderingPlacesTimedTasksBeforeDateOnlyAndUsesStableTies() {
        val day = LocalDate.parse("2026-10-22")
        val late = task("00000000-0000-0000-0000-000000000005", day, LocalTime.of(23, 59))
        val tiedLaterId = task("00000000-0000-0000-0000-000000000004", day, LocalTime.of(9, 5))
        val tiedEarlierId = task("00000000-0000-0000-0000-000000000003", day, LocalTime.of(9, 5))
        val createdFirst = task(
            "00000000-0000-0000-0000-000000000006", day, LocalTime.of(9, 5),
            createdAt = Instant.parse("2026-09-30T00:00:00Z"),
        )
        val midnight = task("00000000-0000-0000-0000-000000000002", day, LocalTime.MIDNIGHT)
        val dateOnly = task("00000000-0000-0000-0000-000000000001", day)

        assertEquals(
            listOf(midnight, createdFirst, tiedEarlierId, tiedLaterId, late, dateOnly).map(TodoTask::id),
            orderCalendarTasks(listOf(dateOnly, late, tiedLaterId, midnight, tiedEarlierId, createdFirst)).map(TodoTask::id),
        )
    }

    @Test fun taskPageLimitsMustBePositiveAndPeriodMovesMustBeSingleStep() {
        assertThrows(IllegalArgumentException::class.java) { requireCalendarTaskLimit(0) }
        assertThrows(IllegalArgumentException::class.java) {
            moveCalendarSelection(LocalDate.parse("2026-10-22"), CalendarMode.WEEK, 2)
        }
    }

    private fun task(
        id: String,
        dueDate: LocalDate?,
        dueTime: LocalTime? = null,
        completedAt: Instant? = null,
        createdAt: Instant = Instant.parse("2026-10-01T00:00:00Z"),
    ) = TodoTask(
        id = UUID.fromString(id),
        title = "Task ${id.takeLast(2)}",
        note = null,
        dueDate = dueDate,
        completedAt = completedAt,
        createdAt = createdAt,
        updatedAt = createdAt,
        revision = UUID.fromString("10000000-0000-0000-0000-${id.takeLast(12)}"),
        dueTime = dueTime,
    )

    private fun datesIn(range: CalendarRange): List<LocalDate> = buildList {
        var date = range.start
        while (!date.isAfter(range.through)) {
            add(date)
            date = date.plusDays(1)
        }
    }
}
