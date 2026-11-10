package com.luma.lifeos.domain.calendar

import com.luma.lifeos.domain.todo.TodoTask
import com.luma.lifeos.domain.todo.TodoTextLimits
import com.luma.lifeos.domain.todo.openTodoTaskComparator
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

private val firstSupportedDate = LocalDate.of(TodoTextLimits.MIN_YEAR, 1, 1)
private val lastSupportedDate = LocalDate.of(TodoTextLimits.MAX_YEAR, 12, 31)

/** Returns the clipped ISO dates in the selected calendar month or locale-aligned week. */
fun calendarRange(
    selected: LocalDate,
    mode: CalendarMode,
    firstDay: DayOfWeek,
    math: CalendarMath = GregorianCalendarMath,
): CalendarRange {
    val dates = calendarGridDates(selected, mode, firstDay, math)
    val first = dates.firstNotNullOfOrNull { it }
        ?: error("A supported calendar selection must have at least one valid grid date")
    val last = dates.asReversed().firstNotNullOfOrNull { it }
        ?: error("A supported calendar selection must have at least one valid grid date")
    return CalendarRange(first, last)
}

/**
 * Returns month rows or the selected week as ISO dates. Slots outside the supported civil years
 * are null, preserving weekday alignment at the supported range boundaries.
 */
fun calendarGridDates(
    selected: LocalDate,
    mode: CalendarMode,
    firstDay: DayOfWeek,
    math: CalendarMath = GregorianCalendarMath,
): List<LocalDate?> {
    requireCalendarDate(selected, "selection")

    val (gridStart, slotCount) = when (mode) {
        CalendarMode.WEEK -> selected.with(TemporalAdjusters.previousOrSame(firstDay)) to 7
        CalendarMode.MONTH -> {
            val selectedParts = math.fromIso(selected)
            val monthStart = math.toIso(selectedParts.copy(day = 1))
            val monthLength = math.monthLength(selectedParts.year, selectedParts.month)
            val leadingDays = (monthStart.dayOfWeek.value - firstDay.value + 7) % 7
            val slots = ((leadingDays + monthLength + 6) / 7) * 7
            monthStart.minusDays(leadingDays.toLong()) to slots
        }
    }
    check(slotCount in 7..CALENDAR_MAX_RANGE_DAYS && slotCount % 7 == 0) {
        "Calendar grid must contain whole weeks and at most $CALENDAR_MAX_RANGE_DAYS slots"
    }

    return List(slotCount) { index ->
        gridStart.plusDays(index.toLong()).takeIf {
            it.year in TodoTextLimits.MIN_YEAR..TodoTextLimits.MAX_YEAR
        }
    }
}

/**
 * Moves by one active-calendar month or one ISO week, preserving the displayed day where possible
 * and keeping the selected date inside the supported civil-date years.
 */
fun moveCalendarSelection(
    selected: LocalDate,
    mode: CalendarMode,
    delta: Int,
    math: CalendarMath = GregorianCalendarMath,
): LocalDate {
    requireCalendarDate(selected, "selection")
    requireCalendarDelta(delta)

    return movedCalendarSelection(selected, mode, delta, math).coerceInCalendarYears()
}

/** Whether moving one period in [delta] direction stays within ISO years 1–9999. */
fun canMoveCalendarSelection(
    selected: LocalDate,
    mode: CalendarMode,
    delta: Int,
    math: CalendarMath = GregorianCalendarMath,
): Boolean {
    requireCalendarDate(selected, "selection")
    requireCalendarDelta(delta)

    return runCatching {
        movedCalendarSelection(selected, mode, delta, math).year in
            TodoTextLimits.MIN_YEAR..TodoTextLimits.MAX_YEAR
    }.getOrDefault(false)
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

private fun movedCalendarSelection(
    selected: LocalDate,
    mode: CalendarMode,
    delta: Int,
    math: CalendarMath,
): LocalDate = when (mode) {
    CalendarMode.MONTH -> {
        val current = math.fromIso(selected)
        val zeroBasedMonth = current.month - 1 + delta
        val targetYear = when {
            zeroBasedMonth < 0 -> Math.subtractExact(current.year, 1)
            zeroBasedMonth >= 12 -> Math.addExact(current.year, 1)
            else -> current.year
        }
        val targetMonth = Math.floorMod(zeroBasedMonth, 12) + 1
        val targetDay = minOf(current.day, math.monthLength(targetYear, targetMonth))
        math.toIso(CalendarDateParts(targetYear, targetMonth, targetDay))
    }
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
