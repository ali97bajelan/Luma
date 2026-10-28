package com.luma.lifeos.ui.todo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import com.luma.lifeos.R
import com.luma.lifeos.domain.todo.TodoConflictException
import com.luma.lifeos.domain.todo.TodoMissingException
import com.luma.lifeos.domain.todo.TodoRepository
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

const val TODO_ACTION_RESULT_KEY = "todo_action_result"

@Composable
fun TodoActionFeedback(
    result: TodoActionResult?,
    snackbarHostState: SnackbarHostState,
    onUndo: suspend (TodoActionResult) -> TodoUndoOutcome,
    onConsumed: () -> Unit,
) {
    val message = stringResource(if (result?.completed == true) R.string.todo_completed_feedback else R.string.todo_reopened_feedback)
    val undoLabel = stringResource(R.string.todo_undo)
    val undoFailed = stringResource(R.string.todo_undo_retryable)
    val undoStale = stringResource(R.string.todo_undo_stale)
    LaunchedEffect(result) {
        val committed = result ?: return@LaunchedEffect
        val outcome = withTimeoutOrNull(3_000) { snackbarHostState.showSnackbar(
            message = message,
            actionLabel = undoLabel.takeIf { committed.completed },
            withDismissAction = true,
            duration = SnackbarDuration.Indefinite,
        ) }
        if (outcome == SnackbarResult.ActionPerformed && committed.completed) {
            var outcome = onUndo(committed)
            while (outcome == TodoUndoOutcome.RETRYABLE_FAILURE) {
                val retry = snackbarHostState.showSnackbar(
                    message = undoFailed,
                    actionLabel = undoLabel,
                    withDismissAction = true,
                    duration = SnackbarDuration.Indefinite,
                )
                if (retry != SnackbarResult.ActionPerformed) break
                outcome = onUndo(committed)
            }
            if (outcome == TodoUndoOutcome.STALE) {
                snackbarHostState.showSnackbar(
                    message = undoStale,
                    withDismissAction = true,
                    duration = SnackbarDuration.Indefinite,
                )
            }
        }
        onConsumed()
    }
}

@Composable
fun TodoActionFeedbackHost(
    entry: NavBackStackEntry?,
    repository: TodoRepository?,
    snackbarHostState: SnackbarHostState,
    resetToken: Int,
) {
    val saved = entry?.savedStateHandle
    val resultFlow = androidx.compose.runtime.remember(entry) {
        saved?.getStateFlow<String?>(TODO_ACTION_RESULT_KEY, null) ?: MutableStateFlow(null)
    }
    val encoded by resultFlow.collectAsStateWithLifecycle()
    val activeState = androidx.compose.runtime.remember(entry) {
        androidx.compose.runtime.mutableStateOf<TodoActionResult?>(null)
    }
    val lastReset = androidx.compose.runtime.remember(entry) {
        androidx.compose.runtime.mutableStateOf(resetToken)
    }

    LaunchedEffect(encoded) {
        val value = encoded ?: return@LaunchedEffect
        activeState.value = value.decodeActionResult()
        saved?.set<String?>(TODO_ACTION_RESULT_KEY, null)
    }
    LaunchedEffect(resetToken) {
        if (lastReset.value != resetToken) {
            lastReset.value = resetToken
            activeState.value = null
            snackbarHostState.currentSnackbarData?.dismiss()
        }
    }

    val current = activeState.value
    if (current != null && repository != null) {
        TodoActionFeedback(
            result = current,
            snackbarHostState = snackbarHostState,
            onUndo = { result ->
                val undoKey = "todo_action_undo_${result.taskId}_${result.resultingRevision}"
                val revision = saved?.get<String>(undoKey)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: UUID.randomUUID().also { saved?.set(undoKey, it.toString()) }
                try {
                    repository.setCompleted(
                        result.taskId,
                        result.resultingRevision,
                        revision,
                        completed = false,
                    )
                    saved?.remove<String>(undoKey)
                    TodoUndoOutcome.UNDONE
                } catch (failure: Exception) {
                    if (failure is kotlinx.coroutines.CancellationException) throw failure
                    val latest = runCatching { repository.get(result.taskId) }
                    val live = latest.getOrNull()
                    when {
                        live?.revision == revision && !live.isCompleted -> {
                            saved?.remove<String>(undoKey)
                            TodoUndoOutcome.UNDONE
                        }
                        live?.revision == result.resultingRevision && live.isCompleted -> TodoUndoOutcome.RETRYABLE_FAILURE
                        failure is TodoConflictException || failure is TodoMissingException || latest.isSuccess -> TodoUndoOutcome.STALE
                        else -> TodoUndoOutcome.RETRYABLE_FAILURE
                    }
                }
            },
            onConsumed = { activeState.value = null },
        )
    }
}

@Composable
fun TodoActionFeedbackCard(
    result: TodoActionResult,
    busy: Boolean,
    failed: Boolean,
    terminal: Boolean,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(result, busy, failed, terminal) {
        if (!busy && !failed && !terminal) {
            delay(3_000)
            onDismiss()
        }
    }
    Card(Modifier.fillMaxWidth().testTag("todo_completion_feedback")) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp).semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(if (result.completed) R.string.todo_completed_feedback else R.string.todo_reopened_feedback))
            if (failed) {
                Text(
                    stringResource(if (terminal) R.string.todo_undo_stale else R.string.todo_undo_retryable),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                if (result.completed && !terminal) {
                    Button(onClick = onUndo, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp).testTag("todo_undo")) {
                        Text(stringResource(if (busy) R.string.todo_undoing else R.string.todo_undo))
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp).testTag("todo_feedback_dismiss")) {
                    Text(stringResource(R.string.todo_feedback_dismiss))
                }
            }
        }
    }
}

fun TodoActionResult.encodeActionResult(): String = "$taskId|$completed|$resultingRevision"

private fun String.decodeActionResult(): TodoActionResult? = runCatching {
    val pieces = split('|')
    require(pieces.size == 3)
    TodoActionResult(UUID.fromString(pieces[0]), pieces[1].toBooleanStrict(), UUID.fromString(pieces[2]))
}.getOrNull()
