package com.luma.lifeos.domain.calendar

import com.luma.lifeos.domain.todo.TODO_DEFAULT_PAGE_SIZE
import com.luma.lifeos.domain.todo.TodoPage
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

/**
 * Read-only Calendar projection of dated tasks and saved memories. Habits and unmarked records
 * never contribute to Calendar. Failures propagate instead of becoming empty results.
 * Dates must be in years 1–9999, ranges contain at most 42 days, and page limits are positive.
 */
interface CalendarRepository {
    /** Exact-date open task markers, without today's overdue carry-in. */
    fun observeTaskMarkers(range: CalendarRange, today: LocalDate): Flow<Map<LocalDate, CalendarTaskMarker>>

    /** Saved memory events grouped into [zone]'s civil days, each original event counted once. */
    fun observeMemoryMarkers(range: CalendarRange, zone: ZoneId): Flow<Map<LocalDate, Int>>

    /**
     * Open dated tasks: today includes overdue carry-in, other dates match exactly.
     * [overdueOnly] selects all open tasks due strictly before [today], independent of [date].
     * Full total and ordered rows share a query snapshot; rows use openTodoTaskComparator order.
     */
    fun observeTasks(
        date: LocalDate,
        today: LocalDate,
        overdueOnly: Boolean,
        limit: Int = TODO_DEFAULT_PAGE_SIZE,
    ): Flow<TodoPage>

    fun observeOverdueCount(today: LocalDate): Flow<Int>

    /**
     * Events with a saved memory row on [date] in [zone], including marked moods/journals.
     * Ordered occurrence descending, event ID ascending; full total and bounded rows share a
     * snapshot. Preview prefers memory note, journal body, mood note, then event note; whitespace
     * is collapsed and limited to [CALENDAR_MEMORY_PREVIEW_CODE_POINTS] Unicode code points.
     */
    fun observeMemories(
        date: LocalDate,
        zone: ZoneId,
        limit: Int = TODO_DEFAULT_PAGE_SIZE,
    ): Flow<CalendarMemories>
}
