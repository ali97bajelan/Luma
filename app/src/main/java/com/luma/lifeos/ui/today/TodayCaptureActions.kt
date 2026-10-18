package com.luma.lifeos.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.model.EventType
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.FormatStyle

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TodayCaptureActions(
    date: LocalDate,
    zone: ZoneId,
    empty: Boolean,
    onCapture: (EventType, LocalDate?) -> Unit,
) {
    var choosingDateFor by rememberSaveable(date) { mutableStateOf<EventType?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (empty) Text(stringResource(R.string.today_no_entries), style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag("today_empty"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(EventType.MOOD to R.string.today_capture_mood, EventType.JOURNAL to R.string.today_capture_journal,
                EventType.MEMORY to R.string.today_capture_memory).forEach { (type, label) ->
                OutlinedButton(onClick = {
                    if (date == LocalDate.now(zone)) onCapture(type, null) else choosingDateFor = type
                }, modifier = Modifier.heightIn(min = 48.dp).testTag("today_capture_${type.name.lowercase()}")) {
                    Text(stringResource(label))
                }
            }
        }
    }
    choosingDateFor?.let { type ->
        AlertDialog(
            onDismissRequest = { choosingDateFor = null },
            modifier = Modifier.testTag("today_capture_date_dialog"),
            title = { Text(stringResource(R.string.today_capture_when)) },
            text = { Text(stringResource(R.string.capture_add_to_date,
                date.format(com.luma.lifeos.language.localizedDate(FormatStyle.MEDIUM)))) },
            confirmButton = {
                TextButton(onClick = { choosingDateFor = null; onCapture(type, date) },
                    modifier = Modifier.testTag("today_capture_selected_date")) {
                    Text(stringResource(R.string.capture_add_to_date,
                        date.format(com.luma.lifeos.language.localizedDate(FormatStyle.MEDIUM))))
                }
            },
            dismissButton = {
                TextButton(onClick = { choosingDateFor = null; onCapture(type, null) },
                    modifier = Modifier.testTag("today_capture_now")) {
                    Text(stringResource(R.string.capture_use_now))
                }
            },
        )
    }
}
