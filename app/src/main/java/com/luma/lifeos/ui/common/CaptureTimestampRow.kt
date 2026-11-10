package com.luma.lifeos.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.luma.lifeos.ui.common.LumaTextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.calendar.rememberCalendarPolicy
import com.luma.lifeos.language.rememberDisplayTimePolicy
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
    val displayTime = rememberDisplayTimePolicy()
    val use24Hour = displayTime.use24Hour
    val dateText = if (current.toLocalDate() == LocalDate.now(zone)) {
        stringResource(R.string.capture_timestamp_today, current.toLocalTime().format(displayTime.time()))
    } else {
        stringResource(R.string.capture_timestamp_value,
            displayTime.formatDate(current.toLocalDate(), FormatStyle.MEDIUM), current.toLocalTime().format(displayTime.time()))
    }
    val timeErrorMessage = stringResource(R.string.mood_time_error)
    var dialogOpen by rememberSaveable(milliseconds) { mutableStateOf(false) }
    var timezoneExpanded by rememberSaveable(milliseconds) { mutableStateOf(false) }

    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp)
            .semantics { if (timeError) error(timeErrorMessage) }
            .testTag("capture_timestamp"),
        shape = CircleShape,
        color = colors.surfaceContainerLow,
        contentColor = colors.onSurface,
        border = BorderStroke(1.dp, colors.outlineVariant),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(LumaUi.RelatedGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LumaTextButton(
                enabled = enabled,
                onClick = { dialogOpen = true },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("capture_timestamp_value"),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(LumaUi.ContentGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_clock),
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(dateText, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                }
            }
            LumaTextButton(
                enabled = enabled,
                onClick = { dialogOpen = true },
                modifier = Modifier.heightIn(min = 48.dp).testTag("capture_timestamp_change"),
            ) { Text(stringResource(R.string.capture_timestamp_change)) }
        }
    }
    if (timeError && !dialogOpen) {
        AccessibleFeedback(timeErrorMessage, Modifier.testTag("mood_time_error"))
    }

    if (dialogOpen) {
        val yearRange = minOf(1900, current.year)..maxOf(2100, current.year)
        val (minDate, maxDate) = calendarPickerBounds(yearRange)
        val date = rememberLumaCalendarDatePickerState(
            initialDate = current.toLocalDate(),
            displayedDate = current.toLocalDate(),
            minDate = minDate,
            maxDate = maxDate,
        )
        val calendarPolicy = rememberCalendarPolicy()
        val isSelectable: (LocalDate) -> Boolean = { true }
        val time = rememberTimePickerState(
            initialHour = current.hour,
            initialMinute = current.minute,
            is24Hour = use24Hour,
        )
        val selectedDate = date.selectedDate ?: current.toLocalDate()
        val selectedTime = captureTimestampForSelection(
            current.toLocalDateTime(), selectedDate, time.hour, time.minute,
        ).toLocalTime()
        LumaAlertDialog(
            onDismissRequest = { dialogOpen = false },
            title = { Text(stringResource(R.string.mood_timestamp)) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())
                        .testTag("capture_timestamp_dialog"),
                    verticalArrangement = Arrangement.spacedBy(LumaUi.ContentGap),
                ) {
                    Text(stringResource(R.string.capture_timestamp_date), style = MaterialTheme.typography.titleSmall)
                    LumaCalendarDatePicker(
                        state = date,
                        policy = calendarPolicy,
                        isSelectable = isSelectable,
                        modifier = Modifier.testTag("capture_timestamp_date_picker"),
                    )
                    Text(stringResource(R.string.capture_timestamp_time), style = MaterialTheme.typography.titleSmall)
                    TimeInput(state = time, modifier = Modifier.testTag("capture_timestamp_time_input"))
                    LumaTextButton(
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
                LumaTextButton(
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
                LumaTextButton(
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
