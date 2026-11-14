package com.luma.lifeos.ui.todo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.luma.lifeos.R
import com.luma.lifeos.domain.todo.TodoMoveDirection
import com.luma.lifeos.domain.todo.TodoTaskRow
import com.luma.lifeos.ui.common.AccessibleFeedback
import com.luma.lifeos.ui.common.LumaAlertDialog
import com.luma.lifeos.ui.common.LumaCard
import com.luma.lifeos.ui.common.LumaDestructiveTextButton
import com.luma.lifeos.ui.common.LumaOutlinedButton
import com.luma.lifeos.ui.common.LumaTextButton
import com.luma.lifeos.ui.common.LumaUi
import java.util.UUID

@Composable
internal fun TodoChildList(
    state: TodoUiState,
    canEdit: Boolean,
    canDeleteTaskOrigin: Boolean,
    onAddSubtask: () -> Unit,
    onOpenTask: (UUID) -> Unit,
    onMoveChild: (UUID, UUID, TodoMoveDirection) -> Unit,
    onRequestDeleteChild: (UUID, UUID) -> Unit,
    onConfirmDeleteChild: () -> Unit,
    onCancelDeleteChild: () -> Unit,
    onRetryChildDelete: () -> Unit,
    onRetryChildren: () -> Unit,
    onLoadMoreChildren: () -> Unit,
    onRequestDeleteUnlock: () -> Unit,
) {
    LumaCard(
        modifier = Modifier.fillMaxWidth().testTag("todo_editor_subtasks"),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(LumaUi.SectionPadding),
            verticalArrangement = Arrangement.spacedBy(LumaUi.ContentGap),
        ) {
            Text(
                stringResource(R.string.todo_subtasks_title),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
            )
            state.childProgress?.let { progress ->
                Text(
                    stringResource(R.string.todo_subtask_progress, progress.completed, progress.total),
                    modifier = Modifier.testTag("todo_editor_subtask_progress"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LumaOutlinedButton(
                onClick = onAddSubtask,
                enabled = canEdit && !state.taskCompleted && state.childActionPendingId == null,
                modifier = Modifier.fillMaxWidth().testTag("todo_editor_add_subtask"),
            ) {
                Text(stringResource(R.string.todo_add_subtask))
            }

            if (state.editing) {
                when {
                    state.childrenLoading && state.children.isEmpty() -> Text(
                        stringResource(R.string.todo_subtasks_loading),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    state.childTotal == 0 && !state.childrenLoading && !state.childrenError -> Text(
                        stringResource(R.string.todo_subtasks_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.childrenError) {
                    AccessibleFeedback(
                        stringResource(R.string.todo_subtasks_load_error),
                        Modifier.testTag("todo_editor_subtasks_error"),
                    )
                    LumaTextButton(
                        onClick = onRetryChildren,
                        modifier = Modifier.fillMaxWidth().testTag("todo_editor_subtasks_retry"),
                    ) { Text(stringResource(R.string.retry)) }
                }
                when (state.childActionError) {
                    TodoChildActionError.MOVE -> {
                        AccessibleFeedback(stringResource(R.string.todo_subtask_order_error))
                        LumaTextButton(
                            onClick = onRetryChildren,
                            modifier = Modifier.fillMaxWidth().testTag("todo_editor_subtask_order_retry"),
                        ) { Text(stringResource(R.string.retry)) }
                    }
                    TodoChildActionError.DELETE -> {
                        AccessibleFeedback(stringResource(R.string.todo_subtask_delete_error))
                        LumaTextButton(
                            onClick = onRetryChildDelete,
                            modifier = Modifier.fillMaxWidth().testTag("todo_editor_subtask_delete_retry"),
                        ) { Text(stringResource(R.string.retry)) }
                    }
                    TodoChildActionError.STALE -> AccessibleFeedback(
                        stringResource(R.string.todo_subtask_changed),
                        isError = false,
                    )
                    null -> Unit
                }
                state.children.forEach { row ->
                    TodoChildRow(
                        row = row,
                        state = state,
                        enabled = canEdit && !state.busy,
                        canDeleteTaskOrigin = canDeleteTaskOrigin,
                        onOpenTask = onOpenTask,
                        onMoveChild = onMoveChild,
                        onRequestDeleteChild = onRequestDeleteChild,
                        onRequestDeleteUnlock = onRequestDeleteUnlock,
                    )
                }
                if (state.children.size < state.childTotal) {
                    LumaTextButton(
                        onClick = onLoadMoreChildren,
                        enabled = !state.childrenLoading,
                        modifier = Modifier.fillMaxWidth().testTag("todo_editor_subtasks_load_more"),
                    ) {
                        Text(stringResource(R.string.todo_subtasks_load_more))
                    }
                }
            }
        }
    }

    if (state.childDeleteConfirmationRequested) {
        LumaAlertDialog(
            onDismissRequest = onCancelDeleteChild,
            modifier = Modifier.testTag("todo_editor_subtask_delete_dialog"),
            title = { Text(stringResource(R.string.todo_subtask_delete_title)) },
            text = { Text(stringResource(R.string.todo_subtask_delete_body)) },
            confirmButton = {
                if (canDeleteTaskOrigin) {
                    LumaDestructiveTextButton(
                        onClick = onConfirmDeleteChild,
                        modifier = Modifier.testTag("todo_editor_subtask_confirm_delete"),
                    ) {
                        Text(stringResource(R.string.delete))
                    }
                } else {
                    LumaTextButton(
                        onClick = onRequestDeleteUnlock,
                        modifier = Modifier.fillMaxWidth().testTag("todo_editor_subtask_unlock_delete"),
                    ) { Text(stringResource(R.string.app_lock_gate_unlock)) }
                }
            },
            dismissButton = {
                LumaTextButton(onClick = onCancelDeleteChild) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun TodoChildRow(
    row: TodoTaskRow,
    state: TodoUiState,
    enabled: Boolean,
    canDeleteTaskOrigin: Boolean,
    onOpenTask: (UUID) -> Unit,
    onMoveChild: (UUID, UUID, TodoMoveDirection) -> Unit,
    onRequestDeleteChild: (UUID, UUID) -> Unit,
    onRequestDeleteUnlock: () -> Unit,
) {
    val task = row.task
    val id = task.id.toString()
    val position = requireNotNull(task.siblingPosition)
    val rowBusy = state.childActionPendingId == task.id
    LumaCard(
        modifier = Modifier.fillMaxWidth().testTag("todo_editor_subtask_$id"),
        shape = RoundedCornerShape(LumaUi.InnerCardRadius),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(LumaUi.SectionPadding),
            verticalArrangement = Arrangement.spacedBy(LumaUi.RelatedGap),
        ) {
            Text(task.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(if (task.isCompleted) R.string.todo_status_completed else R.string.todo_status_open),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LumaOutlinedButton(
                onClick = { onOpenTask(task.id) },
                enabled = enabled && !rowBusy,
                modifier = Modifier.fillMaxWidth().testTag("todo_editor_subtask_open_$id"),
            ) { Text(stringResource(R.string.todo_open_subtask)) }
            LumaTextButton(
                onClick = { onMoveChild(task.id, task.revision, TodoMoveDirection.UP) },
                enabled = enabled && !rowBusy && position > 0,
                modifier = Modifier.fillMaxWidth().testTag("todo_editor_subtask_move_up_$id"),
            ) { Text(stringResource(R.string.todo_subtask_move_up)) }
            LumaTextButton(
                onClick = { onMoveChild(task.id, task.revision, TodoMoveDirection.DOWN) },
                enabled = enabled && !rowBusy && position < (state.childTotal - 1).toLong(),
                modifier = Modifier.fillMaxWidth().testTag("todo_editor_subtask_move_down_$id"),
            ) { Text(stringResource(R.string.todo_subtask_move_down)) }
            if (canDeleteTaskOrigin) {
                LumaDestructiveTextButton(
                    onClick = { onRequestDeleteChild(task.id, task.revision) },
                    enabled = enabled && !rowBusy,
                    modifier = Modifier.fillMaxWidth().testTag("todo_editor_subtask_delete_$id"),
                ) { Text(stringResource(R.string.todo_subtask_delete)) }
            } else {
                LumaTextButton(
                    onClick = onRequestDeleteUnlock,
                    enabled = enabled && !rowBusy,
                    modifier = Modifier.fillMaxWidth().testTag("todo_editor_subtask_unlock_delete_$id"),
                ) { Text(stringResource(R.string.app_lock_gate_unlock)) }
            }
        }
    }
}

