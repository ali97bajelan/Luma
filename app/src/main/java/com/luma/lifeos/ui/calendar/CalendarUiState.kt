package com.luma.lifeos.ui.calendar

import com.luma.lifeos.domain.calendar.CalendarFilter
import com.luma.lifeos.domain.calendar.CalendarMode
import com.luma.lifeos.domain.calendar.CalendarReflections
import com.luma.lifeos.domain.calendar.CalendarTaskMarker
import com.luma.lifeos.domain.habit.HabitDay
import com.luma.lifeos.domain.todo.TodoPage
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

enum class CalendarSource { TASKS, HABITS, JOURNALS }

sealed interface CalendarDataState<out T> {
    data object Loading : CalendarDataState<Nothing>
    data class Ready<T>(val value: T) : CalendarDataState<T>
    data object Error : CalendarDataState<Nothing>
}

data class CalendarUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    val mode: CalendarMode = CalendarMode.MONTH,
    val filter: CalendarFilter = CalendarFilter.ALL,
    val zone: ZoneId = ZoneId.systemDefault(),
    val firstDay: DayOfWeek = DayOfWeek.MONDAY,
    val restoreGeneration: Int = 0,
    val pageLimit: Int = 50,
    val loadingMore: Boolean = false,
    val loadMoreError: Boolean = false,
    val taskPage: CalendarDataState<TodoPage> = CalendarDataState.Loading,
    val taskMarkers: CalendarDataState<Map<LocalDate, CalendarTaskMarker>> = CalendarDataState.Loading,
    val overdueCount: CalendarDataState<Int> = CalendarDataState.Loading,
    val habitMarkers: CalendarDataState<Map<LocalDate, Int>> = CalendarDataState.Loading,
    val habits: CalendarDataState<List<HabitDay>> = CalendarDataState.Loading,
    val journalMarkers: CalendarDataState<Map<LocalDate, Int>> = CalendarDataState.Loading,
    val reflections: CalendarDataState<CalendarReflections> = CalendarDataState.Loading,
) {
    val exactTaskCount: Int?
        get() = (taskMarkers as? CalendarDataState.Ready)?.value?.get(selectedDate)?.count ?: when (taskMarkers) {
            is CalendarDataState.Ready -> 0
            else -> null
        }

    val scheduledTaskCount: Int?
        get() {
            if (filter != CalendarFilter.OVERDUE) {
                (taskPage as? CalendarDataState.Ready)?.value?.total?.let { return it }
            }
            val exact = exactTaskCount ?: return null
            if (selectedDate != today) return exact
            val overdue = overdueTotal ?: return null
            return exact + overdue
        }

    val overdueTotal: Int?
        get() = (overdueCount as? CalendarDataState.Ready)?.value
            ?: if (filter == CalendarFilter.OVERDUE) (taskPage as? CalendarDataState.Ready)?.value?.total else null

    val allItemsCount: Int?
        get() {
            val tasks = scheduledTaskCount ?: return null
            val habitCount = selectedHabitCount ?: return null
            val journalCount = selectedJournalCount ?: return null
            return tasks + habitCount + journalCount
        }

    val selectedHabitCount: Int?
        get() = (habitMarkers as? CalendarDataState.Ready)?.value?.get(selectedDate) ?: when (habitMarkers) {
            is CalendarDataState.Ready -> 0
            else -> null
        }

    val selectedJournalCount: Int?
        get() = (journalMarkers as? CalendarDataState.Ready)?.value?.get(selectedDate) ?: when (journalMarkers) {
            is CalendarDataState.Ready -> 0
            else -> null
        }

    val carryInCount: Int?
        get() = if (selectedDate == today) overdueTotal else 0
}
