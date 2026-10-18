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
import com.luma.lifeos.ui.common.entryLogCounts
import com.luma.lifeos.ui.common.AccessibleFeedback
import com.luma.lifeos.ui.integrations.IntegrationAvailabilityState
import com.luma.lifeos.ui.integrations.MetricAbsence
import com.luma.lifeos.ui.integrations.MetricPeriod
import com.luma.lifeos.ui.integrations.label
import com.luma.lifeos.ui.integrations.metricAbsence
import com.luma.lifeos.ui.integrations.needsAccessSecondary
import java.time.format.FormatStyle

internal fun mostSampledActivity(activities: List<ReviewActivity>): ReviewActivity? =
    activities.minWithOrNull(compareByDescending<ReviewActivity> { it.total.samples }
        .thenBy { it.total.type }.thenBy { it.total.unit }.thenBy { it.total.source.name })

@Composable
internal fun ReviewSectionNavigation(selected: String?, onSection: (String) -> Unit, includeHabits: Boolean = false) {
    val sections = buildList {
        add("mood" to R.string.review_mood)
        add("memories" to R.string.review_memories)
        add("catalog" to R.string.review_people_places)
        add("usage" to R.string.review_usage)
        add("activity" to R.string.review_activity)
        if (includeHabits) add("habits" to R.string.review_habits)
    }
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
    onEvent: (LifeEventRecord) -> Unit, onSources: (ReviewDates, ReviewMetric) -> Unit, habitLogCount: Int? = null,
    availability: IntegrationAvailabilityState = IntegrationAvailabilityState(), onIntegrations: () -> Unit = {},
    onCheckIn: () -> Unit = {}) {
    val current = report.current
    Column(Modifier.fillMaxWidth().testTag("review_overview"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.review_overview), style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() }.testTag("review_overview_heading"))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(entryLogCounts(current.records, habitLogCount), style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.testTag("review_overview_counts"))
                BoxWithConstraints {
                    val stack = maxWidth < 280.dp || LocalDensity.current.fontScale >= 1.5f
                    val mood = current.mood?.score
                    val usageAbsence = current.usageAbsence(availability)
                    val usage = current.usage
                    val usageMissing = if (usage != null && usage.totalMillis == null)
                        stringResource(R.string.review_usage_partial_total_unknown)
                    else stringResource((usageAbsence ?: MetricAbsence.NO_SAVED_DATA).label(MetricPeriod.WEEK))
                    val usageValue = reviewDuration(usage?.totalMillis, usageMissing)
                    val moodValue = mood?.let { stringResource(R.string.review_scale_value, reviewNumber(it.average)) }
                        ?: stringResource(R.string.metric_no_check_ins_week)
                    val samples = mood?.let { stringResource(R.string.review_sample_count, it.samples) }
                    if (stack) Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        ReviewOverviewMetric(stringResource(R.string.review_mood), moodValue, "review_overview_mood",
                            absence = if (mood == null) MetricAbsence.NO_CHECK_INS else null, detail = samples)
                        ReviewOverviewMetric(stringResource(R.string.review_saved_usage), usageValue,
                            "review_overview_usage", absence = usageAbsence)
                    } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ReviewOverviewMetric(stringResource(R.string.review_mood), moodValue, "review_overview_mood",
                            modifier = Modifier.weight(1f), absence = if (mood == null) MetricAbsence.NO_CHECK_INS else null, detail = samples)
                        ReviewOverviewMetric(stringResource(R.string.review_saved_usage), usageValue,
                            "review_overview_usage", modifier = Modifier.weight(1f), absence = usageAbsence)
                    }
                }
                if (current.mood == null) TextButton(onClick = onCheckIn, modifier = Modifier.testTag("review_overview_checkin")) {
                    Text(stringResource(R.string.review_checkin_action))
                }
                if (current.usage?.totalMillis == null) TextButton(onClick = onIntegrations,
                    modifier = Modifier.testTag("review_overview_usage_integrations")) {
                    Text(stringResource(R.string.metric_integrations_action))
                }
                if (availability.usage.needsAccessSecondary()) Text(stringResource(R.string.integration_access_also_needed),
                    modifier = Modifier.testTag("review_overview_usage_access_secondary"), style = MaterialTheme.typography.bodySmall)
                if (current.usage != null) Text(reviewUsageCoverageSummary(report, availability), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("review_overview_usage_coverage"))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val activity = mostSampledActivity(current.activities)
                val value = activity?.total?.let {
                    stringResource(R.string.review_overview_activity_value,
                        activityTypeDisplayName(it.type, it.unit, it.source), reviewNumber(it.total),
                        activityUnitDisplayName(it.type, it.unit, it.source), it.samples, stringResource(it.source.label()))
                } ?: stringResource(R.string.metric_no_saved_measurements)
                ReviewOverviewMetric(stringResource(R.string.review_activity), value, "review_overview_activity", dense = true,
                    absence = if (activity == null) MetricAbsence.NO_SAVED_DATA else null)
                if (activity == null) TextButton(onClick = onIntegrations,
                    modifier = Modifier.testTag("review_overview_activity_integrations")) {
                    Text(stringResource(R.string.metric_integrations_action))
                }
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
    dense: Boolean = false, absence: MetricAbsence? = null, detail: String? = null) {
    val unavailable = absence != null
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

private fun ReviewStatistics.usageAbsence(availability: IntegrationAvailabilityState): MetricAbsence? {
    val savedUsage = usage
    return if (savedUsage != null && savedUsage.totalMillis == null) MetricAbsence.NO_SAVED_DATA
    else metricAbsence(false, false, savedUsage?.totalMillis != null, availability.usage)
}

@Composable
internal fun reviewUsageCoverageSummary(report: WeeklyReview, availability: IntegrationAvailabilityState): String {
    val usage = report.current.usage
    if (usage == null) {
        val absence = metricAbsence(false, false, false, availability.usage) ?: MetricAbsence.NO_SAVED_DATA
        return stringResource(absence.label(MetricPeriod.WEEK))
    }
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
