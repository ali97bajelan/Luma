package com.luma.lifeos.ui.todo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.ui.common.LumaSectionHeader
import com.luma.lifeos.ui.common.LumaSectionSurface
import com.luma.lifeos.domain.todo.TodoBucket
import com.luma.lifeos.domain.todo.TodoTask
import com.luma.lifeos.domain.todo.todoBucket
import java.text.NumberFormat
import java.time.LocalDate
import java.util.UUID

@Composable
fun TodayTasksCard(
    state: TodayTasksState,
    actions: TodayTasksActionState,
    today: LocalDate,
    onTask: (UUID) -> Unit,
    onSetCompleted: (TodoTask, Boolean) -> Unit,
    onAllTasks: () -> Unit,
    onAddTask: () -> Unit,
    onRetry: () -> Unit,
    onRetryTask: (UUID) -> Unit,
    onDismissTaskError: (UUID) -> Unit,
) {
    LumaSectionSurface(modifier = Modifier.testTag("today_tasks_card")) {
        LumaSectionHeader(
            title = stringResource(R.string.todo_list_title),
            modifier = Modifier.testTag("today_tasks_heading"),
            actionLabel = stringResource(R.string.todo_today_view_all),
            onAction = onAllTasks,
            actionModifier = Modifier.heightIn(min = 48.dp).testTag("today_tasks_all"),
        )
        when (state) {
            TodayTasksState.Loading -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(Modifier.heightIn(min = 24.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.todo_list_loading), modifier = Modifier.testTag("today_tasks_loading"))
            }
            TodayTasksState.Error -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.todo_list_load_error),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("today_tasks_error"),
                )
                TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp).testTag("today_tasks_retry")) {
                    Text(stringResource(R.string.retry))
                }
            }
            is TodayTasksState.Data -> {
                if (state.total > 0) {
                    Text(
                        stringResource(R.string.todo_today_count, NumberFormat.getIntegerInstance().format(state.total)),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("today_tasks_count"),
                    )
                }
                if (state.total == 0 && state.tasks.isEmpty()) {
                    Text(stringResource(R.string.todo_today_empty), modifier = Modifier.testTag("today_tasks_empty"))
                } else if (state.tasks.isNotEmpty()) {
                    listOf(TodoBucket.OVERDUE, TodoBucket.TODAY).forEach { bucket ->
                        val bucketTasks = state.tasks.filter { task ->
                            !task.isCompleted && todoBucket(task, today) == bucket
                        }
                        if (bucketTasks.isNotEmpty()) {
                            Text(
                                stringResource(if (bucket == TodoBucket.OVERDUE) R.string.todo_group_overdue else R.string.todo_group_today),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (bucket == TodoBucket.OVERDUE) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.semantics { heading() }
                                    .testTag("today_tasks_group_${bucket.name.lowercase()}"),
                            )
                            bucketTasks.forEach { task ->
                                key(task.id) {
                                    val focusRequester = remember { FocusRequester() }
                                    val rowError = actions.rowErrors[task.id]
                                    TodoRow(
                                        task = task,
                                        today = today,
                                        completedList = false,
                                        canOpen = rowError != TodoListRowError.MISSING,
                                        canMutate = rowError != TodoListRowError.CONFLICT && rowError != TodoListRowError.MISSING,
                                        pending = task.id in actions.pendingIntents,
                                        error = rowError,
                                        onOpen = { onTask(task.id) },
                                        onSetCompleted = { onSetCompleted(task, it) },
                                        onRetry = { onRetryTask(task.id) },
                                        onDismissError = { onDismissTaskError(task.id) },
                                        focusRequester = focusRequester,
                                        compact = true,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        val visibleTaskIds = state.visibleTaskIds()
        val offPreviewIntents = actions.pendingIntents.values
            .filter { it.taskId !in visibleTaskIds }
            .sortedBy { it.title.lowercase() }
        val offPreviewErrors = actions.rowFeedback.entries
            .filter { (taskId, _) -> taskId !in visibleTaskIds && taskId !in actions.pendingIntents }
            .sortedBy { it.value.title.lowercase() }
        if (offPreviewIntents.isNotEmpty() || offPreviewErrors.isNotEmpty()) {
            HorizontalDivider()
            Text(
                stringResource(R.string.todo_today_task_feedback),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() }.testTag("today_tasks_feedback_heading"),
            )
            Column(
                Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState())
                    .testTag("today_tasks_feedback_scroll"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                offPreviewIntents.forEach { intent ->
                    val feedback = actions.rowFeedback[intent.taskId]
                    TodayTaskRecoverySummary(
                        taskId = intent.taskId,
                        title = intent.title,
                        completesTask = intent.completed,
                        error = feedback?.error,
                        pending = intent.taskId in actions.inFlight,
                        retryEnabled = feedback?.error == TodoListRowError.FAILED && intent.taskId !in actions.inFlight,
                        onRetry = { onRetryTask(intent.taskId) },
                        onOpen = { onTask(intent.taskId) },
                        onDismiss = { onDismissTaskError(intent.taskId) },
                    )
                }
                offPreviewErrors.forEach { (taskId, feedback) ->
                    TodayTaskRecoverySummary(
                        taskId = taskId,
                        title = feedback.title,
                        completesTask = feedback.completesTask,
                        error = feedback.error,
                        pending = false,
                        retryEnabled = false,
                        onRetry = {},
                        onOpen = { onTask(taskId) },
                        onDismiss = { onDismissTaskError(taskId) },
                    )
                }
            }
        }
        TextButton(
            onClick = onAddTask,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("today_tasks_add"),
        ) {
            Text(stringResource(R.string.todo_list_add))
        }
    }
}

@Composable
internal fun TodayTaskRecoverySummary(
    taskId: UUID,
    title: String,
    completesTask: Boolean,
    error: TodoListRowError?,
    pending: Boolean,
    retryEnabled: Boolean,
    onRetry: () -> Unit,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().testTag("today_task_feedback_$taskId"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            stringResource(
                R.string.todo_today_task_action,
                stringResource(if (completesTask) R.string.todo_mark_complete else R.string.todo_reopen),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(
                when {
                    pending -> if (completesTask) R.string.todo_completing else R.string.todo_reopening
                    error == TodoListRowError.CONFLICT -> R.string.todo_list_row_conflict
                    error == TodoListRowError.MISSING -> R.string.todo_list_row_missing
                    else -> R.string.todo_list_row_failed
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = if (pending) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                .testTag("today_task_feedback_status_$taskId"),
        )
        if (retryEnabled || error == TodoListRowError.CONFLICT || error == TodoListRowError.MISSING) {
            Column(Modifier.fillMaxWidth()) {
                if (retryEnabled) {
                    TextButton(
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .testTag("today_task_feedback_retry_$taskId"),
                    ) {
                        Text(stringResource(R.string.todo_retry_action))
                    }
                }
                if (error == TodoListRowError.CONFLICT) {
                    TextButton(
                        onClick = onOpen,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .testTag("today_task_feedback_open_$taskId"),
                    ) {
                        Text(stringResource(R.string.todo_status_open))
                    }
                }
                if (error == TodoListRowError.CONFLICT || error == TodoListRowError.MISSING) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .testTag("today_task_feedback_dismiss_$taskId"),
                    ) {
                        Text(stringResource(R.string.todo_feedback_dismiss))
                    }
                }
            }
        }
    }
}
