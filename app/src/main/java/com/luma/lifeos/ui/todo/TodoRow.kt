package com.luma.lifeos.ui.todo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.ui.common.LumaSectionSurface
import com.luma.lifeos.domain.todo.TodoBucket
import com.luma.lifeos.domain.todo.TodoTask
import com.luma.lifeos.domain.todo.todoBucket
import com.luma.lifeos.language.localizedDate
import java.time.ZoneId
import java.time.format.FormatStyle

@Composable
internal fun TodoRow(
    task: TodoTask,
    today: java.time.LocalDate,
    completedList: Boolean,
    canOpen: Boolean,
    canMutate: Boolean,
    pending: Boolean,
    error: TodoListRowError?,
    onOpen: () -> Unit,
    onSetCompleted: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onDismissError: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val statusDescription = stringResource(if (task.isCompleted) R.string.todo_status_completed else R.string.todo_status_open)
    val checkboxDescription = stringResource(
        if (task.isCompleted) R.string.todo_reopen else R.string.todo_mark_complete,
    )
    val secondary = task.completedAt?.let { completed ->
        stringResource(
            R.string.todo_completed_on,
            completed.atZone(ZoneId.systemDefault()).toLocalDate().format(localizedDate(FormatStyle.MEDIUM)),
        )
    } ?: task.dueDate?.let { due ->
        val formatted = due.format(localizedDate(FormatStyle.MEDIUM))
        stringResource(
            when (todoBucket(task, today)) {
                TodoBucket.OVERDUE -> R.string.todo_due_overdue
                TodoBucket.TODAY -> R.string.todo_due_today_list
                TodoBucket.UPCOMING -> R.string.todo_due_on
                TodoBucket.NO_DATE -> R.string.todo_no_date
            },
            formatted,
        )
    } ?: if (completedList) null else stringResource(R.string.todo_no_date)
    val originalDueDate = task.dueDate
    val originalDue = if (task.isCompleted && originalDueDate != null) {
        stringResource(R.string.todo_due_original, originalDueDate.format(localizedDate(FormatStyle.MEDIUM)))
    } else null
    val accessDescription = listOfNotNull(task.title, secondary, originalDue, statusDescription).joinToString(". ")

    val rowContent: @Composable ColumnScope.() -> Unit = {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = if (compact) 8.dp else 0.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = { onSetCompleted(it) },
                enabled = canMutate && !pending,
                modifier = Modifier.heightIn(min = 48.dp).semantics {
                    contentDescription = "${task.title}. $checkboxDescription"
                    stateDescription = statusDescription
                }.testTag("todo_status_${task.id}"),
            )
            TextButton(
                onClick = onOpen,
                enabled = canOpen,
                modifier = Modifier.weight(1f).heightIn(min = 56.dp).focusRequester(focusRequester)
                    .semantics { contentDescription = accessDescription }
                    .testTag("todo_open_${task.id}"),
            ) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(task.title, style = MaterialTheme.typography.bodyLarge)
                    secondary?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    originalDue?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        if (pending || error != null) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = if (compact) 8.dp else 0.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    stringResource(
                        when {
                            error == TodoListRowError.CONFLICT -> R.string.todo_list_row_conflict
                            error == TodoListRowError.MISSING -> R.string.todo_list_row_missing
                            error == TodoListRowError.FAILED -> R.string.todo_list_row_failed
                            task.isCompleted -> R.string.todo_reopening
                            else -> R.string.todo_completing
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (error == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("todo_row_status_${task.id}"),
                )
                if (error == TodoListRowError.FAILED) {
                    Button(
                        onClick = onRetry,
                        enabled = canMutate,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("todo_retry_${task.id}"),
                    ) {
                        Text(stringResource(R.string.todo_retry_action))
                    }
                } else if (error == TodoListRowError.CONFLICT || error == TodoListRowError.MISSING) {
                    if (error == TodoListRowError.CONFLICT) {
                        TextButton(
                            onClick = onOpen,
                            enabled = canOpen,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("todo_feedback_open_${task.id}"),
                        ) {
                            Text(stringResource(R.string.todo_status_open))
                        }
                    }
                    TextButton(onClick = onDismissError, modifier = Modifier.heightIn(min = 48.dp).testTag("todo_dismiss_${task.id}")) {
                        Text(stringResource(R.string.todo_feedback_dismiss))
                    }
                }
            }
        }
    }

    val rowModifier = modifier.fillMaxWidth().testTag("todo_row_${task.id}")
    if (compact) {
        Surface(
            modifier = rowModifier,
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                content = rowContent,
            )
        }
    } else {
        LumaSectionSurface(modifier = rowModifier, content = rowContent)
    }
}
