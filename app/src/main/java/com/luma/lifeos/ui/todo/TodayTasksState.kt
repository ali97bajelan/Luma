package com.luma.lifeos.ui.todo

import com.luma.lifeos.domain.todo.TODO_DEFAULT_TODAY_LIMIT
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

/** Observe the bounded due/overdue preview and its full eligible count. */
fun observeTodayTasks(repository: TodoRepository, date: LocalDate): Flow<TodayTasksState> =
    flow<TodayTasksState> {
        emit(TodayTasksState.Loading)
        repository.observeToday(date, TODO_DEFAULT_TODAY_LIMIT).collect { page ->
            emit(TodayTasksState.Data(page.items.take(TODO_DEFAULT_TODAY_LIMIT), page.total))
        }
    }.catch { failure ->
        if (failure is CancellationException) throw failure
        emit(TodayTasksState.Error)
    }

fun shouldShowTodayTasks(selectedDate: LocalDate, deviceDate: LocalDate): Boolean =
    selectedDate == deviceDate
