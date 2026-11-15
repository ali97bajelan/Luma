package com.luma.lifeos.ui.todo

import com.luma.lifeos.domain.todo.TodoField
import com.luma.lifeos.domain.todo.TodoInput
import com.luma.lifeos.domain.todo.TodoChildProgress
import com.luma.lifeos.domain.todo.TodoImportance
import com.luma.lifeos.domain.todo.TodoTaskRow
import com.luma.lifeos.domain.todo.TodoValidationReason
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class TodoDraft(
    val title: String = "",
    val note: String = "",
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val importance: TodoImportance = TodoImportance.NONE,
) {
    fun toInput(): TodoInput = TodoInput(
        title = title,
        note = note,
        dueDate = dueDate,
        dueTime = dueTime,
        importance = importance,
    )
}

enum class TodoEditorError { LOAD, SAVE, STATUS, DELETE }

/** A status write result carried back to the list or Today surface that opened the editor. */
data class TodoActionResult(
    val taskId: UUID,
    val completed: Boolean,
    val resultingRevision: UUID?,
    val completionCommandId: UUID? = null,
)

enum class TodoUndoOutcome { UNDONE, RETRYABLE_FAILURE, STALE }

enum class TodoChildActionError { MOVE, DELETE, STALE }

enum class TodoMoveIssue { STALE, FAILED }

data class TodoMoveUiState(
    val open: Boolean = false,
    val targets: List<TodoTaskRow> = emptyList(),
    val targetsPageLimit: Int = 50,
    val hasMoreTargets: Boolean = false,
    val loadingTargets: Boolean = false,
    val targetLoadFailed: Boolean = false,
    val selectedTargetId: UUID? = null,
    val moveToRootSelected: Boolean = false,
    val applying: Boolean = false,
    val hasPendingOperation: Boolean = false,
    val issue: TodoMoveIssue? = null,
)

typealias TodoEditorResult = TodoActionResult

data class TodoUiState(
    val draft: TodoDraft = TodoDraft(),
    val baselineTitle: String = "",
    val loading: Boolean = true,
    val ready: Boolean = false,
    val busy: Boolean = false,
    val editing: Boolean = false,
    val taskCompleted: Boolean = false,
    val dirty: Boolean = false,
    val fieldErrors: Map<TodoField, TodoValidationReason> = emptyMap(),
    val error: TodoEditorError? = null,
    val conflict: Boolean = false,
    val missing: Boolean = false,
    val finished: Boolean = false,
    val deleted: Boolean = false,
    val deleteConfirmationRequested: Boolean = false,
    val pendingAction: String? = null,
    val result: TodoEditorResult? = null,
    val taskId: UUID? = null,
    val parentId: UUID? = null,
    val familyRevision: UUID? = null,
    val parentTitle: String? = null,
    val parentContextLoading: Boolean = false,
    val parentContextError: Boolean = false,
    val parentMissing: Boolean = false,
    val parentCompleted: Boolean = false,
    val children: List<TodoTaskRow> = emptyList(),
    val childrenPageLimit: Int = 50,
    val childTotal: Int = 0,
    val childProgress: TodoChildProgress? = null,
    val childrenLoading: Boolean = false,
    val childrenError: Boolean = false,
    val childDeleteConfirmationRequested: Boolean = false,
    val childDeleteTargetId: UUID? = null,
    val childContinuationTaskId: UUID? = null,
    val childActionPendingId: UUID? = null,
    val childActionError: TodoChildActionError? = null,
    val familyAction: TodoFamilyActionState = TodoFamilyActionState(),
    val move: TodoMoveUiState = TodoMoveUiState(),
) {
    /** A failed read after conflict resolution was approved can be retried without another prompt. */
    val canRetryConfirmedReload: Boolean
        get() = conflict && error == TodoEditorError.LOAD
}
