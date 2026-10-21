package com.luma.lifeos.ui.review

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.review.ReviewActivityKey
import com.luma.lifeos.domain.review.ReviewDates
import com.luma.lifeos.domain.review.ReviewMetric
import com.luma.lifeos.domain.review.ReviewMetricKind
import com.luma.lifeos.domain.review.ReviewStatistics
import com.luma.lifeos.domain.review.WeeklyReview
import com.luma.lifeos.language.activityTypeDisplayName
import com.luma.lifeos.language.activityUnitDisplayName
import com.luma.lifeos.language.formatActivityQuantity
import com.luma.lifeos.ui.common.DetailsDisclosure
import com.luma.lifeos.ui.common.LumaSectionHeader
import com.luma.lifeos.ui.common.LumaSectionSurface
import com.luma.lifeos.ui.integrations.IntegrationAvailabilityState
import com.luma.lifeos.ui.integrations.MetricAbsence
import com.luma.lifeos.ui.integrations.MetricPeriod
import com.luma.lifeos.ui.integrations.label
import com.luma.lifeos.ui.integrations.metricAbsence
import com.luma.lifeos.ui.integrations.needsAccessSecondary
import com.luma.lifeos.ui.timeline.label
import java.util.Locale

@Composable
internal fun ReviewOverviewSignals(
    report: WeeklyReview,
    availability: IntegrationAvailabilityState,
    onSources: (ReviewDates, ReviewMetric) -> Unit,
    onIntegrations: () -> Unit,
) {
    val current = report.current
    val usage = current.usage
    val usageAbsence = current.usageAbsence(availability)
    val usageMissing = if (usage != null && usage.totalMillis == null) {
        stringResource(R.string.review_usage_partial_total_unknown)
    } else {
        stringResource((usageAbsence ?: MetricAbsence.NO_SAVED_DATA).label(MetricPeriod.WEEK))
    }
    val usageValue = reviewDuration(usage?.totalMillis, usageMissing)
    val activity = mostSampledActivity(current.activities)
    val activityValue = activity?.total?.let { total ->
        stringResource(
            R.string.review_overview_activity_value,
            activityTypeDisplayName(total.type, total.unit, total.source),
            formatActivityQuantity(total.total, Locale.getDefault()),
            activityUnitDisplayName(total.type, total.unit, total.source),
            total.samples,
            stringResource(total.source.label()),
        )
    } ?: stringResource(R.string.metric_no_saved_measurements)
    val shouldOfferSetup = usage?.totalMillis == null || activity == null || availability.usage.needsAccessSecondary()

    LumaSectionSurface(modifier = Modifier.testTag("review_overview_signals")) {
        LumaSectionHeader(title = stringResource(R.string.review_saved_signals))
        ReviewOverviewMetric(
            label = stringResource(R.string.review_saved_usage),
            value = usageValue,
            tag = "review_overview_usage",
            absence = usageAbsence,
        )
        if (usage != null) {
            Text(
                reviewUsageCoverageSummary(report, availability),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("review_overview_usage_coverage"),
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        ReviewOverviewMetric(
            label = stringResource(R.string.review_activity),
            value = activityValue,
            tag = "review_overview_activity",
            dense = true,
            absence = if (activity == null) MetricAbsence.NO_SAVED_DATA else null,
        )
        activity?.total?.let { total ->
            TextButton(
                onClick = {
                    onSources(
                        report.period.current,
                        ReviewMetric(
                            ReviewMetricKind.ACTIVITY,
                            activity = ReviewActivityKey(total.type, total.unit, total.source),
                        ),
                    )
                },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("review_overview_activity_sources"),
            ) {
                Text(stringResource(R.string.review_view_sources))
            }
        }

        if (shouldOfferSetup) {
            DetailsDisclosure(R.string.review_data_setup, "review_overview_data_setup") {
                if (availability.usage.needsAccessSecondary()) {
                    Text(
                        stringResource(R.string.integration_access_also_needed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("review_overview_usage_access_secondary"),
                    )
                }
                TextButton(
                    onClick = onIntegrations,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        .testTag("review_overview_integrations"),
                ) {
                    Text(stringResource(R.string.metric_integrations_action))
                }
            }
        }
    }
}

private fun ReviewStatistics.usageAbsence(availability: IntegrationAvailabilityState): MetricAbsence? {
    val savedUsage = usage
    return if (savedUsage != null && savedUsage.totalMillis == null) MetricAbsence.NO_SAVED_DATA
    else metricAbsence(false, false, savedUsage?.totalMillis != null, availability.usage)
}
