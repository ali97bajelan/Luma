package com.luma.lifeos.ui.todo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luma.lifeos.domain.todo.TODO_DEFAULT_PAGE_SIZE
import com.luma.lifeos.domain.todo.TodoChildProgress
import com.luma.lifeos.domain.todo.TodoConflictException
import com.luma.lifeos.domain.todo.TodoMissingException
import com.luma.lifeos.domain.todo.TodoPage
import com.luma.lifeos.domain.todo.TodoRowPage
import com.luma.lifeos.domain.todo.TodoTaskRow
import com.luma.lifeos.domain.todo.TodoImportance
import com.luma.lifeos.domain.todo.TodoListQuery
import com.luma.lifeos.domain.todo.TodoRepository
import com.luma.lifeos.domain.todo.TodoSort
import com.luma.lifeos.domain.todo.TodoTask
import java.time.LocalDate
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TodoListLoadError { INITIAL, REFRESH, PAGE }
enum class TodoListRowError { FAILED, CONFLICT, MISSING }

data class TodoMutationIntent(
    val taskId: UUID,
    val expectedRevision: UUID,
    val resultingRevision: UUID,
    val completed: Boolean,
    val title: String,
)

data class TodoParentCompletionOffer(
    val parentTask: TodoTask,
    val progress: TodoChildProgress,
)

