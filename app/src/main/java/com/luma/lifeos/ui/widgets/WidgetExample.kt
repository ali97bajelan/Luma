package com.luma.lifeos.ui.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.widget.WidgetKind

/** Pure fictional content. Never reads records, configuration or command tokens. */
@Composable
internal fun WidgetExample(kind: WidgetKind, details: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.widget_preview_label), style = MaterialTheme.typography.labelMedium)
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        stringResource(if (kind == WidgetKind.HABITS) R.string.widget_preview_habits_title else R.string.widget_preview_tasks_title),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                        Box(Modifier.heightIn(min = 48.dp).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.widget_capture), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(R.drawable.ic_widget_refresh),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                val summary = if (kind == WidgetKind.HABITS) {
                    stringResource(R.string.widget_habits_summary, 1, 3)
                } else {
                    androidx.compose.ui.res.pluralStringResource(R.plurals.widget_tasks_summary, 3, 3)
                }
                Text("${stringResource(R.string.widget_preview_date)} · $summary", style = MaterialTheme.typography.bodyMedium)
                if (details) {
                    val rowNames = if (kind == WidgetKind.HABITS) {
                        listOf(R.string.widget_preview_habit, R.string.widget_preview_habit_second)
                    } else {
                        listOf(R.string.widget_preview_task, R.string.widget_preview_task_second)
                    }
                    val rowActions = if (kind == WidgetKind.HABITS) {
                        listOf(R.string.widget_preview_habit_action, R.string.widget_preview_habit_second_action)
                    } else {
                        listOf(R.string.widget_preview_task_action, R.string.widget_preview_task_second_action)
                    }
                    rowNames.zip(rowActions).forEach { (name, action) ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(stringResource(name), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(action), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    Text(stringResource(R.string.widget_scroll_for_more), style = MaterialTheme.typography.bodySmall)
                }
                Text(stringResource(R.string.widget_data_updated_time, "13:07"), style = MaterialTheme.typography.bodySmall)
                if (!details) {
                    Text(stringResource(R.string.widget_settings_title_navigation_hint), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Text(
            stringResource(if (details) R.string.widget_mode_details else R.string.widget_mode_summary),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
