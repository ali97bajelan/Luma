package com.luma.lifeos.ui.memory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luma.lifeos.R
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.EventType
import com.luma.lifeos.domain.repository.MemoryRepository
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun MemoryReviewRoute(repository: MemoryRepository, date: LocalDate, zone: ZoneId,
    onMemory: (EventId) -> Unit, onJournal: (EventId) -> Unit) {
    var retry by remember { mutableIntStateOf(0) }
    val week = memoryWeekRange(date, zone)
    key(repository, week, retry) {
        val observation = remember { observeMemoryReview(repository, date, zone) }
        val state by observation.collectAsStateWithLifecycle(MemoryReviewState.Loading)
        val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        Box(Modifier.fillMaxSize().testTag("screen_review")) {
            LazyColumn(Modifier.fillMaxSize().testTag("memory_review_list"),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Text(stringResource(R.string.memory_review_heading), style = MaterialTheme.typography.headlineLarge)
                    Text(stringResource(R.string.memory_review_intro))
                    Text(stringResource(R.string.memory_review_range, week.start.atZone(zone).toLocalDate().format(format),
                        week.endExclusive.atZone(zone).toLocalDate().minusDays(1).format(format)))
                }
                when (val current = state) {
                    MemoryReviewState.Loading -> item { CircularProgressIndicator() }
                    MemoryReviewState.Empty -> item { Text(stringResource(R.string.memory_review_empty)) }
                    MemoryReviewState.Error -> item {
                        Text(stringResource(R.string.memory_review_error), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { retry++ }) { Text(stringResource(R.string.retry)) }
                    }
                    is MemoryReviewState.Data -> items(current.records, key = { it.event.id.toString() }) { record ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                record.memory?.let { MemoryStatus(it) }
                                Text(record.event.occurredAt.atZone(zone).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)))
                                record.event.title?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                                record.journal?.let { Text(it.body, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                                record.mood?.let {
                                    Text(stringResource(R.string.memory_mood_score, it.score))
                                    it.note?.let { note -> Text(note, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                                }
                                record.memory?.note?.let { Text(it, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                                if (record.journal != null) TextButton(onClick = { onJournal(record.event.id) }) {
                                    Text(stringResource(R.string.journal_open))
                                }
                                if (record.canPromoteToMemory ||
                                    (record.event.type == EventType.MEMORY && record.event.isEditable && record.memory != null)) {
                                    TextButton(onClick = { onMemory(record.event.id) }) { Text(stringResource(R.string.memory_edit)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
