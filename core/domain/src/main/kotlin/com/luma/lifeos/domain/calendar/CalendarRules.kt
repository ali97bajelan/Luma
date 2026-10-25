package com.luma.lifeos.domain.calendar

import com.luma.lifeos.domain.todo.TodoTask
import com.luma.lifeos.domain.todo.TodoTextLimits
import com.luma.lifeos.domain.todo.openTodoTaskComparator
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

private val firstSupportedDate = LocalDate.of(TodoTextLimits.MIN_YEAR, 1, 1)
private val lastSupportedDate = LocalDate.of(TodoTextLimits.MAX_YEAR, 12, 31)

/** Returns the valid dates in the selected month or locale-aligned week. */
fun calendarRange(
    selected: LocalDate,
    mode: CalendarMode,
    firstDay: DayOfWeek,
): CalendarRange {
    requireCalendarDate(selected, "selection")

    return when (mode) {
        CalendarMode.MONTH -> {
            val firstOfMonth = selected.withDayOfMonth(1)
            val lastOfMonth = selected.withDayOfMonth(selected.lengthOfMonth())
            val firstInGrid = firstOfMonth.with(TemporalAdjusters.previousOrSame(firstDay))
            val lastInGrid = lastOfMonth.with(TemporalAdjusters.nextOrSame(firstDay.minus(1L)))

            CalendarRange(
                start = firstInGrid.coerceInCalendarYears(),
                through = lastInGrid.coerceInCalendarYears(),
            )
        }

        CalendarMode.WEEK -> {
            val firstInWeek = selected.with(TemporalAdjusters.previousOrSame(firstDay))
            CalendarRange(
                start = firstInWeek.coerceInCalendarYears(),
                through = firstInWeek.plusDays(6).coerceInCalendarYears(),
            )
        }
    }
}

/**
 * Moves by one month or week, preserving LocalDate's month-end clamping and keeping the result
 * inside the supported civil-date years.
 */
fun moveCalendarSelection(selected: LocalDate, mode: CalendarMode, delta: Int): LocalDate {
    requireCalendarDate(selected, "selection")
    requireCalendarDelta(delta)

    val candidate = movedCalendarSelection(selected, mode, delta)
    return candidate.coerceInCalendarYears()
}

/** Whether moving one period in [delta] direction stays within years 1–9999. */
fun canMoveCalendarSelection(selected: LocalDate, mode: CalendarMode, delta: Int): Boolean {
    requireCalendarDate(selected, "selection")
    requireCalendarDelta(delta)

    return movedCalendarSelection(selected, mode, delta).year in
        TodoTextLimits.MIN_YEAR..TodoTextLimits.MAX_YEAR
}

/**
 * True when an open, dated task belongs in the selected agenda section. Overdue is always global
 * to [today]; only today's regular section carries open tasks forward from earlier dates.
 */
fun calendarTaskEligible(
    task: TodoTask,
    selected: LocalDate,
    today: LocalDate,
    overdueOnly: Boolean,
): Boolean {
    requireCalendarDate(selected, "selection")
    requireCalendarDate(today, "today")

    val dueDate = task.dueDate ?: return false
    if (task.isCompleted) return false

    return if (overdueOnly) {
        dueDate.isBefore(today)
    } else if (selected == today) {
        !dueDate.isAfter(today)
    } else {
        dueDate == selected
    }
}

/** Sorts a bounded Calendar task result with the app's canonical open-task order. */
fun orderCalendarTasks(tasks: Iterable<TodoTask>): List<TodoTask> =
    tasks.sortedWith(openTodoTaskComparator)

/** Shared guard for repository implementations before creating a page observation. */
fun requireCalendarTaskLimit(limit: Int) {
    require(limit > 0) { "Calendar task limit must be positive" }
}

private fun movedCalendarSelection(selected: LocalDate, mode: CalendarMode, delta: Int): LocalDate =
    when (mode) {
        CalendarMode.MONTH -> selected.plusMonths(delta.toLong())
        CalendarMode.WEEK -> selected.plusWeeks(delta.toLong())
    }

private fun requireCalendarDelta(delta: Int) {
    require(delta == -1 || delta == 1) { "Calendar period delta must be -1 or 1" }
}

private fun LocalDate.coerceInCalendarYears(): LocalDate = when {
    isBefore(firstSupportedDate) -> firstSupportedDate
    isAfter(lastSupportedDate) -> lastSupportedDate
    else -> this
}
