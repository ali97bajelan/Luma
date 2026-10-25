package com.luma.lifeos.domain.calendar

import com.luma.lifeos.domain.habit.HabitDay
import com.luma.lifeos.domain.todo.TODO_DEFAULT_PAGE_SIZE
import com.luma.lifeos.domain.todo.TodoPage
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

/**
 * Read-only projection of saved task, habit-schedule and journal data for Calendar.
 * Implementations expose source failures through their flows instead of converting them to empty
 * values, so callers can distinguish unavailable data from a successfully loaded zero. Every
 * [LocalDate] input must be in years 1–9999; implementations reject invalid dates with
 * [IllegalArgumentException]. [requireCalendarDate] is the shared validation helper.
 */
interface CalendarRepository {
    /**
     * Exact-date open task markers with no today carry-in; map keys with no open tasks are omitted.
     * A marker is overdue when one or more tasks for that exact date are overdue as of [today].
     */
    fun observeTaskMarkers(
        range: CalendarRange,
        today: LocalDate,
    ): Flow<Map<LocalDate, CalendarTaskMarker>>

    /**
     * Counts distinct habits with an effective active schedule and matching weekday on each date
     * in [range]; dates without scheduled habits are omitted.
     */
    fun observeHabitMarkers(range: CalendarRange): Flow<Map<LocalDate, Int>>

    /**
     * Counts complete saved manual journals by occurrence date in [zone], including promoted
     * journals once and grouping each occurrence in its local day interval.
     */
    fun observeJournalMarkers(range: CalendarRange, zone: ZoneId): Flow<Map<LocalDate, Int>>

    /**
     * Open dated tasks for the selected date, or the global open overdue set when [overdueOnly].
     * Today's regular section includes due dates through today; other dates require an exact
     * due-date match. [TodoPage.total] is the full matching count and items use
     * [com.luma.lifeos.domain.todo.openTodoTaskComparator] order before paging.
     * Implementations reject non-positive [limit] values with [IllegalArgumentException].
     */
    fun observeTasks(
        date: LocalDate,
        today: LocalDate,
        overdueOnly: Boolean,
        limit: Int = TODO_DEFAULT_PAGE_SIZE,
    ): Flow<TodoPage>

    /** Full global count of open tasks with due date strictly before [today]. */
    fun observeOverdueCount(today: LocalDate): Flow<Int>

    /** Active habits scheduled on [date], including target-reached and skipped habits. */
    fun observeHabits(date: LocalDate): Flow<List<HabitDay>>

    /**
     * Complete saved manual journals for [date] in [zone], including journals promoted to memory.
     * The latest uses occurrence descending then event ID ascending; its saved-body preview is at
     * most $CALENDAR_REFLECTION_PREVIEW_CODE_POINTS Unicode code points with whitespace collapsed.
     * A successful empty result is [CalendarReflections] with total zero and no latest reflection.
     */
    fun observeReflections(date: LocalDate, zone: ZoneId): Flow<CalendarReflections>
}
