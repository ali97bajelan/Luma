package com.luma.lifeos.ui.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.focus.FocusSession
import com.luma.lifeos.domain.focus.FocusState
import com.luma.lifeos.domain.focus.FocusTerminalReason
import java.util.UUID

@Composable
internal fun FocusTaskCompletionAction(
    session: FocusSession,
    generation: UUID?,
    state: FocusTaskCompletionState,
    canAccessTaskActions: Boolean,
    onSessionChanged: (UUID, Long, UUID?, UUID?) -> Unit,
    onMarkComplete: (UUID, Long, UUID, UUID) -> Unit,
    onRetryCompletion: (UUID, Long, UUID, UUID) -> Unit,
    onUnlinkTask: (UUID, Long, UUID, UUID) -> Unit,
    onRetryUnlink: (UUID, Long, UUID, UUID) -> Unit,
    onChanged: () -> Unit,
) {
    if (!canAccessTaskActions || !session.taskOrigin) return
    if (!session.isTerminal) return

    val taskId = session.taskId
    LaunchedEffect(session.id, session.revision, taskId, generation) {
        onSessionChanged(session.id, session.revision, taskId, generation)
    }
    if (taskId == null) {
        Text(
            stringResource(R.string.focus_task_link_removed),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                .testTag("focus_task_link_removed"),
        )
        return
    }
    val currentGeneration = generation ?: return
    val stateMatches = state.matches(session.id, session.revision, taskId, currentGeneration)
    if (stateMatches && state.unlinkPhase == FocusTaskUnlinkPhase.UNLINKED) {
        Text(
            stringResource(R.string.focus_task_link_removed),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                .testTag("focus_task_link_removed"),
        )
        return
    }
    val visibleState = if (stateMatches) state else FocusTaskCompletionState(
        sessionId = session.id,
        sessionRevision = session.revision,
        taskId = taskId,
        generation = currentGeneration,
    )
    val completionEligible = session.state == FocusState.FINISHED &&
        (session.terminalReason in setOf(FocusTerminalReason.USER_STOP, FocusTerminalReason.TARGET_REACHED))

    LaunchedEffect(visibleState.completionPhase, visibleState.unlinkPhase, stateMatches) {
        if (stateMatches && (
                visibleState.completionPhase == FocusTaskCompletionPhase.COMPLETED ||
                    visibleState.completionPhase == FocusTaskCompletionPhase.CONFLICT ||
                    visibleState.unlinkPhase == FocusTaskUnlinkPhase.UNLINKED ||
                    visibleState.unlinkPhase == FocusTaskUnlinkPhase.CONFLICT
                )
        ) onChanged()
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        if (completionEligible) {
            when (visibleState.completionPhase) {
                FocusTaskCompletionPhase.COMPLETED ->
                    Text(
                        stringResource(R.string.todo_status_completed),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                            .testTag("focus_task_completed"),
                    )
                FocusTaskCompletionPhase.MISSING ->
                    Text(stringResource(R.string.todo_list_row_missing), modifier = Modifier.testTag("focus_task_completion_missing"))
                FocusTaskCompletionPhase.CONFLICT ->
                    Text(stringResource(R.string.todo_list_row_conflict), modifier = Modifier.testTag("focus_task_completion_conflict"))
                FocusTaskCompletionPhase.ERROR ->
                    Text(
                        stringResource(R.string.todo_list_row_failed),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                            .testTag("focus_task_completion_error"),
                    )
                FocusTaskCompletionPhase.SAVING, FocusTaskCompletionPhase.LOADING ->
                    Text(
                        stringResource(R.string.focus_task_complete_saving),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                            .testTag("focus_task_completion_saving"),
                    )
                else -> Unit
            }
            if (visibleState.completionPhase != FocusTaskCompletionPhase.COMPLETED &&
                visibleState.completionPhase != FocusTaskCompletionPhase.MISSING
            ) {
                TextButton(
                    onClick = {
                        if (visibleState.pendingCompletion != null) {
                            onRetryCompletion(session.id, session.revision, taskId, currentGeneration)
                        } else {
                            onMarkComplete(session.id, session.revision, taskId, currentGeneration)
                        }
                    },
                    enabled = visibleState.completionPhase != FocusTaskCompletionPhase.SAVING &&
                        visibleState.completionPhase != FocusTaskCompletionPhase.LOADING,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("focus_task_mark_complete"),
                ) {
                    if (visibleState.completionPhase == FocusTaskCompletionPhase.SAVING ||
                        visibleState.completionPhase == FocusTaskCompletionPhase.LOADING
                    ) CircularProgressIndicator()
                    else Text(stringResource(
                        if (visibleState.completionPhase == FocusTaskCompletionPhase.ERROR) R.string.todo_retry_action
                        else R.string.todo_mark_complete,
                    ))
                }
            }
        }

        if (visibleState.unlinkPhase == FocusTaskUnlinkPhase.CONFLICT) {
            Text(stringResource(R.string.todo_conflict), modifier = Modifier.testTag("focus_task_unlink_conflict"))
        } else if (visibleState.unlinkPhase == FocusTaskUnlinkPhase.ERROR) {
            Text(
                stringResource(R.string.todo_status_error),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag("focus_task_unlink_error"),
            )
        } else if (visibleState.unlinkPhase == FocusTaskUnlinkPhase.SAVING) {
            Text(
                stringResource(R.string.focus_task_unlink_saving),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag("focus_task_unlink_saving"),
            )
        }
        if (visibleState.unlinkPhase != FocusTaskUnlinkPhase.UNLINKED) {
            TextButton(
                onClick = {
                    if (visibleState.pendingUnlink != null) {
                        onRetryUnlink(session.id, session.revision, taskId, currentGeneration)
                    } else {
                        onUnlinkTask(session.id, session.revision, taskId, currentGeneration)
                    }
                },
                enabled = visibleState.unlinkPhase != FocusTaskUnlinkPhase.SAVING,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("focus_task_unlink"),
            ) {
                if (visibleState.unlinkPhase == FocusTaskUnlinkPhase.SAVING) CircularProgressIndicator()
                else Text(stringResource(
                    if (visibleState.unlinkPhase == FocusTaskUnlinkPhase.ERROR) R.string.todo_retry_action
                    else R.string.focus_task_remove,
                ))
            }
        }
    }
}
