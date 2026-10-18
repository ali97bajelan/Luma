package com.luma.lifeos.ui.summary

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.usage.APP_USAGE_DISPLAY_THRESHOLD_MILLIS
import com.luma.lifeos.domain.summary.ScaleTotal
import com.luma.lifeos.language.activityTypeDisplayName
import com.luma.lifeos.language.activityUnitDisplayName
import com.luma.lifeos.ui.timeline.label
import com.luma.lifeos.ui.usage.appUsageDuration
import com.luma.lifeos.ui.common.entryLogCounts
import com.luma.lifeos.ui.common.DetailsDisclosure
import com.luma.lifeos.domain.usage.UsageHistory
import com.luma.lifeos.ui.integrations.IntegrationAvailabilityState
import com.luma.lifeos.ui.integrations.MetricAbsence
import com.luma.lifeos.ui.integrations.MetricPeriod
import com.luma.lifeos.ui.integrations.label
import com.luma.lifeos.ui.integrations.metricAbsence
import com.luma.lifeos.ui.integrations.needsAccessSecondary
import java.text.NumberFormat

@Composable
fun DailySummaryCard(state: DailySummaryState, onRetry: () -> Unit, habitLogCount: Int? = null,
    availability: IntegrationAvailabilityState = IntegrationAvailabilityState(),
    onIntegrations: () -> Unit = {}, onCheckIn: () -> Unit = {}) {
    Card(Modifier.fillMaxWidth().testTag("daily_summary")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.summary_heading), style = MaterialTheme.typography.titleMedium)
            when (state) {
                DailySummaryState.Loading -> CircularProgressIndicator(Modifier.testTag("daily_summary_loading"))
                DailySummaryState.Error -> {
                    Text(stringResource(R.string.summary_error), modifier = Modifier.testTag("daily_summary_error"))
                    TextButton(onClick = onRetry, modifier = Modifier.testTag("daily_summary_retry")) { Text(stringResource(R.string.retry)) }
                }
                is DailySummaryState.Data -> {
                    val summary = state.summary
                    Text(entryLogCounts(summary.records, habitLogCount), style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.testTag("summary_records"))
                    val mood = summary.mood
                    if (mood != null) ScaleLine(stringResource(R.string.summary_mood), mood.score, "summary_mood")
                    else {
                        Text(stringResource(R.string.metric_no_check_ins_day), modifier = Modifier.testTag("summary_mood_empty"))
                        TextButton(onClick = onCheckIn, modifier = Modifier.testTag("summary_check_in")) {
                            Text(stringResource(R.string.summary_checkin_action))
                        }
                    }
                    val usage = summary.usage
                    val displayedApps = usage?.apps.orEmpty()
                        .filter { it.durationMillis > APP_USAGE_DISPLAY_THRESHOLD_MILLIS }
                        .sortedWith(compareByDescending<com.luma.lifeos.domain.usage.AppUsageTotal> { it.durationMillis }
                            .thenBy { it.packageName })
                        .take(3)
                    if (usage == null) {
                        val absence = metricAbsence(false, false, false, availability.usage) ?: MetricAbsence.NO_SAVED_DATA
                        Text(stringResource(absence.label(MetricPeriod.DAY)), modifier = Modifier.testTag("summary_usage"),
                            style = MaterialTheme.typography.bodyMedium)
                        if (availability.usage.needsAccessSecondary()) Text(stringResource(R.string.integration_access_also_needed),
                            modifier = Modifier.testTag("summary_usage_access_secondary"), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = onIntegrations, modifier = Modifier.testTag("summary_usage_integrations")) {
                            Text(stringResource(R.string.metric_integrations_action))
                        }
                    } else Text(stringResource(R.string.summary_usage_value, appUsageDuration(usage.totalMillis), displayedApps.size),
                        modifier = Modifier.testTag("summary_usage"), style = MaterialTheme.typography.bodyMedium)
                    if (usage != null) {
                        if (usage.history == UsageHistory.PARTIAL) Text(stringResource(R.string.summary_usage_partial),
                            style = MaterialTheme.typography.labelMedium)
                    }
                    val activity = summary.activities
                    DetailsDisclosure(R.string.summary_details, "summary_details") {
                        Text(stringResource(R.string.summary_saved_only), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.summary_journals, summary.journals),
                            modifier = Modifier.testTag("summary_journals"), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.summary_memories, summary.memories),
                            modifier = Modifier.testTag("summary_memories"), style = MaterialTheme.typography.bodySmall)
                        val format = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }
                        listOf(R.string.summary_mood to summary.mood?.score, R.string.summary_energy to summary.mood?.energy,
                            R.string.summary_stress to summary.mood?.stress).forEach { (label, scale) ->
                            if (scale != null) Text(stringResource(R.string.summary_average, stringResource(label), format.format(scale.average), scale.samples),
                                modifier = Modifier.testTag(when (label) {
                                    R.string.summary_energy -> "summary_energy"
                                    R.string.summary_stress -> "summary_stress"
                                    else -> "summary_mood_average"
                                }), style = MaterialTheme.typography.bodySmall)
                            else if (label != R.string.summary_mood && summary.mood != null) Text(
                                stringResource(R.string.summary_rating_empty,
                                    stringResource(if (label == R.string.summary_energy) R.string.mood_energy else R.string.mood_stress)),
                                modifier = Modifier.testTag(if (label == R.string.summary_energy) "summary_energy_empty" else "summary_stress_empty"),
                                style = MaterialTheme.typography.bodySmall)
                        }
                        if (usage != null) {
                            displayedApps.forEach { app ->
                                Row(Modifier.fillMaxWidth().testTag("summary_usage_app_${app.packageName}"),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(app.appLabel ?: app.packageName, style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f).testTag("summary_usage_label_${app.packageName}"))
                                    Text(appUsageDuration(app.durationMillis), style = MaterialTheme.typography.labelLarge,
                                        modifier = Modifier.testTag("summary_usage_duration_${app.packageName}"))
                                }
                                Text(app.packageName, style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.testTag("summary_usage_package_${app.packageName}"))
                            }
                            Text(stringResource(R.string.usage_daily_explanation), style = MaterialTheme.typography.bodySmall)
                            if (usage.history != UsageHistory.COMPLETE) Text(stringResource(if (usage.history == UsageHistory.PARTIAL)
                                R.string.usage_history_partial else R.string.usage_legacy_totals), style = MaterialTheme.typography.bodySmall)
                        }
                        if (activity == null) {
                            Text(stringResource(MetricAbsence.NO_SAVED_DATA.label(MetricPeriod.GENERIC)),
                                modifier = Modifier.testTag("summary_activity_unavailable"), style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = onIntegrations, modifier = Modifier.testTag("summary_activity_integrations")) {
                                Text(stringResource(R.string.metric_integrations_action))
                            }
                        }
                        else {
                            Text(stringResource(R.string.summary_activity), style = MaterialTheme.typography.titleMedium)
                            // Preserve small nonzero recorded values instead of displaying a rounded zero.
                            val activityFormat = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 340 }
                            activity.forEach { total ->
                                Text(stringResource(R.string.summary_activity_value,
                                    activityTypeDisplayName(total.type, total.unit, total.source), activityFormat.format(total.total),
                                    activityUnitDisplayName(total.type, total.unit, total.source), stringResource(total.source.label()), total.samples),
                                    modifier = Modifier.testTag("summary_activity_${total.type}_${total.unit}_${total.source}"),
                                    style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun ScaleLine(label: String, scale: ScaleTotal?, tag: String) {
    if (scale != null) {
        val format = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }
        Text(stringResource(R.string.summary_scale_value, label, format.format(scale.average)),
            modifier = Modifier.testTag(tag), style = MaterialTheme.typography.bodyMedium)
    }
}
