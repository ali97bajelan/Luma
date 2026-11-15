package com.luma.lifeos.ui.todo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luma.lifeos.domain.todo.TodoConflictException
import com.luma.lifeos.domain.todo.TodoField
import com.luma.lifeos.domain.todo.TodoInput
import com.luma.lifeos.domain.todo.TodoImportance
import com.luma.lifeos.domain.todo.TODO_DEFAULT_PAGE_SIZE
import com.luma.lifeos.domain.todo.TodoMissingException
import com.luma.lifeos.domain.todo.TodoMoveDirection
import com.luma.lifeos.domain.todo.TodoMoveRejectedException
import com.luma.lifeos.domain.todo.TodoListQuery
import com.luma.lifeos.domain.todo.TodoRepository
import com.luma.lifeos.domain.todo.TodoTask
import com.luma.lifeos.domain.todo.TodoValidationException
import com.luma.lifeos.domain.todo.TodoValidationReason
import com.luma.lifeos.domain.todo.normalizeTodoInput
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Draft and revision-safe task mutations for the shared create/edit screen. */
class TodoEditorViewModel(
    private val repository: TodoRepository,
    taskId: UUID?,
    initialDueDate: LocalDate?,
    requestedParentId: UUID?,
    private val saved: SavedStateHandle,
) : ViewModel() {
    /** Retained source-compatible constructor for callers creating or editing root tasks. */
    constructor(
        repository: TodoRepository,
        taskId: UUID?,
        initialDueDate: LocalDate?,
        saved: SavedStateHandle,
    ) : this(repository, taskId, initialDueDate, requestedParentId = null, saved = saved)

    private val creating = taskId == null
    private val creationParentId = if (creating) {
        saved.uuid(KEY_PARENT_ID) ?: requestedParentId?.also { saved[KEY_PARENT_ID] = it.toString() }
    } else {
        null
    }
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
    private var childListJob: Job? = null
    private var parentContextJob: Job? = null
    private var childActionJob: Job? = null
    private var moveTargetsJob: Job? = null
    private var moveRefreshJob: Job? = null

    private val initialChildPageLimit = saved.get<Int>(KEY_CHILD_PAGE_LIMIT)
        ?.takeIf { it >= TODO_DEFAULT_PAGE_SIZE } ?: TODO_DEFAULT_PAGE_SIZE
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
            taskId = operationId,
            parentId = creationParentId,
            parentContextLoading = creationParentId != null,
            childrenPageLimit = initialChildPageLimit,
            childDeleteConfirmationRequested = saved[KEY_CHILD_DELETE_REQUESTED] ?: false,
            childDeleteTargetId = saved.uuid(KEY_CHILD_DELETE_ID),
            childContinuationTaskId = saved.uuid(KEY_CHILD_CONTINUATION),
        ),
    )
    val state: StateFlow<TodoUiState> = mutableState.asStateFlow()
    private val familyActions = TodoFamilyActions(
        repository = repository,
        saved = saved,
        scope = viewModelScope,
        keyPrefix = "todo_editor_family",
        onCompletionApplied = { receipt ->
            clearPending()
            finish(TodoEditorResult(receipt.targetId, receipt.completed, null, receipt.commandId))
        },
        onDeleteApplied = ::onFamilyDeleteApplied,
    )

    init {
        viewModelScope.launch {
            familyActions.state.collect { action ->
                mutableState.value = state.value.copy(
                    familyAction = action,
                    busy = if (action.type == null) state.value.busy else action.busy,
                    pendingAction = when {
                        action.type == null -> state.value.pendingAction
                        action.busy && action.type == TodoFamilyActionType.DELETE -> "delete"
                        action.busy && action.completed == true -> "complete"
                        action.busy && action.completed == false -> "reopen"
                        else -> null
                    },
                    childDeleteConfirmationRequested = action.phase == TodoFamilyActionPhase.AWAITING_CONFIRMATION &&
                        action.type == TodoFamilyActionType.DELETE && action.targetId == state.value.childDeleteTargetId,
                    childActionPendingId = if (action.type == TodoFamilyActionType.DELETE &&
                        action.targetId == state.value.childDeleteTargetId && action.busy
                    ) action.targetId else state.value.childActionPendingId,
                )
            }
        }
        if (baselineInput == null && creating) {
            baselineInput = TodoInput(title = "", note = null, dueDate = initialDue)
            persistBaseline(baselineInput!!, revision = null, completed = false)
        }
        if (!state.value.finished) load()
    }

    fun setTitle(value: String) = change(TodoField.TITLE) { it.copy(title = value) }

    fun setNote(value: String) = change(TodoField.NOTE) { it.copy(note = value) }

    fun setDueDate(value: LocalDate?) = change(TodoField.DUE_DATE) {
        it.copy(dueDate = value, dueTime = if (value == null) null else it.dueTime)
    }

    fun setDueTime(value: LocalTime?) {
        if (value != null && state.value.draft.dueDate == null) return
        change(TodoField.DUE_TIME) { it.copy(dueTime = value) }
    }

    fun setImportance(value: TodoImportance) = change(field = null) { it.copy(importance = value) }

    fun saveAndContinueToSubtask() {
        val current = state.value
        if (!current.ready || current.busy || current.finished || current.conflict || current.missing ||
            current.parentId != null
        ) return
        saved[KEY_CONTINUE_TO_SUBTASK] = true
        save()
    }

    fun consumeSubtaskContinuation(): UUID? {
        val id = saved.uuid(KEY_CHILD_CONTINUATION) ?: return null
        saved.remove<String>(KEY_CHILD_CONTINUATION)
        mutableState.value = state.value.copy(childContinuationTaskId = null)
        return id
    }

    fun loadMoreChildren() {
        val current = state.value
        val rootId = current.taskId ?: return
        if (!current.ready || !current.editing || current.parentId != null || current.childrenLoading ||
            current.children.size >= current.childTotal
        ) return
        val nextLimit = current.childrenPageLimit + TODO_DEFAULT_PAGE_SIZE
        saved[KEY_CHILD_PAGE_LIMIT] = nextLimit
        mutableState.value = current.copy(
            childrenPageLimit = nextLimit,
            childrenLoading = true,
            childrenError = false,
        )
        observeChildren(rootId, nextLimit)
    }

    fun retryChildren() {
        val current = state.value
        val rootId = current.taskId ?: return
        if (!current.editing || current.parentId != null || current.childrenLoading) return
        observeChildren(rootId, current.childrenPageLimit)
    }

    fun moveChild(childId: UUID, revision: UUID, direction: TodoMoveDirection) {
        val current = state.value
        if (!current.ready || !current.editing || current.parentId != null || current.busy ||
            current.childActionPendingId != null
        ) return
        mutableState.value = current.copy(childActionPendingId = childId, childActionError = null)
        childActionJob = viewModelScope.launch {
            try {
                repository.moveChild(childId, revision, UUID.randomUUID(), direction)
                mutableState.value = state.value.copy(childActionPendingId = null)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutableState.value = state.value.copy(
                    childActionPendingId = null,
                    childActionError = if (failure is TodoConflictException || failure is TodoMissingException) {
                        TodoChildActionError.STALE
                    } else {
                        TodoChildActionError.MOVE
                    },
                )
            }
        }
    }

    fun openMovePicker() {
        val current = state.value
        if (!canMove(current)) return
        val pending = readPendingMove()
        mutableState.value = current.copy(
            move = TodoMoveUiState(
                open = true,
                selectedTargetId = pending?.targetParentId,
                moveToRootSelected = pending?.targetParentId == null && pending != null,
                hasPendingOperation = pending != null,
                issue = if (pending != null) TodoMoveIssue.FAILED else null,
            ),
        )
        loadMoveTargets(TODO_DEFAULT_PAGE_SIZE)
    }

    fun selectMoveTarget(targetId: UUID) {
        val current = state.value
        if (!current.move.open || current.move.applying || current.move.hasPendingOperation ||
            current.move.targets.none { it.task.id == targetId }
        ) return
        mutableState.value = current.copy(
            move = current.move.copy(
                selectedTargetId = targetId,
                moveToRootSelected = false,
                issue = null,
            ),
        )
    }

    fun selectMoveToRoot() {
        val current = state.value
        if (!current.move.open || current.parentId == null || current.move.applying ||
            current.move.hasPendingOperation
        ) return
        mutableState.value = current.copy(
            move = current.move.copy(
                selectedTargetId = null,
                moveToRootSelected = true,
                issue = null,
            ),
        )
    }

    fun loadMoreMoveTargets() {
        val move = state.value.move
        if (!move.open || move.applying || move.hasPendingOperation || move.loadingTargets || !move.hasMoreTargets) return
        loadMoveTargets(move.targetsPageLimit + TODO_DEFAULT_PAGE_SIZE)
    }

    fun retryMoveTargets() {
        val move = state.value.move
        if (!move.open || move.applying || move.hasPendingOperation || move.loadingTargets) return
        refreshMoveTargets()
    }

    fun cancelMovePicker() {
        val current = state.value
        if (current.move.applying) return
        moveTargetsJob?.cancel()
        moveRefreshJob?.cancel()
        mutableState.value = current.copy(
            move = if (current.move.hasPendingOperation) {
                current.move.copy(open = false, targets = emptyList(), loadingTargets = false)
            } else {
                TodoMoveUiState()
            },
        )
    }

    fun confirmMove() {
        val current = state.value
        val move = current.move
        if (!move.open || move.applying || !current.ready || current.busy || current.conflict || current.missing) return
        val retained = readPendingMove()
        if (retained != null) {
            runPendingMove(retained)
            return
        }
        val targetParentId = when {
            move.moveToRootSelected && current.parentId != null -> null
            move.selectedTargetId != null -> move.selectedTargetId
            else -> return
        }
        val targetRevision = targetParentId?.let { id ->
            move.targets.firstOrNull { it.task.id == id }?.task?.familyRevision ?: return
        }
        val expected = baselineRevision ?: return
        val sourceFamily = current.familyRevision ?: return
        val pending = PendingMove(
            expectedRevision = expected,
            nextRevision = UUID.randomUUID(),
            expectedSourceFamilyRevision = sourceFamily,
            targetParentId = targetParentId,
            expectedTargetFamilyRevision = targetRevision,
        )
        persistPendingMove(pending)
        runPendingMove(pending)
    }

    fun retryMove() {
        val current = state.value
        val pending = readPendingMove() ?: return
        if (!current.move.open || current.move.applying || current.busy) return
        runPendingMove(pending)
    }

    private fun canMove(current: TodoUiState): Boolean =
        current.ready && current.editing && !current.busy && !current.finished && !current.conflict &&
            !current.missing && current.pendingAction == null &&
            (current.parentId != null || (!current.childrenLoading && !current.childrenError && current.childTotal == 0))

    private fun loadMoveTargets(limit: Int) {
        val current = state.value
        if (!current.move.open || current.move.applying || current.taskId == null) return
        moveTargetsJob?.cancel()
        mutableState.value = current.copy(
            move = current.move.copy(
                targetsPageLimit = limit,
                loadingTargets = true,
                targetLoadFailed = false,
            ),
        )
        moveTargetsJob = viewModelScope.launch {
            try {
                val openPage = repository.observeRows(TodoListQuery(completed = false), limit).first()
                val completedPage = if (current.taskCompleted) {
                    repository.observeRows(TodoListQuery(completed = true), limit).first()
                } else {
                    null
                }
                val latest = state.value
                val excluded = setOfNotNull(latest.taskId, latest.parentId)
                val targets = (openPage.items + completedPage?.items.orEmpty())
                    .asSequence()
                    .filter { row -> row.task.parentId == null && row.task.id !in excluded }
                    .filter { row -> latest.taskCompleted || !row.task.isCompleted }
                    .distinctBy { row -> row.task.id }
                    .toList()
                mutableState.value = latest.copy(
                    move = latest.move.copy(
                        targets = targets,
                        targetsPageLimit = limit,
                        hasMoreTargets = openPage.items.size < openPage.total ||
                            (completedPage?.let { it.items.size < it.total } == true),
                        loadingTargets = false,
                        targetLoadFailed = false,
                        selectedTargetId = latest.move.selectedTargetId?.takeIf { id -> targets.any { it.task.id == id } },
                    ),
                )
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutableState.value = state.value.copy(
                    move = state.value.move.copy(loadingTargets = false, targetLoadFailed = true),
                )
            }
        }
    }

    private fun refreshMoveTargets() {
        val current = state.value
        if (!current.move.open || current.move.applying) return
        clearPendingMove()
        moveRefreshJob?.cancel()
        mutableState.value = current.copy(
            move = current.move.copy(
                targets = emptyList(),
                selectedTargetId = null,
                moveToRootSelected = false,
                hasPendingOperation = false,
                loadingTargets = true,
                targetLoadFailed = false,
            ),
        )
        moveRefreshJob = viewModelScope.launch {
            try {
                val task = repository.get(operationId)
                if (task == null) {
                    mutableState.value = state.value.copy(
                        busy = false,
                        missing = true,
                        move = state.value.move.copy(open = false, loadingTargets = false, issue = TodoMoveIssue.STALE),
                    )
                    return@launch
                }
                val original = baselineInput
                if (original != null && (!equivalent(task.toInput(), original) || task.isCompleted != baselineCompleted)) {
                    mutableState.value = state.value.copy(
                        busy = false,
                        conflict = true,
                        move = state.value.move.copy(open = false, loadingTargets = false, issue = TodoMoveIssue.STALE),
                    )
                    return@launch
                }
                val parent = task.parentId?.let { repository.get(it) }
                val childPage = if (task.parentId == null) {
                    repository.observeChildren(task.id, current.childrenPageLimit).first()
                } else {
                    null
                }
                val baseline = original ?: task.toInput()
                baselineRevision = task.revision
                baselineCompleted = task.isCompleted
                persistBaseline(baseline, task.revision, task.isCompleted)
                persistDraft(current.draft, baseline)
                saved[KEY_PARENT_ID] = task.parentId?.toString().orEmpty()
                val stillMovable = task.parentId != null || childPage?.total == 0
                mutableState.value = state.value.copy(
                    busy = false,
                    pendingAction = null,
                    taskCompleted = task.isCompleted,
                    dirty = !equivalent(current.draft.toInput(), baseline),
                    conflict = false,
                    missing = false,
                    taskId = task.id,
                    parentId = task.parentId,
                    familyRevision = task.familyRevision,
                    parentTitle = parent?.title,
                    parentContextLoading = false,
                    parentContextError = false,
                    parentMissing = task.parentId != null && parent == null,
                    parentCompleted = parent?.isCompleted == true,
                    children = childPage?.items.orEmpty(),
                    childTotal = childPage?.total ?: 0,
                    childProgress = childPage?.familyProgress,
                    childrenLoading = false,
                    childrenError = false,
                    move = state.value.move.copy(
                        open = stillMovable,
                        loadingTargets = false,
                        issue = TodoMoveIssue.STALE,
                    ),
                )
                if (!stillMovable) return@launch
                loadMoveTargets(state.value.move.targetsPageLimit)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutableState.value = state.value.copy(
                    busy = false,
                    move = state.value.move.copy(loadingTargets = false, targetLoadFailed = true),
                )
            }
        }
    }

    private fun runPendingMove(pending: PendingMove) {
        val current = state.value
        if (current.busy || current.move.applying) return
        mutableState.value = current.copy(
            busy = true,
            pendingAction = "move",
            move = current.move.copy(applying = true, hasPendingOperation = true, issue = null),
        )
        mutationJob = viewModelScope.launch {
            try {
                val task = repository.moveTask(
                    id = operationId,
                    expectedRevision = pending.expectedRevision,
                    nextRevision = pending.nextRevision,
                    expectedSourceFamilyRevision = pending.expectedSourceFamilyRevision,
                    targetParentId = pending.targetParentId,
                    expectedTargetFamilyRevision = pending.expectedTargetFamilyRevision,
                )
                completeSuccessfulMove(task)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                if (failure is TodoConflictException || failure is TodoMissingException ||
                    failure is TodoMoveRejectedException
                ) {
                    clearPendingMove()
                    mutableState.value = state.value.copy(
                        busy = false,
                        pendingAction = null,
                        move = state.value.move.copy(applying = false, hasPendingOperation = false, issue = TodoMoveIssue.STALE),
                    )
                    refreshMoveTargets()
                } else {
                    mutableState.value = state.value.copy(
                        busy = false,
                        pendingAction = null,
                        move = state.value.move.copy(applying = false, hasPendingOperation = true, issue = TodoMoveIssue.FAILED),
                    )
                }
            }
        }
    }

    private suspend fun completeSuccessfulMove(task: TodoTask) {
        moveTargetsJob?.cancel()
        moveRefreshJob?.cancel()
        val current = state.value
        val parent = task.parentId?.let { repository.get(it) }
        val children = if (task.parentId == null) {
            repository.observeChildren(task.id, current.childrenPageLimit).first()
        } else {
            null
        }
        val baseline = baselineInput ?: task.toInput()
        clearPendingMove()
        persistBaseline(baseline, task.revision, task.isCompleted)
        persistDraft(current.draft, baseline)
        saved[KEY_PARENT_ID] = task.parentId?.toString().orEmpty()
        childListJob?.cancel()
        parentContextJob?.cancel()
        mutableState.value = current.copy(
            loading = false,
            ready = true,
            busy = false,
            editing = true,
            taskCompleted = task.isCompleted,
            dirty = !equivalent(current.draft.toInput(), baseline),
            error = null,
            conflict = false,
            missing = false,
            pendingAction = null,
            taskId = task.id,
            parentId = task.parentId,
            familyRevision = task.familyRevision,
            parentTitle = parent?.title,
            parentContextLoading = false,
            parentContextError = false,
            parentMissing = task.parentId != null && parent == null,
            parentCompleted = parent?.isCompleted == true,
            children = children?.items.orEmpty(),
            childTotal = children?.total ?: 0,
            childProgress = children?.familyProgress,
            childrenLoading = false,
            childrenError = false,
            childActionError = null,
            move = TodoMoveUiState(targetsPageLimit = current.move.targetsPageLimit),
        )
        if (task.parentId == null) observeChildren(task.id, current.childrenPageLimit)
    }

    fun requestDeleteChild(childId: UUID, revision: UUID) {
        val current = state.value
        if (!current.ready || !current.editing || current.parentId != null || current.busy ||
            current.childActionPendingId != null
        ) return
        saved[KEY_CHILD_DELETE_ID] = childId.toString()
        saved[KEY_CHILD_DELETE_REVISION] = revision.toString()
        saved[KEY_CHILD_DELETE_REQUESTED] = true
        mutableState.value = current.copy(
            childDeleteConfirmationRequested = false,
            childDeleteTargetId = childId,
            childActionError = null,
        )
        childActionJob = viewModelScope.launch {
            try {
                val latest = repository.get(childId)
                if (latest == null || latest.revision != revision) {
                    clearChildDeleteTarget()
                    mutableState.value = state.value.copy(
                        childDeleteTargetId = null,
                        childActionError = TodoChildActionError.STALE,
                    )
                    return@launch
                }
                familyActions.delete(childId, latest.title, revision)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutableState.value = state.value.copy(
                    childActionError = if (failure is TodoConflictException || failure is TodoMissingException) {
                        TodoChildActionError.STALE
                    } else TodoChildActionError.DELETE,
                )
            }
        }
    }

    fun cancelDeleteChild() {
        familyActions.cancel()
        clearChildDeleteTarget()
        mutableState.value = state.value.copy(
            childDeleteConfirmationRequested = false,
            childDeleteTargetId = null,
            childActionPendingId = null,
        )
    }

    fun confirmDeleteChild() {
        if (!state.value.childDeleteConfirmationRequested || state.value.busy) return
        familyActions.confirm()
    }

    fun retryChildDelete() {
        if (state.value.familyAction.phase == TodoFamilyActionPhase.RETRYABLE_FAILURE) familyActions.retry()
    }

    /** Closes this editor without replaying a draft or mutation after archive replacement. */
    fun invalidateAfterRestore() {
        if (!state.value.finished) {
            familyActions.invalidate()
            loadJob?.cancel()
            mutationJob?.cancel()
            childListJob?.cancel()
            parentContextJob?.cancel()
            childActionJob?.cancel()
            finish()
        }
    }

    fun save() {
        val current = state.value
        if (!current.ready || current.busy || current.finished || current.conflict || current.missing ||
            readPendingMove() != null
        ) return
        if (creating && creationParentId != null &&
            (current.parentContextLoading || current.parentContextError || current.parentMissing || current.parentCompleted)
        ) return
        if (pendingBlocksFields(readPending())) return

        val input = try {
            normalizeTodoInput(current.draft.toInput())
        } catch (failure: TodoValidationException) {
            saved.remove<Boolean>(KEY_CONTINUE_TO_SUBTASK)
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
            current.dirty || current.editing.not() || current.taskCompleted == completed || readPending() != null ||
            readPendingMove() != null
        ) return
        familyActions.complete(operationId, completed, current.draft.title, baselineRevision)
    }

    fun requestDelete() {
        val current = state.value
        if (!current.ready || current.busy || current.finished || current.conflict || current.missing ||
            !current.editing || readPending() != null || readPendingMove() != null
        ) return
        familyActions.delete(operationId, current.draft.title, baselineRevision)
    }

    fun cancelDelete() {
        familyActions.cancel()
        if (state.value.childDeleteTargetId != null) cancelDeleteChild()
    }

    fun confirmDelete() {
        val current = state.value
        if (!current.ready || current.busy || current.finished || current.conflict || current.missing || !current.editing) return
        familyActions.confirm()
    }

    fun confirmFamilyAction() = familyActions.confirm()
    fun cancelFamilyAction() {
        familyActions.cancel()
        if (state.value.childDeleteTargetId != null) cancelDeleteChild()
    }
    fun retryFamilyAction() = familyActions.retry()
    fun reviewFamilyAction() = familyActions.reviewAgain()
    fun dismissFamilyAction() {
        familyActions.dismiss()
        if (state.value.childDeleteTargetId != null) {
            clearChildDeleteTarget()
            mutableState.value = state.value.copy(
                childDeleteConfirmationRequested = false,
                childDeleteTargetId = null,
                childActionPendingId = null,
            )
        }
    }

    /** Retry an approved conflict reload, a mutation with its retained token, or the initial load. */
    fun retry() {
        val current = state.value
        if (readPendingMove() != null) {
            retryMove()
            return
        }
        if (current.familyAction.phase == TodoFamilyActionPhase.RETRYABLE_FAILURE) {
            familyActions.retry()
            return
        }
        if (current.busy || current.finished || current.missing) return
        if (current.canRetryConfirmedReload) {
            reload()
            return
        }
        if (current.conflict) return
        if (!current.ready || current.error == TodoEditorError.LOAD) {
            load()
            return
        }
        when (readPending()?.type) {
            PendingType.COMPLETE, PendingType.REOPEN -> {
                val pending = requireNotNull(readPending())
                clearPending()
                familyActions.complete(
                    operationId,
                    requireNotNull(pending.completed),
                    current.draft.title,
                    pending.expectedRevision,
                )
            }
            PendingType.DELETE -> {
                clearPending()
                familyActions.delete(operationId, current.draft.title, baselineRevision)
            }
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
                        val draft = saved.readDraft() ?: baseline.toDraft()
                        val parent = creationParentId?.let { repository.get(it) }
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
                            taskId = operationId,
                            parentId = creationParentId,
                            parentTitle = parent?.title,
                            parentContextLoading = false,
                            parentContextError = false,
                            parentMissing = creationParentId != null && parent == null,
                            parentCompleted = parent?.isCompleted == true,
                            childContinuationTaskId = saved.uuid(KEY_CHILD_CONTINUATION),
                        )
                    } else if (pending?.type == PendingType.CREATE && task.revision == initialRevision &&
                        task.parentId == creationParentId && pending.input == task.toInput()
                    ) {
                        if (shouldContinueToSubtask()) completeSuccessfulSave(task) else finish(savedResult = null)
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
                            taskId = operationId,
                            parentId = creationParentId,
                        )
                    }
                } else if (task == null) {
                    if (pending?.type == PendingType.DELETE) {
                        finish(deleted = true)
                    } else {
                        clearPendingMove()
                        val draft = saved.readDraft() ?: baselineInput?.toDraft() ?: TodoDraft()
                        val retainedParentId = saved.uuid(KEY_PARENT_ID)
                        mutableState.value = state.value.copy(
                            draft = draft,
                            baselineTitle = baselineInput?.title.orEmpty(),
                            loading = false,
                            ready = true,
                            editing = true,
                            dirty = baselineInput?.let { !equivalent(draft.toInput(), it) } ?: true,
                            missing = true,
                            pendingAction = pending?.type?.label,
                            taskId = operationId,
                            parentId = retainedParentId,
                            parentMissing = retainedParentId != null,
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
                        val pendingMove = readPendingMove()
                        val moveCommitted = pendingMove?.let {
                            task.revision == it.nextRevision && task.parentId == it.targetParentId
                        } == true
                        val stale = savedRevision != null && savedRevision != task.revision && !moveCommitted
                        if (savedRevision == null || !stale) {
                            baselineInput = task.toInput()
                            baselineRevision = task.revision
                            baselineCompleted = task.isCompleted
                            persistBaseline(baselineInput!!, baselineRevision, baselineCompleted)
                        }
                        val draft = saved.readDraft() ?: task.toDraft()
                        persistDraft(draft, baselineInput ?: task.toInput())
                        val pendingConflicts = pending != null && pending.expectedRevision != task.revision
                        val taskParentId = task.parentId
                        saved[KEY_PARENT_ID] = taskParentId?.toString().orEmpty()
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
                            taskId = task.id,
                            parentId = taskParentId,
                            familyRevision = task.familyRevision,
                            parentContextLoading = taskParentId != null,
                            childContinuationTaskId = saved.uuid(KEY_CHILD_CONTINUATION),
                        )
                        if (taskParentId == null) observeChildren(task.id, state.value.childrenPageLimit)
                        else loadParentContext(taskParentId)
                        when {
                            moveCommitted -> {
                                clearPendingMove()
                                mutableState.value = state.value.copy(move = TodoMoveUiState())
                            }
                            pendingMove != null && stale -> {
                                clearPendingMove()
                                mutableState.value = state.value.copy(
                                    move = TodoMoveUiState(issue = TodoMoveIssue.STALE),
                                )
                            }
                            pendingMove != null -> {
                                mutableState.value = state.value.copy(
                                    move = TodoMoveUiState(
                                        open = true,
                                        selectedTargetId = pendingMove.targetParentId,
                                        moveToRootSelected = pendingMove.targetParentId == null,
                                        hasPendingOperation = true,
                                        issue = TodoMoveIssue.FAILED,
                                    ),
                                )
                                loadMoveTargets(TODO_DEFAULT_PAGE_SIZE)
                            }
                        }
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
                    completeSuccessfulSave(committed)
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
            completeSuccessfulSave(live)
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
            PendingType.CREATE -> {
                val revision = requireNotNull(pending.nextRevision)
                val input = requireNotNull(pending.input)
                if (creationParentId == null) repository.create(operationId, revision, input)
                else repository.createChild(operationId, revision, creationParentId, input)
            }
            PendingType.UPDATE -> repository.update(
                operationId,
                requireNotNull(pending.expectedRevision),
                requireNotNull(pending.nextRevision),
                requireNotNull(pending.input),
            )
            else -> error("Only create and update operations save task fields")
        }
        completeSuccessfulSave(task)
    }

    private fun completeSuccessfulSave(task: TodoTask) {
        persistBaseline(task.toInput(), task.revision, task.isCompleted)
        baselineInput = task.toInput()
        baselineRevision = task.revision
        baselineCompleted = task.isCompleted
        if (shouldContinueToSubtask()) {
            saved.remove<Boolean>(KEY_CONTINUE_TO_SUBTASK)
            saved[KEY_CHILD_CONTINUATION] = task.id.toString()
            acceptLatest(task)
        } else {
            finish()
        }
    }

    private fun shouldContinueToSubtask() = saved.get<Boolean>(KEY_CONTINUE_TO_SUBTASK) == true

    private fun change(field: TodoField?, transform: (TodoDraft) -> TodoDraft) {
        val current = state.value
        if (!canEditFields(current, readPending())) return
        val draft = transform(current.draft)
        if (draft == current.draft) return
        persistDraft(draft, baselineInput ?: TodoInput("", null, initialDue))
        val errors = if (field == null) current.fieldErrors else current.fieldErrors - field
        persistFieldErrors(errors)
        mutableState.value = current.copy(
            draft = draft,
            fieldErrors = errors,
            dirty = baselineInput?.let { !equivalent(draft.toInput(), it) } ?: true,
            error = if (errors.isEmpty()) null else current.error,
        )
    }

    private fun observeChildren(rootId: UUID, limit: Int = state.value.childrenPageLimit) {
        childListJob?.cancel()
        mutableState.value = state.value.copy(childrenLoading = true, childrenError = false)
        childListJob = viewModelScope.launch {
            try {
                repository.observeChildren(rootId, limit).collect { page ->
                    val current = state.value
                    if (current.taskId == rootId && current.parentId == null) {
                        mutableState.value = current.copy(
                            children = page.items,
                            childTotal = page.total,
                            childProgress = page.familyProgress,
                            childrenLoading = false,
                            childrenError = false,
                        )
                    }
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutableState.value = state.value.copy(childrenLoading = false, childrenError = true)
            }
        }
    }

    private fun loadParentContext(parentId: UUID) {
        parentContextJob?.cancel()
        mutableState.value = state.value.copy(
            parentId = parentId,
            parentTitle = null,
            parentContextLoading = true,
            parentContextError = false,
            parentMissing = false,
            parentCompleted = false,
        )
        parentContextJob = viewModelScope.launch {
            try {
                val parent = repository.get(parentId)
                val current = state.value
                if (current.parentId == parentId) {
                    mutableState.value = current.copy(
                        parentTitle = parent?.title,
                        parentContextLoading = false,
                        parentContextError = false,
                        parentMissing = parent == null,
                        parentCompleted = parent?.isCompleted == true,
                    )
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                val current = state.value
                if (current.parentId == parentId) {
                    mutableState.value = current.copy(
                        parentContextLoading = false,
                        parentContextError = true,
                        error = TodoEditorError.LOAD,
                    )
                }
            }
        }
    }

    private fun onFamilyDeleteApplied(targetId: UUID) {
        if (targetId == operationId) {
            clearPending()
            finish(deleted = true)
            return
        }
        if (state.value.childDeleteTargetId == targetId) {
            clearChildDeleteTarget()
            mutableState.value = state.value.copy(
                childDeleteConfirmationRequested = false,
                childDeleteTargetId = null,
                childActionPendingId = null,
                childActionError = null,
            )
            state.value.taskId?.let(::observeChildren)
        }
    }

    private fun clearChildDeleteTarget() {
        saved.remove<String>(KEY_CHILD_DELETE_ID)
        saved.remove<String>(KEY_CHILD_DELETE_REVISION)
        saved.remove<Boolean>(KEY_CHILD_DELETE_REQUESTED)
    }

    private suspend fun handleFailure(error: TodoEditorError, failure: Exception) {
        if (error == TodoEditorError.SAVE && failure !is CancellationException) {
            saved.remove<Boolean>(KEY_CONTINUE_TO_SUBTASK)
        }
        val current = state.value
        if (error == TodoEditorError.SAVE && creating && creationParentId != null &&
            (failure is TodoConflictException || failure is TodoMissingException)
        ) {
            val parentId = requireNotNull(creationParentId)
            val parent = try {
                repository.get(parentId)
            } catch (lookupFailure: Exception) {
                if (lookupFailure is CancellationException) throw lookupFailure
                mutableState.value = current.copy(
                    busy = false,
                    parentContextLoading = false,
                    parentContextError = true,
                    error = TodoEditorError.SAVE,
                )
                return
            }
            if (parent == null || parent.isCompleted) {
                mutableState.value = current.copy(
                    busy = false,
                    parentTitle = parent?.title,
                    parentContextLoading = false,
                    parentContextError = false,
                    parentMissing = parent == null,
                    parentCompleted = parent?.isCompleted == true,
                    error = TodoEditorError.SAVE,
                )
                return
            }
        }
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
        moveTargetsJob?.cancel()
        moveRefreshJob?.cancel()
        clearPending()
        clearPendingMove()
        persistFieldErrors(emptyMap())
        baselineInput = task.toInput()
        baselineRevision = task.revision
        baselineCompleted = task.isCompleted
        persistBaseline(baselineInput!!, baselineRevision, baselineCompleted)
        val draft = task.toDraft()
        persistDraft(draft, baselineInput!!)
        val taskParentId = task.parentId
        saved[KEY_PARENT_ID] = taskParentId?.toString().orEmpty()
        val current = state.value
        mutableState.value = current.copy(
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
            taskId = task.id,
            parentId = taskParentId,
            familyRevision = task.familyRevision,
            parentTitle = current.parentTitle.takeIf { current.parentId == taskParentId },
            parentContextLoading = taskParentId != null,
            parentContextError = false,
            parentMissing = false,
            parentCompleted = false,
            childContinuationTaskId = saved.uuid(KEY_CHILD_CONTINUATION),
            move = TodoMoveUiState(),
        )
        if (taskParentId == null) observeChildren(task.id, current.childrenPageLimit)
        else loadParentContext(taskParentId)
    }

    private fun finish(savedResult: TodoEditorResult? = null, deleted: Boolean = false) {
        clearPending()
        clearPendingMove()
        clearChildDeleteTarget()
        childListJob?.cancel()
        parentContextJob?.cancel()
        childActionJob?.cancel()
        moveTargetsJob?.cancel()
        moveRefreshJob?.cancel()
        saved.remove<Boolean>(KEY_CONTINUE_TO_SUBTASK)
        saved.remove<String>(KEY_CHILD_CONTINUATION)
        saved[KEY_FINISHED] = true
        saved[KEY_DELETED] = deleted
        if (savedResult == null) {
            saved.remove<String>(KEY_RESULT_TASK_ID)
            saved.remove<String>(KEY_RESULT_REVISION)
            saved.remove<String>(KEY_RESULT_COMMAND)
            saved.remove<Boolean>(KEY_RESULT_COMPLETED)
        } else {
            saved[KEY_RESULT_TASK_ID] = savedResult.taskId.toString()
            saved[KEY_RESULT_REVISION] = savedResult.resultingRevision?.toString().orEmpty()
            saved[KEY_RESULT_COMMAND] = savedResult.completionCommandId?.toString().orEmpty()
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
            task.parentId == creationParentId &&
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

    private fun persistPendingMove(pending: PendingMove) {
        saved[KEY_MOVE_EXPECTED] = pending.expectedRevision.toString()
        saved[KEY_MOVE_NEXT] = pending.nextRevision.toString()
        saved[KEY_MOVE_SOURCE_FAMILY] = pending.expectedSourceFamilyRevision.toString()
        saved[KEY_MOVE_TARGET_PARENT] = pending.targetParentId?.toString().orEmpty()
        saved[KEY_MOVE_TARGET_FAMILY] = pending.expectedTargetFamilyRevision?.toString().orEmpty()
    }

    private fun clearPendingMove() {
        listOf(
            KEY_MOVE_EXPECTED,
            KEY_MOVE_NEXT,
            KEY_MOVE_SOURCE_FAMILY,
            KEY_MOVE_TARGET_PARENT,
            KEY_MOVE_TARGET_FAMILY,
        ).forEach { saved.remove<String>(it) }
    }

    private fun readPendingMove(): PendingMove? {
        val expected = saved.uuid(KEY_MOVE_EXPECTED) ?: return null
        val next = saved.uuid(KEY_MOVE_NEXT) ?: return null
        val sourceFamily = saved.uuid(KEY_MOVE_SOURCE_FAMILY) ?: return null
        val rawTargetParent = saved.get<String>(KEY_MOVE_TARGET_PARENT) ?: return null
        val targetParent = if (rawTargetParent.isEmpty()) {
            null
        } else {
            runCatching { UUID.fromString(rawTargetParent) }.getOrNull() ?: return null
        }
        val rawTargetFamily = saved.get<String>(KEY_MOVE_TARGET_FAMILY) ?: return null
        val targetFamily = if (targetParent == null) {
            if (rawTargetFamily.isNotEmpty()) return null
            null
        } else {
            if (rawTargetFamily.isEmpty()) return null
            runCatching { UUID.fromString(rawTargetFamily) }.getOrNull() ?: return null
        }
        return PendingMove(expected, next, sourceFamily, targetParent, targetFamily)
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
        saved[KEY_DUE_TIME] = draft.dueTime?.toString().orEmpty()
        saved[KEY_IMPORTANCE] = draft.importance.code
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
            "$KEY_PENDING_INPUT.due_time",
            "$KEY_PENDING_INPUT.importance",
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
            readPendingMove() == null && pending?.type != PendingType.COMPLETE && pending?.type != PendingType.REOPEN &&
            pending?.type != PendingType.DELETE

    private fun pendingBlocksFields(pending: PendingOperation?) =
        readPendingMove() != null || pending?.type == PendingType.COMPLETE || pending?.type == PendingType.REOPEN ||
            pending?.type == PendingType.DELETE

    private fun equivalent(first: TodoInput, second: TodoInput): Boolean = comparable(first) == comparable(second)

    private fun comparable(input: TodoInput): TodoInput {
        val title = if (input.title.contains('\n') || input.title.contains('\r')) input.title else input.title.trim()
        val note = input.note?.trim()?.takeUnless(String::isEmpty)
        return TodoInput(
            title = title,
            note = note,
            dueDate = input.dueDate,
            dueTime = input.dueTime,
            importance = input.importance,
        )
    }

    private fun TodoTask.toInput() = TodoInput(
        title = title,
        note = note,
        dueDate = dueDate,
        dueTime = dueTime,
        importance = importance,
    )

    private fun TodoInput.toDraft() = TodoDraft(
        title = title,
        note = note.orEmpty(),
        dueDate = dueDate,
        dueTime = dueTime,
        importance = importance,
    )

    private fun TodoTask.toDraft() = toInput().toDraft()

    private fun resultFrom(saved: SavedStateHandle): TodoEditorResult? {
        val id = saved.uuid(KEY_RESULT_TASK_ID) ?: return null
        val revision = saved.uuid(KEY_RESULT_REVISION)
        val command = saved.uuid(KEY_RESULT_COMMAND)
        if (revision == null && command == null) return null
        val completed = saved.get<Boolean>(KEY_RESULT_COMPLETED) ?: return null
        return TodoEditorResult(id, completed, revision, command)
    }

    private fun SavedStateHandle.uuid(key: String): UUID? = get<String>(key)?.let { value ->
        value.takeIf(String::isNotEmpty)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    }

    private fun SavedStateHandle.readInput(prefix: String): TodoInput? {
        if (prefix == KEY_BASELINE && get<Boolean>(KEY_BASELINE_SET) != true) return null
        val dueDate = get<String>("$prefix.due_date").toLocalDateOrNull()
        return TodoInput(
            title = get<String>("$prefix.title") ?: return null,
            note = get<String>("$prefix.note").orEmpty().ifEmpty { null },
            dueDate = dueDate,
            dueTime = get<String>("$prefix.due_time").toLocalTimeOrNull().takeIf { dueDate != null },
            importance = get<Int>("$prefix.importance")?.let { TodoImportance.fromCode(it) } ?: TodoImportance.NONE,
        )
    }

    private fun SavedStateHandle.writeInput(prefix: String, input: TodoInput) {
        this["$prefix.title"] = input.title
        this["$prefix.note"] = input.note.orEmpty()
        this["$prefix.due_date"] = input.dueDate?.toString().orEmpty()
        this["$prefix.due_time"] = input.dueTime?.toString().orEmpty()
        this["$prefix.importance"] = input.importance.code
    }

    private fun SavedStateHandle.readDraft(): TodoDraft? {
        if (get<Boolean>(KEY_HAS_DRAFT) != true) return null
        val dueDate = get<String>(KEY_DUE_DATE).toLocalDateOrNull()
        return TodoDraft(
            title = get<String>(KEY_TITLE).orEmpty(),
            note = get<String>(KEY_NOTE).orEmpty(),
            dueDate = dueDate,
            dueTime = get<String>(KEY_DUE_TIME).toLocalTimeOrNull().takeIf { dueDate != null },
            importance = get<Int>(KEY_IMPORTANCE)?.let { TodoImportance.fromCode(it) } ?: TodoImportance.NONE,
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

    private fun String?.toLocalTimeOrNull(): LocalTime? =
        this?.takeIf(String::isNotEmpty)?.let {
            runCatching { LocalTime.parse(it) }.getOrNull()?.takeIf { time -> time.second == 0 && time.nano == 0 }
        }

    private data class PendingMove(
        val expectedRevision: UUID,
        val nextRevision: UUID,
        val expectedSourceFamilyRevision: UUID,
        val targetParentId: UUID?,
        val expectedTargetFamilyRevision: UUID?,
    )

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
        const val KEY_PARENT_ID = "todo_parent_id"
        const val KEY_CHILD_PAGE_LIMIT = "todo_child_page_limit"
        const val KEY_CHILD_DELETE_ID = "todo_child_delete_id"
        const val KEY_CHILD_DELETE_REVISION = "todo_child_delete_revision"
        const val KEY_CHILD_DELETE_REQUESTED = "todo_child_delete_requested"
        const val KEY_CONTINUE_TO_SUBTASK = "todo_continue_to_subtask"
        const val KEY_CHILD_CONTINUATION = "todo_child_continuation"
        const val KEY_INITIAL_REVISION = "todo_initial_revision"
        const val KEY_INITIAL_DUE_DATE = "todo_initial_due_date"
        const val KEY_TITLE = "todo_draft_title"
        const val KEY_NOTE = "todo_draft_note"
        const val KEY_DUE_DATE = "todo_draft_due_date"
        const val KEY_DUE_TIME = "todo_draft_due_time"
        const val KEY_IMPORTANCE = "todo_draft_importance"
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
        const val KEY_RESULT_COMMAND = "todo_result_command"
        const val KEY_MOVE_EXPECTED = "todo_move_expected_revision"
        const val KEY_MOVE_NEXT = "todo_move_next_revision"
        const val KEY_MOVE_SOURCE_FAMILY = "todo_move_source_family_revision"
        const val KEY_MOVE_TARGET_PARENT = "todo_move_target_parent"
        const val KEY_MOVE_TARGET_FAMILY = "todo_move_target_family_revision"
    }
}
