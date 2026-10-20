package com.luma.lifeos.ui.todo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.todo.TodoBucket
import com.luma.lifeos.domain.todo.TodoTask
import com.luma.lifeos.domain.todo.todoBucket
import java.text.NumberFormat
import java.util.UUID
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoListScreen(
    state: TodoListUiState,
    onBack: () -> Unit,
    onAddTask: () -> Unit,
    onOpenTask: (UUID) -> Unit,
    onSelectCompleted: (Boolean) -> Unit,
    onSetCompleted: (UUID, Boolean) -> Unit,
    onRetry: (UUID) -> Unit,
    onDismissRowError: (UUID) -> Unit,
    onRetryLoad: () -> Unit,
    onLoadMore: () -> Unit,
    undoFeedback: @Composable () -> Unit,
) {
    val stateHolder = rememberSaveableStateHolder()
    val focusRequesters = remember { mutableStateMapOf<UUID, FocusRequester>() }
    val pendingFocus = remember { mutableStateOf<PendingRowFocus?>(null) }
    val addFocusRequester = remember { FocusRequester() }
    val openTabFocusRequester = remember { FocusRequester() }
    val count = NumberFormat.getIntegerInstance().format(state.page.total)
    val groups = remember(state.page.items, state.today) {
        TodoBucket.entries.map { bucket -> bucket to state.page.items.filter { todoBucket(it, state.today) == bucket } }
    }
    val visibleTasks = if (state.completed) state.page.items else groups.flatMap { it.second }
    val visibleIds = remember(visibleTasks) { visibleTasks.mapTo(mutableSetOf()) { it.id } }
    val offPageIntents = state.pendingIntents.values.filter { it.taskId !in visibleIds }
    val offPageErrors = state.rowErrors.entries.mapNotNull { (taskId, error) ->
        val title = state.rowErrorTitles[taskId]
        if (taskId !in visibleIds && taskId !in state.pendingIntents && title != null) {
            Triple(taskId, error, title)
        } else null
    }
    val undoItemCount = if (state.undoableCompletion != null) 1 else 0
    val recoveryItemCount = undoItemCount + offPageIntents.size + offPageErrors.size

    Scaffold(
        modifier = Modifier.testTag("screen_todo_list"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.todo_list_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp).testTag("todo_list_back")) {
                        Text(stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(
                        onClick = onAddTask,
                        modifier = Modifier.heightIn(min = 48.dp).focusRequester(addFocusRequester)
                            .testTag("todo_list_add"),
                    ) {
                        Text(stringResource(R.string.todo_list_add))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !state.completed,
                    onClick = { onSelectCompleted(false) },
                    label = { Text(stringResource(R.string.todo_list_open_tab)) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).focusRequester(openTabFocusRequester)
                        .testTag("todo_list_open_tab"),
                )
                FilterChip(
                    selected = state.completed,
                    onClick = { onSelectCompleted(true) },
                    label = { Text(stringResource(R.string.todo_list_completed_tab)) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("todo_list_completed_tab"),
                )
            }
            Text(
                stringResource(
                    if (state.completed) R.string.todo_list_completed_count else R.string.todo_list_open_count,
                    count,
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("todo_list_count"),
            )
            if (state.loadError == TodoListLoadError.REFRESH) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.todo_list_refresh_error),
                        Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(
                        onClick = onRetryLoad,
                        enabled = !state.refreshing,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("todo_list_retry_refresh"),
                    ) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
            if (state.loading || state.refreshing) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.heightIn(min = 24.dp), strokeWidth = 2.dp)
                    Text(
                        stringResource(
                            if (state.loading && state.page.items.isEmpty()) R.string.todo_list_loading
                            else R.string.todo_list_refreshing,
                        ),
                    )
                }
            }
            if (state.loadError == TodoListLoadError.INITIAL) {
                TodoListErrorCard(
                    message = R.string.todo_list_load_error,
                    onRetry = onRetryLoad,
                    retryTag = "todo_list_retry_initial",
                )
            }

            // Do not compose a loading-only list: LazyListState would clamp its saved index to zero.
            if (state.dataFresh || state.page.items.isNotEmpty() || recoveryItemCount > 0) {
                stateHolder.SaveableStateProvider(if (state.completed) "completed" else "open") {
                    val listState = rememberLazyListState()
                    LaunchedEffect(
                        state.completed,
                        visibleIds,
                        state.pendingIntents.keys,
                        state.inFlight,
                        state.dataFresh,
                        state.rowErrors,
                        state.undoableCompletion,
                    ) {
                        val focus = pendingFocus.value ?: return@LaunchedEffect
                        if (focus.completedMode != state.completed) {
                            pendingFocus.value = null
                            return@LaunchedEffect
                        }
                        if (focus.taskId in state.inFlight) return@LaunchedEffect
                        val pendingIntentIndex = offPageIntents.indexOfFirst { it.taskId == focus.taskId }
                        if (pendingIntentIndex >= 0) {
                            listState.animateScrollToItem(undoItemCount + pendingIntentIndex)
                            pendingFocus.value = null
                            return@LaunchedEffect
                        }
                        if (focus.taskId in state.pendingIntents) return@LaunchedEffect
                        if (!state.dataFresh) return@LaunchedEffect
                        if (focus.taskId in visibleIds) {
                            val matchingResult = state.undoableCompletion?.let {
                                it.taskId == focus.taskId && it.completed == focus.desiredCompleted
                            } == true
                            if (!matchingResult) pendingFocus.value = null
                            return@LaunchedEffect
                        }

                        val matchingUndo = state.undoableCompletion?.let {
                            it.taskId == focus.taskId && it.completed == focus.desiredCompleted
                        } == true
                        if (matchingUndo) {
                            listState.animateScrollToItem(0)
                            pendingFocus.value = null
                            return@LaunchedEffect
                        }
                        val terminalRecoveryIndex = offPageErrors.indexOfFirst { it.first == focus.taskId }
                        if (terminalRecoveryIndex >= 0) {
                            listState.animateScrollToItem(undoItemCount + offPageIntents.size + terminalRecoveryIndex)
                            pendingFocus.value = null
                            return@LaunchedEffect
                        }

                        val targetIndex = focus.originalIndex.coerceAtMost(visibleTasks.lastIndex)
                        if (targetIndex < 0) {
                            if (state.completed) openTabFocusRequester.requestFocus() else addFocusRequester.requestFocus()
                        } else {
                            val target = visibleTasks[targetIndex]
                            val lazyIndex = if (state.completed) {
                                recoveryItemCount + targetIndex
                            } else {
                                var index = recoveryItemCount
                                for ((_, rows) in groups) {
                                    if (rows.isNotEmpty()) {
                                        index += 1
                                        if (rows.any { it.id == target.id }) {
                                            index += rows.indexOfFirst { it.id == target.id }
                                            break
                                        }
                                        index += rows.size
                                    }
                                }
                                index
                            }
                            listState.animateScrollToItem(lazyIndex)
                            val requester = snapshotFlow { focusRequesters[target.id] }.filterNotNull().first()
                            requester.requestFocus()
                        }
                        pendingFocus.value = null
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth().testTag("todo_list_rows"),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        state.undoableCompletion?.let { result ->
                            item(key = "undo:${result.taskId}:${result.resultingRevision}") {
                                undoFeedback()
                            }
                        }
                        offPageIntents.forEach { intent ->
                            item(key = "recovery:intent:${intent.taskId}:${intent.resultingRevision}") {
                                TodoPendingActionCard(
                                    intent = intent,
                                    error = state.rowErrors[intent.taskId],
                                    pending = intent.taskId in state.inFlight,
                                    retryEnabled = state.dataFresh,
                                    onRetry = { onRetry(intent.taskId) },
                                    onOpen = { onOpenTask(intent.taskId) },
                                )
                            }
                        }
                        offPageErrors.forEach { (taskId, error, title) ->
                            item(key = "recovery:task:$taskId") {
                                TodoTerminalRowErrorCard(
                                    taskId = taskId,
                                    title = title,
                                    error = error,
                                    onOpen = { onOpenTask(taskId) },
                                    onDismiss = { onDismissRowError(taskId) },
                                )
                            }
                        }
                        if (state.dataFresh && state.page.items.isEmpty()) {
                            item(key = "empty") {
                                Card(Modifier.fillMaxWidth().testTag("todo_list_empty")) {
                                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text(
                                            stringResource(
                                                if (state.completed) R.string.todo_list_empty_completed
                                                else R.string.todo_list_empty_open,
                                            ),
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                        if (state.completed) {
                                            TextButton(
                                                onClick = { onSelectCompleted(false) },
                                                modifier = Modifier.heightIn(min = 48.dp),
                                            ) {
                                                Text(stringResource(R.string.todo_list_open_tab))
                                            }
                                        } else {
                                            Button(
                                                onClick = onAddTask,
                                                modifier = Modifier.heightIn(min = 48.dp),
                                            ) {
                                                Text(stringResource(R.string.todo_list_add))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (state.page.items.isNotEmpty()) {
                            if (state.completed) {
                                items(state.page.items, key = { "task:${it.id}" }) { task ->
                                    val rowError = state.rowErrors[task.id]
                                    val rowFocusRequester = remember(task.id) { FocusRequester() }
                                    DisposableEffect(task.id, rowFocusRequester) {
                                        focusRequesters[task.id] = rowFocusRequester
                                        onDispose {
                                            if (focusRequesters[task.id] === rowFocusRequester) focusRequesters.remove(task.id)
                                        }
                                    }
                                    TodoRow(
                                        task = task,
                                        today = state.today,
                                        completedList = true,
                                        canOpen = rowError != TodoListRowError.MISSING,
                                        canMutate = state.dataFresh && rowError != TodoListRowError.CONFLICT &&
                                            rowError != TodoListRowError.MISSING,
                                        pending = task.id in state.inFlight || task.id in state.pendingIntents ||
                                            (state.undoing && state.undoableCompletion?.taskId == task.id),
                                        error = rowError,
                                        onOpen = {
                                            onOpenTask(task.id)
                                        },
                                        onSetCompleted = { completed ->
                                            pendingFocus.value = PendingRowFocus(
                                                task.id,
                                                state.completed,
                                                state.page.items.indexOfFirst { it.id == task.id },
                                                completed,
                                            )
                                            onSetCompleted(task.id, completed)
                                        },
                                        onRetry = { onRetry(task.id) },
                                        onDismissError = { onDismissRowError(task.id) },
                                        focusRequester = rowFocusRequester,
                                    )
                                }
                            } else {
                                groups.filter { it.second.isNotEmpty() }.forEach { (bucket, rows) ->
                                    item(key = "bucket:${bucket.name}") {
                                        Text(
                                            stringResource(bucket.headingResource()),
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.padding(top = 8.dp).semantics { heading() }
                                                .testTag("todo_group_${bucket.name.lowercase()}"),
                                        )
                                    }
                                    items(rows, key = { "task:${it.id}" }) { task ->
                                        val rowError = state.rowErrors[task.id]
                                        val rowFocusRequester = remember(task.id) { FocusRequester() }
                                        DisposableEffect(task.id, rowFocusRequester) {
                                            focusRequesters[task.id] = rowFocusRequester
                                            onDispose {
                                                if (focusRequesters[task.id] === rowFocusRequester) focusRequesters.remove(task.id)
                                            }
                                        }
                                        TodoRow(
                                            task = task,
                                            today = state.today,
                                            completedList = false,
                                            canOpen = rowError != TodoListRowError.MISSING,
                                            canMutate = state.dataFresh && rowError != TodoListRowError.CONFLICT &&
                                                rowError != TodoListRowError.MISSING,
                                            pending = task.id in state.inFlight || task.id in state.pendingIntents ||
                                                (state.undoing && state.undoableCompletion?.taskId == task.id),
                                            error = rowError,
                                            onOpen = {
                                                onOpenTask(task.id)
                                            },
                                            onSetCompleted = { completed ->
                                                pendingFocus.value = PendingRowFocus(
                                                    task.id,
                                                    state.completed,
                                                    visibleTasks.indexOfFirst { it.id == task.id },
                                                    completed,
                                                )
                                                onSetCompleted(task.id, completed)
                                            },
                                            onRetry = { onRetry(task.id) },
                                            onDismissError = { onDismissRowError(task.id) },
                                            focusRequester = rowFocusRequester,
                                        )
                                    }
                                }
                            }
                        }
                        if (state.loadError == TodoListLoadError.PAGE) {
                            item(key = "page_error") {
                                TodoListErrorCard(
                                    message = R.string.todo_list_page_error,
                                    onRetry = onRetryLoad,
                                    retryTag = "todo_list_retry_page",
                                )
                            }
                        }
                        if (state.dataFresh && state.loadError != TodoListLoadError.PAGE &&
                            state.page.items.size < state.page.total
                        ) {
                            item(key = "load_more") {
                                TextButton(
                                    onClick = onLoadMore,
                                    enabled = !state.loadingMore,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                        .testTag("todo_list_load_more"),
                                ) {
                                    if (state.loadingMore) {
                                        CircularProgressIndicator(Modifier.heightIn(min = 20.dp), strokeWidth = 2.dp)
                                    }
                                    Text(
                                        stringResource(
                                            if (state.loadingMore) R.string.todo_list_loading_more
                                            else R.string.todo_list_load_more,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class PendingRowFocus(
    val taskId: UUID,
    val completedMode: Boolean,
    val originalIndex: Int,
    val desiredCompleted: Boolean,
)

@Composable
private fun TodoPendingActionCard(
    intent: TodoMutationIntent,
    error: TodoListRowError?,
    pending: Boolean,
    retryEnabled: Boolean,
    onRetry: () -> Unit,
    onOpen: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().testTag("todo_pending_${intent.taskId}")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(intent.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(
                    when {
                        pending -> if (intent.completed) R.string.todo_completing else R.string.todo_reopening
                        error == TodoListRowError.CONFLICT -> R.string.todo_list_row_conflict
                        error == TodoListRowError.MISSING -> R.string.todo_list_row_missing
                        else -> R.string.todo_list_row_failed
                    },
                ),
                color = if (pending) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
            if (!pending) {
                TextButton(onClick = onRetry, enabled = retryEnabled, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.todo_retry_action))
                }
                if (error == TodoListRowError.CONFLICT) {
                    TextButton(onClick = onOpen, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.todo_status_open))
                    }
                }
            }
        }
    }
}

@Composable
private fun TodoTerminalRowErrorCard(
    taskId: UUID,
    title: String,
    error: TodoListRowError,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().testTag("todo_row_error_$taskId")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(
                    if (error == TodoListRowError.MISSING) R.string.todo_list_row_missing
                    else R.string.todo_list_row_conflict,
                ),
                color = MaterialTheme.colorScheme.error,
            )
            if (error == TodoListRowError.CONFLICT) {
                TextButton(onClick = onOpen, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.todo_status_open))
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.todo_feedback_dismiss))
            }
        }
    }
}

@Composable
private fun TodoListErrorCard(message: Int, onRetry: () -> Unit, retryTag: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(message), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp).testTag(retryTag)) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

private fun TodoBucket.headingResource(): Int = when (this) {
    TodoBucket.OVERDUE -> R.string.todo_group_overdue
    TodoBucket.TODAY -> R.string.todo_group_today
    TodoBucket.UPCOMING -> R.string.todo_group_upcoming
    TodoBucket.NO_DATE -> R.string.todo_group_no_date
}
