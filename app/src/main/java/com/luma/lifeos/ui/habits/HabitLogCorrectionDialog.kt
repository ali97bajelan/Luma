package com.luma.lifeos.ui.habits

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
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
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HabitLogCorrectionDialog(
    draft: HabitLogCorrectionDraft,
    busy: Boolean,
    error: HabitCorrectionError?,
    onDate: (java.time.LocalDate) -> Unit,
    onTime: (LocalTime?) -> Unit,
    onSave: () -> Unit,
    onRequestClose: () -> Unit,
    onPickerOpenChanged: (Boolean) -> Unit,
) {
    var dateOpen by rememberSaveable { mutableStateOf(false) }
    var timeOpen by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val use24Hour = DateFormat.is24HourFormat(context)
    fun openDate() { dateOpen = true; onPickerOpenChanged(true) }
    fun closeDate() { dateOpen = false; onPickerOpenChanged(false) }
    fun openTime() { timeOpen = true; onPickerOpenChanged(true) }
    fun closeTime() { timeOpen = false; onPickerOpenChanged(false) }
    BackHandler(enabled = dateOpen || timeOpen) {
        if (dateOpen) closeDate() else closeTime()
    }

    val dateError = if (error == HabitCorrectionError.DATE) stringResource(R.string.habit_correction_date_invalid) else null
    val timeError = if (error == HabitCorrectionError.TIME) stringResource(R.string.habit_correction_time_invalid) else null
    val generalError = when (error) {
        HabitCorrectionError.SAVE -> stringResource(R.string.habit_correction_error)
        HabitCorrectionError.MISSING -> stringResource(R.string.habit_correction_missing)
        else -> null
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onRequestClose() },
        title = { Text(stringResource(R.string.habit_log_correction)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    enabled = !busy,
                    onClick = ::openDate,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .semantics { dateError?.let(::error) }
                        .testTag("habit_correction_date"),
                ) {
                    Text("${stringResource(R.string.habit_correction_date)} · ${draft.selectedDate.format(localizedDate(FormatStyle.MEDIUM))}")
                }
                dateError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("habit_correction_date_error"))
                }
                TextButton(
                    enabled = !busy,
                    onClick = ::openTime,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .semantics { timeError?.let(::error) }
                        .testTag("habit_correction_time"),
                ) {
                    val value = draft.selectedTime?.format(localizedTime(use24Hour))
                        ?: stringResource(R.string.habit_correction_no_time)
                    Text("${stringResource(R.string.habit_correction_time)} · $value")
                }
                TextButton(
                    enabled = !busy,
                    onClick = { onTime(null) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("habit_correction_no_time"),
                ) { Text(stringResource(R.string.habit_correction_clear_time)) }
                timeError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("habit_correction_time_error"))
                }
                generalError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("habit_correction_error"))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && error != HabitCorrectionError.MISSING,
                onClick = onSave,
                modifier = Modifier.testTag("habit_save_correction"),
            ) {
                Text(stringResource(when {
                    busy -> R.string.saving
                    error == HabitCorrectionError.SAVE -> R.string.retry
                    else -> R.string.save
                }))
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onRequestClose, modifier = Modifier.testTag("habit_cancel_correction")) {
                Text(stringResource(R.string.cancel))
            }
        },
    )

    if (dateOpen) {
        val date = rememberDatePickerState(
            initialSelectedDateMillis = pickerMillis(draft.selectedDate),
            yearRange = minOf(1900, draft.selectedDate.year)..maxOf(2100, draft.selectedDate.year),
        )
        DatePickerDialog(
            onDismissRequest = ::closeDate,
            confirmButton = {
                TextButton(enabled = date.selectedDateMillis != null, onClick = {
                    date.selectedDateMillis?.let { onDate(pickerDate(it)) }
                    closeDate()
                }, modifier = Modifier.testTag("habit_correction_select_date")) {
                    Text(stringResource(R.string.select_date))
                }
            },
            dismissButton = { TextButton(onClick = ::closeDate) { Text(stringResource(R.string.cancel)) } },
            modifier = Modifier.testTag("habit_correction_date_picker"),
        ) { DatePicker(state = date) }
    }
    if (timeOpen) {
        val selected = draft.selectedTime ?: LocalTime.NOON
        val time = rememberTimePickerState(initialHour = selected.hour, initialMinute = selected.minute, is24Hour = use24Hour)
        AlertDialog(
            onDismissRequest = ::closeTime,
            title = { Text(stringResource(R.string.habit_correction_time)) },
            text = { TimeInput(state = time, modifier = Modifier.testTag("habit_correction_time_picker")) },
            confirmButton = {
                TextButton(onClick = {
                    onTime(LocalTime.of(time.hour, time.minute))
                    closeTime()
                }, modifier = Modifier.testTag("habit_correction_select_time")) {
                    Text(stringResource(R.string.select_time))
                }
            },
            dismissButton = { TextButton(onClick = ::closeTime) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
