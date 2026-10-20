package com.luma.lifeos.ui.habits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.habit.HabitLog
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.UUID

@Composable
fun HabitHistory(
    logs: List<HabitLog>,
    total: Int,
    selectedLogId: UUID?,
    screenZone: ZoneId,
    onEdit: (HabitLog) -> Unit,
    onDelete: (UUID) -> Unit,
    onLoadMore: () -> Unit,
    historyError: Boolean = false,
) {
    Column(Modifier.testTag("habit_history_section"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.habit_history), style = MaterialTheme.typography.titleLarge)
        if (logs.isEmpty()) Text(stringResource(R.string.habit_history_empty), modifier = Modifier.testTag("habit_history_empty"))
        logs.forEach { log ->
            var confirmingDelete by rememberSaveable(log.id.toString()) { mutableStateOf(false) }
            Card(Modifier.fillMaxWidth().testTag(if (log.id == selectedLogId) "habit_log_selected" else "habit_log_${log.id}")) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(log.date.format(com.luma.lifeos.language.localizedDate(FormatStyle.MEDIUM)),
                        style = MaterialTheme.typography.titleMedium)
                    val occurred = log.occurredAt?.atZone(log.zone)
                    Text(occurred?.let {
                        val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()).format(it)
                        if (log.zone != screenZone) "$time · ${log.zone.id}" else time
                    } ?: stringResource(R.string.habit_time_not_recorded))
                    val recorded = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
                        .withZone(screenZone).format(log.recordedAt)
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
            if (confirmingDelete) AlertDialog(onDismissRequest = { confirmingDelete = false },
                title = { Text(stringResource(R.string.habit_delete_log_title)) },
                text = { Text(stringResource(R.string.habit_delete_log_body)) },
                confirmButton = { TextButton(onClick = { confirmingDelete = false; onDelete(log.id) },
                    modifier = Modifier.testTag("habit_confirm_delete_log_${log.id}")) {
                    Text(stringResource(R.string.habit_delete_log))
                } },
                dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(R.string.cancel)) } })
        }
        if (historyError) Text(stringResource(R.string.habit_history_error), color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("habit_history_error"))
        if (total > logs.size || historyError) TextButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .testTag("habit_history_load_more")) { Text(stringResource(R.string.habit_load_older)) }
    }
}
