package com.luma.lifeos.ui.review

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.summary.ScaleTotal
import com.luma.lifeos.ui.common.humanReadableDuration
import java.text.NumberFormat

internal fun reviewNumber(value: Number): String = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }.format(value)
internal fun reviewChange(current: Double?, previous: Double?): String? = if (current == null || previous == null) null else
    (if (current - previous > 0) "+" else "") + reviewNumber(current - previous)
@Composable internal fun reviewScale(scale: ScaleTotal?): String = if (scale == null) stringResource(R.string.review_unavailable)
    else stringResource(R.string.review_scale_samples, reviewNumber(scale.average), scale.samples)
@Composable internal fun reviewDuration(millis: Long?): String = if (millis == null) stringResource(R.string.review_unavailable)
    else humanReadableDuration(millis)

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
            ReviewSourcesControl(label, tag, onCurrent, onPrevious)
        }
    }
}

@Composable
private fun ReviewSourcesControl(label: String, tag: String,
    onCurrent: (() -> Unit)?, onPrevious: (() -> Unit)?) {
    val sources = stringResource(R.string.review_sources)
    if (onCurrent != null && onPrevious != null) {
        var expanded by remember { mutableStateOf(false) }
        val state = stringResource(if (expanded) R.string.review_details_expanded else R.string.review_details_collapsed)
        Box {
            TextButton(onClick = { expanded = true }, modifier = Modifier.testTag("review_sources_menu_$tag")
                .semantics { contentDescription = "$label · $sources"; stateDescription = state }) { Text(sources) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.review_current_period)) },
                    onClick = { expanded = false; onCurrent() }, modifier = Modifier.testTag("review_sources_$tag"))
                DropdownMenuItem(text = { Text(stringResource(R.string.review_previous_period)) },
                    onClick = { expanded = false; onPrevious() }, modifier = Modifier.testTag("review_previous_sources_$tag"))
            }
        }
    } else if (onCurrent != null || onPrevious != null) {
        val action = if (onCurrent != null) stringResource(R.string.review_view_sources)
            else stringResource(R.string.review_view_previous_sources)
        TextButton(onClick = onCurrent ?: onPrevious!!,
            modifier = Modifier.testTag(if (onCurrent != null) "review_sources_$tag" else "review_previous_sources_$tag")
                .semantics { contentDescription = "$label · $action" }) { Text(action) }
    }
}
