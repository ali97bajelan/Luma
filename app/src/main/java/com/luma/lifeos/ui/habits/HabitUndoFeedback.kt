package com.luma.lifeos.ui.habits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.habit.HabitLog
import com.luma.lifeos.ui.common.AccessibleFeedback
import java.util.UUID

@Composable
fun HabitLogSnackbarFeedback(
    state: HabitsUiState,
    snackbarHostState: SnackbarHostState?,
    onUndo: (UUID) -> Unit,
    onAcknowledge: (UUID) -> Unit,
    onRetry: (UUID) -> Unit,
) {
    val savedLog = state.undoableLog
    val failedLog = state.undoFailures.firstOrNull()
    val savedMessage = savedLog?.let { log ->
        state.undoableHabitName?.let { stringResource(R.string.habit_log_saved_named, it) }
            ?: stringResource(R.string.habit_log_saved)
    }
    val failedMessage = failedLog?.let { stringResource(R.string.habit_undo_failed) }
    val undoLabel = stringResource(R.string.habit_undo)
    val retryLabel = stringResource(R.string.habit_retry_action)
    val feedbackKey = savedLog?.let { "saved:${it.id}" } ?: failedLog?.let { "failed:${it.id}" }

    LaunchedEffect(snackbarHostState, feedbackKey, savedMessage, failedMessage, undoLabel, retryLabel) {
        val host = snackbarHostState ?: return@LaunchedEffect
        when {
            savedLog != null && savedMessage != null -> {
                val result = host.showSnackbar(
                    message = savedMessage,
                    actionLabel = undoLabel,
                    withDismissAction = true,
                    duration = SnackbarDuration.Indefinite,
                )
                if (result == SnackbarResult.ActionPerformed) onUndo(savedLog.id)
                else onAcknowledge(savedLog.id)
            }
            failedLog != null && failedMessage != null -> {
                val result = host.showSnackbar(
                    message = failedMessage,
                    actionLabel = retryLabel,
                    withDismissAction = true,
                    duration = SnackbarDuration.Indefinite,
                )
                if (result == SnackbarResult.ActionPerformed) onRetry(failedLog.id)
            }
        }
    }
}

@Composable
fun HabitUndoStatusFeedback(
    state: HabitsUiState,
    onRetry: (UUID) -> Unit,
    modifier: Modifier = Modifier,
    onlyHabitId: UUID? = null,
) {
    val pending = state.undoingLogs.filter { onlyHabitId == null || it.habitId == onlyHabitId }
    val failures = state.undoFailures.filter { onlyHabitId == null || it.habitId == onlyHabitId }
    if (pending.isEmpty() && failures.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        pending.forEach { log ->
            UndoStatusCard(log, state.habitNameFor(log), pending = true, onRetry = onRetry)
        }
        failures.forEach { log ->
            UndoStatusCard(log, state.habitNameFor(log), pending = false, onRetry = onRetry)
        }
    }
}

@Composable
private fun UndoStatusCard(
    log: HabitLog,
    habitName: String?,
    pending: Boolean,
    onRetry: (UUID) -> Unit,
) {
    Card(Modifier.fillMaxWidth().testTag("habit_undo_feedback_${log.id}")) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            habitName?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
            if (pending) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    AccessibleFeedback(
                        stringResource(R.string.habit_undo_pending),
                        modifier = Modifier.weight(1f),
                        isError = false,
                    )
                }
            } else {
                AccessibleFeedback(stringResource(R.string.habit_undo_failed), isError = true)
                TextButton(
                    onClick = { onRetry(log.id) },
                    modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)
                        .testTag("habit_undo_retry_${log.id}"),
                ) {
                    Text(stringResource(R.string.habit_retry_action))
                }
            }
        }
    }
}

private fun HabitsUiState.habitNameFor(log: HabitLog): String? =
    detail?.habit?.takeIf { it.id == log.habitId }?.name
        ?: days.firstOrNull { it.day.habit.id == log.habitId }?.day?.habit?.name
