package com.luma.lifeos.ui.habits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.habit.HabitLog
import com.luma.lifeos.language.rememberDisplayTimePolicy
import java.time.ZoneId
import java.time.format.FormatStyle
import java.util.UUID

fun LazyListScope.habitHistoryItems(
    logs: List<HabitLog>,
    total: Int,
    selectedLogId: UUID?,
    screenZone: ZoneId,
    onEdit: (HabitLog) -> Unit,
    onDelete: (UUID) -> Unit,
    onLoadMore: () -> Unit,
    historyError: Boolean = false,
) {
    item(key = "habit_history_heading") {
        Text(stringResource(R.string.habit_history), style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.testTag("habit_history_section"))
    }
    val visibleLogs = logs.filterNot { it.id == selectedLogId }
    if (visibleLogs.isEmpty() && logs.isEmpty() && total == 0) item(key = "habit_history_empty") {
        Text(stringResource(R.string.habit_history_empty), modifier = Modifier.testTag("habit_history_empty"))
    }
    items(visibleLogs, key = { "habit-log:${it.id}" }) { log ->
        HabitLogCard(log, screenZone, selected = false, onEdit = onEdit, onDelete = onDelete)
    }
    if (historyError) item(key = "habit_history_error") {
        Text(stringResource(R.string.habit_history_error), color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("habit_history_error"))
    }
    if (total > logs.size || historyError) item(key = "habit_history_load_more") {
        TextButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .testTag("habit_history_load_more")) { Text(stringResource(R.string.habit_load_older)) }
    }
}

@Composable
internal fun HabitSelectedLogArrival(
    status: HabitTargetStatus,
    log: HabitLog?,
    screenZone: ZoneId,
    onEdit: (HabitLog) -> Unit,
    onDelete: (UUID) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().testTag("habit_target_arrival"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (status) {
            HabitTargetStatus.NONE -> Unit
            HabitTargetStatus.LOADING -> Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.CircularProgressIndicator(Modifier.heightIn(min = 24.dp).testTag("habit_target_loading"))
                Text(stringResource(R.string.habit_target_loading))
            }
            HabitTargetStatus.FOUND -> log?.let {
                Text(stringResource(R.string.habit_selected_repetition), style = MaterialTheme.typography.titleMedium)
                HabitLogCard(it, screenZone, selected = true, onEdit = onEdit, onDelete = onDelete)
            }
            HabitTargetStatus.MISSING -> Text(stringResource(R.string.habit_target_unavailable),
                modifier = Modifier.testTag("habit_target_unavailable"))
            HabitTargetStatus.ERROR -> {
                Text(stringResource(R.string.habit_target_error), color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("habit_target_error"))
                TextButton(onClick = onRetry, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .testTag("habit_retry_target")) { Text(stringResource(R.string.habit_retry_action)) }
            }
        }
    }
}

@Composable
private fun HabitLogCard(
    log: HabitLog,
    screenZone: ZoneId,
    selected: Boolean,
    onEdit: (HabitLog) -> Unit,
    onDelete: (UUID) -> Unit,
) {
    var confirmingDelete by rememberSaveable(log.id.toString()) { mutableStateOf(false) }
    val displayTime = rememberDisplayTimePolicy()
    val selectedDescription = stringResource(R.string.habit_selected_repetition)
    Column(Modifier.fillMaxWidth().testTag(if (selected) "habit_target_log_${log.id}" else "habit_log_${log.id}")) {
        Card(Modifier.fillMaxWidth().semantics {
            if (selected) {
                this.selected = true
                stateDescription = selectedDescription
            }
        }.testTag(if (selected) "habit_log_selected" else "habit_log_card_${log.id}")) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(log.date.format(displayTime.date(FormatStyle.MEDIUM)),
                    style = MaterialTheme.typography.titleMedium)
                val occurred = log.occurredAt?.atZone(log.zone)
                Text(occurred?.let {
                    val time = displayTime.time().format(it)
                    if (log.zone != screenZone) "$time · ${log.zone.id}" else time
                } ?: stringResource(R.string.habit_time_not_recorded))
                val recorded = displayTime.dateTime(FormatStyle.MEDIUM).withZone(screenZone).format(log.recordedAt)
                Text(stringResource(R.string.habit_recorded_at, recorded), style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onEdit(log) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        .testTag("habit_edit_log_${log.id}")) { Text(stringResource(R.string.habit_edit_log)) }
                    TextButton(onClick = { confirmingDelete = true }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        .testTag("habit_delete_log_${log.id}")) { Text(stringResource(R.string.habit_delete_log)) }
                }
            }
        }
    }
    if (confirmingDelete) AlertDialog(onDismissRequest = { confirmingDelete = false },
        title = { Text(stringResource(R.string.habit_delete_log_title)) },
        text = { Text(stringResource(R.string.habit_delete_log_body)) },
        confirmButton = { TextButton(onClick = { confirmingDelete = false; onDelete(log.id) },
            modifier = Modifier.testTag("habit_confirm_delete_log_${log.id}")) {
            Text(stringResource(R.string.habit_delete_log))
        } },
        dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(R.string.cancel)) } })
}
