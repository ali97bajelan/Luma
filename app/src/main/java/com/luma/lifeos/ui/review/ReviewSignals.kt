package com.luma.lifeos.ui.review

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.luma.lifeos.ui.common.DetailsDisclosure
import com.luma.lifeos.ui.timeline.label
import com.luma.lifeos.R
import com.luma.lifeos.domain.review.*
import com.luma.lifeos.domain.usage.*
import com.luma.lifeos.language.activityTypeDisplayName
import com.luma.lifeos.language.activityUnitDisplayName
import java.time.LocalDate
import java.time.format.FormatStyle
import kotlin.math.abs

@Composable
internal fun ReviewDigital(report: WeeklyReview, onUsage: (LocalDate) -> Unit) {
    Text(stringResource(R.string.review_usage), style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.testTag("review_heading_usage").semantics { heading() })
    DetailsDisclosure(R.string.review_methodology, "review_usage_methodology", keepTitle = true) {
        Text(stringResource(R.string.review_usage_explanation), Modifier.testTag("review_usage_methodology_text"))
    }
    val a = report.current.usage; val b = report.previous.usage
    val currentTotal = a?.totalMillis; val previousTotal = b?.totalMillis
    val delta = if (currentTotal != null && previousTotal != null) currentTotal - previousTotal else null
    ReviewValue(stringResource(R.string.review_usage_total), reviewDuration(a?.totalMillis), reviewDuration(b?.totalMillis),
        delta?.let { stringResource(R.string.review_duration_change, if (it > 0) "+" else if (it < 0) "−" else "", reviewDuration(abs(it))) }, "usage")
    if (a?.totalMillis == null) Text(stringResource(R.string.review_no_patterns), modifier = Modifier.testTag("review_usage_unavailable"))
    Text(reviewUsageCoverageSummary(report), Modifier.testTag("review_usage_summary"))
    DetailsDisclosure(R.string.review_coverage_details, "review_usage_coverage_details", keepTitle = true) {
        a?.let { UsageCoverage(it) }
        b?.let {
            Text(stringResource(R.string.review_previous_value, stringResource(R.string.review_usage)))
            UsageCoverage(it)
        }
    }
    val apps = (a?.apps.orEmpty() + b?.apps.orEmpty()).distinctBy { it.packageName }
        .sortedWith(compareByDescending<AppUsageTotal> { app -> a?.apps?.firstOrNull { it.packageName == app.packageName }?.durationMillis ?: 0L }
            .thenBy { it.packageName }).take(10)
    apps.forEach { app ->
        val current = a?.apps?.firstOrNull { it.packageName == app.packageName }
        val previous = b?.apps?.firstOrNull { it.packageName == app.packageName }
        // Absence of an app in incomplete history is not a measured zero.
        val currentMillis = current?.durationMillis ?: if (a != null && a.completeDates == report.period.current.dates.size) 0L else null
        val previousMillis = previous?.durationMillis ?: if (b != null && b.completeDates == report.period.previous.dates.size) 0L else null
        ReviewValue(app.appLabel ?: app.packageName, reviewDuration(currentMillis), reviewDuration(previousMillis), null, "app_${app.packageName}")
        if (app.appLabel != null) Text(app.packageName, style = MaterialTheme.typography.bodySmall)
    }
    DetailsDisclosure(R.string.review_daily_evidence, "review_usage_daily_details", keepTitle = true) {
        val format = com.luma.lifeos.language.localizedDate(FormatStyle.MEDIUM)
        (a?.days.orEmpty() + b?.days.orEmpty()).sortedBy { it.date }.forEach { day ->
            TextButton(onClick = { onUsage(day.date) }, modifier = Modifier.testTag("review_usage_date_${day.date}")) {
                Text(stringResource(R.string.review_usage_date, day.date.format(format),
                    reviewDuration(day.totalMillis.takeUnless { day.apps.isEmpty() && day.history == UsageHistory.PARTIAL })))
            }
        }
    }
}
@Composable private fun UsageCoverage(usage: ReviewUsage) {
    Text(stringResource(R.string.review_usage_coverage, usage.days.size, usage.completeDates,
        usage.days.count { it.history == UsageHistory.PARTIAL }, usage.days.count { it.history == UsageHistory.UNAVAILABLE }), Modifier.testTag("review_usage_coverage_text"))
}
@Composable
internal fun ReviewActivities(report: WeeklyReview, onSources: (ReviewDates, ReviewMetric) -> Unit) {
    Text(stringResource(R.string.review_activity), style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.testTag("review_heading_activity").semantics { heading() })
    DetailsDisclosure(R.string.review_methodology, "review_activity_methodology", keepTitle = true) {
        Text(stringResource(R.string.review_activity_explanation))
    }
    val current = report.current.activities; val previous = report.previous.activities
    if (current.isEmpty()) Text(stringResource(R.string.review_no_patterns), modifier = Modifier.testTag("review_activity_unavailable"))
    fun key(row: ReviewActivity) = ReviewActivityKey(row.total.type, row.total.unit, row.total.source)
    val groups = (current + previous).map(::key).distinct().sortedWith(compareBy<ReviewActivityKey> { it.type }.thenBy { it.unit }.thenBy { it.source.name })
    groups.forEach { group ->
        val a = current.firstOrNull { key(it) == group }; val b = previous.firstOrNull { key(it) == group }
        val metric = ReviewMetric(ReviewMetricKind.ACTIVITY, activity = group)
        val displayType = activityTypeDisplayName(group.type, group.unit, group.source)
        val displayUnit = activityUnitDisplayName(group.type, group.unit, group.source)
        @Composable fun value(row: ReviewActivity?): String = row?.let {
            stringResource(R.string.review_activity_value, reviewNumber(it.total.total), displayUnit, it.total.samples, it.dates)
        } ?: stringResource(R.string.review_unavailable)
        ReviewValue("$displayType · $displayUnit · ${stringResource(group.source.label())}", value(a), value(b),
            if (a != null && b != null) (if (a.total.total > b.total.total) "+" else "") + reviewNumber(a.total.total - b.total.total) + " " + displayUnit else null,
            "activity_${group.type}_${group.unit}_${group.source}",
            a?.let { { onSources(report.period.current, metric) } }, b?.let { { onSources(report.period.previous, metric) } })
    }
}
