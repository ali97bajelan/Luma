package com.luma.lifeos.ui.today

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.model.EventSource
import com.luma.lifeos.domain.model.EventType
import com.luma.lifeos.domain.model.LifeEvent
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    date: LocalDate,
    zone: ZoneId,
    state: TodayUiState,
    onSelectDate: (LocalDate) -> Unit,
    onToday: () -> Unit,
    onRetry: () -> Unit,
) {
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("screen_today"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.labelLarge)
                Text(stringResource(R.string.today), style = MaterialTheme.typography.headlineLarge)
                TextButton(onClick = { pickingDate = true }, modifier = Modifier.testTag("day_picker")) {
                    Text(date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                        modifier = Modifier.testTag("day_selected"))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { onSelectDate(date.minusDays(1)) }, modifier = Modifier.testTag("day_previous")) {
                        Text(stringResource(R.string.previous_day))
                    }
                    TextButton(onClick = onToday, modifier = Modifier.testTag("day_today")) {
                        Text(stringResource(R.string.today))
                    }
                    TextButton(onClick = { onSelectDate(date.plusDays(1)) }, modifier = Modifier.testTag("day_next")) {
                        Text(stringResource(R.string.next_day))
                    }
                }
            }
        }
        when (state) {
            TodayUiState.Loading -> item {
                Column(Modifier.testTag("today_loading"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.today_loading))
                }
            }
            TodayUiState.Empty -> item {
                Card(Modifier.fillMaxWidth().testTag("today_empty")) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.today_empty_heading), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.today_empty_body))
                        Text(stringResource(R.string.local_first), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            TodayUiState.Error -> item {
                Column(Modifier.testTag("today_error"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.today_error))
                    TextButton(onClick = onRetry, modifier = Modifier.testTag("today_retry")) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
            is TodayUiState.Data -> {
                item {
                    Card(Modifier.fillMaxWidth().testTag("today_summary")) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(pluralStringResource(R.plurals.daily_events, state.events.size, state.events.size),
                                style = MaterialTheme.typography.titleLarge)
                            EventType.entries.forEach { type ->
                                state.counts[type]?.let { count ->
                                    Text(stringResource(R.string.event_type_count, stringResource(type.label()), count))
                                }
                            }
                        }
                    }
                }
                items(state.events, key = { it.id.toString() }) { event -> EventRow(event, zone) }
            }
        }
    }
    if (pickingDate) {
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = pickerMillis(date),
            yearRange = minOf(1900, date.year)..maxOf(2100, date.year),
        )
        DatePickerDialog(
            modifier = Modifier.testTag("date_picker_dialog"),
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(enabled = picker.selectedDateMillis != null, onClick = {
                    picker.selectedDateMillis?.let { onSelectDate(pickerDate(it)) }
                    pickingDate = false
                }) { Text(stringResource(R.string.select_date)) }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state = picker) }
    }
}

@Composable
private fun EventRow(event: LifeEvent, zone: ZoneId) {
    Card(Modifier.fillMaxWidth().testTag("event_${event.id}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(event.type.label()), style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f))
                Text(event.occurredAt.atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm")),
                    style = MaterialTheme.typography.labelLarge)
            }
            event.title?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            event.note?.takeIf { it.isNotBlank() }?.let {
                Text(it, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (event.source != EventSource.MANUAL) {
                Text(stringResource(when (event.source) {
                    EventSource.HEALTH_CONNECT -> R.string.source_health
                    EventSource.USAGE_STATS -> R.string.source_usage
                    EventSource.MANUAL -> R.string.source_manual
                }), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@StringRes
private fun EventType.label(): Int = when (this) {
    EventType.MOOD -> R.string.event_mood
    EventType.JOURNAL -> R.string.event_journal
    EventType.MEMORY -> R.string.event_memory
    EventType.ACTIVITY -> R.string.event_activity
    EventType.APP_USAGE -> R.string.event_app_usage
}
