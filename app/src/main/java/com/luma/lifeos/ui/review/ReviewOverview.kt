package com.luma.lifeos.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.model.LifeEventRecord
import com.luma.lifeos.domain.repository.WeeklyReviewRepository
import com.luma.lifeos.domain.review.ReviewActivity
import com.luma.lifeos.domain.review.ReviewDates
import com.luma.lifeos.domain.review.ReviewMetric
import com.luma.lifeos.domain.review.ReviewStatistics
import com.luma.lifeos.domain.review.WeeklyReview
import com.luma.lifeos.ui.common.LumaSectionHeader
import com.luma.lifeos.ui.common.LumaSectionSurface
import com.luma.lifeos.ui.common.entryLogCounts
import com.luma.lifeos.ui.integrations.IntegrationAvailabilityState
import com.luma.lifeos.ui.integrations.MetricAbsence
import com.luma.lifeos.ui.integrations.MetricPeriod
import com.luma.lifeos.ui.integrations.label
import com.luma.lifeos.ui.integrations.metricAbsence

internal fun mostSampledActivity(activities: List<ReviewActivity>): ReviewActivity? =
    activities.minWithOrNull(compareByDescending<ReviewActivity> { it.total.samples }
        .thenBy { it.total.type }.thenBy { it.total.unit }.thenBy { it.total.source.name })

@Composable
internal fun ReviewOverview(
    report: WeeklyReview,
    repository: WeeklyReviewRepository,
    onEvent: (LifeEventRecord) -> Unit,
    onSources: (ReviewDates, ReviewMetric) -> Unit,
    habitLogCount: Int? = null,
    availability: IntegrationAvailabilityState = IntegrationAvailabilityState(),
    onIntegrations: () -> Unit = {},
    onCheckIn: () -> Unit = {},
    habitState: ReviewHabitsState? = null,
    onHabits: () -> Unit = {},
) {
    val current = report.current
    Column(
        Modifier.fillMaxWidth().testTag("review_overview"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LumaSectionSurface(modifier = Modifier.testTag("review_overview_personal")) {
            LumaSectionHeader(
                title = stringResource(R.string.review_overview),
                modifier = Modifier.testTag("review_overview_heading"),
            )
            Text(
                entryLogCounts(current.records, habitLogCount),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag("review_overview_counts"),
            )
            ReviewHabitsOverviewMetric(habitState, onHabits)

            val mood = current.mood?.score
            val moodValue = mood?.let {
                stringResource(R.string.review_scale_value, reviewNumber(it.average))
            } ?: stringResource(R.string.metric_no_check_ins_week)
            val samples = mood?.let { stringResource(R.string.review_sample_count, it.samples) }
            ReviewOverviewMetric(
                label = stringResource(R.string.review_mood),
                value = moodValue,
                tag = "review_overview_mood",
                absence = if (mood == null) MetricAbsence.NO_CHECK_INS else null,
                detail = samples,
            )
            if (mood == null) {
                TextButton(onClick = onCheckIn, modifier = Modifier.heightIn(min = 48.dp).testTag("review_overview_checkin")) {
                    Text(stringResource(R.string.review_checkin_action))
                }
            }
            Text(
                stringResource(R.string.review_overview_catalog, current.people.size, current.places.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("review_overview_catalog"),
            )
        }

        ReviewOverviewMemory(report, repository, onEvent)
        ReviewOverviewSignals(report, availability, onSources, onIntegrations)
    }
}

@Composable
private fun ReviewHabitsOverviewMetric(state: ReviewHabitsState?, onHabits: () -> Unit) {
    val comparison = (state as? ReviewHabitsState.Data)?.comparison ?: return
    val rows = comparison.rows
    if (rows.isEmpty()) return
    val repetitions = rows.sumOf { it.currentRepetitions }
    val scheduledDays = rows.sumOf { it.currentScheduledDays }
    val repetitionText = pluralStringResource(
        R.plurals.review_habit_repetitions,
        repetitions,
        reviewNumber(repetitions),
    )
    val scheduledText = pluralStringResource(
        R.plurals.review_habit_scheduled_days,
        scheduledDays,
        reviewNumber(scheduledDays),
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Column(
        Modifier.fillMaxWidth().testTag("review_overview_habits"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            stringResource(R.string.review_habits_overview_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.review_habits_summary, repetitionText, scheduledText),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("review_overview_habits_summary"),
        )
        if (rows.none { it.current != null }) {
            Text(
                stringResource(R.string.review_habits_no_records_period),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(
            onClick = onHabits,
            modifier = Modifier.heightIn(min = 48.dp).testTag("review_overview_habits_open"),
        ) {
            Text(stringResource(R.string.review_view_habits))
        }
    }
}

@Composable
internal fun ReviewOverviewMetric(
    label: String,
    value: String,
    tag: String,
    modifier: Modifier = Modifier,
    dense: Boolean = false,
    absence: MetricAbsence? = null,
    detail: String? = null,
) {
    val unavailable = absence != null
    Column(
        modifier.semantics(mergeDescendants = true) {}.testTag(tag),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = if (dense || unavailable) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleLarge,
            fontWeight = if (dense || unavailable) FontWeight.Normal else FontWeight.Medium,
            color = if (unavailable) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
        detail?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
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
