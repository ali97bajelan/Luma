package com.luma.lifeos.ui.common

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.language.localizedDate
import com.luma.lifeos.language.localizedTime
import com.luma.lifeos.ui.today.pickerDate
import com.luma.lifeos.ui.today.pickerMillis
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureTimestampRow(
    milliseconds: Long,
    enabled: Boolean,
    timeError: Boolean,
    onTime: (LocalDateTime, ZoneId) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val zone = ZoneId.systemDefault()
    val current = Instant.ofEpochMilli(milliseconds).atZone(zone)
    val use24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val dateText = if (current.toLocalDate() == LocalDate.now(zone)) {
        stringResource(R.string.capture_timestamp_today, current.format(localizedTime(use24Hour)))
    } else {
        stringResource(R.string.capture_timestamp_value,
            current.format(localizedDate(FormatStyle.MEDIUM)), current.format(localizedTime(use24Hour)))
    }
    val timeErrorMessage = stringResource(R.string.mood_time_error)
    var dialogOpen by rememberSaveable(milliseconds) { mutableStateOf(false) }
    var timezoneExpanded by rememberSaveable(milliseconds) { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp)
            .semantics { if (timeError) error(timeErrorMessage) }
            .testTag("capture_timestamp"),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TextButton(
            enabled = enabled,
            onClick = { dialogOpen = true },
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("capture_timestamp_value"),
        ) { Text(dateText, style = MaterialTheme.typography.bodyLarge) }
        TextButton(
            enabled = enabled,
            onClick = { dialogOpen = true },
            modifier = Modifier.heightIn(min = 48.dp).testTag("capture_timestamp_change"),
        ) { Text(stringResource(R.string.capture_timestamp_change)) }
    }
    if (timeError && !dialogOpen) {
        AccessibleFeedback(timeErrorMessage, Modifier.testTag("mood_time_error"))
    }

    if (dialogOpen) {
        val date = rememberDatePickerState(
            initialSelectedDateMillis = pickerMillis(current.toLocalDate()),
            yearRange = minOf(1900, current.year)..maxOf(2100, current.year),
        )
        val time = rememberTimePickerState(
            initialHour = current.hour,
            initialMinute = current.minute,
            is24Hour = use24Hour,
        )
        val selectedDate = date.selectedDateMillis?.let(::pickerDate) ?: current.toLocalDate()
        val selectedTime = captureTimestampForSelection(
            current.toLocalDateTime(), selectedDate, time.hour, time.minute,
        ).toLocalTime()
        AlertDialog(
            onDismissRequest = { dialogOpen = false },
            title = { Text(stringResource(R.string.mood_timestamp)) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())
                        .testTag("capture_timestamp_dialog"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.capture_timestamp_date), style = MaterialTheme.typography.titleSmall)
                    DatePicker(
                        state = date,
                        modifier = Modifier.testTag("capture_timestamp_date_picker"),
                    )
                    Text(stringResource(R.string.capture_timestamp_time), style = MaterialTheme.typography.titleSmall)
                    TimeInput(state = time, modifier = Modifier.testTag("capture_timestamp_time_input"))
                    TextButton(
                        onClick = { timezoneExpanded = !timezoneExpanded },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("capture_timezone_toggle"),
                    ) { Text(stringResource(R.string.capture_timezone)) }
                    if (timezoneExpanded) {
                        val offset = selectedDate.atTime(selectedTime).atZone(zone).offset
                        Text(
                            stringResource(R.string.capture_timezone_details, zone.id, offset),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 12.dp).testTag("capture_timezone_details"),
                        )
                    }
                    if (timeError) AccessibleFeedback(timeErrorMessage, Modifier.testTag("mood_time_error"))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selected = captureTimestampForSelection(
                            current.toLocalDateTime(), selectedDate, time.hour, time.minute,
                        )
                        if (onTime(selected, zone)) dialogOpen = false
                    },
                    modifier = Modifier.testTag("capture_timestamp_done"),
                ) { Text(stringResource(R.string.capture_timestamp_done)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { dialogOpen = false },
                    modifier = Modifier.testTag("capture_timestamp_cancel"),
                ) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

internal fun captureTimestampForSelection(
    original: LocalDateTime,
    selectedDate: LocalDate,
    selectedHour: Int,
    selectedMinute: Int,
): LocalDateTime {
    val selectedTime = if (selectedHour == original.hour && selectedMinute == original.minute) {
        original.toLocalTime()
    } else {
        LocalTime.of(selectedHour, selectedMinute)
    }
    return selectedDate.atTime(selectedTime)
}
