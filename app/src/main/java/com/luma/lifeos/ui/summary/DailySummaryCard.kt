package com.luma.lifeos.ui.summary

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.summary.ScaleTotal
import com.luma.lifeos.language.activityTypeDisplayName
import com.luma.lifeos.language.activityUnitDisplayName
import com.luma.lifeos.ui.timeline.label
import java.text.NumberFormat

@Composable
fun DailySummaryCard(state: DailySummaryState, onRetry: () -> Unit) {
    Card(Modifier.fillMaxWidth().testTag("daily_summary")) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.summary_heading), style = MaterialTheme.typography.titleLarge)
            when (state) {
                DailySummaryState.Loading -> CircularProgressIndicator(Modifier.testTag("daily_summary_loading"))
                DailySummaryState.Error -> {
                    Text(stringResource(R.string.summary_error), modifier = Modifier.testTag("daily_summary_error"))
                    TextButton(onClick = onRetry, modifier = Modifier.testTag("daily_summary_retry")) { Text(stringResource(R.string.retry)) }
                }
                is DailySummaryState.Data -> {
                    val summary = state.summary
                    Text(stringResource(R.string.summary_saved_only), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.summary_records, summary.records), modifier = Modifier.testTag("summary_records"))
                    Text(stringResource(R.string.summary_journals, summary.journals), modifier = Modifier.testTag("summary_journals"))
                    Text(stringResource(R.string.summary_memories, summary.memories), modifier = Modifier.testTag("summary_memories"))
                    ScaleLine(stringResource(R.string.summary_mood), summary.mood?.score, "summary_mood")
                    ScaleLine(stringResource(R.string.summary_energy), summary.mood?.energy, "summary_energy")
                    ScaleLine(stringResource(R.string.summary_stress), summary.mood?.stress, "summary_stress")
                    val usage = summary.usage
                    Text(if (usage == null) stringResource(R.string.summary_unavailable, stringResource(R.string.summary_usage))
                        else stringResource(R.string.summary_usage_value, usageDuration(usage.totalMillis), usage.apps.size),
                        modifier = Modifier.testTag("summary_usage"))
                    if (usage != null) {
                        Text(stringResource(R.string.usage_daily_explanation), style = MaterialTheme.typography.bodySmall)
                        usage.apps.forEach { app ->
                            Column(Modifier.fillMaxWidth().testTag("summary_usage_app_${app.packageName}")) {
                                Text(app.appLabel ?: app.packageName, style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.testTag("summary_usage_label_${app.packageName}"))
                                if (app.appLabel != null) Text(app.packageName, style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.testTag("summary_usage_package_${app.packageName}"))
                                Text(usageDuration(app.durationMillis),
                                    modifier = Modifier.testTag("summary_usage_duration_${app.packageName}"))
                            }
                        }
                    }
                    val activity = summary.activities
                    if (activity == null) Text(stringResource(R.string.summary_unavailable, stringResource(R.string.summary_activity)),
                        modifier = Modifier.testTag("summary_activity_unavailable"))
                    else {
                        Text(stringResource(R.string.summary_activity), style = MaterialTheme.typography.titleMedium)
                        // Preserve small nonzero recorded values instead of displaying a rounded zero.
                        val format = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 340 }
                        activity.forEach { total ->
                            Text(stringResource(R.string.summary_activity_value,
                                activityTypeDisplayName(total.type, total.unit, total.source), format.format(total.total),
                                activityUnitDisplayName(total.type, total.unit, total.source),
                                stringResource(total.source.label()), total.samples), modifier = Modifier.testTag("summary_activity_${total.type}_${total.unit}_${total.source}"))
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun ScaleLine(label: String, scale: ScaleTotal?, tag: String) {
    val format = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }
    Text(if (scale == null) stringResource(R.string.summary_unavailable, label)
        else stringResource(R.string.summary_average, label, format.format(scale.average), scale.samples), modifier = Modifier.testTag(tag))
}
@Composable
private fun usageDuration(millis: Long): String {
    val format = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 3 }
    return stringResource(R.string.usage_duration, millis / 3_600_000, (millis / 60_000) % 60, format.format((millis % 60_000) / 1000.0))
}
