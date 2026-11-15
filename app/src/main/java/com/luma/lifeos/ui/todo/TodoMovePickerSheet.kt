package com.luma.lifeos.ui.todo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.luma.lifeos.R
import com.luma.lifeos.domain.todo.TodoTaskRow
import com.luma.lifeos.ui.common.AccessibleFeedback
import com.luma.lifeos.ui.common.LumaModalBottomSheet
import com.luma.lifeos.ui.common.LumaPrimaryActionButton
import com.luma.lifeos.ui.common.LumaTextButton
import com.luma.lifeos.ui.common.LumaUi
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TodoMovePickerSheet(
    state: TodoUiState,
    onDismiss: () -> Unit,
    onSelectTarget: (UUID) -> Unit,
    onSelectRoot: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryTargets: () -> Unit,
    onMove: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    BackHandler(enabled = state.move.applying) {}
    LumaModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = LumaUi.PageGutter, vertical = LumaUi.SectionGap)
                .testTag("todo_move_picker"),
            verticalArrangement = Arrangement.spacedBy(LumaUi.ContentGap),
        ) {
            Text(
                stringResource(R.string.todo_move_picker_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                stringResource(R.string.todo_move_picker_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.parentId != null) {
                TodoMoveRootChoice(
                    selected = state.move.moveToRootSelected,
                    enabled = !state.move.applying && !state.move.hasPendingOperation,
                    onClick = onSelectRoot,
                )
            }

            when {
                state.move.loadingTargets && state.move.targets.isEmpty() ->
                    Text(stringResource(R.string.todo_move_loading), style = MaterialTheme.typography.bodyMedium)
                state.move.targetLoadFailed ->
                    AccessibleFeedback(
                        stringResource(R.string.todo_move_targets_error),
                        Modifier.testTag("todo_move_targets_error"),
                    )
                !state.move.loadingTargets && !state.move.targetLoadFailed && state.move.targets.isEmpty() ->
                    Text(
                        stringResource(R.string.todo_move_no_targets),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
            }

            if (state.move.targets.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(LumaUi.ContentGap),
                ) {
                    items(state.move.targets, key = { it.task.id }) { row ->
                        TodoMoveTargetChoice(
                            row = row,
                            selected = state.move.selectedTargetId == row.task.id,
                            enabled = !state.move.applying && !state.move.hasPendingOperation,
                            onClick = { onSelectTarget(row.task.id) },
                        )
                    }
                }
            }

            if (state.move.hasMoreTargets) {
                LumaTextButton(
                    onClick = onLoadMore,
                    enabled = !state.move.loadingTargets && !state.move.applying &&
                        !state.move.hasPendingOperation,
                    modifier = Modifier.fillMaxWidth().testTag("todo_move_load_more"),
                ) {
                    Text(stringResource(R.string.todo_move_load_more))
                }
            }

            state.move.issue?.let { issue ->
                AccessibleFeedback(
                    stringResource(
                        if (issue == TodoMoveIssue.STALE) R.string.todo_move_stale else R.string.todo_move_error,
                    ),
                    Modifier.testTag("todo_move_feedback"),
                )
                if (issue == TodoMoveIssue.STALE) {
                    LumaTextButton(
                        onClick = onRetryTargets,
                        enabled = !state.move.applying && !state.move.loadingTargets,
                        modifier = Modifier.fillMaxWidth().testTag("todo_move_refresh"),
                    ) {
                        Text(stringResource(R.string.todo_move_refresh))
                    }
                }
            }

            if (state.move.targetLoadFailed) {
                LumaTextButton(
                    onClick = onRetryTargets,
                    enabled = !state.move.loadingTargets && !state.move.applying &&
                        !state.move.hasPendingOperation,
                    modifier = Modifier.fillMaxWidth().testTag("todo_move_targets_retry"),
                ) {
                    Text(stringResource(R.string.retry))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LumaTextButton(
                    onClick = onDismiss,
                    enabled = !state.move.applying,
                    modifier = Modifier.testTag("todo_move_cancel"),
                ) {
                    Text(stringResource(R.string.cancel))
                }
                LumaPrimaryActionButton(
                    onClick = onMove,
                    enabled = !state.move.applying && !state.move.loadingTargets &&
                        (state.move.hasPendingOperation || state.move.moveToRootSelected ||
                            state.move.selectedTargetId != null),
                    modifier = Modifier.testTag("todo_move_confirm"),
                ) {
                    Text(
                        stringResource(
                            if (state.move.hasPendingOperation) R.string.todo_move_retry
                            else R.string.todo_move_confirm,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun TodoMoveRootChoice(
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = LumaUi.MinTouchTargetHeight)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .testTag("todo_move_to_root"),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = LumaUi.SectionPadding, vertical = LumaUi.ContentGap),
            horizontalArrangement = Arrangement.spacedBy(LumaUi.ContentGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null)
            Text(
                stringResource(R.string.todo_move_to_root),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun TodoMoveTargetChoice(
    row: TodoTaskRow,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = LumaUi.MinTouchTargetHeight)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .testTag("todo_move_target_${row.task.id}"),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = LumaUi.SectionPadding, vertical = LumaUi.ContentGap),
            horizontalArrangement = Arrangement.spacedBy(LumaUi.ContentGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(LumaUi.RelatedGap),
            ) {
                Text(row.task.title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(
                        if (row.task.isCompleted) R.string.todo_status_completed else R.string.todo_status_open,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                row.progress?.let { progress ->
                    Text(
                        stringResource(R.string.todo_subtask_progress, progress.completed, progress.total),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
