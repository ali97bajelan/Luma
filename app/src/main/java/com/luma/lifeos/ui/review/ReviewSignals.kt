package com.luma.lifeos.ui.review

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luma.lifeos.ui.common.DetailsDisclosure
import com.luma.lifeos.ui.timeline.label
import com.luma.lifeos.R
import com.luma.lifeos.domain.review.*
import com.luma.lifeos.domain.usage.*
import com.luma.lifeos.domain.model.EventSource
import com.luma.lifeos.ui.integrations.IntegrationAvailability
import com.luma.lifeos.ui.integrations.MetricAbsence
import com.luma.lifeos.ui.integrations.MetricPeriod
import com.luma.lifeos.ui.integrations.label
import com.luma.lifeos.ui.integrations.metricAbsence
import com.luma.lifeos.ui.integrations.needsAccessSecondary
import com.luma.lifeos.language.activityTypeDisplayName
import com.luma.lifeos.language.activityUnitDisplayName
import com.luma.lifeos.language.formatActivityChange
import com.luma.lifeos.language.formatActivityQuantity
import java.time.LocalDate
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs

@Composable
internal fun ReviewDigital(report: WeeklyReview, onUsage: (LocalDate) -> Unit,
    availability: com.luma.lifeos.ui.integrations.IntegrationAvailabilityState,
    onIntegrations: () -> Unit) {
    ReviewSection(R.string.review_usage, R.drawable.ic_review_usage, "usage",
        explanation = R.string.review_usage_explanation, infoTag = "review_usage_methodology",
        explanationTag = "review_usage_methodology_text") {
        val a = report.current.usage; val b = report.previous.usage
        val currentTotal = a?.totalMillis; val previousTotal = b?.totalMillis
        val delta = if (currentTotal != null && previousTotal != null) currentTotal - previousTotal else null
        if (a != null || b != null) ReviewValueGroup {
            ReviewValue(stringResource(R.string.review_usage_total), usageValue(a, currentTotal, availability.usage),
                usageValue(b, previousTotal, availability.usage),
                delta?.let { stringResource(R.string.review_duration_change, if (it > 0) "+" else if (it < 0) "−" else "",
                    reviewDuration(abs(it), stringResource(R.string.review_usage_partial_total_unknown))) }, "usage")
        }
        if (a == null && b == null) {
            val absence = metricAbsence(false, false, false, availability.usage) ?: MetricAbsence.NO_SAVED_DATA
            ReviewEmpty(absence.label(MetricPeriod.WEEK), "review_usage_unavailable")
            if (availability.usage.needsAccessSecondary()) Text(stringResource(R.string.integration_access_also_needed),
                Modifier.testTag("review_usage_access_secondary"), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onIntegrations, modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                .testTag("review_usage_integrations")) { Text(stringResource(R.string.metric_integrations_action)) }
        }
        if (a != null || b != null) DetailsDisclosure(R.string.review_coverage_details, "review_usage_coverage_details") {
            a?.let { UsageCoverage(it) }
            b?.let {
                Text(stringResource(R.string.review_previous_value, stringResource(R.string.review_usage)))
                UsageCoverage(it)
            }
        }
        val apps = (a?.apps.orEmpty() + b?.apps.orEmpty()).distinctBy { it.packageName }
            .sortedWith(compareByDescending<AppUsageTotal> { app -> a?.apps?.firstOrNull { it.packageName == app.packageName }?.durationMillis ?: 0L }
                .thenBy { it.packageName }).take(10)
        if (apps.isNotEmpty()) ReviewValueGroup {
            apps.forEach { app ->
                val current = a?.apps?.firstOrNull { it.packageName == app.packageName }
                val previous = b?.apps?.firstOrNull { it.packageName == app.packageName }
                // Absence of an app in incomplete history is not a measured zero.
                val currentMillis = current?.durationMillis ?: if (a != null && a.completeDates == report.period.current.dates.size) 0L else null
                val previousMillis = previous?.durationMillis ?: if (b != null && b.completeDates == report.period.previous.dates.size) 0L else null
                ReviewValue(app.appLabel ?: app.packageName,
                    reviewDuration(currentMillis, stringResource(R.string.review_usage_app_missing)),
                    reviewDuration(previousMillis, stringResource(R.string.review_usage_app_missing)), null, "app_${app.packageName}")
            }
        }
        if (a?.days.orEmpty().isNotEmpty() || b?.days.orEmpty().isNotEmpty()) DetailsDisclosure(R.string.review_daily_evidence, "review_usage_daily_details") {
            val format = com.luma.lifeos.language.localizedDate(FormatStyle.MEDIUM)
            (a?.days.orEmpty() + b?.days.orEmpty()).sortedBy { it.date }.forEach { day ->
                TextButton(onClick = { onUsage(day.date) }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    .testTag("review_usage_date_${day.date}")) {
                    Text(stringResource(R.string.review_usage_date, day.date.format(format),
                        reviewDuration(day.totalMillis.takeUnless { day.apps.isEmpty() && day.history == UsageHistory.PARTIAL },
                            stringResource(R.string.review_usage_partial_total_unknown))))
                }
            }
        }
    }
}
@Composable private fun usageValue(usage: ReviewUsage?, millis: Long?, availability: IntegrationAvailability): String {
    if (usage != null && millis == null) return stringResource(R.string.review_usage_partial_total_unknown)
    val absence = metricAbsence(false, false, millis != null, availability) ?: MetricAbsence.NO_SAVED_DATA
    return reviewDuration(millis, stringResource(absence.label(MetricPeriod.WEEK)))
}
@Composable private fun UsageCoverage(usage: ReviewUsage) {
    Text(stringResource(R.string.review_usage_coverage, usage.days.size, usage.completeDates,
        usage.days.count { it.history == UsageHistory.PARTIAL }, usage.days.count { it.history == UsageHistory.UNAVAILABLE }), Modifier.testTag("review_usage_coverage_text"))
}
@Composable
internal fun ReviewActivities(report: WeeklyReview, onSources: (ReviewDates, ReviewMetric) -> Unit,
    healthAvailability: IntegrationAvailability, onIntegrations: () -> Unit) {
    ReviewSection(R.string.review_activity, R.drawable.ic_review_activity, "activity",
        explanation = R.string.review_activity_explanation, infoTag = "review_activity_methodology") {
        val current = report.current.activities; val previous = report.previous.activities
        if (current.isEmpty() && previous.isEmpty()) {
            ReviewEmpty(R.string.metric_no_saved_measurements, "review_activity_unavailable")
            TextButton(onClick = onIntegrations, modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                .testTag("review_activity_integrations")) { Text(stringResource(R.string.metric_integrations_action)) }
        }
        fun key(row: ReviewActivity) = ReviewActivityKey(row.total.type, row.total.unit, row.total.source)
        val groups = (current + previous).map(::key).distinct().sortedWith(compareBy<ReviewActivityKey> { it.type }.thenBy { it.unit }.thenBy { it.source.name })
        if (groups.isNotEmpty()) ReviewValueGroup {
            groups.forEach { group ->
                val a = current.firstOrNull { key(it) == group }; val b = previous.firstOrNull { key(it) == group }
                val metric = ReviewMetric(ReviewMetricKind.ACTIVITY, activity = group)
                val displayType = activityTypeDisplayName(group.type, group.unit, group.source)
                val displayUnit = activityUnitDisplayName(group.type, group.unit, group.source)
                val healthSteps = group.source == EventSource.HEALTH_CONNECT && group.type == "steps" && group.unit == "count"
                val absence = if (healthSteps) metricAbsence(false, false, false, healthAvailability)
                    ?: MetricAbsence.NO_SAVED_DATA else MetricAbsence.NO_SAVED_DATA
                @Composable fun value(row: ReviewActivity?): String = row?.let {
                    stringResource(R.string.review_activity_value,
                        formatActivityQuantity(it.total.total, Locale.getDefault()), displayUnit, it.total.samples, it.dates)
                } ?: stringResource(if (healthSteps) absence.label(MetricPeriod.WEEK) else R.string.review_no_measurement_period)
                ReviewValue("$displayType · $displayUnit · ${stringResource(group.source.label())}", value(a), value(b),
                    formatActivityChange(a?.total?.total, b?.total?.total, Locale.getDefault())?.let { "$it $displayUnit" },
                    "activity_${group.type}_${group.unit}_${group.source}",
                    a?.let { { onSources(report.period.current, metric) } }, b?.let { { onSources(report.period.previous, metric) } })
            }
        }
    }
}
