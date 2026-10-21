package com.luma.lifeos.ui.habits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.ui.common.LumaReadingPane
import com.luma.lifeos.ui.common.LumaSectionSurface
import com.luma.lifeos.domain.habit.HabitDay
import com.luma.lifeos.domain.habit.HabitDayState
import com.luma.lifeos.domain.habit.HabitDefinition
import com.luma.lifeos.domain.habit.HabitSchedule
import com.luma.lifeos.domain.habit.reviewTarget
import com.luma.lifeos.ui.timeline.DayNavigation
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
    val selectedDate = date
    val rows = state.rowsFor(selectedDate)
    val groups = splitHabitRows(rows)
    val trackingDate = LocalDate.now(state.trackingZone ?: deviceZone)
    val forceOtherOpen = groups.other.any { row ->
        state.pendingLogs.any { it.input.habitId == row.day.habit.id && it.input.date == selectedDate }
    }
    val saveableStateHolder = rememberSaveableStateHolder()

    Scaffold(
        modifier = Modifier.testTag("screen_habits"),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.habit_title),
                style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() }) }, navigationIcon = {
                onBack?.let { back ->
                    TextButton(onClick = back, modifier = Modifier.testTag("habits_back")) {
                        Text(stringResource(R.string.back))
                    }
                }
            }, actions = {
                TextButton(onClick = onCreate, modifier = Modifier.testTag("habit_create")) {
                    Text(stringResource(R.string.habit_add))
                }
            }, windowInsets = WindowInsets(0, 0, 0, 0))
        },
    ) { padding ->
        LumaReadingPane(modifier = Modifier.fillMaxSize().padding(padding)) {
            saveableStateHolder.SaveableStateProvider(selectedDate.toString()) {
                var otherExpanded by rememberSaveable { mutableStateOf(false) }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { DayNavigation(selectedDate, onSelectDate, onToday) }
                    item(key = "habit_undo_status") {
                        HabitUndoStatusFeedback(
                            state = state,
                            onRetry = onUndo,
                            modifier = Modifier.testTag("habits_undo_feedback"),
                        )
                    }
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
                    if (state.loading || (state.date != date && state.error == null)) item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(Modifier.testTag("habits_loading"))
                            Text(stringResource(R.string.habit_loading))
                        }
                    }
                    if (state.error == HabitUiError.LOAD) item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("habits_error")) {
                            Text(stringResource(R.string.habit_load_error))
                            TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                        }
                    }
                    if (!state.loading && state.loadedDate == date && rows.isEmpty() && state.error == null) item {
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
                    if (rows.isNotEmpty()) {
                        item {
                            Text(stringResource(R.string.habit_scheduled_heading), style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.testTag("habits_scheduled_heading"))
                        }
                        if (groups.scheduled.isEmpty()) item {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.testTag("habits_none_scheduled")) {
                                Text(stringResource(R.string.habit_no_scheduled))
                                if (selectedDate != trackingDate) {
                                    TextButton(onClick = onToday, modifier = Modifier.heightIn(min = 48.dp).testTag("habits_today")) {
                                        Text(stringResource(R.string.today))
                                    }
                                }
                            }
                        }
                        items(groups.scheduled, key = { "scheduled:${it.day.habit.id}" }) { row ->
                            HabitRow(
                                row = row,
                                pending = state.pendingLogs.filter { it.input.habitId == row.day.habit.id && it.input.date == selectedDate },
                                pendingSchedule = state.pendingSchedules[row.day.habit.id],
                                onOpen = { onHabit(row.day.habit.id) },
                                onLog = { onLog(row.day.habit.id) },
                                onRetry = onRetryLog,
                                onOpenDate = onSelectDate,
                                compact = false,
                            )
                        }
                        if (groups.other.isNotEmpty()) {
                            item {
                                val isExpanded = otherExpanded || forceOtherOpen
                                val expansionDescription = stringResource(
                                    if (isExpanded) R.string.habit_other_expanded else R.string.habit_other_collapsed,
                                )
                                TextButton(enabled = !forceOtherOpen, onClick = { otherExpanded = !otherExpanded },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                        .semantics { stateDescription = expansionDescription }
                                        .testTag("habit_other_toggle")) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Text(pluralStringResource(R.plurals.habit_other_count, groups.other.size, groups.other.size),
                                            modifier = Modifier.weight(1f))
                                        Text(stringResource(when {
                                            forceOtherOpen -> R.string.habit_other_expanded
                                            isExpanded -> R.string.habit_other_hide
                                            else -> R.string.habit_other_show
                                        }))
                                    }
                                }
                            }
                            if (otherExpanded || forceOtherOpen) {
                                items(groups.other, key = { "other:${it.day.habit.id}" }) { row ->
                                    HabitRow(
                                        row = row,
                                        pending = state.pendingLogs.filter { it.input.habitId == row.day.habit.id && it.input.date == selectedDate },
                                        pendingSchedule = state.pendingSchedules[row.day.habit.id],
                                        onOpen = { onHabit(row.day.habit.id) },
                                        onLog = {},
                                        onRetry = onRetryLog,
                                        onOpenDate = onSelectDate,
                                        compact = false,
                                        showLog = false,
                                    )
                                }
                            }
                        }
                    }
                    state.actionError?.let { item {
                        Text(stringResource(R.string.habit_action_error), color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("habit_action_error"))
                    } }
                    val representedHabitIds = rows.mapTo(mutableSetOf()) { it.day.habit.id }
                    val retained = state.pendingLogs.filter {
                        it.input.date != selectedDate || it.input.habitId !in representedHabitIds
                    }
                    if (retained.isNotEmpty()) {
                        item(key = "habit_retained_actions_heading") {
                            Text(stringResource(R.string.habit_retained_actions), style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.testTag("habit_retained_actions_heading"))
                        }
                        items(retained, key = { "retained:${it.id}" }) { action ->
                            PendingHabitFeedback(action, onRetry = { onRetryLog(action.id) }, onOpenDate = onSelectDate)
                        }
                    }
                }
            }
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
    showLog: Boolean = true,
    onOpenDate: (LocalDate) -> Unit = {},
) {
    val day = row.day
    val logUnit = habitUnitLabel(day.habit, 1)
    val logLabel = stringResource(R.string.habit_log_accessibility, day.habit.name, logUnit)
    val showStatus = pendingSchedule != null || !compact || !row.canLog || day.state != HabitDayState.NO_RECORDS ||
        day.skipRemaining || pending.isNotEmpty()
    val rowContent: @Composable ColumnScope.() -> Unit = {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val minimumInlineWidth = if (compact) 240.dp else 336.dp
            val stackAction = maxWidth < minimumInlineWidth || LocalDensity.current.fontScale >= 1.5f
            if (showLog && !stackAction) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HabitOpenTarget(day, pendingSchedule, onOpen, showStatus, Modifier.weight(1f))
                    HabitLogTarget(row, onLog, logLabel)
                }
            } else {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    HabitOpenTarget(day, pendingSchedule, onOpen, showStatus, Modifier.fillMaxWidth())
                    if (showLog) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            HabitLogTarget(row, onLog, logLabel)
                        }
                    }
                }
            }
        }
        if (showLog) row.disabledReason?.let { Text(stringResource(disabledLabel(it)), style = MaterialTheme.typography.bodySmall) }
        pending.forEach { action ->
            PendingHabitFeedback(action, onRetry = { onRetry(action.id) }, onOpenDate = onOpenDate)
        }
    }
    val rowModifier = Modifier.fillMaxWidth().testTag("habit_row_${day.habit.id}")
    if (compact) {
        Column(
            modifier = rowModifier.padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = rowContent,
        )
    } else {
        LumaSectionSurface(modifier = rowModifier, content = rowContent)
    }
}

