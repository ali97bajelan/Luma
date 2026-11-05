package com.luma.lifeos.ui.todo

import com.luma.lifeos.domain.todo.TodoRepository
import com.luma.lifeos.domain.todo.TodoTask
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow

sealed interface TodayTasksState {
    data object Loading : TodayTasksState
    data object Error : TodayTasksState
    data class Data(val tasks: List<TodoTask>, val total: Int) : TodayTasksState
}

fun TodayTasksState.visibleTaskIds(): Set<UUID> = when (this) {
    is TodayTasksState.Data -> tasks.mapTo(mutableSetOf()) { it.id }
    TodayTasksState.Error, TodayTasksState.Loading -> emptySet()
}

/** Observe all due and overdue tasks for the scrollable Today card. */
fun observeTodayTasks(repository: TodoRepository, date: LocalDate): Flow<TodayTasksState> =
    flow<TodayTasksState> {
        emit(TodayTasksState.Loading)
        repository.observeToday(date, Int.MAX_VALUE).collect { page ->
            emit(TodayTasksState.Data(page.items, page.total))
        }
    }.catch { failure ->
        if (failure is CancellationException) throw failure
        emit(TodayTasksState.Error)
    }

fun shouldShowTodayTasks(selectedDate: LocalDate, deviceDate: LocalDate): Boolean =
    selectedDate == deviceDate
