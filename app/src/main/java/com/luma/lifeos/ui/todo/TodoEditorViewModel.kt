package com.luma.lifeos.ui.todo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luma.lifeos.domain.todo.TodoConflictException
import com.luma.lifeos.domain.todo.TodoField
import com.luma.lifeos.domain.todo.TodoInput
import com.luma.lifeos.domain.todo.TodoMissingException
import com.luma.lifeos.domain.todo.TodoRepository
import com.luma.lifeos.domain.todo.TodoTask
import com.luma.lifeos.domain.todo.TodoValidationException
import com.luma.lifeos.domain.todo.TodoValidationReason
import com.luma.lifeos.domain.todo.normalizeTodoInput
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Draft and revision-safe task mutations for the shared create/edit screen. */
class TodoEditorViewModel(
    private val repository: TodoRepository,
    taskId: UUID?,
    initialDueDate: LocalDate?,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val creating = taskId == null
    private val operationId = taskId ?: saved.uuid(KEY_OPERATION_ID)
        ?: UUID.randomUUID().also { saved[KEY_OPERATION_ID] = it.toString() }
    private val initialRevision = saved.uuid(KEY_INITIAL_REVISION)
        ?: UUID.randomUUID().also { saved[KEY_INITIAL_REVISION] = it.toString() }
    private val initialDue = if (saved.contains(KEY_INITIAL_DUE_DATE)) {
        saved.get<String>(KEY_INITIAL_DUE_DATE).toLocalDateOrNull()
    } else {
        initialDueDate.also { saved[KEY_INITIAL_DUE_DATE] = it?.toString().orEmpty() }
    }

    private var baselineInput: TodoInput? = saved.readInput(KEY_BASELINE)
    private var baselineRevision: UUID? = saved.uuid(KEY_BASELINE_REVISION)
    private var baselineCompleted = saved[KEY_BASELINE_COMPLETED] ?: false
    private var loadJob: Job? = null
    private var mutationJob: Job? = null

    private val mutableState = MutableStateFlow(
        TodoUiState(
            draft = saved.readDraft() ?: TodoDraft(dueDate = initialDue),
            editing = taskId != null,
            taskCompleted = baselineCompleted,
            dirty = saved[KEY_DIRTY] ?: false,
            fieldErrors = saved.readFieldErrors(),
            finished = saved[KEY_FINISHED] ?: false,
            deleted = saved[KEY_DELETED] ?: false,
            pendingAction = readPending()?.type?.label,
            result = saved.readResult(),
        ),
    )
    val state: StateFlow<TodoUiState> = mutableState.asStateFlow()

    init {
        if (baselineInput == null && creating) {
            baselineInput = TodoInput(title = "", note = null, dueDate = initialDue)
            persistBaseline(baselineInput!!, revision = null, completed = false)
        }
        if (!state.value.finished) load()
    }

    fun setTitle(value: String) = change(TodoField.TITLE) { it.copy(title = value) }

    fun setNote(value: String) = change(TodoField.NOTE) { it.copy(note = value) }

    fun setDueDate(value: LocalDate?) = change(TodoField.DUE_DATE) { it.copy(dueDate = value) }

    /** Closes this editor without replaying a draft or mutation after archive replacement. */
    fun invalidateAfterRestore() {
        if (!state.value.finished) {
            loadJob?.cancel()
            mutationJob?.cancel()
            finish()
        }
    }

    fun save() {
        val current = state.value
        if (!current.ready || current.busy || current.finished || current.conflict || current.missing) return
        if (pendingBlocksFields(readPending())) return

        val input = try {
            normalizeTodoInput(current.draft.toInput())
        } catch (failure: TodoValidationException) {
            val errors = mapOf(failure.field to failure.reason)
            persistFieldErrors(errors)
            mutableState.value = current.copy(
                fieldErrors = errors,
                error = null,
            )
            return
        }

        mutableState.value = current.copy(busy = true, error = null, fieldErrors = emptyMap())
        persistFieldErrors(emptyMap())
        mutationJob = viewModelScope.launch {
            try {
                saveInput(input)
            } catch (failure: Exception) {
                handleFailure(TodoEditorError.SAVE, failure)
            }
        }
    }

    fun setCompleted(completed: Boolean) {
        val current = state.value
        if (!current.ready || current.busy || current.finished || current.conflict || current.missing ||
            current.dirty || current.editing.not() || current.taskCompleted == completed || readPending() != null
        ) return
        val expected = baselineRevision ?: return
        val pending = PendingOperation(
            type = if (completed) PendingType.COMPLETE else PendingType.REOPEN,
            expectedRevision = expected,
            nextRevision = UUID.randomUUID(),
            completed = completed,
        )
        executeStatus(pending)
    }

    fun requestDelete() {
        val current = state.value
        if (!current.ready || current.busy || current.finished || current.conflict || current.missing ||
            !current.editing || readPending() != null
        ) return
        mutableState.value = current.copy(deleteConfirmationRequested = true)
    }

    fun cancelDelete() {
        mutableState.value = state.value.copy(deleteConfirmationRequested = false)
    }

    fun confirmDelete() {
        val current = state.value
        if (!current.ready || current.busy || current.finished || current.conflict || current.missing || !current.editing) return
        val pending = readPending()?.takeIf { it.type == PendingType.DELETE } ?: run {
            val expected = baselineRevision ?: return
            PendingOperation(PendingType.DELETE, expectedRevision = expected)
                .also(::persistPending)
        }
        mutableState.value = current.copy(deleteConfirmationRequested = false)
        executeDelete(pending)
    }

    /** Retry a failed mutation with its retained operation token, or retry the initial load. */
    fun retry() {
        val current = state.value
        if (current.busy || current.finished || current.conflict || current.missing) return
        if (!current.ready || current.error == TodoEditorError.LOAD) {
            load()
            return
        }
        when (readPending()?.type) {
            PendingType.COMPLETE, PendingType.REOPEN -> executeStatus(requireNotNull(readPending()))
            PendingType.DELETE -> executeDelete(requireNotNull(readPending()))
            PendingType.CREATE, PendingType.UPDATE -> save()
            null -> when (current.error) {
                TodoEditorError.SAVE -> save()
                TodoEditorError.STATUS -> baselineRevision?.let { setCompleted(!current.taskCompleted) }
                TodoEditorError.DELETE -> requestDelete()
                else -> Unit
            }
        }
    }

    /** Discards the current draft only after the screen has confirmed with the user. */
    fun reload() {
        val current = state.value
        if (current.busy || current.finished) return
        mutableState.value = current.copy(loading = true, ready = false, error = null)
        loadJob = viewModelScope.launch {
            try {
                val task = repository.get(operationId)
                if (task == null) {
                    mutableState.value = state.value.copy(
                        loading = false,
                        ready = true,
                        missing = true,
                        conflict = false,
                        error = null,
                        pendingAction = null,
                    )
                    clearPending()
                    return@launch
                }
                acceptLatest(task)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutableState.value = state.value.copy(loading = false, ready = true, error = TodoEditorError.LOAD)
            }
        }
    }

    private fun load() {
        if (state.value.busy || state.value.finished || loadJob?.isActive == true) return
        mutableState.value = state.value.copy(loading = true, error = null)
        loadJob = viewModelScope.launch {
            try {
                val task = repository.get(operationId)
                val pending = readPending()
                if (creating) {
                    if (task == null) {
                        val baseline = baselineInput ?: TodoInput("", null, initialDue)
                        val draft = saved.readDraft() ?: TodoDraft(baseline.title, baseline.note.orEmpty(), baseline.dueDate)
                        persistDraft(draft, baseline)
                        mutableState.value = state.value.copy(
                            draft = draft,
                            baselineTitle = baseline.title,
                            loading = false,
                            ready = true,
                            editing = false,
                            dirty = equivalent(draft.toInput(), baseline).not(),
                            pendingAction = pending?.type?.label,
                            error = pending?.type?.editorError,
                        )
                    } else if (pending?.type == PendingType.CREATE && task.revision == initialRevision &&
                        pending.input == task.toInput()
                    ) {
                        finish(savedResult = null)
                    } else {
                        val draft = saved.readDraft() ?: TodoDraft(dueDate = initialDue)
                        mutableState.value = state.value.copy(
                            draft = draft,
                            baselineTitle = baselineInput?.title.orEmpty(),
                            loading = false,
                            ready = true,
                            editing = false,
                            dirty = true,
                            conflict = true,
                            pendingAction = pending?.type?.label,
                        )
                    }
                } else if (task == null) {
                    if (pending?.type == PendingType.DELETE) {
                        finish(deleted = true)
                    } else {
                        val draft = saved.readDraft() ?: TodoDraft()
                        mutableState.value = state.value.copy(
                            draft = draft,
                            baselineTitle = (baselineInput ?: task.toInput()).title,
                            loading = false,
                            ready = true,
                            editing = true,
                            dirty = baselineInput?.let { !equivalent(draft.toInput(), it) } ?: true,
                            missing = true,
                            pendingAction = pending?.type?.label,
                        )
                    }
                } else {
                    val committed = pending?.let { committedTask(it, task) }
                    if (committed != null) {
                        if (pending.type == PendingType.COMPLETE || pending.type == PendingType.REOPEN) {
                            finish(resultFor(committed))
                        } else {
                            finish()
                        }
                    } else {
                        val savedRevision = baselineRevision
                        val stale = savedRevision != null && savedRevision != task.revision
                        if (savedRevision == null || !stale) {
                            baselineInput = task.toInput()
                            baselineRevision = task.revision
                            baselineCompleted = task.isCompleted
                            persistBaseline(baselineInput!!, baselineRevision, baselineCompleted)
                        }
                        val draft = saved.readDraft() ?: TodoDraft(task.title, task.note.orEmpty(), task.dueDate)
                        persistDraft(draft, baselineInput ?: task.toInput())
                        val pendingConflicts = pending != null && pending.expectedRevision != task.revision
                        mutableState.value = state.value.copy(
                            draft = draft,
                            loading = false,
                            ready = true,
                            editing = true,
                            taskCompleted = baselineCompleted,
                            dirty = !equivalent(draft.toInput(), baselineInput ?: task.toInput()),
                            conflict = stale || pendingConflicts,
                            missing = false,
                            pendingAction = pending?.type?.label,
                            error = if (stale || pendingConflicts) null else pending?.type?.editorError,
                        )
                    }
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutableState.value = state.value.copy(loading = false, ready = false, error = TodoEditorError.LOAD)
            }
        }
    }

    private suspend fun saveInput(input: TodoInput) {
        var live = repository.get(operationId)
        val pending = readPending()?.takeIf { it.type == PendingType.CREATE || it.type == PendingType.UPDATE }

        if (pending != null) {
            val committed = live?.let { committedTask(pending, it) }
            if (committed != null) {
                persistBaseline(committed.toInput(), committed.revision, committed.isCompleted)
                baselineInput = committed.toInput()
                baselineRevision = committed.revision
                baselineCompleted = committed.isCompleted
                clearPending()
                mutableState.value = state.value.copy(
                    editing = true,
                    baselineTitle = committed.title,
                    taskCompleted = committed.isCompleted,
                    pendingAction = null,
                )
                if (input == committed.toInput()) {
                    finish()
                    return
                }
                live = committed
            } else if (pending.type == PendingType.CREATE) {
                if (live != null) throw TodoConflictException(operationId)
                if (pending.input == input) {
                    executePendingSave(pending)
                    return
                }
                clearPending()
            } else {
                val expected = pending.expectedRevision ?: throw TodoConflictException(operationId)
                if (live == null) throw TodoMissingException(operationId)
                if (live.revision != expected) throw TodoConflictException(operationId)
                if (pending.input == input) {
                    executePendingSave(pending)
                    return
                }
                clearPending()
            }
        }

        if (!state.value.editing && live == null) {
            val create = PendingOperation(
                type = PendingType.CREATE,
                nextRevision = initialRevision,
                input = input,
            )
            executePendingSave(create)
            return
        }

        if (live == null) throw TodoMissingException(operationId)
        val expected = baselineRevision ?: throw TodoConflictException(operationId)
        if (live.revision != expected) throw TodoConflictException(operationId)
        if (input == live.toInput()) {
            finish()
            return
        }
        val update = PendingOperation(
            type = PendingType.UPDATE,
            expectedRevision = expected,
            nextRevision = UUID.randomUUID(),
            input = input,
        )
        executePendingSave(update)
    }

    private suspend fun executePendingSave(pending: PendingOperation) {
        persistPending(pending)
        mutableState.value = state.value.copy(pendingAction = pending.type.label)
        val task = when (pending.type) {
            PendingType.CREATE -> repository.create(operationId, requireNotNull(pending.nextRevision), requireNotNull(pending.input))
            PendingType.UPDATE -> repository.update(
                operationId,
                requireNotNull(pending.expectedRevision),
                requireNotNull(pending.nextRevision),
                requireNotNull(pending.input),
            )
            else -> error("Only create and update operations save task fields")
        }
        persistBaseline(task.toInput(), task.revision, task.isCompleted)
        baselineInput = task.toInput()
        baselineRevision = task.revision
        baselineCompleted = task.isCompleted
        finish()
    }

    private fun executeStatus(pending: PendingOperation) {
        persistPending(pending)
        mutableState.value = state.value.copy(busy = true, error = null, pendingAction = pending.type.label)
        mutationJob = viewModelScope.launch {
            try {
                val task = repository.setCompleted(
                    operationId,
                    requireNotNull(pending.expectedRevision),
                    requireNotNull(pending.nextRevision),
                    requireNotNull(pending.completed),
                )
                persistBaseline(task.toInput(), task.revision, task.isCompleted)
                baselineInput = task.toInput()
                baselineRevision = task.revision
                baselineCompleted = task.isCompleted
                finish(resultFor(task))
            } catch (failure: Exception) {
                handleFailure(TodoEditorError.STATUS, failure)
            }
        }
    }

    private fun executeDelete(pending: PendingOperation) {
        persistPending(pending)
        mutableState.value = state.value.copy(busy = true, error = null, pendingAction = PendingType.DELETE.label)
        mutationJob = viewModelScope.launch {
            try {
                repository.delete(operationId, requireNotNull(pending.expectedRevision))
                finish(deleted = true)
            } catch (failure: Exception) {
                handleFailure(TodoEditorError.DELETE, failure)
            }
        }
    }

    private fun change(field: TodoField, transform: (TodoDraft) -> TodoDraft) {
        val current = state.value
        if (!canEditFields(current, readPending())) return
        val draft = transform(current.draft)
        if (draft == current.draft) return
        persistDraft(draft, baselineInput ?: TodoInput("", null, initialDue))
        val errors = current.fieldErrors - field
        persistFieldErrors(errors)
        mutableState.value = current.copy(
            draft = draft,
            fieldErrors = errors,
            dirty = baselineInput?.let { !equivalent(draft.toInput(), it) } ?: true,
            error = if (errors.isEmpty()) null else current.error,
        )
    }

    private fun handleFailure(error: TodoEditorError, failure: Exception) {
        val current = state.value
        when (failure) {
            is CancellationException -> {
                mutableState.value = current.copy(busy = false)
                throw failure
            }
            is TodoConflictException -> mutableState.value = current.copy(
                busy = false,
                conflict = true,
                error = null,
            )
            is TodoMissingException -> mutableState.value = current.copy(
                busy = false,
                missing = true,
                error = null,
            )
            else -> mutableState.value = current.copy(busy = false, error = error)
        }
    }

    private fun acceptLatest(task: TodoTask) {
        clearPending()
        persistFieldErrors(emptyMap())
        baselineInput = task.toInput()
        baselineRevision = task.revision
        baselineCompleted = task.isCompleted
        persistBaseline(baselineInput!!, baselineRevision, baselineCompleted)
        val draft = TodoDraft(task.title, task.note.orEmpty(), task.dueDate)
        persistDraft(draft, baselineInput!!)
        mutableState.value = state.value.copy(
            draft = draft,
            baselineTitle = task.title,
            loading = false,
            ready = true,
            busy = false,
            editing = true,
            taskCompleted = task.isCompleted,
            dirty = false,
            fieldErrors = emptyMap(),
            error = null,
            conflict = false,
            missing = false,
            deleteConfirmationRequested = false,
            pendingAction = null,
        )
    }

    private fun finish(savedResult: TodoEditorResult? = null, deleted: Boolean = false) {
        clearPending()
        saved[KEY_FINISHED] = true
        saved[KEY_DELETED] = deleted
        if (savedResult == null) {
            saved.remove<String>(KEY_RESULT_TASK_ID)
            saved.remove<String>(KEY_RESULT_REVISION)
            saved.remove<Boolean>(KEY_RESULT_COMPLETED)
        } else {
            saved[KEY_RESULT_TASK_ID] = savedResult.taskId.toString()
            saved[KEY_RESULT_REVISION] = savedResult.resultingRevision.toString()
            saved[KEY_RESULT_COMPLETED] = savedResult.completed
        }
        mutableState.value = state.value.copy(
            loading = false,
            busy = false,
            finished = true,
            deleted = deleted,
            deleteConfirmationRequested = false,
            pendingAction = null,
            result = savedResult,
            error = null,
        )
    }

    private fun resultFor(task: TodoTask) = TodoEditorResult(task.id, task.isCompleted, task.revision)

    private fun committedTask(pending: PendingOperation, task: TodoTask): TodoTask? = when (pending.type) {
        PendingType.CREATE -> task.takeIf {
            task.revision == pending.nextRevision && pending.input == task.toInput()
        }
        PendingType.UPDATE -> task.takeIf {
            task.revision == pending.nextRevision && pending.input == task.toInput()
        }
        PendingType.COMPLETE, PendingType.REOPEN -> task.takeIf {
            task.revision == pending.nextRevision && task.isCompleted == pending.completed
        }
        PendingType.DELETE -> null
    }

    private fun persistBaseline(input: TodoInput, revision: UUID?, completed: Boolean) {
        baselineInput = input
        baselineRevision = revision
        baselineCompleted = completed
        saved.writeInput(KEY_BASELINE, input)
        saved[KEY_BASELINE_SET] = true
        saved[KEY_BASELINE_REVISION] = revision?.toString().orEmpty()
        saved[KEY_BASELINE_COMPLETED] = completed
    }

    private fun persistDraft(draft: TodoDraft, baseline: TodoInput) {
        saved[KEY_HAS_DRAFT] = true
        saved[KEY_TITLE] = draft.title
        saved[KEY_NOTE] = draft.note
        saved[KEY_DUE_DATE] = draft.dueDate?.toString().orEmpty()
        val dirty = !equivalent(draft.toInput(), baseline)
        saved[KEY_DIRTY] = dirty
    }

    private fun persistFieldErrors(errors: Map<TodoField, TodoValidationReason>) {
        saved[KEY_FIELD_ERRORS] = ArrayList(errors.map { (field, reason) -> "${field.name}:${reason.name}" }.sorted())
    }

    private fun persistPending(pending: PendingOperation) {
        saved[KEY_PENDING_TYPE] = pending.type.name
        saved[KEY_PENDING_EXPECTED] = pending.expectedRevision?.toString().orEmpty()
        saved[KEY_PENDING_NEXT] = pending.nextRevision?.toString().orEmpty()
        saved[KEY_PENDING_COMPLETED] = pending.completed ?: false
        saved[KEY_PENDING_HAS_INPUT] = pending.input != null
        pending.input?.let { saved.writeInput(KEY_PENDING_INPUT, it) }
    }

    private fun clearPending() {
        listOf(
            KEY_PENDING_TYPE,
            KEY_PENDING_EXPECTED,
            KEY_PENDING_NEXT,
            KEY_PENDING_COMPLETED,
            KEY_PENDING_HAS_INPUT,
            "$KEY_PENDING_INPUT.title",
            "$KEY_PENDING_INPUT.note",
            "$KEY_PENDING_INPUT.due_date",
        ).forEach { saved.remove<Any>(it) }
    }

    private fun readPending(): PendingOperation? {
        val type = saved.get<String>(KEY_PENDING_TYPE)?.let { runCatching { PendingType.valueOf(it) }.getOrNull() } ?: return null
        val hasInput = saved[KEY_PENDING_HAS_INPUT] ?: false
        return PendingOperation(
            type = type,
            expectedRevision = saved.uuid(KEY_PENDING_EXPECTED),
            nextRevision = saved.uuid(KEY_PENDING_NEXT),
            input = if (hasInput) saved.readInput(KEY_PENDING_INPUT) else null,
            completed = if (type == PendingType.COMPLETE || type == PendingType.REOPEN) saved[KEY_PENDING_COMPLETED] else null,
        )
    }

    private fun canEditFields(current: TodoUiState, pending: PendingOperation?) =
        current.ready && !current.busy && !current.finished && !current.conflict && !current.missing &&
            pending?.type != PendingType.COMPLETE && pending?.type != PendingType.REOPEN && pending?.type != PendingType.DELETE

    private fun pendingBlocksFields(pending: PendingOperation?) =
        pending?.type == PendingType.COMPLETE || pending?.type == PendingType.REOPEN || pending?.type == PendingType.DELETE

    private fun equivalent(first: TodoInput, second: TodoInput): Boolean = comparable(first) == comparable(second)

    private fun comparable(input: TodoInput): TodoInput {
        val title = if (input.title.contains('\n') || input.title.contains('\r')) input.title else input.title.trim()
        val note = input.note?.trim()?.takeUnless(String::isEmpty)
        return TodoInput(title = title, note = note, dueDate = input.dueDate)
    }

    private fun TodoTask.toInput() = TodoInput(title = title, note = note, dueDate = dueDate)

    private fun resultFrom(saved: SavedStateHandle): TodoEditorResult? {
        val id = saved.uuid(KEY_RESULT_TASK_ID) ?: return null
        val revision = saved.uuid(KEY_RESULT_REVISION) ?: return null
        val completed = saved[KEY_RESULT_COMPLETED] ?: return null
        return TodoEditorResult(id, completed, revision)
    }

    private fun SavedStateHandle.uuid(key: String): UUID? = get<String>(key)?.let { value ->
        value.takeIf(String::isNotEmpty)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    }

    private fun SavedStateHandle.readInput(prefix: String): TodoInput? {
        if (prefix == KEY_BASELINE && get<Boolean>(KEY_BASELINE_SET) != true) return null
        return TodoInput(
            title = get<String>("$prefix.title") ?: return null,
            note = get<String>("$prefix.note").orEmpty().ifEmpty { null },
            dueDate = get<String>("$prefix.due_date").toLocalDateOrNull(),
        )
    }

    private fun SavedStateHandle.writeInput(prefix: String, input: TodoInput) {
        this["$prefix.title"] = input.title
        this["$prefix.note"] = input.note.orEmpty()
        this["$prefix.due_date"] = input.dueDate?.toString().orEmpty()
    }

    private fun SavedStateHandle.readDraft(): TodoDraft? {
        if (get<Boolean>(KEY_HAS_DRAFT) != true) return null
        return TodoDraft(
            title = get<String>(KEY_TITLE).orEmpty(),
            note = get<String>(KEY_NOTE).orEmpty(),
            dueDate = get<String>(KEY_DUE_DATE).toLocalDateOrNull(),
        )
    }

    private fun SavedStateHandle.readFieldErrors(): Map<TodoField, TodoValidationReason> =
        get<ArrayList<String>>(KEY_FIELD_ERRORS).orEmpty().mapNotNull { value ->
            val separator = value.indexOf(':')
            if (separator <= 0) return@mapNotNull null
            val field = runCatching { TodoField.valueOf(value.substring(0, separator)) }.getOrNull() ?: return@mapNotNull null
            val reason = runCatching { TodoValidationReason.valueOf(value.substring(separator + 1)) }.getOrNull()
                ?: return@mapNotNull null
            field to reason
        }.toMap()

    private fun SavedStateHandle.readResult(): TodoEditorResult? = resultFrom(this)

    private fun String?.toLocalDateOrNull(): LocalDate? =
        this?.takeIf(String::isNotEmpty)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private data class PendingOperation(
        val type: PendingType,
        val expectedRevision: UUID? = null,
        val nextRevision: UUID? = null,
        val input: TodoInput? = null,
        val completed: Boolean? = null,
    )

    private enum class PendingType(val label: String, val editorError: TodoEditorError?) {
        CREATE("create", TodoEditorError.SAVE),
        UPDATE("update", TodoEditorError.SAVE),
        COMPLETE("complete", TodoEditorError.STATUS),
        REOPEN("reopen", TodoEditorError.STATUS),
        DELETE("delete", TodoEditorError.DELETE),
    }

    private companion object {
        const val KEY_OPERATION_ID = "todo_operation_id"
        const val KEY_INITIAL_REVISION = "todo_initial_revision"
        const val KEY_INITIAL_DUE_DATE = "todo_initial_due_date"
        const val KEY_TITLE = "todo_draft_title"
        const val KEY_NOTE = "todo_draft_note"
        const val KEY_DUE_DATE = "todo_draft_due_date"
        const val KEY_HAS_DRAFT = "todo_has_draft"
        const val KEY_DIRTY = "todo_dirty"
        const val KEY_FIELD_ERRORS = "todo_field_errors"
        const val KEY_BASELINE = "todo_baseline"
        const val KEY_BASELINE_SET = "todo_baseline_set"
        const val KEY_BASELINE_REVISION = "todo_baseline_revision"
        const val KEY_BASELINE_COMPLETED = "todo_baseline_completed"
        const val KEY_PENDING_TYPE = "todo_pending_type"
        const val KEY_PENDING_EXPECTED = "todo_pending_expected"
        const val KEY_PENDING_NEXT = "todo_pending_next"
        const val KEY_PENDING_COMPLETED = "todo_pending_completed"
        const val KEY_PENDING_HAS_INPUT = "todo_pending_has_input"
        const val KEY_PENDING_INPUT = "todo_pending_input"
        const val KEY_FINISHED = "todo_finished"
        const val KEY_DELETED = "todo_deleted"
        const val KEY_RESULT_TASK_ID = "todo_result_task_id"
        const val KEY_RESULT_COMPLETED = "todo_result_completed"
        const val KEY_RESULT_REVISION = "todo_result_revision"
    }
}
