package com.luma.lifeos.ui.todo

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.SavedStateHandle
import com.luma.lifeos.R
import com.luma.lifeos.domain.todo.TodoCompletionReceipt
import com.luma.lifeos.domain.todo.TodoConflictException
import com.luma.lifeos.domain.todo.TodoMissingException
import com.luma.lifeos.domain.todo.TodoRepository
import com.luma.lifeos.ui.common.LumaAlertDialog
import com.luma.lifeos.ui.common.LumaDestructiveTextButton
import com.luma.lifeos.ui.common.LumaTextButton
import com.luma.lifeos.ui.common.LumaUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Shared family mutation policy for every in-app task entry point.
 *
 * Saved state contains only command/plan handles and small operation metadata. The immutable
 * family snapshot and idempotency receipt remain in the repository's local command journal.
 */
class TodoFamilyActions(
    private val repository: TodoRepository,
    private val saved: SavedStateHandle,
    private val scope: CoroutineScope,
    private val keyPrefix: String,
    private val onCompletionApplied: (TodoCompletionReceipt) -> Unit,
    private val onDeleteApplied: (UUID) -> Unit,
    private val beforeCompletionApply: suspend (TodoFamilyActionState) -> Unit = {},
) {
    private val mutableState = MutableStateFlow(readState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null

    init {
        reconcileRestoredHandle()
    }

    fun complete(taskId: UUID, completed: Boolean, title: String? = null, expectedRevision: UUID? = null) {
        if (mutableState.value.isActive) return
        begin(TodoFamilyActionType.COMPLETION, taskId, title, completed, expectedRevision)
    }

    fun delete(taskId: UUID, title: String? = null, expectedRevision: UUID? = null) {
        if (mutableState.value.isActive) return
        begin(TodoFamilyActionType.DELETE, taskId, title, completed = null, expectedRevision = expectedRevision)
    }

    fun confirm() {
        val current = mutableState.value
        if (current.phase != TodoFamilyActionPhase.AWAITING_CONFIRMATION) return
        apply(current)
    }

    fun cancel() {
        val current = mutableState.value
        if (current.phase != TodoFamilyActionPhase.AWAITING_CONFIRMATION &&
            current.phase != TodoFamilyActionPhase.PREVIEWING
        ) return
        job?.cancel()
        current.planId?.let(::release)
        clear()
    }

    fun retry() {
        val current = mutableState.value
        if (current.phase != TodoFamilyActionPhase.RETRYABLE_FAILURE) return
        if (current.planId == null) begin(
            current.type ?: return,
            current.targetId ?: return,
            current.targetTitle,
            current.completed,
            current.expectedRevision,
        ) else apply(current)
    }

    /** A stale preview can only be acted on after a fresh preview and confirmation. */
    fun reviewAgain() {
        val current = mutableState.value
        if (current.phase != TodoFamilyActionPhase.CONFLICT && current.phase != TodoFamilyActionPhase.MISSING) return
        current.planId?.let(::release)
        begin(current.type ?: return, current.targetId ?: return, current.targetTitle, current.completed, expectedRevision = null)
    }

    fun dismiss() {
        val current = mutableState.value
        if (current.phase == TodoFamilyActionPhase.APPLIED) return
        current.planId?.let(::release)
        clear()
    }

    fun release(commandId: UUID) {
        scope.launch { runCatching { repository.releaseCommandHandle(commandId) } }
    }

    fun invalidate() {
        job?.cancel()
        val current = mutableState.value
        current.planId?.let(::release)
        clear()
    }

    suspend fun undo(commandId: UUID, undoCommandId: UUID): TodoCompletionReceipt =
        repository.undoCompletion(commandId, undoCommandId)

    private fun begin(
        type: TodoFamilyActionType,
        taskId: UUID,
        title: String?,
        completed: Boolean?,
        expectedRevision: UUID?,
    ) {
        job?.cancel()
        val commandId = UUID.randomUUID()
        val initial = TodoFamilyActionState(
            phase = TodoFamilyActionPhase.PREVIEWING,
            type = type,
            targetId = taskId,
            targetTitle = title,
            expectedRevision = expectedRevision,
            commandId = commandId,
            completed = completed,
        )
        save(initial)
        setState(initial)
        job = scope.launch {
            try {
                val latest = repository.get(taskId) ?: throw TodoMissingException(taskId)
                if (expectedRevision != null && latest.revision != expectedRevision) {
                    throw TodoConflictException(taskId)
                }
                val currentTitle = latest.title
                when (type) {
                    TodoFamilyActionType.COMPLETION -> {
                        val preview = repository.prepareCompletion(taskId, requireNotNull(completed))
                        val prepared = initial.copy(
                            targetTitle = currentTitle,
                            phase = if (preview.confirmationRequired) {
                                TodoFamilyActionPhase.AWAITING_CONFIRMATION
                            } else {
                                TodoFamilyActionPhase.APPLYING
                            },
                            planId = preview.planId,
                            changedTaskCount = preview.changedTaskCount,
                            openChildCount = preview.openChildCount,
                            reopensParent = preview.reopensParent,
                        )
                        setState(prepared)
                        if (!preview.confirmationRequired) apply(prepared)
                    }
                    TodoFamilyActionType.DELETE -> {
                        val preview = repository.prepareDelete(taskId)
                        setState(
                            initial.copy(
                                targetTitle = currentTitle,
                                phase = TodoFamilyActionPhase.AWAITING_CONFIRMATION,
                                planId = preview.planId,
                                childCount = preview.childCount,
                                changedTaskCount = preview.childCount + 1,
                            ),
                        )
                    }
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                fail(initial, failure)
            }
        }
    }

    private fun apply(current: TodoFamilyActionState) {
        val planId = current.planId ?: return
        val commandId = current.commandId ?: return
        val applying = current.copy(phase = TodoFamilyActionPhase.APPLYING)
        setState(applying)
        job?.cancel()
        job = scope.launch {
            try {
                when (current.type) {
                    TodoFamilyActionType.COMPLETION -> {
                        val receipt = repository.applyCompletionValidated(planId, commandId) {
                            beforeCompletionApply(current)
                        }
                        val applied = applying.copy(
                            phase = TodoFamilyActionPhase.APPLIED,
                            changedTaskCount = receipt.changedTaskCount,
                        )
                        setState(applied)
                        onCompletionApplied(receipt)
                        if (!receipt.undoAvailable) runCatching { repository.releaseCommandHandle(commandId) }
                    }
                    TodoFamilyActionType.DELETE -> {
                        repository.applyDelete(planId, commandId)
                        val targetId = requireNotNull(current.targetId)
                        setState(applying.copy(phase = TodoFamilyActionPhase.APPLIED))
                        onDeleteApplied(targetId)
                        runCatching { repository.releaseCommandHandle(commandId) }
                    }
                    null -> Unit
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                fail(applying, failure)
            }
        }
    }

    private fun fail(current: TodoFamilyActionState, failure: Exception) {
        val phase = when (failure) {
            is TodoConflictException -> TodoFamilyActionPhase.CONFLICT
            is TodoMissingException -> TodoFamilyActionPhase.MISSING
            else -> TodoFamilyActionPhase.RETRYABLE_FAILURE
        }
        setState(current.copy(phase = phase))
    }

    private fun reconcileRestoredHandle() {
        val current = mutableState.value
        if (current.phase == TodoFamilyActionPhase.IDLE || current.phase == TodoFamilyActionPhase.APPLIED) return
        if (current.phase == TodoFamilyActionPhase.AWAITING_CONFIRMATION ||
            current.phase == TodoFamilyActionPhase.PREVIEWING
        ) {
            current.planId?.let(::release)
            setState(current.copy(phase = TodoFamilyActionPhase.CONFLICT, planId = null))
            return
        }
        val commandId = current.commandId ?: run {
            current.planId?.let(::release)
            setState(current.copy(phase = TodoFamilyActionPhase.CONFLICT, planId = null))
            return
        }
        scope.launch {
            try {
                when (current.type) {
                    TodoFamilyActionType.COMPLETION -> {
                        val receipt = repository.completionReceipt(commandId)
                        if (receipt == null) {
                            current.planId?.let(::release)
                            setState(current.copy(phase = TodoFamilyActionPhase.CONFLICT, planId = null))
                        } else {
                            setState(current.copy(
                                phase = TodoFamilyActionPhase.APPLIED,
                                changedTaskCount = receipt.changedTaskCount,
                            ))
                            onCompletionApplied(receipt)
                            if (!receipt.undoAvailable) runCatching { repository.releaseCommandHandle(commandId) }
                        }
                    }
                    TodoFamilyActionType.DELETE -> {
                        val targetId = current.targetId ?: return@launch
                        if (repository.get(targetId) == null) {
                            setState(current.copy(phase = TodoFamilyActionPhase.APPLIED))
                            onDeleteApplied(targetId)
                            repository.releaseCommandHandle(commandId)
                        } else {
                            current.planId?.let(::release)
                            setState(current.copy(phase = TodoFamilyActionPhase.CONFLICT, planId = null))
                        }
                    }
                    null -> {
                        current.planId?.let(::release)
                        setState(current.copy(phase = TodoFamilyActionPhase.CONFLICT, planId = null))
                    }
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                setState(current.copy(phase = TodoFamilyActionPhase.RETRYABLE_FAILURE))
            }
        }
    }

    private fun readState(): TodoFamilyActionState {
        val phase = saved.get<String>(key("phase"))?.let {
            runCatching { TodoFamilyActionPhase.valueOf(it) }.getOrNull()
        } ?: return TodoFamilyActionState()
        val type = saved.get<String>(key("type"))?.let {
            runCatching { TodoFamilyActionType.valueOf(it) }.getOrNull()
        }
        return TodoFamilyActionState(
            phase = phase,
            type = type,
            targetId = saved.uuid(key("target")),
            commandId = saved.uuid(key("command")),
            planId = saved.uuid(key("plan")),
            expectedRevision = saved.uuid(key("expected_revision")),
            completed = saved.get<Boolean>(key("completed")),
            changedTaskCount = saved.get<Int>(key("changed_count")) ?: 0,
            openChildCount = saved.get<Int>(key("open_count")) ?: 0,
            childCount = saved.get<Int>(key("child_count")) ?: 0,
            reopensParent = saved.get<Boolean>(key("reopens_parent")) ?: false,
        )
    }

    private fun setState(value: TodoFamilyActionState) {
        save(value)
        mutableState.value = value
    }

    private fun save(value: TodoFamilyActionState) {
        saved[key("phase")] = value.phase.name
        saved[key("type")] = value.type?.name.orEmpty()
        saved[key("target")] = value.targetId?.toString().orEmpty()
        saved[key("command")] = value.commandId?.toString().orEmpty()
        saved[key("plan")] = value.planId?.toString().orEmpty()
        saved[key("expected_revision")] = value.expectedRevision?.toString().orEmpty()
        saved[key("completed")] = value.completed ?: false
        saved[key("changed_count")] = value.changedTaskCount
        saved[key("open_count")] = value.openChildCount
        saved[key("child_count")] = value.childCount
        saved[key("reopens_parent")] = value.reopensParent
    }

    private fun clear() {
        listOf(
            "phase", "type", "target", "command", "plan", "expected_revision", "completed",
            "changed_count", "open_count", "child_count", "reopens_parent",
        ).forEach { saved.remove<Any>(key(it)) }
        mutableState.value = TodoFamilyActionState()
    }

    private fun key(suffix: String) = "${keyPrefix}_$suffix"

    private fun SavedStateHandle.uuid(key: String): UUID? =
        get<String>(key)?.takeIf(String::isNotBlank)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
}

@Composable
fun TodoFamilyActionDialogs(
    state: TodoFamilyActionState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onReviewAgain: () -> Unit,
    onDismiss: () -> Unit,
    unsavedChanges: Boolean = false,
    canDelete: Boolean = true,
    onRequestDeleteUnlock: () -> Unit = {},
) {
    when (state.phase) {
        TodoFamilyActionPhase.AWAITING_CONFIRMATION -> {
            val deleting = state.type == TodoFamilyActionType.DELETE
            val title = when {
                deleting && state.childCount > 0 -> R.string.todo_family_delete_title
                deleting -> R.string.todo_delete_title
                state.reopensParent -> R.string.todo_family_reopen_title
                else -> R.string.todo_family_complete_title
            }
            val body = when {
                deleting && state.childCount > 0 ->
                    stringResource(R.string.todo_family_delete_body, state.targetTitle.orEmpty(), state.childCount)
                deleting -> stringResource(R.string.todo_family_delete_single_body, state.targetTitle.orEmpty())
                state.reopensParent -> stringResource(R.string.todo_family_reopen_body)
                else -> stringResource(R.string.todo_family_complete_body, state.openChildCount)
            } + if (deleting && unsavedChanges) "\n\n" + stringResource(R.string.todo_discard_body) else ""
            LumaAlertDialog(
                onDismissRequest = onCancel,
                modifier = Modifier.testTag("todo_family_action_confirmation"),
                title = { Text(stringResource(title)) },
                text = { Text(body) },
                confirmButton = {
                    if (deleting && canDelete) {
                        LumaDestructiveTextButton(
                            onClick = onConfirm,
                            modifier = Modifier.heightIn(min = LumaUi.MinTouchTargetHeight)
                                .testTag("todo_family_action_confirm"),
                        ) { Text(stringResource(R.string.delete)) }
                    } else if (deleting) {
                        LumaTextButton(
                            onClick = onRequestDeleteUnlock,
                            modifier = Modifier.heightIn(min = LumaUi.MinTouchTargetHeight)
                                .testTag("todo_family_action_unlock_delete"),
                        ) { Text(stringResource(R.string.app_lock_gate_unlock)) }
                    } else {
                        LumaTextButton(onClick = onConfirm, modifier = Modifier.testTag("todo_family_action_confirm")) {
                            Text(stringResource(if (state.completed == true) R.string.todo_mark_complete else R.string.todo_reopen))
                        }
                    }
                },
                dismissButton = {
                    LumaTextButton(onClick = onCancel, modifier = Modifier.testTag("todo_family_action_cancel")) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }
        TodoFamilyActionPhase.RETRYABLE_FAILURE,
        TodoFamilyActionPhase.CONFLICT,
        TodoFamilyActionPhase.MISSING -> {
            val conflict = state.phase == TodoFamilyActionPhase.CONFLICT
            val missing = state.phase == TodoFamilyActionPhase.MISSING
            LumaAlertDialog(
                onDismissRequest = onDismiss,
                modifier = Modifier.testTag("todo_family_action_recovery"),
                title = { Text(stringResource(R.string.todo_family_recovery_title)) },
                text = {
                    Text(
                        stringResource(
                            when {
                                missing -> R.string.todo_family_missing
                                conflict -> R.string.todo_family_conflict
                                else -> R.string.todo_family_failed
                            },
                        ),
                    )
                },
                confirmButton = {
                    if (!missing) {
                        LumaTextButton(
                            onClick = if (conflict) onReviewAgain else onRetry,
                            modifier = Modifier.testTag("todo_family_action_retry"),
                        ) {
                            Text(stringResource(if (conflict) R.string.todo_family_review_again else R.string.todo_retry_action))
                        }
                    }
                },
                dismissButton = {
                    LumaTextButton(onClick = onDismiss, modifier = Modifier.testTag("todo_family_action_dismiss")) {
                        Text(stringResource(R.string.todo_feedback_dismiss))
                    }
                },
            )
        }
        TodoFamilyActionPhase.IDLE,
        TodoFamilyActionPhase.PREVIEWING,
        TodoFamilyActionPhase.APPLYING,
        TodoFamilyActionPhase.APPLIED -> Unit
    }
}
