package com.luma.lifeos.ui.habits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.FormatStyle
import java.util.UUID

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
) {
    val shownDate = state.date ?: fallbackDate
    val rows = state.rowsFor(shownDate)
    Card(Modifier.fillMaxWidth().testTag("today_habits_card")) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.habit_title), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = onViewAll, modifier = Modifier.heightIn(min = 48.dp).testTag("habits_view_all")) {
                    Text(stringResource(R.string.habit_view_all))
                }
            }
            state.trackingZone?.let { zone ->
                if (zone != deviceZone || (state.deviceDate != null && state.deviceDate != LocalDate.now(zone))) {
                    Text(stringResource(R.string.habit_tracking_elsewhere,
                        LocalDate.now(zone).format(com.luma.lifeos.language.localizedDate(FormatStyle.FULL)), zone.id),
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("today_habit_tracking_day_difference"))
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
            if (!state.loading && state.loadedDate == shownDate && rows.isEmpty() && state.error == null) {
                Text(stringResource(R.string.habit_empty_body))
                Button(onClick = onCreate, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.habit_add))
                }
            }
            if (rows.isNotEmpty()) {
                val scheduled = splitHabitRows(rows).scheduled
                val visible = scheduled.take(3)
                if (visible.isEmpty()) Text(stringResource(R.string.habit_no_scheduled),
                    modifier = Modifier.testTag("today_habits_none_scheduled"))
                visible.forEach { row ->
                    HabitRow(row, state.pendingLogs.filter { it.input.habitId == row.day.habit.id },
                        state.pendingSchedules[row.day.habit.id],
                        onOpen = { onHabit(row.day.habit.id, row.day.date) },
                        onLog = { onLog(row.day.habit.id) }, onRetry = onRetryLog, compact = true)
                }
            }
            state.undoableLog?.let { log ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.habit_log_saved))
                    TextButton(onClick = { onUndo(log.id) }, modifier = Modifier.heightIn(min = 48.dp).testTag("today_habit_undo")) {
                        Text(stringResource(R.string.habit_undo))
                    }
                }
            }
        }
    }
}
