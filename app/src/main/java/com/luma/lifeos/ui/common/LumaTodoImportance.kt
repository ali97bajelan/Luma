package com.luma.lifeos.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import java.util.Locale
import com.luma.lifeos.R
import com.luma.lifeos.domain.todo.TodoImportance

@Composable
internal fun LumaTodoImportanceSelector(
    importance: TodoImportance,
    onImportanceChange: (TodoImportance) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    LumaSectionSurface(modifier = modifier) {
        Text(
            text = stringResource(R.string.todo_importance_label),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(LumaUi.RelatedGap),
            verticalArrangement = Arrangement.spacedBy(LumaUi.RelatedGap),
        ) {
            TodoImportance.entries.forEach { option ->
                LumaSegmentedOption(
                    selected = option == importance,
                    onClick = { onImportanceChange(option) },
                    label = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(LumaUi.RelatedGap),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_bookmark_outline),
                                contentDescription = null,
                                modifier = Modifier.size(LumaUi.IconSize),
                            )
                            Text(stringResource(todoImportanceLabelRes(option)))
                        }
                    },
                    enabled = enabled,
                    modifier = Modifier.testTag("todo_importance_${option.name.lowercase(Locale.ROOT)}"),
                )
            }
        }
    }
}

@Composable
internal fun LumaTodoImportanceBadge(
    importance: TodoImportance,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(todoImportanceLabelRes(importance))
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(LumaUi.RelatedGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_bookmark_outline),
            contentDescription = null,
            modifier = Modifier.size(LumaUi.IconSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.todo_importance_value, label),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@StringRes
internal fun todoImportanceLabelRes(importance: TodoImportance): Int = when (importance) {
    TodoImportance.NONE -> R.string.todo_importance_none
    TodoImportance.LOW -> R.string.todo_importance_low
    TodoImportance.MEDIUM -> R.string.todo_importance_medium
    TodoImportance.HIGH -> R.string.todo_importance_high
}
