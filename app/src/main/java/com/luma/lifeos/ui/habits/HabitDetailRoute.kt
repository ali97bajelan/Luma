package com.luma.lifeos.ui.habits

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.luma.lifeos.R
import com.luma.lifeos.ui.common.DetailTopBar
import com.luma.lifeos.domain.habit.HabitDayState
import com.luma.lifeos.domain.habit.HabitLifecycle
import com.luma.lifeos.domain.habit.HabitRepository
import com.luma.lifeos.domain.habit.scheduleFor
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

@Composable
fun HabitDetailRoute(
    repository: HabitRepository,
    habitId: UUID,
    date: LocalDate,
    logId: UUID? = null,
    snackbarHostState: SnackbarHostState? = null,
    onEdit: (UUID) -> Unit,
    onBack: () -> Unit,
    deviceZone: ZoneId = ZoneId.systemDefault(),
    onOpenDate: (LocalDate) -> Unit = {},
) {
    val factory = androidx.compose.runtime.remember(repository, habitId) {
        viewModelFactory { initializer { HabitsViewModel(repository, createSavedStateHandle()) } }
    }
    val habits: HabitsViewModel = viewModel(factory = factory)
    val state by habits.state.collectAsStateWithLifecycle()
    LaunchedEffect(habitId, date, deviceZone, logId) { habits.openDetail(habitId, date, deviceZone, logId) }
    LaunchedEffect(state.detail?.deleted) { if (state.detail?.deleted == true) onBack() }
    val renderedState = state
    HabitLogSnackbarFeedback(
        state = renderedState,
        snackbarHostState = snackbarHostState,
        onUndo = habits::undoLog,
        onAcknowledge = habits::acknowledgeUndoLog,
        onRetry = habits::undoLog,
    )
    HabitDetailScreen(renderedState, habitId, date, logId, onBack,
        onEdit = { onEdit(habitId) }, onLog = {
            renderedState.rowsFor(date).firstOrNull { it.day.habit.id == habitId }?.let(habits::log)
        },
        onRetryLog = habits::retryLog, onUndoLog = habits::undoLog, onOpenDate = onOpenDate,
        onSkip = { habits.setSkipRemaining(habitId, true) }, onResumeToday = { habits.setSkipRemaining(habitId, false) },
        onPauseResume = { habits.changeLifecycle(habitId, it) },
        onCancelPending = { habits.cancelPendingSchedule(habitId) },
        onLoadMore = habits::loadMoreHistory,
        onDeleteHabit = { habits.deleteHabit(habitId) }, onDeleteLog = habits::deleteHistoryLog,
        onEditLog = habits::beginCorrection, onChangeDate = habits::changeCorrectionDate,
        onChangeTime = habits::changeCorrectionTime, onSaveCorrection = habits::saveCorrection,
        onCancelCorrection = habits::cancelCorrection, onRetry = habits::retry,
        onRetrySelectedLog = habits::retrySelectedLog,
        onAnnounceSelectedLog = habits::consumeSelectedTargetAnnouncement)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitDetailScreen(
    state: HabitsUiState,
    habitId: UUID,
    date: LocalDate,
    selectedLogId: UUID?,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onLog: () -> Unit,
    onRetryLog: (UUID) -> Unit,
    onUndoLog: (UUID) -> Unit,
    onSkip: () -> Unit,
    onResumeToday: () -> Unit,
    onPauseResume: (HabitLifecycle) -> Unit,
    onCancelPending: () -> Unit,
    onLoadMore: () -> Unit,
    onDeleteHabit: () -> Unit,
    onDeleteLog: (UUID) -> Unit,
    onEditLog: (com.luma.lifeos.domain.habit.HabitLog) -> Unit,
    onChangeDate: (LocalDate) -> Unit,
    onChangeTime: (LocalTime?) -> Unit,
    onSaveCorrection: () -> Unit,
    onCancelCorrection: () -> Unit,
    onRetry: () -> Unit,
    onRetrySelectedLog: () -> Unit = {},
    onAnnounceSelectedLog: () -> Boolean = { false },
    onOpenDate: (LocalDate) -> Unit = {},
) {
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    var confirmingDiscard by rememberSaveable { mutableStateOf(false) }
    var correctionPickerOpen by rememberSaveable { mutableStateOf(false) }
    val detail = state.detail
    val habit = detail?.habit
    val targetLogId = detail?.selectedLogId ?: selectedLogId
    val selectedTarget = detail?.takeIf { it.selectedLogStatus == HabitTargetStatus.FOUND }?.selectedLog
    val day = detail?.day
    val row = state.rowsFor(date).firstOrNull { it.day.habit.id == habitId }
    val trackingZone = state.trackingZone ?: habit?.let { day?.zone } ?: ZoneId.systemDefault()
    val today = LocalDate.now(trackingZone)
    val futureStart = habit?.start?.isAfter(today) == true
    val lifecycleDate = if (futureStart) habit?.start else today
    val trackingSchedule = lifecycleDate?.let { scheduleFor(it, detail?.schedules.orEmpty()) }
    val lifecycle = detail?.pendingSchedule?.lifecycle ?: trackingSchedule?.lifecycle
        ?: detail?.schedules?.lastOrNull()?.lifecycle ?: day?.schedule?.lifecycle
    val canSkip = date == today && day?.schedule?.lifecycle == HabitLifecycle.ACTIVE &&
        day.state != HabitDayState.NOT_SCHEDULED && day.state != HabitDayState.PAUSED && day.state != HabitDayState.ARCHIVED
    fun requestCorrectionClose() {
        val current = detail ?: return
        if (current.correctionBusy) return
        val draft = current.correction ?: return
        if (draft.hasChanges) confirmingDiscard = true else onCancelCorrection()
    }
    BackHandler(enabled = detail?.correction != null && !correctionPickerOpen) { requestCorrectionClose() }
    val listState = rememberLazyListState()
    val view = LocalView.current
    val targetAnnouncement = selectedTarget?.let {
        stringResource(R.string.habit_selected_repetition_arrived,
            it.date.format(com.luma.lifeos.language.localizedDate(FormatStyle.MEDIUM)))
    }
    LaunchedEffect(selectedTarget?.id, habit?.id, detail?.loading) {
        if (selectedTarget != null && habit != null && detail?.loading != true) {
            val targetItem = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                it.key == "habit_target_arrival_${selectedTarget.id}"
            }
            listState.animateScrollToItem(targetItem?.index ?: 0)
            if (onAnnounceSelectedLog()) view.announceForAccessibility(targetAnnouncement.orEmpty())
        }
    }
    val canEditHabit = habit != null && detail?.loading != true
    Scaffold(modifier = Modifier.testTag("screen_habit_detail"),
        contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = {
        DetailTopBar(title = habit?.name ?: stringResource(R.string.habit_title),
            backTag = "habit_detail_back",
            onBack = { if (detail?.correction != null) requestCorrectionClose() else onBack() },
            editTag = "habit_detail_edit".takeIf { canEditHabit },
            editLabel = stringResource(R.string.detail_edit).takeIf { canEditHabit },
            onEdit = onEdit.takeIf { canEditHabit })
    }) { padding ->
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding).testTag("habit_detail_list"), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (detail?.loading == true) item { CircularProgressIndicator(Modifier.testTag("habit_detail_loading")) }
            if (detail?.error == HabitUiError.LOAD || state.error == HabitUiError.LOAD) item {
                Text(stringResource(R.string.habit_load_error), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
            if (detail != null && habit != null) {
                if (targetLogId != null) item(key = "habit_target_arrival_$targetLogId") {
                    HabitSelectedLogArrival(
                        status = detail.selectedLogStatus,
                        log = detail.selectedLog,
                        screenZone = trackingZone,
                        onEdit = onEditLog,
                        onDelete = onDeleteLog,
                        onRetry = onRetrySelectedLog,
                    )
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(date.format(com.luma.lifeos.language.localizedDate(FormatStyle.FULL)), style = MaterialTheme.typography.titleLarge)
                        day?.let {
                            HabitProgressContent(it)
                            Text(stringResource(stateLabel(it)), style = MaterialTheme.typography.bodyMedium)
                            if (it.skipRemaining) Text(stringResource(if (it.count == 0) R.string.habit_state_skipped else R.string.habit_remaining_skipped))
                        }
                        row?.let { activeRow ->
                            if (activeRow.disabledReason != null) Text(stringResource(when (activeRow.disabledReason) {
                                HabitLogDisabledReason.FUTURE_DATE -> R.string.habit_log_disabled_future
                                HabitLogDisabledReason.NOT_SCHEDULED -> R.string.habit_log_disabled_not_scheduled
                                HabitLogDisabledReason.PAUSED -> R.string.habit_log_disabled_paused
                                HabitLogDisabledReason.ARCHIVED -> R.string.habit_log_disabled_archived
                            }))
                            Button(onClick = onLog, enabled = activeRow.canLog,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("habit_detail_log")) {
                                Text(stringResource(R.string.habit_log))
                            }
                        }
                        state.pendingLogs.filter { it.input.habitId == habitId && it.input.date == date }.forEach { action ->
                            PendingHabitFeedback(action, onRetry = { onRetryLog(action.id) }, onOpenDate = onOpenDate,
                                modifier = Modifier.testTag("habit_detail_pending_${action.id}"),
                                retryTag = "habit_detail_retry_${action.id}")
                        }
                    }
                }
                item(key = "habit_detail_undo_feedback") {
                    HabitUndoStatusFeedback(state, onUndoLog, onlyHabitId = habitId)
                }
                val retained = state.pendingLogs.filter { it.input.habitId == habitId && it.input.date != date }
                if (retained.isNotEmpty()) {
                    item(key = "habit_detail_retained_heading") {
                        Text(stringResource(R.string.habit_retained_actions), style = MaterialTheme.typography.titleMedium)
                    }
                    items(retained, key = { "detail-retained:${it.id}" }) { action ->
                        PendingHabitFeedback(action, onRetry = { onRetryLog(action.id) }, onOpenDate = onOpenDate,
                            modifier = Modifier.testTag("habit_detail_retained_${action.id}"))
                    }
                }
                if (canSkip) item {
                    TextButton(onClick = if (day?.skipRemaining == true) onResumeToday else onSkip,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("habit_skip_remaining")) {
                        Text(stringResource(if (day?.skipRemaining == true) R.string.habit_resume_today else R.string.habit_skip_remaining))
                    }
                }
                if (detail.correction == null && detail.correctionError == HabitCorrectionError.MISSING) item {
                    Text(stringResource(R.string.habit_correction_missing), color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("habit_correction_missing"))
                }
                state.actionError?.let { item { Text(stringResource(R.string.habit_action_error),
                    color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("habit_detail_action_error")) } }
                item(key = "schedule-${habit.id}") {
                    Column(Modifier.fillMaxWidth().testTag("habit_schedule_details"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.habit_schedule_details), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.habit_tracking_zone, trackingZone.id), style = MaterialTheme.typography.bodySmall)
                        trackingSchedule?.let { schedule ->
                            Text(stringResource(R.string.habit_schedule_target, schedule.target))
                            val days = schedule.weekdays.sortedBy { it.value }.joinToString { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
                            Text(stringResource(R.string.habit_schedule_weekdays, days))
                        }
                        detail.pendingSchedule?.let { pendingSchedule ->
                            val message = if (futureStart && pendingSchedule.effectiveFrom == habit.start) R.string.habit_effective_start
                                else R.string.habit_effective_tomorrow
                            Text(stringResource(message,
                                pendingSchedule.effectiveFrom.format(com.luma.lifeos.language.localizedDate(FormatStyle.MEDIUM))),
                                modifier = Modifier.testTag("habit_pending_effective_date"))
                            val isFutureInitial = futureStart && pendingSchedule.effectiveFrom == habit.start
                            if (!isFutureInitial) TextButton(onClick = onCancelPending,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("habit_cancel_pending")) {
                                Text(stringResource(R.string.habit_cancel_pending))
                            }
                        }
                    }
                }
                item(key = "manage-${habit.id}") {
                    HabitManagementSection(
                        lifecycle = lifecycle,
                        futureStart = futureStart,
                        onLifecycle = onPauseResume,
                        onDelete = { confirmingDelete = true },
                        deleteError = (detail.actionErrors[HabitDetailOperation.DELETE] as? HabitDetailActionError.Delete)
                            ?.takeIf { it.habitId == habit.id },
                        onRetryDelete = onDeleteHabit,
                    )
                }
                habitHistoryItems(detail.history, detail.historyTotal, selectedTarget?.id, trackingZone, onEditLog, onDeleteLog,
                    onLoadMore, historyError = detail.actionErrors[HabitDetailOperation.HISTORY] is HabitDetailActionError.History)
            }
        }
    }
    if (confirmingDelete) AlertDialog(onDismissRequest = { confirmingDelete = false },
        title = { Text(stringResource(R.string.habit_delete_title)) }, text = { Text(stringResource(R.string.habit_delete_body)) },
        confirmButton = { TextButton(onClick = { confirmingDelete = false; onDeleteHabit() },
            modifier = Modifier.testTag("habit_confirm_delete")) { Text(stringResource(R.string.habit_delete)) } },
        dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(R.string.cancel)) } })
    detail?.correction?.let { correction ->
        HabitLogCorrectionDialog(
            draft = correction,
            busy = detail.correctionBusy,
            error = detail.correctionError,
            onDate = onChangeDate,
            onTime = onChangeTime,
            onSave = onSaveCorrection,
            onRequestClose = ::requestCorrectionClose,
            onPickerOpenChanged = { correctionPickerOpen = it },
        )
    }
    if (confirmingDiscard) AlertDialog(onDismissRequest = { confirmingDiscard = false },
        title = { Text(stringResource(R.string.habit_correction_discard_title)) }, text = { Text(stringResource(R.string.habit_correction_discard_body)) },
        confirmButton = { TextButton(enabled = detail?.correctionBusy != true,
            onClick = { confirmingDiscard = false; onCancelCorrection() }, modifier = Modifier.testTag("habit_discard_correction")) {
            Text(stringResource(R.string.discard))
        } }, dismissButton = { TextButton(onClick = { confirmingDiscard = false }) {
            Text(stringResource(R.string.keep_editing))
        } })
}
