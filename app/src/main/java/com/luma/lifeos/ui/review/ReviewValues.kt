package com.luma.lifeos.ui.review

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.summary.ScaleTotal
import java.text.NumberFormat

internal fun reviewNumber(value: Number): String = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }.format(value)
internal fun reviewChange(current: Double?, previous: Double?): String? = if (current == null || previous == null) null else
    (if (current - previous > 0) "+" else "") + reviewNumber(current - previous)
@Composable internal fun reviewScale(scale: ScaleTotal?): String = if (scale == null) stringResource(R.string.review_unavailable)
    else stringResource(R.string.review_scale_samples, reviewNumber(scale.average), scale.samples)
@Composable internal fun reviewDuration(millis: Long?): String = if (millis == null) stringResource(R.string.review_unavailable)
    else stringResource(R.string.usage_duration, millis / 3_600_000, millis / 60_000 % 60,
        NumberFormat.getNumberInstance().apply { maximumFractionDigits = 3 }.format(millis % 60_000 / 1000.0))

@Composable
internal fun ReviewValue(label: String, current: String, previous: String, change: String?, tag: String,
    onCurrent: (() -> Unit)? = null, onPrevious: (() -> Unit)? = null) {
    Card(Modifier.fillMaxWidth().padding(vertical = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.review_current_period), style = MaterialTheme.typography.labelSmall)
                    Text(current, modifier = Modifier.testTag("review_metric_$tag"), style = MaterialTheme.typography.titleMedium)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.review_previous_period), style = MaterialTheme.typography.labelSmall)
                    Text(previous, style = MaterialTheme.typography.titleMedium)
                }
            }
            change?.let { Text(stringResource(R.string.review_change, it), style = MaterialTheme.typography.bodySmall) }
            if (onCurrent != null || onPrevious != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    onCurrent?.let {
                        val width = if (onPrevious == null) Modifier.fillMaxWidth() else Modifier.weight(1f)
                        TextButton(onClick = it, modifier = width.testTag("review_sources_$tag")) {
                            Text(stringResource(R.string.review_view_sources))
                        }
                    }
                    onPrevious?.let {
                        val width = if (onCurrent == null) Modifier.fillMaxWidth() else Modifier.weight(1f)
                        TextButton(onClick = it, modifier = width.testTag("review_previous_sources_$tag")) {
                            Text(stringResource(R.string.review_view_previous_sources))
                        }
                    }
                }
            }
        }
    }
}
