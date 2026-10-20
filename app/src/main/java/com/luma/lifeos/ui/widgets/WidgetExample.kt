package com.luma.lifeos.ui.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.widget.WidgetKind

/** Pure fictional content. Never reads records, configuration or command tokens. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WidgetExample(kind: WidgetKind?, details: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.widget_preview_label), style = MaterialTheme.typography.labelMedium)
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (kind == null) {
                    // Wrap with large text just as the placed capture layout does.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (label in listOf(R.string.widget_capture_mood, R.string.widget_capture_memory, R.string.widget_capture_journal)) {
                            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                                androidx.compose.foundation.layout.Box(Modifier.heightIn(min = 48.dp).padding(8.dp), contentAlignment = Alignment.Center) {
                                    Text(stringResource(label))
                                }
                            }
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(if (kind == WidgetKind.HABITS) R.string.widget_habits_title else R.string.widget_tasks_title),
                            Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Icon(painterResource(R.drawable.ic_widget_refresh), contentDescription = null)
                    }
                    val summary = if (kind == WidgetKind.HABITS) stringResource(R.string.widget_habits_summary, 1, 3)
                        else androidx.compose.ui.res.pluralStringResource(R.plurals.widget_tasks_summary, 3, 3)
                    Text("${stringResource(R.string.widget_preview_date)} · $summary", style = MaterialTheme.typography.bodyMedium)
                    if (details) {
                        Text(stringResource(if (kind == WidgetKind.HABITS) R.string.widget_preview_habit else R.string.widget_preview_task))
                        Text(if (kind == WidgetKind.HABITS)
                            "${stringResource(R.string.widget_habit_count, 0, 1)}  ·  ${stringResource(R.string.widget_habit_log_short)}"
                            else "${stringResource(R.string.widget_task_due_today)}  ·  ${stringResource(R.string.widget_task_complete)}")
                    }
                    Text(stringResource(R.string.widget_data_updated_time, "13:07"), style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(if (kind == WidgetKind.HABITS) R.string.widget_habits_view_all else R.string.widget_tasks_view_all),
                            Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                        if (kind == WidgetKind.TASKS) Text(stringResource(R.string.widget_tasks_add), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        if (kind != null) Text(stringResource(if (details) R.string.widget_mode_details else R.string.widget_mode_summary),
            style = MaterialTheme.typography.bodySmall)
    }
}
