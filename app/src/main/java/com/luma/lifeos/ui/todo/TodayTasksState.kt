package com.luma.lifeos.ui.todo

import com.luma.lifeos.domain.todo.TODO_DEFAULT_PAGE_SIZE
import com.luma.lifeos.domain.todo.TodoRepository
import com.luma.lifeos.domain.todo.TodoRowPage
import com.luma.lifeos.domain.todo.TodoTask
import com.luma.lifeos.domain.todo.TodoTaskRow
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow

sealed interface TodayTasksState {
    data object Loading : TodayTasksState
    data object Error : TodayTasksState
    data class Data(
        val tasks: List<TodoTask>,
        val total: Int,
        val rows: List<TodoTaskRow> = tasks.map { TodoTaskRow(it, null, null) },
    ) : TodayTasksState {
        constructor(page: TodoRowPage) : this(page.items.map { it.task }, page.total, page.items)
    }
}

fun TodayTasksState.visibleTaskIds(): Set<UUID> = when (this) {
    is TodayTasksState.Data -> tasks.mapTo(mutableSetOf()) { it.id }
    TodayTasksState.Error, TodayTasksState.Loading -> emptySet()
}

/** Observe a bounded prefix of independently due and overdue tasks for the scrollable Today card. */
fun observeTodayTasks(
    repository: TodoRepository,
    date: LocalDate,
    limit: Int = TODO_DEFAULT_PAGE_SIZE,
): Flow<TodayTasksState> = flow<TodayTasksState> {
    emit(TodayTasksState.Loading)
    repository.observeTodayRows(date, limit).collect { page ->
        emit(TodayTasksState.Data(page))
    }
}.catch { failure ->
    if (failure is CancellationException) throw failure
    emit(TodayTasksState.Error)
}

fun shouldShowTodayTasks(selectedDate: LocalDate, deviceDate: LocalDate): Boolean =
    selectedDate == deviceDate
