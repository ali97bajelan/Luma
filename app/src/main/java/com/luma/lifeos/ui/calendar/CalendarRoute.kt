package com.luma.lifeos.ui.calendar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.luma.lifeos.domain.calendar.CalendarRepository
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.todo.TodoRepository
import com.luma.lifeos.ui.todo.TodayTasksActionState
import com.luma.lifeos.ui.todo.TodayTasksViewModel
import com.luma.lifeos.ui.todo.TodoActionResult
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.UUID

@Composable
fun CalendarRoute(
    repository: CalendarRepository,
    todos: TodoRepository,
    today: LocalDate,
    zone: ZoneId,
    restoreGeneration: Int,
    onTask: (UUID) -> Unit,
    onDeleteTask: (UUID) -> Unit,
    onAddTask: (LocalDate) -> Unit,
    onSchedule: (LocalDate) -> Unit,
    onMemory: (EventId) -> Unit,
    onAddMemory: (LocalDate) -> Unit,
    onAllTasks: () -> Unit,
    onTaskActionResult: (TodoActionResult) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0] ?: Locale.getDefault()
    val firstDay = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
    val calendarFactory = remember(repository) {
        viewModelFactory { initializer { CalendarViewModel(repository, createSavedStateHandle()) } }
    }
    val calendarViewModel: CalendarViewModel = viewModel(factory = calendarFactory)
    val state by calendarViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(today, zone, firstDay, restoreGeneration) {
        calendarViewModel.setEnvironment(today, zone, firstDay, restoreGeneration)
    }

    val taskFactory = remember(todos) {
        viewModelFactory { initializer { TodayTasksViewModel(todos, createSavedStateHandle()) } }
    }
    val taskViewModel: TodayTasksViewModel = viewModel(factory = taskFactory)
    val actions by taskViewModel.state.collectAsStateWithLifecycle()
    val handledRestore = rememberSaveable { mutableStateOf(restoreGeneration) }
    val latestResultCallback by rememberUpdatedState(onTaskActionResult)
    LaunchedEffect(restoreGeneration) {
        if (handledRestore.value != restoreGeneration) {
            handledRestore.value = restoreGeneration
            taskViewModel.invalidateAfterRestore()
        }
    }
    LaunchedEffect(actions.actionResult) {
        actions.actionResult?.let { result ->
            latestResultCallback(result)
            taskViewModel.consumeActionResult(result)
        }
    }

    CalendarScreen(
        state = state,
        actions = actions,
        displayTime = com.luma.lifeos.language.rememberDisplayTimePolicy(),
        onSelectDate = calendarViewModel::selectDate,
        onToday = calendarViewModel::goToToday,
        onSetMode = calendarViewModel::setMode,
        onMovePeriod = calendarViewModel::movePeriod,
        onFilter = calendarViewModel::setFilter,
        onRetry = calendarViewModel::retry,
        onLoadMore = calendarViewModel::loadMore,
        onLoadMoreMemories = calendarViewModel::loadMoreMemories,
        onTask = onTask,
        onDeleteTask = onDeleteTask,
        onAddTask = onAddTask,
        onSchedule = onSchedule,
        onSetCompleted = taskViewModel::setCompleted,
        onRetryTask = taskViewModel::retry,
        onDismissTaskError = taskViewModel::dismissRowError,
        onMemory = onMemory,
        onAddMemory = onAddMemory,
        onAllTasks = onAllTasks,
    )
}
