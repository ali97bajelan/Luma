package com.luma.lifeos.ui.review

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
        "catalog" to R.string.review_people_places, "usage" to R.string.review_usage,
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
    Column(Modifier.fillMaxWidth().testTag("review_overview"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.review_overview), style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() }.testTag("review_overview_heading"))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BoxWithConstraints {
                    val stack = maxWidth < 280.dp || LocalDensity.current.fontScale >= 1.5f
                    val mood = current.mood?.score
                    val moodValue = mood?.let { stringResource(R.string.review_scale_value, reviewNumber(it.average)) }
                        ?: stringResource(R.string.review_unavailable)
                    val samples = mood?.let { stringResource(R.string.review_sample_count, it.samples) }
                    if (stack) Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        ReviewOverviewMetric(stringResource(R.string.review_mood), moodValue, "review_overview_mood", detail = samples)
                        ReviewOverviewMetric(stringResource(R.string.review_saved_usage), reviewDuration(current.usage?.totalMillis), "review_overview_usage")
                    } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ReviewOverviewMetric(stringResource(R.string.review_mood), moodValue, "review_overview_mood",
                            modifier = Modifier.weight(1f), detail = samples)
                        ReviewOverviewMetric(stringResource(R.string.review_saved_usage), reviewDuration(current.usage?.totalMillis),
                            "review_overview_usage", modifier = Modifier.weight(1f))
                    }
                }
                if (current.usage != null) Text(reviewUsageCoverageSummary(report), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("review_overview_usage_coverage"))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
                Text(stringResource(R.string.review_overview_catalog, current.people.size, current.places.size),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("review_overview_catalog"))
            }
        }
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_bookmark_outline), contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LatestMemory(report, repository, onEvent)
                }
            }
        }
    }
}

@Composable
private fun ReviewOverviewMetric(label: String, value: String, tag: String, modifier: Modifier = Modifier,
    dense: Boolean = false, detail: String? = null) {
    val unavailable = value == stringResource(R.string.review_unavailable)
    Column(modifier.semantics(mergeDescendants = true) {}.testTag(tag), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = when {
            dense -> MaterialTheme.typography.bodyMedium
            unavailable -> MaterialTheme.typography.titleMedium
            else -> MaterialTheme.typography.titleLarge
        }, fontWeight = if (dense || unavailable) FontWeight.Normal else FontWeight.SemiBold,
            color = if (unavailable) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
        detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
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
    Text(stringResource(R.string.review_latest_memory), style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (report.current.memories == 0) {
        Text(stringResource(R.string.review_memory_empty_short), style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("review_overview_memory_empty"))
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
            if (row == null) Text(stringResource(R.string.review_memory_empty_short), Modifier.testTag("review_overview_memory_empty"))
            else {
                val date = row.event.occurredAt.atZone(report.period.zone)
                    .format(com.luma.lifeos.language.localizedDateTime(FormatStyle.MEDIUM))
                TextButton(onClick = { onEvent(row) }, contentPadding = PaddingValues(vertical = 4.dp), modifier = Modifier.fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp).testTag("review_overview_memory_open")) {
                    Column(Modifier.weight(1f)) {
                        Text(row.event.title?.takeIf { it.isNotBlank() } ?: stringResource(row.event.type.label()))
                        Text(date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(painterResource(R.drawable.ic_chevron_forward), contentDescription = null)
                }
            }
        }
    }
}
