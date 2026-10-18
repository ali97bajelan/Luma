package com.luma.lifeos.ui.habits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.habit.HabitDay
import com.luma.lifeos.domain.habit.HabitDayState
import com.luma.lifeos.domain.habit.HabitDefinition
import com.luma.lifeos.domain.habit.HabitLifecycle
import com.luma.lifeos.domain.habit.HabitSchedule
import com.luma.lifeos.ui.timeline.DayNavigation
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.FormatStyle
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsScreen(
    state: HabitsUiState,
    date: LocalDate,
    deviceZone: ZoneId,
    onBack: (() -> Unit)?,
    onSelectDate: (LocalDate) -> Unit,
    onToday: () -> Unit,
    onCreate: () -> Unit,
    onHabit: (UUID) -> Unit,
    onLog: (UUID) -> Unit,
    onRetryLog: (UUID) -> Unit,
    onUndo: (UUID) -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.testTag("screen_habits"),
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.habit_title)) }, navigationIcon = {
                onBack?.let { back ->
                    TextButton(onClick = back, modifier = Modifier.testTag("habits_back")) {
                        Text(stringResource(R.string.back))
                    }
                }
            }, actions = {
                TextButton(onClick = onCreate, modifier = Modifier.testTag("habit_create")) {
                    Text(stringResource(R.string.habit_add))
                }
            })
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { DayNavigation(date, onSelectDate, onToday) }
            state.trackingZone?.let { zone ->
                item { Text(stringResource(R.string.habit_tracking_zone, zone.id), style = MaterialTheme.typography.labelLarge) }
                item { Text(stringResource(R.string.habit_tracking_zone_note), style = MaterialTheme.typography.bodySmall) }
                if (state.trackingZone != deviceZone || (state.deviceDate != null && state.deviceDate != LocalDate.now(zone))) {
                    item {
                        Text(stringResource(R.string.habit_tracking_elsewhere,
                            LocalDate.now(zone).format(com.luma.lifeos.language.localizedDate(FormatStyle.FULL)), zone.id),
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("habit_tracking_day_difference"))
                    }
                }
            }
            if (state.loading) item { CircularProgressIndicator(Modifier.testTag("habits_loading")) }
            if (state.error == HabitUiError.LOAD) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("habits_error")) {
                    Text(stringResource(R.string.habit_load_error))
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
            }
            if (!state.loading && state.days.isEmpty() && state.error == null) item {
                Card(Modifier.fillMaxWidth().testTag("habits_empty")) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.habit_empty_heading), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.habit_empty_body))
                        Button(onClick = onCreate, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.habit_add))
                        }
                    }
                }
            }
            if (state.days.isNotEmpty() && state.days.all { it.day.state == HabitDayState.NOT_SCHEDULED }) {
                item { Text(stringResource(R.string.habit_no_scheduled), modifier = Modifier.testTag("habits_none_scheduled")) }
            }
            items(state.days, key = { it.day.habit.id.toString() }) { row ->
                HabitRow(
                    row = row,
                    pending = state.pendingLogs.filter { it.input.habitId == row.day.habit.id },
                    pendingSchedule = state.pendingSchedules[row.day.habit.id],
                    onOpen = { onHabit(row.day.habit.id) },
                    onLog = { onLog(row.day.habit.id) },
                    onRetry = onRetryLog,
                    compact = false,
                )
            }
            state.actionError?.let { item {
                Text(stringResource(R.string.habit_action_error), color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("habit_action_error"))
            } }
            state.undoableLog?.let { log -> item {
                Card(Modifier.fillMaxWidth().testTag("habit_undo_card")) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.habit_log_saved))
                        TextButton(onClick = { onUndo(log.id) }, modifier = Modifier.heightIn(min = 48.dp).testTag("habit_undo")) {
                            Text(stringResource(R.string.habit_undo))
                        }
                    }
                }
            } }
        }
    }
}

