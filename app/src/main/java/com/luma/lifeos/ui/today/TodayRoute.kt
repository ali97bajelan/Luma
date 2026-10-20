package com.luma.lifeos.ui.today

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luma.lifeos.domain.repository.LifeEventRepository
import com.luma.lifeos.domain.repository.DailySummaryRepository
import com.luma.lifeos.ui.summary.DailySummaryState
import com.luma.lifeos.ui.summary.observeDailySummary
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.EventType
import com.luma.lifeos.R
import java.time.LocalDate
import java.time.ZoneId
import com.luma.lifeos.domain.habit.HabitDay
import com.luma.lifeos.domain.habit.HabitRepository
import com.luma.lifeos.domain.todo.TodoRepository
import com.luma.lifeos.domain.todo.TodoTask
import com.luma.lifeos.ui.habits.HabitsUiState
import com.luma.lifeos.ui.habits.HabitsViewModel
import com.luma.lifeos.ui.habits.HabitLogSnackbarFeedback
import com.luma.lifeos.ui.todo.TodayTasksActionState
import com.luma.lifeos.ui.todo.TodayTasksState
import com.luma.lifeos.ui.todo.TodayTasksViewModel
import com.luma.lifeos.ui.todo.TodoActionResult
import com.luma.lifeos.ui.todo.observeTodayTasks
import com.luma.lifeos.ui.todo.shouldShowTodayTasks
import com.luma.lifeos.ui.integrations.IntegrationAvailabilityState
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

