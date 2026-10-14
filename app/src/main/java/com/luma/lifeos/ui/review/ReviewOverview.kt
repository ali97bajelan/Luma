package com.luma.lifeos.ui.review

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.luma.lifeos.language.activityTypeDisplayName
import com.luma.lifeos.language.activityUnitDisplayName
import com.luma.lifeos.ui.timeline.label
import com.luma.lifeos.ui.common.AccessibleFeedback
import java.time.format.FormatStyle

internal fun mostSampledActivity(activities: List<ReviewActivity>): ReviewActivity? =
    activities.minWithOrNull(compareByDescending<ReviewActivity> { it.total.samples }
        .thenBy { it.total.type }.thenBy { it.total.unit }.thenBy { it.total.source.name })

@Composable
internal fun ReviewSectionNavigation(selected: String?, onSection: (String) -> Unit) {
    val sections = listOf("mood" to R.string.review_mood, "memories" to R.string.review_memories,
        "catalog" to R.string.review_people_places, "usage" to R.string.review_saved_usage,
        "activity" to R.string.review_activity)
    val requesters = remember { sections.associate { (key, _) -> key to BringIntoViewRequester() } }
    LaunchedEffect(selected) { selected?.let { requesters[it]?.bringIntoView() } }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        sections.forEach { (key, label) ->
            FilterChip(selected = selected == key, onClick = { onSection(key) },
                label = { Text(stringResource(label)) }, modifier = Modifier.heightIn(min = 48.dp)
                    .bringIntoViewRequester(requesters.getValue(key)).testTag("review_section_$key"))
        }
    }
}

@Composable
internal fun ReviewOverview(report: WeeklyReview, repository: WeeklyReviewRepository,
    onEvent: (LifeEventRecord) -> Unit, onSources: (ReviewDates, ReviewMetric) -> Unit) {
    val current = report.current
    Card(Modifier.fillMaxWidth().testTag("review_overview")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.review_overview), style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }.testTag("review_overview_heading"))
            Text(stringResource(R.string.review_missing_context), style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("review_missing_context"))
            ReviewOverviewMetric(stringResource(R.string.review_mood), reviewScale(current.mood?.score), "review_overview_mood")
            LatestMemory(report, repository, onEvent)
            ReviewOverviewMetric(stringResource(R.string.review_saved_usage), reviewDuration(current.usage?.totalMillis), "review_overview_usage")
            if (current.usage != null) Text(reviewUsageCoverageSummary(report), style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("review_overview_usage_coverage"))
            val activity = mostSampledActivity(current.activities)
            val value = activity?.total?.let {
                stringResource(R.string.review_overview_activity_value,
                    activityTypeDisplayName(it.type, it.unit, it.source), reviewNumber(it.total),
                    activityUnitDisplayName(it.type, it.unit, it.source), it.samples, stringResource(it.source.label()))
            } ?: stringResource(R.string.review_unavailable)
            ReviewOverviewMetric(stringResource(R.string.review_activity), value, "review_overview_activity", dense = true)
            activity?.total?.let { total ->
                TextButton(onClick = { onSources(report.period.current, ReviewMetric(ReviewMetricKind.ACTIVITY,
                    activity = ReviewActivityKey(total.type, total.unit, total.source))) },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("review_overview_activity_sources")) {
                    Text(stringResource(R.string.review_view_sources))
                }
            }
            ReviewOverviewMetric(stringResource(R.string.review_people_places),
                stringResource(R.string.review_overview_catalog, current.people.size, current.places.size),
                "review_overview_catalog", dense = true)
        }
    }
}

@Composable
private fun ReviewOverviewMetric(label: String, value: String, tag: String, dense: Boolean = false) {
    Text("$label · $value", style = if (dense) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
        modifier = Modifier.testTag(tag))
}

@Composable
internal fun reviewUsageCoverageSummary(report: WeeklyReview): String {
    val usage = report.current.usage
    if (usage == null) return stringResource(R.string.review_usage_no_data)
    val summary = stringResource(R.string.review_usage_summary, usage.days.size, report.period.current.dates.size, usage.completeDates)
    return if (usage.completeDates == report.period.current.dates.size) summary
        else summary + " · " + stringResource(R.string.review_usage_incomplete)
}

@Composable
private fun LatestMemory(report: WeeklyReview, repository: WeeklyReviewRepository, onEvent: (LifeEventRecord) -> Unit) {
    Text(stringResource(R.string.review_latest_memory), style = MaterialTheme.typography.labelMedium)
    if (report.current.memories == 0) {
        Text(stringResource(R.string.memory_review_empty), Modifier.testTag("review_overview_memory_empty"))
        return
    }
    var retry by remember { mutableIntStateOf(0) }
    val flow = remember(repository, report.period, report.current.memories, retry) {
        observeReviewSources(repository, report.period.current, report.period.zone,
            ReviewMetric(ReviewMetricKind.MEMORIES), maxOf(0, report.current.memories - 1), 1)
    }
    val state by flow.collectAsStateWithLifecycle(ReviewSourcesState.Loading)
    when (val current = state) {
        ReviewSourcesState.Loading -> CircularProgressIndicator(Modifier.testTag("review_overview_memory_loading"))
        ReviewSourcesState.Error -> {
            AccessibleFeedback(stringResource(R.string.review_error))
            TextButton(onClick = { retry++ }, modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("review_overview_memory_retry")) {
                Text(stringResource(R.string.retry))
            }
        }
        is ReviewSourcesState.Data -> {
            val row = current.page.records.lastOrNull()
            if (row == null) Text(stringResource(R.string.memory_review_empty), Modifier.testTag("review_overview_memory_empty"))
            else {
                val date = row.event.occurredAt.atZone(report.period.zone)
                    .format(com.luma.lifeos.language.localizedDateTime(FormatStyle.MEDIUM))
                TextButton(onClick = { onEvent(row) }, modifier = Modifier.fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp).testTag("review_overview_memory_open")) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(row.event.title?.takeIf { it.isNotBlank() } ?: stringResource(row.event.type.label()))
                        Text(date, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
