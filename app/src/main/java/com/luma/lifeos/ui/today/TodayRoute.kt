package com.luma.lifeos.ui.today

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.annotation.StringRes
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
import com.luma.lifeos.ui.habits.HabitsUiState
import com.luma.lifeos.ui.habits.HabitsViewModel
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
    habits: HabitRepository? = null,
    habitsDate: LocalDate? = null,
    deviceDate: LocalDate = date,
    onHabits: () -> Unit = {},
    onCreateHabit: () -> Unit = {},
    onHabit: (UUID, LocalDate) -> Unit = { _, _ -> },
    onCapture: (EventType, LocalDate?) -> Unit = { _, _ -> },
) {
    var retry by remember { mutableIntStateOf(0) }
    var summaryRetry by remember { mutableIntStateOf(0) }
    // Recreate collection state so old-day rows cannot appear under the new date header.
    key(repository, summaries, date, zone) {
        val observation = remember(retry) { observeToday(repository, date, zone) }
        val state by observation.collectAsStateWithLifecycle(initialValue = TodayUiState.Loading)
        val summary = if (summaries != null) {
            val summaryFlow = remember(summaryRetry) { observeDailySummary(summaries, date, zone) }
            val summaryState by summaryFlow.collectAsStateWithLifecycle(initialValue = DailySummaryState.Loading)
            summaryState
        } else null
        if (habits == null) {
            TodayScreen(date, zone, state, onSelectDate, onToday, onRetry = { retry++ },
                onMoodClick = onMoodClick, onJournalClick = onJournalClick, onMemoryClick = onMemoryClick, heading = heading, screenTag = screenTag, onEventClick = onEventClick,
                summaryState = summary, onSummaryRetry = { summaryRetry++ }, onCapture = onCapture)
        } else {
            val habitFactory = remember(habits) {
                viewModelFactory { initializer { HabitsViewModel(habits, createSavedStateHandle()) } }
            }
            val habitViewModel: HabitsViewModel = viewModel(factory = habitFactory)
            val habitsState by habitViewModel.state.collectAsStateWithLifecycle(initialValue = HabitsUiState())
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
                summaryState = summary, onSummaryRetry = { summaryRetry++ }, habitsState = habitsState,
                habitLogCount = habitLogCount, onHabits = onHabits, onCreateHabit = onCreateHabit, onHabit = onHabit,
                onHabitLog = habitViewModel::log, onHabitRetryLog = habitViewModel::retryLog,
                onHabitRetry = habitViewModel::retry, onHabitUndo = habitViewModel::undoLog, onCapture = onCapture)
        }
    }
}
