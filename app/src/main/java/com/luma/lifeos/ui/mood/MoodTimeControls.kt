package com.luma.lifeos.ui.mood

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.ui.common.AccessibleFeedback
import com.luma.lifeos.ui.today.pickerDate
import com.luma.lifeos.ui.today.pickerMillis
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodTimeControls(milliseconds: Long, enabled: Boolean, timeError: Boolean,
    onTime: (LocalDateTime, ZoneId) -> Boolean) {
    val zone = ZoneId.systemDefault()
    val current = Instant.ofEpochMilli(milliseconds).atZone(zone)
    val timeErrorMessage = stringResource(R.string.mood_time_error)
    var dateOpen by rememberSaveable { mutableStateOf(false) }
    var timeOpen by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.mood_timestamp), style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }.testTag("mood_timestamp"))
        TextButton(enabled = enabled, onClick = { dateOpen = true }, modifier = Modifier.testTag("mood_date").semantics {
            if (timeError) error(timeErrorMessage)
        }) {
            Text(current.toLocalDate().format(com.luma.lifeos.language.localizedDate(FormatStyle.MEDIUM)))
        }
        TextButton(enabled = enabled, onClick = { timeOpen = true }, modifier = Modifier.testTag("mood_time").semantics {
            if (timeError) error(timeErrorMessage)
        }) {
            Text(current.format(com.luma.lifeos.language.localizedTime()))
        }
        Text(stringResource(R.string.mood_timezone, zone.id, current.offset.toString()), style = MaterialTheme.typography.bodySmall)
        if (timeError && !dateOpen && !timeOpen) {
            AccessibleFeedback(timeErrorMessage, Modifier.testTag("mood_time_error"))
        }
    }
    if (dateOpen) {
        val date = rememberDatePickerState(initialSelectedDateMillis = pickerMillis(current.toLocalDate()),
            yearRange = minOf(1900, current.year)..maxOf(2100, current.year))
        DatePickerDialog(onDismissRequest = { dateOpen = false },
            confirmButton = {
                TextButton(enabled = date.selectedDateMillis != null, onClick = {
                    date.selectedDateMillis?.let {
                        if (onTime(pickerDate(it).atTime(current.toLocalTime()), zone)) dateOpen = false
                    }
                }) { Text(stringResource(R.string.select_date)) }
            },
            dismissButton = { TextButton(onClick = { dateOpen = false }) { Text(stringResource(R.string.cancel)) } },
        ) {
            Column {
                DatePicker(state = date)
                if (timeError) AccessibleFeedback(timeErrorMessage, Modifier.testTag("mood_time_error"))
            }
        }
    }
    if (timeOpen) {
        val time = rememberTimePickerState(initialHour = current.hour, initialMinute = current.minute, is24Hour = true)
        AlertDialog(onDismissRequest = { timeOpen = false }, title = { Text(stringResource(R.string.mood_timestamp)) },
            text = {
                Column {
                    TimeInput(state = time)
                    if (timeError) AccessibleFeedback(timeErrorMessage, Modifier.testTag("mood_time_error"))
                }
            },
            confirmButton = { TextButton(onClick = {
                if (onTime(current.toLocalDate().atTime(LocalTime.of(time.hour, time.minute)), zone)) timeOpen = false
            }) { Text(stringResource(R.string.select_time)) } },
            dismissButton = { TextButton(onClick = { timeOpen = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
