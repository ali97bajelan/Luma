package com.luma.lifeos.ui.habits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.ui.common.LumaSectionHeader
import com.luma.lifeos.ui.common.LumaSectionSurface
import com.luma.lifeos.language.localizedDate
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.FormatStyle
import java.util.UUID

internal const val TODAY_HABIT_PREVIEW_LIMIT = 2

@Composable
fun TodayHabitsCard(
    state: HabitsUiState,
    fallbackDate: LocalDate,
    deviceZone: ZoneId,
    onViewAll: () -> Unit,
    onCreate: () -> Unit,
    onHabit: (UUID, LocalDate) -> Unit,
    onLog: (UUID) -> Unit,
    onRetryLog: (UUID) -> Unit,
    onUndo: (UUID) -> Unit,
    onRetry: () -> Unit,
    onOpenDate: (LocalDate) -> Unit = {},
) {
    val shownDate = state.date ?: fallbackDate
    val rows = state.rowsFor(shownDate)
    val scheduled = splitHabitRows(rows).scheduled
    val visible = scheduled.take(TODAY_HABIT_PREVIEW_LIMIT)
    val visibleHabitIds = visible.mapTo(mutableSetOf()) { it.day.habit.id }
    val showScheduledCount = !state.loading && state.loadedDate == shownDate &&
        state.error != HabitUiError.LOAD && scheduled.isNotEmpty()
    val scheduledCount = NumberFormat.getIntegerInstance().format(scheduled.size)
    val viewAllText = if (showScheduledCount) {
        stringResource(R.string.habit_view_all_count, scheduledCount)
    } else {
        stringResource(R.string.habit_view_all)
    }
    val viewAllDescription = if (showScheduledCount) {
        stringResource(R.string.habit_view_all_scheduled_accessibility, scheduledCount,
            shownDate.format(localizedDate(FormatStyle.FULL)))
    } else {
        null
    }
    LumaSectionSurface(modifier = Modifier.testTag("today_habits_card")) {
        LumaSectionHeader(
            title = stringResource(R.string.habit_title),
            actionLabel = viewAllText,
            onAction = onViewAll,
            actionModifier = Modifier.heightIn(min = 48.dp).testTag("habits_view_all").semantics {
                viewAllDescription?.let { contentDescription = it }
            },
        )
        state.trackingZone?.let { zone ->
            val currentDeviceDate = state.deviceDate ?: LocalDate.now(deviceZone)
            if (shownDate == currentDeviceDate && currentDeviceDate != LocalDate.now(zone)) {
                Text(stringResource(R.string.habit_tracking_zone_mismatch,
                    LocalDate.now(zone).format(com.luma.lifeos.language.localizedDate(FormatStyle.FULL)), zone.id,
                    currentDeviceDate.format(com.luma.lifeos.language.localizedDate(FormatStyle.FULL))),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("today_habit_tracking_day_difference"))
            }
        }
        if (state.loading) {
            CircularProgressIndicator(Modifier.testTag("today_habits_loading"))
            Text(stringResource(R.string.habit_loading))
        }
        if (state.error == HabitUiError.LOAD) {
            Text(stringResource(R.string.habit_load_error), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.retry))
            }
        }
        HabitUndoStatusFeedback(
            state = state,
            onRetry = onUndo,
            modifier = Modifier.testTag("today_habit_undo_feedback"),
        )
        if (!state.loading && state.loadedDate == shownDate && rows.isEmpty() && state.error == null) {
            Text(stringResource(R.string.habit_empty_body))
            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.habit_add))
            }
        }
        if (rows.isNotEmpty()) {
            if (visible.isEmpty()) Text(stringResource(R.string.habit_no_scheduled),
                modifier = Modifier.testTag("today_habits_none_scheduled"))
            if (visible.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    visible.forEach { row ->
                        HabitRow(row, state.pendingLogs.filter {
                            it.input.habitId == row.day.habit.id && it.input.date == row.day.date
                        }, state.pendingSchedules[row.day.habit.id],
                            onOpen = { onHabit(row.day.habit.id, row.day.date) },
                            onLog = { onLog(row.day.habit.id) }, onRetry = onRetryLog,
                            compact = true, onOpenDate = onOpenDate)
                    }
                }
            }
        }
        val retained = state.pendingLogs.filter {
            it.input.date != shownDate || it.input.habitId !in visibleHabitIds
        }
        if (retained.isNotEmpty()) {
            Text(stringResource(R.string.habit_retained_actions), style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.testTag("today_habit_retained_actions_heading"))
            retained.forEach { action ->
                PendingHabitFeedback(action, onRetry = { onRetryLog(action.id) }, onOpenDate = onOpenDate,
                    modifier = Modifier.testTag("today_habit_retained_${action.id}"))
            }
        }
    }
}
