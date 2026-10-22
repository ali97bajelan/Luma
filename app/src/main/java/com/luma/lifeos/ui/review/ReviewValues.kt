package com.luma.lifeos.ui.review

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.summary.ScaleTotal
import com.luma.lifeos.ui.common.LumaUi
import com.luma.lifeos.ui.common.humanReadableDuration
import java.text.NumberFormat

internal fun reviewNumber(value: Number): String = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }.format(value)
internal fun reviewChange(current: Double?, previous: Double?): String? = if (current == null || previous == null) null else
    (if (current - previous > 0) "+" else "") + reviewNumber(current - previous)
@Composable internal fun reviewScale(scale: ScaleTotal?, absence: String): String =
    if (scale == null) absence else stringResource(R.string.review_scale_samples, reviewNumber(scale.average), scale.samples)
@Composable internal fun reviewDuration(millis: Long?, absence: String): String =
    if (millis == null) absence else humanReadableDuration(millis)

@Composable
internal fun ReviewValueGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LumaUi.InnerCardRadius),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
    }
}

@Composable
internal fun ReviewValue(label: String, current: String, previous: String, change: String?, tag: String,
    onCurrent: (() -> Unit)? = null, onPrevious: (() -> Unit)? = null,
    currentUnavailable: Boolean = false, previousUnavailable: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val stack = maxWidth < 280.dp || LocalDensity.current.fontScale >= 1.5f
            if (stack) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReviewPeriodValue(
                        stringResource(R.string.review_current_period), current, currentUnavailable,
                        Modifier.fillMaxWidth(), "review_metric_$tag",
                    )
                    ReviewPeriodValue(
                        stringResource(R.string.review_previous_period), previous, previousUnavailable,
                        Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReviewPeriodValue(
                        stringResource(R.string.review_current_period), current, currentUnavailable,
                        Modifier.weight(1f), "review_metric_$tag",
                    )
                    ReviewPeriodValue(
                        stringResource(R.string.review_previous_period), previous, previousUnavailable,
                        Modifier.weight(1f),
                    )
                }
            }
        }
        change?.let {
            Surface(
                shape = RoundedCornerShape(percent = 50),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("review_change_$tag"),
            ) {
                Text(
                    stringResource(R.string.review_change, it),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        ReviewSourcesControl(label, tag, onCurrent, onPrevious)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun ReviewPeriodValue(
    period: String,
    value: String,
    unavailable: Boolean,
    modifier: Modifier = Modifier,
    valueTag: String? = null,
) {
    val resolvedValueModifier = if (valueTag == null) Modifier.fillMaxWidth()
        else Modifier.fillMaxWidth().testTag(valueTag)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(LumaUi.InnerCardRadius),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(period, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                modifier = resolvedValueModifier,
                style = MaterialTheme.typography.titleMedium,
                color = if (unavailable) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
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
            TextButton(onClick = { expanded = true }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                .testTag("review_sources_menu_$tag")
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
            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                .testTag(if (onCurrent != null) "review_sources_$tag" else "review_previous_sources_$tag")
                .semantics { contentDescription = "$label · $action" }) { Text(action) }
    }
}