@Composable
fun TodayRoute(
    repository: LifeEventRepository,
    date: LocalDate,
    zone: ZoneId,
    onSelectDate: (LocalDate) -> Unit,
    onToday: () -> Unit,
    onMoodClick: (EventId) -> Unit = {},
    onJournalClick: (EventId) -> Unit = {},
    onMemoryClick: (EventId) -> Unit = {},
    @StringRes heading: Int = R.string.today,
    screenTag: String = "screen_today",
    onEventClick: (EventId) -> Unit = {},
    summaries: DailySummaryRepository? = null,
    integrationAvailability: IntegrationAvailabilityState = IntegrationAvailabilityState(),
    onIntegrations: () -> Unit = {},
    onCheckIn: () -> Unit = {},
    habits: HabitRepository? = null,
    habitsDate: LocalDate? = null,
    deviceDate: LocalDate = date,
    onHabits: () -> Unit = {},
    onCreateHabit: () -> Unit = {},
    onHabit: (UUID, LocalDate) -> Unit = { _, _ -> },
    onCapture: (EventType, LocalDate?) -> Unit = { _, _ -> },
    todos: TodoRepository? = null,
    restoreGeneration: Int = 0,
    onTask: (UUID) -> Unit = {},
    onAllTasks: () -> Unit = {},
    onAddTask: (LocalDate) -> Unit = {},
    onTaskActionResult: (TodoActionResult) -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    onHabitOpenDate: (LocalDate) -> Unit = {},
) {
    var retry by remember { mutableIntStateOf(0) }
    var summaryRetry by remember { mutableIntStateOf(0) }
    var todoRetry by remember { mutableIntStateOf(0) }
    val todayTasksViewModel = if (todos != null) {
        val tasksFactory = remember(todos) {
            viewModelFactory { initializer { TodayTasksViewModel(todos, createSavedStateHandle()) } }
        }
        viewModel<TodayTasksViewModel>(factory = tasksFactory)
    } else null
    val todayTaskActions: TodayTasksActionState
    if (todayTasksViewModel != null) {
        val taskActions by todayTasksViewModel.state.collectAsStateWithLifecycle()
        val handledRestore = rememberSaveable { mutableStateOf(restoreGeneration) }
        LaunchedEffect(restoreGeneration) {
            if (handledRestore.value != restoreGeneration) {
                handledRestore.value = restoreGeneration
                todayTasksViewModel.invalidateAfterRestore()
            }
        }
        LaunchedEffect(taskActions.actionResult) {
            taskActions.actionResult?.let { result ->
                onTaskActionResult(result)
                todayTasksViewModel.consumeActionResult(result)
            }
        }
        todayTaskActions = taskActions
    } else {
        todayTaskActions = TodayTasksActionState()
    }
    // Recreate collection state so old-day rows cannot appear under the new date header.
    key(repository, summaries, date, zone) {
        val observation = remember(retry) { observeToday(repository, date, zone) }
        val state by observation.collectAsStateWithLifecycle(initialValue = TodayUiState.Loading)
        val summary = if (summaries != null) {
            val summaryFlow = remember(summaryRetry) { observeDailySummary(summaries, date, zone) }
            val summaryState by summaryFlow.collectAsStateWithLifecycle(initialValue = DailySummaryState.Loading)
            summaryState
        } else null
        val todayTasks = if (todos != null && shouldShowTodayTasks(date, deviceDate)) {
            val tasksFlow = remember(todos, deviceDate, todoRetry) { observeTodayTasks(todos, deviceDate) }
            val tasksState by tasksFlow.collectAsStateWithLifecycle(initialValue = TodayTasksState.Loading)
            tasksState
        } else null
        if (habits == null) {
            TodayScreen(date, zone, state, onSelectDate, onToday, onRetry = { retry++ },
                onMoodClick = onMoodClick, onJournalClick = onJournalClick, onMemoryClick = onMemoryClick, heading = heading, screenTag = screenTag, onEventClick = onEventClick,
                summaryState = summary, onSummaryRetry = { summaryRetry++ }, onCapture = onCapture,
                integrationAvailability = integrationAvailability, onIntegrations = onIntegrations, onCheckIn = onCheckIn,
                todayTasks = todayTasks, todayTaskActions = todayTaskActions, onTask = onTask, onSetTaskCompleted = { task, completed ->
                    todayTasksViewModel?.setCompleted(task, completed)
                }, onAllTasks = onAllTasks, onAddTask = { onAddTask(deviceDate) }, onRetryTasks = { todoRetry++ },
                onRetryTask = { todayTasksViewModel?.retry(it) }, onDismissTaskError = { todayTasksViewModel?.dismissRowError(it) })
        } else {
            val habitFactory = remember(habits) {
                viewModelFactory { initializer { HabitsViewModel(habits, createSavedStateHandle()) } }
            }
            val habitViewModel: HabitsViewModel = viewModel(factory = habitFactory)
            val habitsState by habitViewModel.state.collectAsStateWithLifecycle(initialValue = HabitsUiState())
            HabitLogSnackbarFeedback(
                state = habitsState,
                snackbarHostState = snackbarHostState,
                onUndo = habitViewModel::undoLog,
                onAcknowledge = habitViewModel::acknowledgeUndoLog,
                onRetry = habitViewModel::undoLog,
            )
            // Hide the previous selection even in the render before LaunchedEffect invalidates it.
            val displayedHabits = if (habitsState.deviceDate != deviceDate || habitsState.deviceZone != zone ||
                (habitsDate != null && habitsState.date != habitsDate)) {
                habitsState.copy(days = emptyList(), loadedDate = null, loading = true, error = null)
            } else habitsState
            // The habit preview can follow its tracking-zone date; counts belong to the selected date.
            val logCounts = remember(habits, date, retry, summaryRetry, habitsState.loading) {
                habits.observeDay(date).map<List<HabitDay>, Int?> { days -> days.sumOf { it.count } }.catch { failure ->
                    if (failure is CancellationException) throw failure
                    emit(null)
                }
            }
            val habitLogCount by logCounts.collectAsStateWithLifecycle(initialValue = null)
            LaunchedEffect(habitsDate, deviceDate, zone) { habitViewModel.selectDate(habitsDate, zone, deviceDate) }
            TodayScreen(date, zone, state, onSelectDate, onToday, onRetry = { retry++ },
                onMoodClick = onMoodClick, onJournalClick = onJournalClick, onMemoryClick = onMemoryClick, heading = heading, screenTag = screenTag, onEventClick = onEventClick,
                summaryState = summary, onSummaryRetry = { summaryRetry++ }, habitsState = displayedHabits,
                integrationAvailability = integrationAvailability, onIntegrations = onIntegrations, onCheckIn = onCheckIn,
                habitLogCount = habitLogCount, onHabits = onHabits, onCreateHabit = onCreateHabit,
                onHabit = { id, rowDate -> displayedHabits.rowsFor(rowDate).firstOrNull { it.day.habit.id == id }?.let { row ->
                    habitViewModel.dateForRow(row)?.let { onHabit(id, it) }
                } },
                onHabitLog = { id -> displayedHabits.date?.let { selected ->
                    displayedHabits.rowsFor(selected).firstOrNull { it.day.habit.id == id }?.let(habitViewModel::log)
                } }, onHabitRetryLog = habitViewModel::retryLog,
                onHabitRetry = habitViewModel::retry, onHabitUndo = habitViewModel::undoLog,
                onHabitOpenDate = onHabitOpenDate, onCapture = onCapture,
                todayTasks = todayTasks, todayTaskActions = todayTaskActions, onTask = onTask, onSetTaskCompleted = { task, completed ->
                    todayTasksViewModel?.setCompleted(task, completed)
                }, onAllTasks = onAllTasks, onAddTask = { onAddTask(deviceDate) }, onRetryTasks = { todoRetry++ },
                onRetryTask = { todayTasksViewModel?.retry(it) }, onDismissTaskError = { todayTasksViewModel?.dismissRowError(it) })
        }
    }
}
