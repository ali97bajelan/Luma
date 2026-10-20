package com.luma.lifeos.ui.today

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.EventType
import com.luma.lifeos.domain.model.LifeEventRecord
import com.luma.lifeos.ui.timeline.DayNavigation
import com.luma.lifeos.ui.timeline.EventCard
import com.luma.lifeos.ui.common.entryLogCounts
import com.luma.lifeos.ui.common.DetailsDisclosure
import com.luma.lifeos.ui.common.CaptureDateChoice
import com.luma.lifeos.ui.summary.DailySummaryState
import com.luma.lifeos.ui.summary.DailySummaryCard
import java.time.LocalDate
import java.time.ZoneId
import com.luma.lifeos.ui.habits.HabitsUiState
import com.luma.lifeos.ui.habits.TodayHabitsCard
import com.luma.lifeos.ui.integrations.IntegrationAvailabilityState
import com.luma.lifeos.domain.todo.TodoTask
import com.luma.lifeos.ui.todo.TodayTasksActionState
import com.luma.lifeos.ui.todo.TodayTasksCard
import com.luma.lifeos.ui.todo.TodayTasksState
import java.util.UUID

@Composable
fun TodayScreen(
    date: LocalDate,
    zone: ZoneId,
    state: TodayUiState,
    onSelectDate: (LocalDate) -> Unit,
    onToday: () -> Unit,
    onRetry: () -> Unit,
    onMoodClick: (EventId) -> Unit = {},
    onJournalClick: (EventId) -> Unit = {},
    onMemoryClick: (EventId) -> Unit = {},
    @StringRes heading: Int = R.string.today,
    screenTag: String = "screen_today",
    onEventClick: (EventId) -> Unit = {},
    summaryState: DailySummaryState? = null,
    onSummaryRetry: () -> Unit = {},
    integrationAvailability: IntegrationAvailabilityState = IntegrationAvailabilityState(),
    onIntegrations: () -> Unit = {},
    habitsState: HabitsUiState? = null,
    habitLogCount: Int? = null,
    onHabits: () -> Unit = {},
    onCreateHabit: () -> Unit = {},
    onHabit: (UUID, LocalDate) -> Unit = { _, _ -> },
    onHabitLog: (UUID) -> Unit = {},
    onHabitRetryLog: (UUID) -> Unit = {},
    onHabitRetry: () -> Unit = {},
    onHabitUndo: (UUID) -> Unit = {},
    onCapture: (EventType, LocalDate?) -> Unit = { _, _ -> },
    todayTasks: TodayTasksState? = null,
    todayTaskActions: TodayTasksActionState = TodayTasksActionState(),
    onTask: (UUID) -> Unit = {},
    onSetTaskCompleted: (TodoTask, Boolean) -> Unit = { _, _ -> },
    onAllTasks: () -> Unit = {},
    onAddTask: () -> Unit = {},
    onRetryTasks: () -> Unit = {},
    onRetryTask: (UUID) -> Unit = {},
    onDismissTaskError: (UUID) -> Unit = {},
    onHabitOpenDate: (LocalDate) -> Unit = {},
) {
    var choosingDateFor by rememberSaveable(date) { mutableStateOf<EventType?>(null) }
    var choosingCaptureDate by rememberSaveable(date) { mutableStateOf<String?>(null) }
    fun requestCapture(type: EventType, captureDate: LocalDate) {
        if (captureDate == LocalDate.now(zone)) onCapture(type, null)
        else {
            choosingDateFor = type
            choosingCaptureDate = captureDate.toString()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(screenTag),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            Column {
                Text(stringResource(heading), style = MaterialTheme.typography.titleLarge)
                DayNavigation(date, onSelectDate, onToday)
            }
        }
        item(key = "capture") {
            TodayCaptureActions(onDateCapture = { requestCapture(it, date) }, empty = state == TodayUiState.Empty)
        }
        habitsState?.let { item(key = "habits") {
            TodayHabitsCard(it, date, zone, onHabits, onCreateHabit, onHabit, onHabitLog, onHabitRetryLog, onHabitUndo, onHabitRetry,
                onOpenDate = onHabitOpenDate)
        } }
        todayTasks?.let { taskState -> item(key = "today_tasks") {
            TodayTasksCard(
                state = taskState,
                actions = todayTaskActions,
                today = date,
                onTask = onTask,
                onSetCompleted = onSetTaskCompleted,
                onAllTasks = onAllTasks,
                onAddTask = onAddTask,
                onRetry = onRetryTasks,
                onRetryTask = onRetryTask,
                onDismissTaskError = onDismissTaskError,
            )
        } }
        item(key = "recent_entries") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.today_recent_entries), style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.testTag("today_recent_entries"))
                if (state is TodayUiState.Data || state == TodayUiState.Empty) {
                    Text(entryLogCounts((state as? TodayUiState.Data)?.events?.size ?: 0, habitLogCount),
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("today_summary"))
                }
            }
        }
        when (state) {
            TodayUiState.Loading -> item(key = "loading") {
                Column(Modifier.testTag("today_loading"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.today_loading))
                }
            }
            TodayUiState.Empty -> Unit
            TodayUiState.Error -> item(key = "error") {
                Column(Modifier.testTag("today_error"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.today_error))
                    TextButton(onClick = onRetry, modifier = Modifier.testTag("today_retry")) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
            is TodayUiState.Data -> {
                items(state.events, key = { it.id.toString() }) { event ->
                    val record = state.records[event.id] ?: LifeEventRecord(event, state.moods[event.id],
                        state.journals[event.id], state.memories[event.id], state.people[event.id].orEmpty(), state.places[event.id])
                    EventCard(record, zone, onOpen = {
                        when {
                            record.canEditContent && record.journal != null -> onJournalClick(event.id)
                            record.canEditContent && event.type == EventType.MOOD -> onMoodClick(event.id)
                            record.canEditContent && event.type == EventType.MEMORY -> onMemoryClick(event.id)
                            else -> onEventClick(event.id)
                        }
                    }, onMemory = { onMemoryClick(event.id) })
                }
            }
        }
        summaryState?.let { summary -> item(key = "daily_summary") {
            DetailsDisclosure(R.string.summary_heading, "today_daily_summary", keepTitle = true) {
                DailySummaryCard(summary, onSummaryRetry, habitLogCount, integrationAvailability, onIntegrations,
                    onCheckIn = { requestCapture(EventType.MOOD, it) })
            }
        } }
    }
    val selectedCaptureType = choosingDateFor
    val selectedCaptureDate = choosingCaptureDate?.let { LocalDate.parse(it) }
    if (selectedCaptureType != null && selectedCaptureDate != null) {
        fun dismissCaptureDateChoice() {
            choosingDateFor = null
            choosingCaptureDate = null
        }
        CaptureDateChoice(
            date = selectedCaptureDate,
            onSelectedDate = {
                dismissCaptureDateChoice()
                onCapture(selectedCaptureType, selectedCaptureDate)
            },
            onNow = {
                dismissCaptureDateChoice()
                onCapture(selectedCaptureType, null)
            },
            onDismiss = ::dismissCaptureDateChoice,
        )
    }
}
