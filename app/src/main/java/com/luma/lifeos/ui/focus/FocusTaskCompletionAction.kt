package com.luma.lifeos.ui.focus

import com.luma.lifeos.ui.common.LumaUi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.luma.lifeos.ui.common.LumaTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.luma.lifeos.R
import com.luma.lifeos.ui.todo.TodoActionFeedbackCard
import com.luma.lifeos.ui.todo.TodoFamilyActions
import com.luma.lifeos.ui.todo.TodoFamilyActionDialogs
import com.luma.lifeos.ui.todo.TodoFamilyActionPhase
import com.luma.lifeos.domain.focus.FocusSession
import com.luma.lifeos.domain.focus.FocusState
import com.luma.lifeos.domain.focus.FocusTerminalReason
import java.util.UUID
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
internal fun FocusTaskCompletionAction(
    session: FocusSession,
    generation: UUID?,
    state: FocusTaskCompletionState,
    canAccessTaskActions: Boolean,
    onSessionChanged: (UUID, Long, UUID?, UUID?) -> Unit,
    onRefreshSession: (UUID, Long, UUID?, UUID?) -> Unit,
    onUndoCompletion: () -> Unit,
    onDismissUndo: () -> Unit,
    onMarkComplete: (UUID, Long, UUID, UUID) -> Unit,
    onRetryCompletion: (UUID, Long, UUID, UUID) -> Unit,
    onUnlinkTask: (UUID, Long, UUID, UUID) -> Unit,
    onRetryUnlink: (UUID, Long, UUID, UUID) -> Unit,
    onChanged: () -> Unit,
    familyActions: TodoFamilyActions? = null,
) {
    if (!canAccessTaskActions || !session.taskOrigin) return
    if (!session.isTerminal) return

    val taskId = session.taskId
    LaunchedEffect(session.id, session.revision, taskId, generation) {
        onSessionChanged(session.id, session.revision, taskId, generation)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, session.id, session.revision, taskId, generation) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onRefreshSession(session.id, session.revision, taskId, generation)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
    val visibleState = if (stateMatches) state else FocusTaskCompletionState(
        sessionId = session.id,
        sessionRevision = session.revision,
        taskId = taskId,
        generation = currentGeneration,
        completionPhase = FocusTaskCompletionPhase.CHECKING,
    )
    val completionEligible = session.state == FocusState.FINISHED &&
        (session.terminalReason in setOf(FocusTerminalReason.USER_STOP, FocusTerminalReason.TARGET_REACHED))

    var lastNotifiedAction by remember(session.id, stateMatches) {
        mutableStateOf(visibleState.familyAction.phase to visibleState.unlinkPhase)
    }
    LaunchedEffect(visibleState.familyAction.phase, visibleState.unlinkPhase, stateMatches) {
        val currentAction = visibleState.familyAction.phase to visibleState.unlinkPhase
        val previousAction = lastNotifiedAction
        lastNotifiedAction = currentAction
        val familyActionFinished = previousAction.first != currentAction.first && currentAction.first in setOf(
            TodoFamilyActionPhase.APPLIED,
            TodoFamilyActionPhase.CONFLICT,
            TodoFamilyActionPhase.MISSING,
        )
        val unlinkActionFinished = previousAction.second != currentAction.second && currentAction.second in setOf(
            FocusTaskUnlinkPhase.UNLINKED,
            FocusTaskUnlinkPhase.CONFLICT,
        )
        if (stateMatches && (familyActionFinished || unlinkActionFinished)) onChanged()
    }
    if (stateMatches && visibleState.unlinkPhase == FocusTaskUnlinkPhase.UNLINKED) {
        Text(
            stringResource(R.string.focus_task_link_removed),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                .testTag("focus_task_link_removed"),
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(LumaUi.RelatedGap), modifier = Modifier.fillMaxWidth()) {
        if (completionEligible && visibleState.familyAction.phase !in setOf(
                TodoFamilyActionPhase.AWAITING_CONFIRMATION,
                TodoFamilyActionPhase.RETRYABLE_FAILURE,
                TodoFamilyActionPhase.CONFLICT,
                TodoFamilyActionPhase.MISSING,
            )
        ) {
            if (visibleState.completionPhase in setOf(
                    FocusTaskCompletionPhase.IDLE,
                    FocusTaskCompletionPhase.ERROR,
                    FocusTaskCompletionPhase.CONFLICT,
                )
            ) {
                Text(
                    stringResource(R.string.focus_task_completion_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("focus_task_completion_explanation"),
                )
            }
            when (visibleState.completionPhase) {
                FocusTaskCompletionPhase.CHECKING ->
                    Text(
                        stringResource(R.string.focus_task_checking),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                            .testTag("focus_task_checking"),
                    )
                FocusTaskCompletionPhase.CHECK_ERROR ->
                    Text(
                        stringResource(R.string.todo_list_row_failed),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                            .testTag("focus_task_check_error"),
                    )
                FocusTaskCompletionPhase.LINK_CONFLICT ->
                    Text(
                        stringResource(R.string.todo_list_row_conflict),
                        modifier = Modifier.testTag("focus_task_link_conflict"),
                    )
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
                    if (visibleState.familyAction.phase == TodoFamilyActionPhase.PREVIEWING ||
                        visibleState.familyAction.phase == TodoFamilyActionPhase.APPLYING ||
                        familyActions == null
                    ) Text(
                        stringResource(R.string.focus_task_complete_saving),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                            .testTag("focus_task_completion_saving"),
                    )
                else -> Unit
            }
            if (visibleState.completionPhase == FocusTaskCompletionPhase.CHECK_ERROR) {
                LumaTextButton(
                    onClick = { onRefreshSession(session.id, session.revision, taskId, currentGeneration) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = LumaUi.MinTouchTargetHeight)
                        .testTag("focus_task_check_again"),
                ) {
                    Text(stringResource(R.string.focus_task_check_again))
                }
            }
            if (!visibleState.familyAction.isActive && visibleState.completionPhase in setOf(
                    FocusTaskCompletionPhase.IDLE,
                    FocusTaskCompletionPhase.ERROR,
                    FocusTaskCompletionPhase.CONFLICT,
                )
            ) {
                LumaTextButton(
                    onClick = {
                        if (visibleState.pendingCompletion != null ||
                            visibleState.completionPhase == FocusTaskCompletionPhase.ERROR ||
                            visibleState.completionPhase == FocusTaskCompletionPhase.CONFLICT
                        ) {
                            onRetryCompletion(session.id, session.revision, taskId, currentGeneration)
                        } else {
                            onMarkComplete(session.id, session.revision, taskId, currentGeneration)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = LumaUi.MinTouchTargetHeight).testTag("focus_task_mark_complete"),
                ) {
                    Text(stringResource(
                        if (visibleState.completionPhase in setOf(
                            FocusTaskCompletionPhase.ERROR,
                            FocusTaskCompletionPhase.CONFLICT,
                        )) R.string.todo_retry_action else R.string.focus_task_mark_complete,
                    ))
                }
            }
        }

        visibleState.undoableCompletion?.let { result ->
            TodoActionFeedbackCard(
                result = result,
                busy = visibleState.undoing,
                failed = visibleState.undoFailed,
                terminal = visibleState.undoTerminal,
                onUndo = onUndoCompletion,
                onDismiss = onDismissUndo,
            )
        }

        if (familyActions != null) {
            TodoFamilyActionDialogs(
                state = visibleState.familyAction,
                onConfirm = familyActions::confirm,
                onCancel = familyActions::cancel,
                onRetry = familyActions::retry,
                onReviewAgain = familyActions::reviewAgain,
                onDismiss = familyActions::dismiss,
            )
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
            LumaTextButton(
                onClick = {
                    if (visibleState.pendingUnlink != null) {
                        onRetryUnlink(session.id, session.revision, taskId, currentGeneration)
                    } else {
                        onUnlinkTask(session.id, session.revision, taskId, currentGeneration)
                    }
                },
                enabled = visibleState.unlinkPhase != FocusTaskUnlinkPhase.SAVING,
                modifier = Modifier.fillMaxWidth().heightIn(min = LumaUi.MinTouchTargetHeight).testTag("focus_task_unlink"),
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
