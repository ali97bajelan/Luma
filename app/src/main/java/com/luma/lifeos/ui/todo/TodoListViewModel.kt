package com.luma.lifeos.ui.todo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luma.lifeos.domain.todo.TODO_DEFAULT_PAGE_SIZE
import com.luma.lifeos.domain.todo.TodoConflictException
import com.luma.lifeos.domain.todo.TodoMissingException
import com.luma.lifeos.domain.todo.TodoPage
import com.luma.lifeos.domain.todo.TodoRepository
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

enum class TodoListLoadError { INITIAL, REFRESH, PAGE }
enum class TodoListRowError { FAILED, CONFLICT, MISSING }

data class TodoMutationIntent(
    val taskId: UUID,
    val expectedRevision: UUID,
    val resultingRevision: UUID,
    val completed: Boolean,
    val title: String,
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
)

/** Observed prefix, retained list selection and revision-safe row actions. */
@OptIn(ExperimentalCoroutinesApi::class)
class TodoListViewModel(
    private val repository: TodoRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private var openLimit = saved[KEY_OPEN_LIMIT] ?: TODO_DEFAULT_PAGE_SIZE
    private var completedLimit = saved[KEY_COMPLETED_LIMIT] ?: TODO_DEFAULT_PAGE_SIZE
    private val refreshVersion = MutableStateFlow(0)
    private val reconciling = mutableSetOf<UUID>()
    private val mutationJobs = mutableMapOf<UUID, Job>()
    private val undoJobs = mutableMapOf<TodoActionResult, Job>()
    private val mutableState = MutableStateFlow(
        TodoListUiState(
            completed = saved[KEY_COMPLETED] ?: false,
            pageLimit = if (saved.get<Boolean>(KEY_COMPLETED) == true) completedLimit else openLimit,
            pendingIntents = readPendingIntents(),
            rowErrors = readPendingIntents().keys.associateWith { TodoListRowError.FAILED },
            rowErrorTitles = readPendingIntents().mapValues { it.value.title },
            undoableCompletion = readUndoResult(),
        ),
    )
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                mutableState.map { it.completed to it.pageLimit }.distinctUntilChanged(),
                refreshVersion,
            ) { request, _ -> request }
                .flatMapLatest { (completed, limit) ->
                    repository.observeList(completed, limit)
                        .map<TodoPage, ListObservation> { ListObservation.Loaded(it) }
                        .catch { emit(ListObservation.Failed) }
                }
                .collect { observation ->
                    when (observation) {
                        is ListObservation.Loaded -> mutableState.update {
                            it.copy(
                                page = observation.page,
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
    }

    fun selectCompleted(completed: Boolean) {
        val current = mutableState.value
        if (current.completed == completed) return
        val limit = if (completed) completedLimit else openLimit
        saved[KEY_COMPLETED] = completed
        mutableState.value = current.copy(
            completed = completed,
            page = TodoPage(emptyList(), 0),
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
        if (current.completed) {
            completedLimit = expandedLimit
            saved[KEY_COMPLETED_LIMIT] = expandedLimit
        } else {
            openLimit = expandedLimit
            saved[KEY_OPEN_LIMIT] = expandedLimit
        }
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
        val task = current.page.items.firstOrNull { it.id == taskId } ?: return
        if (!current.dataFresh || current.inFlight.contains(taskId) || current.pendingIntents.containsKey(taskId) ||
            (current.undoing && current.undoableCompletion?.taskId == taskId) || task.isCompleted == completed
        ) return
        clearUndoResult()
        val intent = TodoMutationIntent(task.id, task.revision, UUID.randomUUID(), completed, task.title)
        updatePending(current.pendingIntents + (taskId to intent))
        mutableState.update {
            it.copy(
                inFlight = it.inFlight + taskId,
                rowErrors = it.rowErrors - taskId,
                rowErrorTitles = it.rowErrorTitles - taskId,
                undoFailed = false,
            )
        }
        execute(intent)
    }

    fun retry(taskId: UUID) {
        val current = mutableState.value
        val intent = current.pendingIntents[taskId] ?: return
        if (!current.dataFresh || current.inFlight.contains(taskId)) return
        mutableState.update {
            it.copy(
                inFlight = it.inFlight + taskId,
                rowErrors = it.rowErrors - taskId,
                rowErrorTitles = it.rowErrorTitles - taskId,
            )
        }
        execute(intent)
    }

    fun dismissRowError(taskId: UUID) {
        mutableState.update { it.copy(rowErrors = it.rowErrors - taskId, rowErrorTitles = it.rowErrorTitles - taskId) }
    }

    fun undoCompletion() {
        val result = mutableState.value.undoableCompletion ?: return
        if (!result.completed) return
        if (mutableState.value.undoing || mutableState.value.undoTerminal || result in undoJobs) return
        val undoNextKey = undoNextKey(result)
        val nextRevision = saved.get<String>(undoNextKey)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: UUID.randomUUID().also { saved[undoNextKey] = it.toString() }
        mutableState.update {
            if (it.undoableCompletion == result) it.copy(undoing = true, undoFailed = false) else it
        }
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                repository.setCompleted(result.taskId, result.resultingRevision, nextRevision, completed = false)
                clearUndoResult(result)
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
                    live?.revision == nextRevision && !live.isCompleted -> clearUndoResult(result)
                    live?.revision == result.resultingRevision && live.isCompleted -> mutableState.update {
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

    fun dismissUndo() = clearUndoResult()

    /** Clears retained operations and reloads after a committed backup replacement. */
    fun invalidateAfterRestore() {
        mutationJobs.values.toList().forEach { it.cancel() }
        mutationJobs.clear()
        undoJobs.values.toList().forEach { it.cancel() }
        undoJobs.clear()
        clearPendingIntents()
        clearUndoResult()
        openLimit = TODO_DEFAULT_PAGE_SIZE
        completedLimit = TODO_DEFAULT_PAGE_SIZE
        saved[KEY_OPEN_LIMIT] = openLimit
        saved[KEY_COMPLETED_LIMIT] = completedLimit
        saved[KEY_COMPLETED] = false
        mutableState.value = TodoListUiState(today = mutableState.value.today)
        refreshVersion.update { it + 1 }
    }

    private fun execute(intent: TodoMutationIntent) {
        mutationJobs[intent.taskId]?.cancel()
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val task = repository.setCompleted(
                    intent.taskId,
                    intent.expectedRevision,
                    intent.resultingRevision,
                    intent.completed,
                )
                acceptCommitted(intent, task)
            } catch (failure: Exception) {
                if (failure is CancellationException) {
                    mutableState.update { it.copy(inFlight = it.inFlight - intent.taskId) }
                    throw failure
                }
                val latest = runCatching { repository.get(intent.taskId) }
                val live = latest.getOrNull()
                when {
                    live != null && isCommitted(intent, live) -> acceptCommitted(intent, live)
                    latest.isSuccess && live == null || failure is TodoMissingException -> {
                        failAndRemove(intent.taskId, TodoListRowError.MISSING, intent.title)
                        refresh()
                    }
                    live != null && live.revision != intent.expectedRevision || failure is TodoConflictException -> {
                        failAndRemove(intent.taskId, TodoListRowError.CONFLICT, intent.title)
                        refresh()
                    }
                    else -> mutableState.update {
                        it.copy(
                            inFlight = it.inFlight - intent.taskId,
                            rowErrors = it.rowErrors + (intent.taskId to TodoListRowError.FAILED),
                        )
                    }
                }
            } finally {
                if (mutationJobs[intent.taskId] === coroutineContext[Job]) mutationJobs.remove(intent.taskId)
            }
        }
        mutationJobs[intent.taskId] = job
        job.start()
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
        previous?.let { saved.remove<String>(undoNextKey(it)) }
        if (result == null) {
            saved.remove<String>(KEY_UNDO_ID)
            saved.remove<String>(KEY_UNDO_REVISION)
        } else {
            saved[KEY_UNDO_ID] = result.taskId.toString()
            saved[KEY_UNDO_REVISION] = result.resultingRevision.toString()
        }
    }

    private fun clearUndoResult(expected: TodoActionResult? = null) {
        val current = mutableState.value.undoableCompletion
        if (expected != null && current != expected) return
        if (current != null) saved.remove<String>(undoNextKey(current))
        saveUndoResult(null)
        mutableState.update { it.copy(undoableCompletion = null, undoing = false, undoFailed = false, undoTerminal = false) }
    }

    private fun undoNextKey(result: TodoActionResult) =
        "$KEY_UNDO_NEXT_PREFIX${result.taskId}_${result.resultingRevision}"

    private fun readUndoResult(): TodoActionResult? {
        val id = saved.get<String>(KEY_UNDO_ID)?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return null
        val revision = saved.get<String>(KEY_UNDO_REVISION)?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return null
        return TodoActionResult(id, true, revision)
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

    private sealed interface ListObservation {
        data class Loaded(val page: TodoPage) : ListObservation
        data object Failed : ListObservation
    }

    private companion object {
        const val KEY_COMPLETED = "todo_list_completed"
        const val KEY_OPEN_LIMIT = "todo_list_open_limit"
        const val KEY_COMPLETED_LIMIT = "todo_list_completed_limit"
        const val KEY_PENDING = "todo_list_pending"
        const val KEY_UNDO_ID = "todo_list_undo_id"
        const val KEY_UNDO_REVISION = "todo_list_undo_revision"
        const val KEY_UNDO_NEXT_PREFIX = "todo_list_undo_next_"
    }
}
