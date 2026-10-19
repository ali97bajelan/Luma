package com.luma.lifeos.ui.todo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.todo.TodoTask
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
    Card(Modifier.fillMaxWidth().testTag("today_tasks_card")) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.todo_list_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).semantics { heading() }.testTag("today_tasks_heading"),
                )
                TextButton(
                    onClick = onAllTasks,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("today_tasks_all"),
                ) {
                    Text(stringResource(R.string.todo_today_view_all))
                }
            }
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
                    Text(
                        stringResource(R.string.todo_today_count, NumberFormat.getIntegerInstance().format(state.total)),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("today_tasks_count"),
                    )
                    if (state.tasks.isEmpty()) {
                        Text(stringResource(R.string.todo_today_empty), modifier = Modifier.testTag("today_tasks_empty"))
                    } else {
                        state.tasks.forEach { task ->
                            key(task.id) {
                                val focusRequester = remember { FocusRequester() }
                                val rowError = actions.rowErrors[task.id]
                                TodoRow(
                                    task = task,
                                    today = today,
                                    completedList = false,
                                    enabled = rowError != TodoListRowError.CONFLICT && rowError != TodoListRowError.MISSING,
                                    pending = task.id in actions.pendingIntents,
                                    error = rowError,
                                    onOpen = { onTask(task.id) },
                                    onSetCompleted = { onSetCompleted(task, it) },
                                    onRetry = { onRetryTask(task.id) },
                                    onDismissError = { onDismissTaskError(task.id) },
                                    focusRequester = focusRequester,
                                )
                            }
                        }
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
}
