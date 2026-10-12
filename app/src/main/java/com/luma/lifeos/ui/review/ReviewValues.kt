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
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Text(current, modifier = Modifier.testTag("review_metric_$tag"), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.review_previous_value, previous))
        change?.let { Text(stringResource(R.string.review_change, it), style = MaterialTheme.typography.bodySmall) }
        // Stack actions so large text and long translations do not overlap.
        if (onCurrent != null) TextButton(onClick = onCurrent, modifier = Modifier.testTag("review_sources_$tag")) {
            Text(stringResource(R.string.review_view_sources))
        }
        if (onPrevious != null) TextButton(onClick = onPrevious, modifier = Modifier.testTag("review_previous_sources_$tag")) {
            Text(stringResource(R.string.review_view_previous_sources))
        }
    }
}