@Composable
private fun HabitOpenTarget(
    day: HabitDay,
    pendingSchedule: HabitSchedule?,
    onOpen: () -> Unit,
    showStatus: Boolean,
    modifier: Modifier,
) {
    val target = day.reviewTarget()
    val openLabel = stringResource(R.string.habit_open_accessibility, day.habit.name)
    val progressLabel = target?.let {
        val unit = habitUnitLabel(day.habit, it)
        stringResource(R.string.habit_progress_accessibility, day.habit.name, day.count, it, unit)
    }
    val statusLabel = stringResource(stateLabel(day))
    val accessibilityLabel = listOfNotNull(openLabel, progressLabel, statusLabel).joinToString(". ")
    TextButton(onClick = onOpen, modifier = modifier.heightIn(min = 48.dp).testTag("habit_open_${day.habit.id}")
        .semantics { contentDescription = accessibilityLabel }) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(day.habit.name, style = MaterialTheme.typography.titleMedium)
            HabitProgressContent(day)
            if (showStatus) Text(stringResource(stateLabel(day)), style = MaterialTheme.typography.bodyMedium)
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

@Composable
private fun HabitLogTarget(row: HabitDayRow, onLog: () -> Unit, logLabel: String) {
    Button(onClick = onLog, enabled = row.canLog, modifier = Modifier.heightIn(min = 48.dp)
        .testTag("habit_log_${row.day.habit.id}").semantics { contentDescription = logLabel }) {
        Text(stringResource(R.string.habit_log))
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
