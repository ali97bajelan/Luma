package com.luma.lifeos.ui.summary

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.usage.APP_USAGE_DISPLAY_THRESHOLD_MILLIS
import com.luma.lifeos.domain.summary.ScaleTotal
import com.luma.lifeos.language.activityTypeDisplayName
import com.luma.lifeos.language.activityUnitDisplayName
import com.luma.lifeos.ui.timeline.label
import com.luma.lifeos.ui.usage.appUsageDuration
import com.luma.lifeos.ui.common.DetailsDisclosure
import com.luma.lifeos.domain.usage.UsageHistory
import java.text.NumberFormat

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DailySummaryCard(state: DailySummaryState, onRetry: () -> Unit) {
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
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CountMetric(R.string.summary_count_entries, summary.records, "summary_records", Modifier.weight(1f))
                        CountMetric(R.string.summary_count_journals, summary.journals, "summary_journals", Modifier.weight(1f))
                        CountMetric(R.string.summary_count_memories, summary.memories, "summary_memories", Modifier.weight(1f))
                    }
                    val mood = summary.mood
                    if (mood != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ScaleLine(stringResource(R.string.summary_mood), mood.score, "summary_mood")
                        ScaleLine(stringResource(R.string.summary_energy), mood.energy, "summary_energy")
                        ScaleLine(stringResource(R.string.summary_stress), mood.stress, "summary_stress")
                    }
                    val usage = summary.usage
                    val displayedApps = usage?.apps.orEmpty()
                        .filter { it.durationMillis > APP_USAGE_DISPLAY_THRESHOLD_MILLIS }
                        .sortedWith(compareByDescending<com.luma.lifeos.domain.usage.AppUsageTotal> { it.durationMillis }
                            .thenBy { it.packageName })
                        .take(3)
                    Text(if (usage == null) stringResource(R.string.summary_unavailable, stringResource(R.string.summary_usage))
                        else stringResource(R.string.summary_usage_value, appUsageDuration(usage.totalMillis), displayedApps.size),
                        modifier = Modifier.testTag("summary_usage"), style = MaterialTheme.typography.bodyMedium)
                    if (usage != null) {
                        if (usage.history == UsageHistory.PARTIAL) Text(stringResource(R.string.summary_usage_partial),
                            style = MaterialTheme.typography.labelMedium)
                        displayedApps.forEach { app ->
                            Row(Modifier.fillMaxWidth().testTag("summary_usage_app_${app.packageName}"),
                                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(app.appLabel ?: app.packageName, style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).testTag("summary_usage_label_${app.packageName}"))
                                Text(appUsageDuration(app.durationMillis), style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.testTag("summary_usage_duration_${app.packageName}"))
                            }
                        }
                    }
                    val activity = summary.activities
                    DetailsDisclosure(R.string.summary_details, "summary_details") {
                        Text(stringResource(R.string.summary_saved_only), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.summary_memories, summary.memories), style = MaterialTheme.typography.bodySmall)
                        val format = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }
                        listOf(R.string.summary_mood to summary.mood?.score, R.string.summary_energy to summary.mood?.energy,
                            R.string.summary_stress to summary.mood?.stress).forEach { (label, scale) ->
                            if (scale != null) Text(stringResource(R.string.summary_average, stringResource(label), format.format(scale.average), scale.samples),
                                style = MaterialTheme.typography.bodySmall)
                        }
                        if (usage != null) {
                            displayedApps.forEach { app ->
                                app.appLabel?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
                                Text(app.packageName, style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.testTag("summary_usage_package_${app.packageName}"))
                            }
                            Text(stringResource(R.string.usage_daily_explanation), style = MaterialTheme.typography.bodySmall)
                            if (usage.history != UsageHistory.COMPLETE) Text(stringResource(if (usage.history == UsageHistory.PARTIAL)
                                R.string.usage_history_partial else R.string.usage_legacy_totals), style = MaterialTheme.typography.bodySmall)
                        }
                        if (activity == null) Text(stringResource(R.string.summary_unavailable, stringResource(R.string.summary_activity)),
                            modifier = Modifier.testTag("summary_activity_unavailable"), style = MaterialTheme.typography.bodySmall)
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

@Composable
private fun CountMetric(@StringRes label: Int, count: Int, tag: String, modifier: Modifier) {
    Column(modifier.testTag(tag).semantics(mergeDescendants = true) {}) {
        Text(NumberFormat.getIntegerInstance().format(count), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(label), style = MaterialTheme.typography.bodySmall)
    }
}