@Composable
internal fun HabitRow(
    row: HabitDayRow,
    pending: List<PendingHabitLog>,
    pendingSchedule: HabitSchedule?,
    onOpen: () -> Unit,
    onLog: () -> Unit,
    onRetry: (UUID) -> Unit,
    compact: Boolean,
) {
    val day = row.day
    val logUnit = habitUnitLabel(day.habit, 1)
    val openLabel = stringResource(R.string.habit_open_accessibility, day.habit.name)
    val logLabel = stringResource(R.string.habit_log_accessibility, day.habit.name, logUnit)
    Card(Modifier.fillMaxWidth().testTag("habit_row_${day.habit.id}")) {
        Column(Modifier.fillMaxWidth().padding(if (compact) 12.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val open: @Composable () -> Unit = {
                TextButton(onClick = onOpen, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("habit_open_${day.habit.id}")
                    .semantics { contentDescription = "${day.habit.name}. $openLabel" }) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(day.habit.name, style = MaterialTheme.typography.titleMedium)
                        ProgressText(day)
                        Text(stringResource(stateLabel(day)), style = MaterialTheme.typography.bodyMedium)
                        if (day.skipRemaining && day.count > 0) Text(stringResource(R.string.habit_remaining_skipped),
                            style = MaterialTheme.typography.bodySmall)
                        pendingSchedule?.let { schedule ->
                            val message = if (day.date.isBefore(day.habit.start)) R.string.habit_effective_start else R.string.habit_effective_tomorrow
                            Text(stringResource(message,
                                schedule.effectiveFrom.format(com.luma.lifeos.language.localizedDate(FormatStyle.MEDIUM))),
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            val log: @Composable () -> Unit = {
                Button(onClick = onLog, enabled = row.canLog, modifier = Modifier.then(if (compact) Modifier else Modifier.fillMaxWidth()).heightIn(min = 48.dp)
                    .testTag("habit_log_${day.habit.id}")
                    .semantics { contentDescription = logLabel }) {
                    Text(stringResource(R.string.habit_log))
                }
            }
            if (compact) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { open() }
                    log()
                }
                row.disabledReason?.let { Text(stringResource(disabledLabel(it)), style = MaterialTheme.typography.bodySmall) }
            } else {
                open()
                row.disabledReason?.let { Text(stringResource(disabledLabel(it)), style = MaterialTheme.typography.bodySmall) }
                log()
            }
            pending.forEach { action ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(if (action.error == null) R.string.habit_log_saving else R.string.habit_log_failed),
                        style = MaterialTheme.typography.bodySmall)
                    if (action.error != null) TextButton(onClick = { onRetry(action.id) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("habit_retry_${action.id}")) {
                        Text(stringResource(R.string.habit_retry_action))
                    }
                }
            }
        }
    }
}

@Composable
internal fun ProgressText(day: HabitDay) {
    val schedule = day.schedule
    if (schedule != null && schedule.lifecycle == HabitLifecycle.ACTIVE && day.date.dayOfWeek in schedule.weekdays) {
        val unit = habitUnitLabel(day.habit, schedule.target)
        val count = NumberFormat.getIntegerInstance().format(day.count)
        val target = NumberFormat.getIntegerInstance().format(schedule.target)
        val description = stringResource(R.string.habit_progress_accessibility, day.habit.name, day.count, schedule.target, unit)
        Text(stringResource(R.string.habit_progress, count, target, unit),
            modifier = Modifier.semantics { contentDescription = description }.testTag("habit_progress_${day.habit.id}"))
    }
}

@Composable
internal fun habitUnitLabel(habit: HabitDefinition, quantity: Int): String =
    habit.unitLabel ?: pluralStringResource(R.plurals.habit_unit_default, quantity)

@Composable
internal fun stateLabel(day: HabitDay): Int = when {
    day.skipRemaining && day.count == 0 -> R.string.habit_state_skipped
    else -> when (day.state) {
        HabitDayState.NOT_SCHEDULED -> R.string.habit_state_not_scheduled
        HabitDayState.PAUSED -> R.string.habit_state_paused
        HabitDayState.ARCHIVED -> R.string.habit_state_archived
        HabitDayState.NO_RECORDS -> R.string.habit_state_no_records
        HabitDayState.PARTIAL -> R.string.habit_state_partial
        HabitDayState.TARGET_REACHED -> R.string.habit_state_reached
    }
}

@Composable
private fun disabledLabel(reason: HabitLogDisabledReason): Int = when (reason) {
    HabitLogDisabledReason.FUTURE_DATE -> R.string.habit_log_disabled_future
    HabitLogDisabledReason.NOT_SCHEDULED -> R.string.habit_log_disabled_not_scheduled
    HabitLogDisabledReason.PAUSED -> R.string.habit_log_disabled_paused
    HabitLogDisabledReason.ARCHIVED -> R.string.habit_log_disabled_archived
}
