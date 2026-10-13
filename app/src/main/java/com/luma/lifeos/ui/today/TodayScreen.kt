package com.luma.lifeos.ui.today

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.EventType
import com.luma.lifeos.domain.model.LifeEventRecord
import com.luma.lifeos.ui.timeline.DayNavigation
import com.luma.lifeos.ui.timeline.EventCard
import com.luma.lifeos.ui.timeline.label
import com.luma.lifeos.ui.summary.DailySummaryState
import com.luma.lifeos.ui.summary.DailySummaryCard
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.FormatStyle

@Composable
fun TodayScreen(
    date: LocalDate,
    zone: ZoneId,
    state: TodayUiState,
    onSelectDate: (LocalDate) -> Unit,
    onToday: () -> Unit,
    onRetry: () -> Unit,
    onMoodClick: (EventId) -> Unit = {},
    onJournalClick: (EventId) -> Unit = {},
    onMemoryClick: (EventId) -> Unit = {},
    @StringRes heading: Int = R.string.today,
    screenTag: String = "screen_today",
    onEventClick: (EventId) -> Unit = {},
    summaryState: DailySummaryState? = null,
    onSummaryRetry: () -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(screenTag),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.labelLarge)
                val dayHeading = if (date == LocalDate.now(zone)) stringResource(heading) else
                    stringResource(R.string.today_selected_date,
                        date.format(com.luma.lifeos.language.localizedDate(FormatStyle.MEDIUM)))
                Text(dayHeading, style = MaterialTheme.typography.headlineLarge)
                DayNavigation(date, onSelectDate, onToday)
            }
        }
        summaryState?.let { item { DailySummaryCard(it, onSummaryRetry) } }
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
                    Column(Modifier.fillMaxWidth().testTag("today_summary"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(pluralStringResource(R.plurals.daily_events, state.events.size, state.events.size),
                                style = MaterialTheme.typography.titleLarge)
                            if (summaryState !is DailySummaryState.Data) EventType.entries.forEach { type ->
                                state.counts[type]?.let { count ->
                                    Text(stringResource(R.string.event_type_count, stringResource(type.label()), count))
                                }
                            }
                    }
                }
                items(state.events, key = { it.id.toString() }) { event ->
                    val record = state.records[event.id] ?: LifeEventRecord(event, state.moods[event.id],
                        state.journals[event.id], state.memories[event.id], state.people[event.id].orEmpty(), state.places[event.id])
                    EventCard(record, zone, onOpen = {
                        when {
                            record.canEditContent && record.journal != null -> onJournalClick(event.id)
                            record.canEditContent && event.type == EventType.MOOD -> onMoodClick(event.id)
                            record.canEditContent && event.type == EventType.MEMORY -> onMemoryClick(event.id)
                            else -> onEventClick(event.id)
                        }
                    }, onMemory = { onMemoryClick(event.id) })
                }
            }
        }
    }
}
