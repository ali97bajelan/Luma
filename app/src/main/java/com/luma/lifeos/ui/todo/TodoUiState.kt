package com.luma.lifeos.ui.todo

import com.luma.lifeos.domain.todo.TodoField
import com.luma.lifeos.domain.todo.TodoInput
import com.luma.lifeos.domain.todo.TodoValidationReason
import java.time.LocalDate
import java.util.UUID

data class TodoDraft(
    val title: String = "",
    val note: String = "",
    val dueDate: LocalDate? = null,
) {
    fun toInput(): TodoInput = TodoInput(title = title, note = note, dueDate = dueDate)
}

enum class TodoEditorError { LOAD, SAVE, STATUS, DELETE }

/** A status write result carried back to the list or Today surface that opened the editor. */
data class TodoActionResult(
    val taskId: UUID,
    val completed: Boolean,
    val resultingRevision: UUID,
)

enum class TodoUndoOutcome { UNDONE, RETRYABLE_FAILURE, STALE }

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
)
