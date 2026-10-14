package com.luma.lifeos.ui.review

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luma.lifeos.R
import com.luma.lifeos.domain.model.LifeEventRecord
import com.luma.lifeos.domain.repository.WeeklyReviewRepository
import com.luma.lifeos.domain.review.*
import com.luma.lifeos.ui.common.AccessibleFeedback
import com.luma.lifeos.ui.timeline.EventCard
import java.time.ZoneId
import java.time.format.FormatStyle

@Composable
fun ReviewSourcesRoute(repository: WeeklyReviewRepository, dates: ReviewDates, zone: ZoneId, metric: ReviewMetric,
    onBack: () -> Unit, onEvent: (LifeEventRecord) -> Unit, onMemory: ((LifeEventRecord) -> Unit)? = null) {
    var offset by rememberSaveable(dates, metric) { mutableIntStateOf(0) }
    var retry by remember { mutableIntStateOf(0) }
    val format = com.luma.lifeos.language.localizedDate(FormatStyle.MEDIUM)
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).testTag("screen_review_sources")) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        Text(stringResource(R.string.review_source_heading), style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() })
        Text(stringResource(metric.kind.label()))
        Text(stringResource(R.string.memory_review_range, dates.start.format(format), dates.through.format(format)))
        Text(zone.id, style = MaterialTheme.typography.bodySmall)
        key(dates, metric, zone, offset, retry) {
            val flow = remember(repository) { observeReviewSources(repository, dates, zone, metric, offset) }
            val state by flow.collectAsStateWithLifecycle(ReviewSourcesState.Loading)
            LazyColumn(Modifier.weight(1f).testTag("review_source_list"), contentPadding = PaddingValues(top = 12.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (val current = state) {
                    ReviewSourcesState.Loading -> item { CircularProgressIndicator() }
                    ReviewSourcesState.Error -> item {
                        AccessibleFeedback(stringResource(R.string.review_error))
                        TextButton(onClick = { retry++ }) { Text(stringResource(R.string.retry)) }
                    }
                    is ReviewSourcesState.Data -> {
                        val page = current.page
                        item { AccessibleFeedback(stringResource(R.string.search_results, page.totalCount),
                            Modifier.testTag("review_source_count"), isError = false) }
                        if (page.totalCount > 0) item {
                            Text(stringResource(R.string.entry_order_oldest_first), style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.testTag("review_source_order"))
                        }
                        if (page.records.isEmpty()) item { Text(stringResource(R.string.review_no_sources)) }
                        if (page.offset > 0) item { TextButton(onClick = { offset = maxOf(0, page.offset - 50) }, modifier = Modifier.testTag("review_source_previous")) { Text(stringResource(R.string.search_earlier)) } }
                        items(page.records, key = { it.event.id.toString() }, contentType = { it.event.type }) { row ->
                            Column {
                                Text(row.event.occurredAt.atZone(zone).toLocalDate().format(format))
                                EventCard(row, zone, { onEvent(row) }, onMemory?.let { { it(row) } })
                            }
                        }
                        if (page.offset + page.records.size < page.totalCount) item {
                            TextButton(onClick = { offset = page.offset + 50 }, modifier = Modifier.testTag("review_source_next")) { Text(stringResource(R.string.search_later)) }
                        }
                    }
                }
            }
        }
    }
}
internal fun ReviewMetricKind.label(): Int = when (this) {
    ReviewMetricKind.ENTRIES -> R.string.review_entries
    ReviewMetricKind.JOURNALS -> R.string.review_journals
    ReviewMetricKind.MEMORIES -> R.string.review_memories
    ReviewMetricKind.MOOD -> R.string.review_mood
    ReviewMetricKind.ENERGY -> R.string.mood_energy
    ReviewMetricKind.STRESS -> R.string.mood_stress
    ReviewMetricKind.EMOTION -> R.string.mood_emotions
    ReviewMetricKind.PERSON -> R.string.people
    ReviewMetricKind.PLACE -> R.string.places
    ReviewMetricKind.ACTIVITY -> R.string.review_activity
}