data class TodoListUiState(
    val completed: Boolean = false,
    val page: TodoPage = TodoPage(emptyList(), 0),
    val pageLimit: Int = TODO_DEFAULT_PAGE_SIZE,
    val today: LocalDate = LocalDate.now(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val dataFresh: Boolean = false,
    val loadError: TodoListLoadError? = null,
    val pendingIntents: Map<UUID, TodoMutationIntent> = emptyMap(),
    val inFlight: Set<UUID> = emptySet(),
    val rowErrors: Map<UUID, TodoListRowError> = emptyMap(),
    val rowErrorTitles: Map<UUID, String> = emptyMap(),
    val undoableCompletion: TodoActionResult? = null,
    val undoing: Boolean = false,
    val undoFailed: Boolean = false,
    val undoTerminal: Boolean = false,
    val importanceFilter: TodoImportance? = null,
    val sort: TodoSort = TodoSort.DATE,
    val rowPage: TodoRowPage = TodoRowPage(emptyList(), 0),
    val expandedRootIds: Set<UUID> = emptySet(),
    val childrenByRoot: Map<UUID, TodoRowPage> = emptyMap(),
    val childPageLimits: Map<UUID, Int> = emptyMap(),
    val loadingChildren: Set<UUID> = emptySet(),
    val childLoadErrors: Set<UUID> = emptySet(),
    val parentCompletionOffer: TodoParentCompletionOffer? = null,
    val familyAction: TodoFamilyActionState = TodoFamilyActionState(),
)

/** Observed prefix, retained list selection and revision-safe row actions. */
@OptIn(ExperimentalCoroutinesApi::class)
class TodoListViewModel(
    private val repository: TodoRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val refreshVersion = MutableStateFlow(0)
    private val reconciling = mutableSetOf<UUID>()
    private val mutationJobs = mutableMapOf<UUID, Job>()
    private val childJobs = mutableMapOf<UUID, Job>()
    private val undoJobs = mutableMapOf<TodoActionResult, Job>()
    private val mutableState = MutableStateFlow(
        TodoListUiState(
            completed = saved[KEY_COMPLETED] ?: false,
            pageLimit = pageLimitFor(
                completed = saved[KEY_COMPLETED] ?: false,
                importanceFilter = readImportanceFilter(),
                sort = readSort(),
            ),
            importanceFilter = readImportanceFilter(),
            sort = readSort(),
            expandedRootIds = readExpandedRootIds(),
            childPageLimits = readExpandedRootIds().associateWith(::childPageLimitFor),
            pendingIntents = readPendingIntents(),
            rowErrors = readPendingIntents().keys.associateWith { TodoListRowError.FAILED },
            rowErrorTitles = readPendingIntents().mapValues { it.value.title },
            undoableCompletion = readUndoResult(),
        ),
    )
    val state = mutableState.asStateFlow()
    private val familyActions = TodoFamilyActions(
        repository = repository,
        saved = saved,
        scope = viewModelScope,
        keyPrefix = "todo_list_family",
        onCompletionApplied = ::acceptFamilyCompletion,
        onDeleteApplied = {},
    )

    init {
        viewModelScope.launch {
            familyActions.state.collect { action ->
                mutableState.update { it.copy(familyAction = action) }
            }
        }
        viewModelScope.launch {
            combine(
                mutableState.map { state ->
                    ListRequest(
                        query = TodoListQuery(state.completed, state.importanceFilter, state.sort),
                        limit = state.pageLimit,
                    )
                }.distinctUntilChanged(),
                refreshVersion,
            ) { request, _ -> request }
                .flatMapLatest { request ->
                    observeRows(request.query, request.limit)
                        .map<TodoRowPage, ListObservation> { ListObservation.Loaded(it) }
                        .catch { emit(ListObservation.Failed) }
                }
                .collect { observation ->
                    when (observation) {
                        is ListObservation.Loaded -> mutableState.update {
                            it.copy(
                                page = TodoPage(observation.page.items.map { row -> row.task }, observation.page.total),
                                rowPage = observation.page,
                                loading = false,
                                refreshing = false,
                                loadingMore = false,
                                dataFresh = true,
                                loadError = null,
                            )
                        }
                        ListObservation.Failed -> {
                            val current = mutableState.value
                            val error = when {
                                current.loadingMore -> TodoListLoadError.PAGE
                                current.page.items.isNotEmpty() || current.dataFresh -> TodoListLoadError.REFRESH
                                else -> TodoListLoadError.INITIAL
                            }
                            mutableState.update {
                                it.copy(
                                    loading = false,
                                    refreshing = false,
                                    loadingMore = false,
                                    dataFresh = error == TodoListLoadError.PAGE && current.dataFresh,
                                    loadError = error,
                                )
                            }
                        }
                    }
                }
        }
        reconcilePendingIntents()
        mutableState.value.expandedRootIds.forEach { rootId ->
            startChildObservation(rootId, childPageLimitFor(rootId))
        }
        restoreParentCompletionOffer()
    }

    fun selectCompleted(completed: Boolean) {
        val current = mutableState.value
        if (current.completed == completed) return
        val limit = pageLimitFor(completed, current.importanceFilter, current.sort)
        saved[KEY_COMPLETED] = completed
        mutableState.value = current.copy(
            completed = completed,
            page = TodoPage(emptyList(), 0),
            rowPage = TodoRowPage(emptyList(), 0),
            pageLimit = limit,
            loading = true,
            refreshing = true,
            loadingMore = false,
            dataFresh = false,
            loadError = null,
        )
    }

    fun setImportanceFilter(value: TodoImportance?) {
        updateQuery(importanceFilter = value, sort = mutableState.value.sort)
    }

    fun setSort(value: TodoSort) {
        updateQuery(importanceFilter = mutableState.value.importanceFilter, sort = value)
    }

    fun setExpanded(rootId: UUID, expanded: Boolean) {
        if (!expanded) {
            collapseRoot(rootId)
            return
        }
        val current = mutableState.value
        val root = current.rowPage.items.firstOrNull { it.task.id == rootId } ?: return
        if (root.task.parentId != null || root.progress == null || rootId in current.expandedRootIds) return
        val expandedIds = current.expandedRootIds + rootId
        saveExpandedRootIds(expandedIds)
        val limit = childPageLimitFor(rootId)
        mutableState.update {
            it.copy(
                expandedRootIds = expandedIds,
                childPageLimits = it.childPageLimits + (rootId to limit),
                loadingChildren = it.loadingChildren + rootId,
                childLoadErrors = it.childLoadErrors - rootId,
            )
        }
        startChildObservation(rootId, limit)
    }

    fun loadMoreChildren(rootId: UUID) {
        val current = mutableState.value
        val page = current.childrenByRoot[rootId] ?: return
        if (rootId !in current.expandedRootIds || rootId in current.loadingChildren || page.items.size >= page.total) return
        val nextLimit = (current.childPageLimits[rootId] ?: childPageLimitFor(rootId)) + TODO_DEFAULT_PAGE_SIZE
        saved[childPageLimitKey(rootId)] = nextLimit
        mutableState.update {
            it.copy(
                childPageLimits = it.childPageLimits + (rootId to nextLimit),
                loadingChildren = it.loadingChildren + rootId,
                childLoadErrors = it.childLoadErrors - rootId,
            )
        }
        startChildObservation(rootId, nextLimit)
    }

    fun retryChildren(rootId: UUID) {
        val current = mutableState.value
        if (rootId !in current.expandedRootIds || rootId in current.loadingChildren) return
        startChildObservation(rootId, current.childPageLimits[rootId] ?: childPageLimitFor(rootId))
    }

    fun resetQuery() {
        updateQuery(importanceFilter = null, sort = TodoSort.DATE)
    }

    private fun updateQuery(importanceFilter: TodoImportance?, sort: TodoSort) {
        val current = mutableState.value
        if (current.importanceFilter == importanceFilter && current.sort == sort) return
        val limit = pageLimitFor(current.completed, importanceFilter, sort)
        saved[KEY_IMPORTANCE_FILTER] = importanceFilter?.code ?: FILTER_ALL
        saved[KEY_IMPORTANCE_SORT] = sort == TodoSort.IMPORTANCE
        mutableState.value = current.copy(
            importanceFilter = importanceFilter,
            sort = sort,
            page = TodoPage(emptyList(), 0),
            rowPage = TodoRowPage(emptyList(), 0),
            pageLimit = limit,
            loading = true,
            refreshing = true,
            loadingMore = false,
            dataFresh = false,
            loadError = null,
        )
    }

    fun loadMore() {
        val current = mutableState.value
        if (!current.dataFresh || current.loading || current.loadingMore || current.page.items.size >= current.page.total) return
        val expandedLimit = current.pageLimit + TODO_DEFAULT_PAGE_SIZE
        saved[pageLimitKey(current.completed, current.importanceFilter, current.sort)] = expandedLimit
        mutableState.update { it.copy(pageLimit = expandedLimit, loadingMore = true, loadError = null) }
    }

    fun retryLoad() {
        val current = mutableState.value
        if (current.loading || current.refreshing || current.loadingMore) return
        val pageRetry = current.loadError == TodoListLoadError.PAGE
        mutableState.update {
            it.copy(
                loading = !pageRetry,
                refreshing = !pageRetry,
                loadingMore = pageRetry,
                dataFresh = pageRetry && current.dataFresh,
                loadError = null,
            )
        }
        refreshVersion.update { it + 1 }
    }

    fun refresh() {
        mutableState.update { it.copy(refreshing = true, dataFresh = false, loadError = null) }
        refreshVersion.update { it + 1 }
    }

    fun refreshDay(date: LocalDate) {
        mutableState.update { if (it.today == date) it else it.copy(today = date) }
    }

    fun setCompleted(taskId: UUID, completed: Boolean) {
        val current = mutableState.value
        val task = current.rowPage.items.firstOrNull { it.task.id == taskId }?.task
            ?: current.childrenByRoot.values.asSequence()
                .flatMap { it.items.asSequence() }
                .firstOrNull { it.task.id == taskId }?.task
            ?: return
        queueCompletion(task, completed)
    }

    fun completeSuggestedParent() {
        val offer = mutableState.value.parentCompletionOffer ?: return
        queueCompletion(offer.parentTask, completed = true)
    }

    fun dismissParentCompletionOffer() {
        clearParentCompletionOffer()
    }

    private fun queueCompletion(task: TodoTask, completed: Boolean) {
        val current = mutableState.value
        val taskId = task.id
        if (!current.dataFresh || current.inFlight.contains(taskId) || current.pendingIntents.containsKey(taskId) ||
            (current.undoing && current.undoableCompletion?.taskId == taskId) ||
            (current.familyAction.busy && current.familyAction.targetId == taskId) || task.isCompleted == completed
        ) return
        clearUndoResult()
        if (completed && current.parentCompletionOffer?.parentTask?.id == taskId) clearParentCompletionOffer(taskId)
        mutableState.update {
            it.copy(
                rowErrors = it.rowErrors - taskId,
                rowErrorTitles = it.rowErrorTitles - taskId,
                undoFailed = false,
                parentCompletionOffer = it.parentCompletionOffer?.takeUnless { offer -> offer.parentTask.id == taskId },
            )
        }
        familyActions.complete(taskId, completed, task.title, task.revision)
    }

    fun retry(taskId: UUID) {
        val current = mutableState.value
        if (current.familyAction.targetId == taskId &&
            current.familyAction.phase == TodoFamilyActionPhase.RETRYABLE_FAILURE
        ) {
            familyActions.retry()
            return
        }
        val intent = current.pendingIntents[taskId] ?: return
        if (!current.dataFresh || current.inFlight.contains(taskId)) return
        familyActions.complete(taskId, intent.completed, intent.title, intent.expectedRevision)
        removePending(taskId)
    }

    fun dismissRowError(taskId: UUID) {
        mutableState.update { it.copy(rowErrors = it.rowErrors - taskId, rowErrorTitles = it.rowErrorTitles - taskId) }
    }

    fun undoCompletion() {
        val current = mutableState.value
        val result = current.undoableCompletion ?: return
        if (!result.completed) return
        if (current.undoing || current.undoTerminal || result in undoJobs) return
        if (result.completionCommandId != null) {
            undoFamilyCompletion(result)
            return
        }
        val undoParentId = current.rowPage.items.firstOrNull { it.task.id == result.taskId }?.task?.parentId
            ?: current.childrenByRoot.values.asSequence()
                .flatMap { it.items.asSequence() }
                .firstOrNull { it.task.id == result.taskId }?.task?.parentId
        val undoNextKey = undoNextKey(result)
        val nextRevision = saved.get<String>(undoNextKey)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: UUID.randomUUID().also { saved[undoNextKey] = it.toString() }
        mutableState.update {
            if (it.undoableCompletion == result) it.copy(undoing = true, undoFailed = false) else it
        }
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                repository.setCompleted(result.taskId, requireNotNull(result.resultingRevision), nextRevision, completed = false)
                clearParentOfferAfterUndo(result, undoParentId)
            } catch (failure: Exception) {
                if (failure is CancellationException) {
                    mutableState.update {
                        if (it.undoableCompletion == result) it.copy(undoing = false) else it
                    }
                    throw failure
                }
                val latest = runCatching { repository.get(result.taskId) }
                val live = latest.getOrNull()
                when {
                    live?.revision == nextRevision && !live.isCompleted ->
                        clearParentOfferAfterUndo(result, undoParentId)
                    live != null && live.revision == result.resultingRevision && live.isCompleted -> mutableState.update {
                        if (it.undoableCompletion == result) it.copy(undoing = false, undoFailed = true) else it
                    }
                    failure is TodoConflictException || failure is TodoMissingException ||
                        latest.isSuccess -> {
                        mutableState.update {
                            if (it.undoableCompletion == result) {
                                it.copy(undoing = false, undoFailed = true, undoTerminal = true)
                            } else it
                        }
                        refresh()
                    }
                    else -> mutableState.update {
                        if (it.undoableCompletion == result) it.copy(undoing = false, undoFailed = true) else it
                    }
                }
            } finally {
                if (undoJobs[result] === coroutineContext[Job]) undoJobs.remove(result)
            }
        }
        undoJobs[result] = job
        job.start()
    }

    private fun undoFamilyCompletion(result: TodoActionResult) {
        val commandId = requireNotNull(result.completionCommandId)
        val undoKey = undoNextKey(result)
        val undoCommandId = saved.get<String>(undoKey)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: UUID.randomUUID().also { saved[undoKey] = it.toString() }
        mutableState.update { if (it.undoableCompletion == result) it.copy(undoing = true, undoFailed = false) else it }
        val job = viewModelScope.launch {
            try {
                repository.undoCompletion(commandId, undoCommandId)
                familyActions.release(commandId)
                familyActions.release(undoCommandId)
                clearParentOfferAfterUndo(result, null)
                refresh()
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                val terminal = failure is TodoConflictException || failure is TodoMissingException
                mutableState.update {
                    if (it.undoableCompletion == result) {
                        it.copy(undoing = false, undoFailed = true, undoTerminal = terminal)
                    } else it
                }
                if (terminal) refresh()
            } finally {
                undoJobs.remove(result)
            }
        }
        undoJobs[result] = job
    }

    fun dismissUndo() {
        mutableState.value.undoableCompletion?.completionCommandId?.let(familyActions::release)
        clearUndoResult()
    }

    fun confirmFamilyAction() = familyActions.confirm()
    fun cancelFamilyAction() = familyActions.cancel()
    fun retryFamilyAction() = familyActions.retry()
    fun reviewFamilyAction() = familyActions.reviewAgain()
    fun dismissFamilyAction() = familyActions.dismiss()

    /** Clears retained operations and reloads after a committed backup replacement. */
    fun invalidateAfterRestore() {
        mutationJobs.values.toList().forEach { it.cancel() }
        mutationJobs.clear()
        childJobs.values.toList().forEach { it.cancel() }
        childJobs.clear()
        saved.remove<ArrayList<String>>(KEY_EXPANDED_ROOTS)
        saved.keys().filter { it.startsWith(KEY_CHILD_PAGE_LIMIT_PREFIX) }.forEach { saved.remove<Int>(it) }
        saved.remove<String>(KEY_PARENT_COMPLETION_OFFER)
        undoJobs.values.toList().forEach { it.cancel() }
        undoJobs.clear()
        familyActions.invalidate()
        clearPendingIntents()
        clearUndoResult()
        saved.keys().filter { it.startsWith(KEY_PAGE_LIMIT_PREFIX) }.forEach { saved.remove<Int>(it) }
        saved.remove<Int>(KEY_OPEN_LIMIT)
        saved.remove<Int>(KEY_COMPLETED_LIMIT)
        saved[KEY_IMPORTANCE_FILTER] = FILTER_ALL
        saved[KEY_IMPORTANCE_SORT] = false
        saved[KEY_COMPLETED] = false
        mutableState.value = TodoListUiState(today = mutableState.value.today)
        refreshVersion.update { it + 1 }
    }

    private fun acceptFamilyCompletion(receipt: com.luma.lifeos.domain.todo.TodoCompletionReceipt) {
        val taskId = receipt.targetId
        if (receipt.changedTaskCount == 0) {
            refresh()
            return
        }
        clearUndoResult()
        val result = TodoActionResult(taskId, receipt.completed, null, receipt.commandId)
        mutableState.update {
            it.copy(
                rowErrors = it.rowErrors - taskId,
                rowErrorTitles = it.rowErrorTitles - taskId,
                undoableCompletion = result,
                undoing = false,
                undoFailed = false,
                undoTerminal = false,
            )
        }
        saveUndoResult(result.takeIf { receipt.undoAvailable })
        if (receipt.completed) {
            viewModelScope.launch {
                repository.get(taskId)?.parentId?.let(::suggestParentCompletion)
            }
        }
        refresh()
    }

    private fun acceptCommitted(intent: TodoMutationIntent, task: TodoTask) {
        removePending(intent.taskId)
        val result = TodoActionResult(task.id, intent.completed, task.revision)
        mutableState.update {
            it.copy(
                inFlight = it.inFlight - intent.taskId,
                rowErrors = it.rowErrors - intent.taskId,
                rowErrorTitles = it.rowErrorTitles - intent.taskId,
                undoableCompletion = result,
                undoing = false,
                undoFailed = false,
                undoTerminal = false,
            )
        }
        saveUndoResult(result.takeIf { it.completed })
        if (intent.completed) {
            val parentId = task.parentId
            if (parentId == null) clearParentCompletionOffer(task.id)
            else suggestParentCompletion(parentId)
        }
    }

    private fun suggestParentCompletion(parentId: UUID) {
        saved[KEY_PARENT_COMPLETION_OFFER] = parentId.toString()
        mutableState.update { it.copy(parentCompletionOffer = null) }
        refreshParentCompletionOffer(parentId)
    }

    private fun restoreParentCompletionOffer() {
        val parentId = saved.get<String>(KEY_PARENT_COMPLETION_OFFER)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return
        refreshParentCompletionOffer(parentId)
    }

    private fun refreshParentCompletionOffer(parentId: UUID) {
        viewModelScope.launch {
            try {
                val parent = repository.get(parentId)
                if (parent == null || parent.parentId != null || parent.isCompleted) {
                    clearParentCompletionOffer(parentId)
                    return@launch
                }
                val progress = repository.observeChildren(parentId, limit = 1).first().familyProgress
                if (progress == null || progress.completed != progress.total) {
                    clearParentCompletionOffer(parentId)
                    return@launch
                }
                if (saved.get<String>(KEY_PARENT_COMPLETION_OFFER) == parentId.toString()) {
                    mutableState.update { it.copy(parentCompletionOffer = TodoParentCompletionOffer(parent, progress)) }
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
            }
        }
    }

    private fun clearParentCompletionOffer(parentId: UUID? = null) {
        val savedId = saved.get<String>(KEY_PARENT_COMPLETION_OFFER)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        if (parentId == null || savedId == parentId) saved.remove<String>(KEY_PARENT_COMPLETION_OFFER)
        mutableState.update { current ->
            if (parentId == null || current.parentCompletionOffer?.parentTask?.id == parentId) {
                current.copy(parentCompletionOffer = null)
            } else current
        }
    }

    private fun reconcilePendingIntents() {
        mutableState.value.pendingIntents.keys.forEach { taskId ->
            if (!reconciling.add(taskId)) return@forEach
            viewModelScope.launch {
                try {
                    val intent = mutableState.value.pendingIntents[taskId] ?: return@launch
                    val live = repository.get(taskId)
                    when {
                        live == null -> failAndRemove(taskId, TodoListRowError.MISSING, intent.title)
                        isCommitted(intent, live) -> acceptCommitted(intent, live)
                        live.revision != intent.expectedRevision -> failAndRemove(taskId, TodoListRowError.CONFLICT, intent.title)
                        else -> mutableState.update {
                            it.copy(rowErrors = it.rowErrors + (taskId to TodoListRowError.FAILED))
                        }
                    }
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    mutableState.update { it.copy(rowErrors = it.rowErrors + (taskId to TodoListRowError.FAILED)) }
                } finally {
                    reconciling.remove(taskId)
                }
            }
        }
    }

    private fun failAndRemove(taskId: UUID, error: TodoListRowError, title: String) {
        removePending(taskId)
        mutableState.update {
            it.copy(
                inFlight = it.inFlight - taskId,
                rowErrors = it.rowErrors + (taskId to error),
                rowErrorTitles = it.rowErrorTitles + (taskId to title),
            )
        }
    }

    private fun isCommitted(intent: TodoMutationIntent, task: TodoTask) =
        task.revision == intent.resultingRevision && task.isCompleted == intent.completed

    private fun updatePending(intents: Map<UUID, TodoMutationIntent>) {
        mutableState.update { it.copy(pendingIntents = intents) }
        saved[KEY_PENDING] = ArrayList(intents.values.map(::encodeIntent))
    }

    private fun removePending(taskId: UUID) = updatePending(mutableState.value.pendingIntents - taskId)

    private fun clearPendingIntents() = updatePending(emptyMap())

    private fun saveUndoResult(result: TodoActionResult?) {
        val previous = readUndoResult()
        previous?.let {
            saved.remove<String>(undoNextKey(it))
            it.completionCommandId?.let(familyActions::release)
        }
        if (result == null) {
            saved.remove<String>(KEY_UNDO_ID)
            saved.remove<String>(KEY_UNDO_REVISION)
            saved.remove<String>(KEY_UNDO_COMMAND)
        } else {
            saved[KEY_UNDO_ID] = result.taskId.toString()
            saved[KEY_UNDO_REVISION] = result.resultingRevision?.toString().orEmpty()
            saved[KEY_UNDO_COMMAND] = result.completionCommandId?.toString().orEmpty()
        }
    }

    private fun clearParentOfferAfterUndo(result: TodoActionResult, parentId: UUID?) {
        parentId?.let(::clearParentCompletionOffer)
        clearUndoResult(result)
    }

    private fun clearUndoResult(expected: TodoActionResult? = null) {
        val current = mutableState.value.undoableCompletion
        if (expected != null && current != expected) return
        if (current != null) saved.remove<String>(undoNextKey(current))
        saveUndoResult(null)
        mutableState.update { it.copy(undoableCompletion = null, undoing = false, undoFailed = false, undoTerminal = false) }
    }

    private fun undoNextKey(result: TodoActionResult) =
        "$KEY_UNDO_NEXT_PREFIX${result.taskId}_${result.completionCommandId ?: result.resultingRevision}"

    private fun readUndoResult(): TodoActionResult? {
        val id = saved.get<String>(KEY_UNDO_ID)?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return null
        val revision = saved.get<String>(KEY_UNDO_REVISION)?.takeIf(String::isNotEmpty)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val command = saved.get<String>(KEY_UNDO_COMMAND)?.takeIf(String::isNotEmpty)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        if (revision == null && command == null) return null
        return TodoActionResult(id, true, revision, command)
    }

    private fun readPendingIntents(): Map<UUID, TodoMutationIntent> =
        saved.get<ArrayList<String>>(KEY_PENDING).orEmpty().mapNotNull(::decodeIntent).associateBy { it.taskId }

    private fun encodeIntent(intent: TodoMutationIntent): String {
        val title = Base64.getUrlEncoder().withoutPadding().encodeToString(intent.title.toByteArray(Charsets.UTF_8))
        return listOf(intent.taskId, intent.expectedRevision, intent.resultingRevision, intent.completed, title).joinToString("|")
    }

    private fun decodeIntent(value: String): TodoMutationIntent? = runCatching {
        val fields = value.split('|', limit = 5)
        require(fields.size == 5)
        TodoMutationIntent(
            UUID.fromString(fields[0]),
            UUID.fromString(fields[1]),
            UUID.fromString(fields[2]),
            fields[3].toBooleanStrict(),
            String(Base64.getUrlDecoder().decode(fields[4]), Charsets.UTF_8),
        )
    }.getOrNull()

    private fun readImportanceFilter(): TodoImportance? = when (val code = saved.get<Int>(KEY_IMPORTANCE_FILTER)) {
        null, FILTER_ALL -> null
        else -> runCatching { TodoImportance.fromCode(code) }.getOrNull()
    }

    private fun readSort(): TodoSort =
        if (saved.get<Boolean>(KEY_IMPORTANCE_SORT) == true) TodoSort.IMPORTANCE else TodoSort.DATE

    private fun pageLimitFor(
        completed: Boolean,
        importanceFilter: TodoImportance?,
        sort: TodoSort,
    ): Int {
        val key = pageLimitKey(completed, importanceFilter, sort)
        val savedLimit = saved.get<Int>(key)
        if (savedLimit != null) return savedLimit.coerceAtLeast(TODO_DEFAULT_PAGE_SIZE)
        if (importanceFilter == null && sort == TodoSort.DATE) {
            val legacyKey = if (completed) KEY_COMPLETED_LIMIT else KEY_OPEN_LIMIT
            return (saved.get<Int>(legacyKey) ?: TODO_DEFAULT_PAGE_SIZE).coerceAtLeast(TODO_DEFAULT_PAGE_SIZE)
        }
        return TODO_DEFAULT_PAGE_SIZE
    }

    private fun pageLimitKey(completed: Boolean, importanceFilter: TodoImportance?, sort: TodoSort): String =
        "$KEY_PAGE_LIMIT_PREFIX${if (completed) "completed" else "open"}_${importanceFilter?.code ?: FILTER_ALL}_${sort.name}"

    private fun observeRows(query: TodoListQuery, limit: Int): kotlinx.coroutines.flow.Flow<TodoRowPage> = try {
        repository.observeRows(query, limit)
    } catch (_: UnsupportedOperationException) {
        repository.observeList(query, limit).map { page ->
            val roots = page.items.filter { it.parentId == null }
            TodoRowPage(roots.map { TodoTaskRow(it, parent = null, progress = null) }, page.total)
        }
    }

    private fun startChildObservation(rootId: UUID, limit: Int) {
        if (rootId !in mutableState.value.expandedRootIds) return
        childJobs.remove(rootId)?.cancel()
        mutableState.update {
            it.copy(
                childPageLimits = it.childPageLimits + (rootId to limit),
                loadingChildren = it.loadingChildren + rootId,
                childLoadErrors = it.childLoadErrors - rootId,
            )
        }
        val job = viewModelScope.launch {
            try {
                repository.observeChildren(rootId, limit).collect { page ->
                    if (rootId !in mutableState.value.expandedRootIds) return@collect
                    if (page.total == 0) {
                        collapseRoot(rootId)
                        return@collect
                    }
                    mutableState.update {
                        it.copy(
                            childrenByRoot = it.childrenByRoot + (rootId to page),
                            loadingChildren = it.loadingChildren - rootId,
                            childLoadErrors = it.childLoadErrors - rootId,
                        )
                    }
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutableState.update {
                    if (rootId in it.expandedRootIds) {
                        it.copy(
                            loadingChildren = it.loadingChildren - rootId,
                            childLoadErrors = it.childLoadErrors + rootId,
                        )
                    } else it
                }
            }
        }
        childJobs[rootId] = job
    }

    private fun collapseRoot(rootId: UUID) {
        childJobs.remove(rootId)?.cancel()
        val expandedIds = mutableState.value.expandedRootIds - rootId
        saveExpandedRootIds(expandedIds)
        mutableState.update {
            it.copy(
                expandedRootIds = expandedIds,
                childrenByRoot = it.childrenByRoot - rootId,
                loadingChildren = it.loadingChildren - rootId,
                childLoadErrors = it.childLoadErrors - rootId,
            )
        }
    }

    private fun readExpandedRootIds(): Set<UUID> =
        saved.get<ArrayList<String>>(KEY_EXPANDED_ROOTS).orEmpty().mapNotNull { value ->
            runCatching { UUID.fromString(value) }.getOrNull()
        }.toSet()

    private fun saveExpandedRootIds(ids: Set<UUID>) {
        saved[KEY_EXPANDED_ROOTS] = ArrayList(ids.map(UUID::toString))
    }

    private fun childPageLimitFor(rootId: UUID): Int =
        (saved.get<Int>(childPageLimitKey(rootId)) ?: TODO_DEFAULT_PAGE_SIZE).coerceAtLeast(TODO_DEFAULT_PAGE_SIZE)

    private fun childPageLimitKey(rootId: UUID): String = "$KEY_CHILD_PAGE_LIMIT_PREFIX$rootId"

    private data class ListRequest(val query: TodoListQuery, val limit: Int)

    private sealed interface ListObservation {
        data class Loaded(val page: TodoRowPage) : ListObservation
        data object Failed : ListObservation
    }

    private companion object {
        const val FILTER_ALL = -1
        const val KEY_COMPLETED = "todo_list_completed"
        const val KEY_IMPORTANCE_FILTER = "todo_list_importance_filter"
        const val KEY_IMPORTANCE_SORT = "todo_list_importance_sort"
        const val KEY_PAGE_LIMIT_PREFIX = "todo_list_page_limit_"
        const val KEY_EXPANDED_ROOTS = "todo_list_expanded_roots"
        const val KEY_CHILD_PAGE_LIMIT_PREFIX = "todo_list_child_page_limit_"
        const val KEY_PARENT_COMPLETION_OFFER = "todo_list_parent_completion_offer"
        const val KEY_OPEN_LIMIT = "todo_list_open_limit"
        const val KEY_COMPLETED_LIMIT = "todo_list_completed_limit"
        const val KEY_PENDING = "todo_list_pending"
        const val KEY_UNDO_ID = "todo_list_undo_id"
        const val KEY_UNDO_REVISION = "todo_list_undo_revision"
        const val KEY_UNDO_COMMAND = "todo_list_undo_command"
        const val KEY_UNDO_NEXT_PREFIX = "todo_list_undo_next_"
    }
}
