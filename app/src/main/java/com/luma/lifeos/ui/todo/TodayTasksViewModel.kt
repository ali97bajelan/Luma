package com.luma.lifeos.ui.todo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luma.lifeos.domain.todo.TodoConflictException
import com.luma.lifeos.domain.todo.TodoMissingException
import com.luma.lifeos.domain.todo.TodoRepository
import com.luma.lifeos.domain.todo.TodoTask
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

data class TodayTasksActionState(
    val pendingIntents: Map<UUID, TodoMutationIntent> = emptyMap(),
    val inFlight: Set<UUID> = emptySet(),
    val rowErrors: Map<UUID, TodoListRowError> = emptyMap(),
    val actionResult: TodoActionResult? = null,
)

/** Revision-safe completion actions for the small Today preview. */
class TodayTasksViewModel(
    private val repository: TodoRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        TodayTasksActionState(
            pendingIntents = readPendingIntents(),
            rowErrors = readPendingIntents().keys.associateWith { TodoListRowError.FAILED },
            actionResult = readActionResult(),
        ),
    )
    val state = mutableState.asStateFlow()

    private val mutationJobs = mutableMapOf<UUID, Job>()
    private val reconciling = mutableSetOf<UUID>()

    init {
        reconcilePendingIntents()
    }

    fun setCompleted(task: TodoTask, completed: Boolean) {
        val current = mutableState.value
        if (!completed || task.isCompleted || task.id in current.pendingIntents || task.id in current.inFlight) return
        clearActionResult()
        val intent = TodoMutationIntent(
            task.id,
            task.revision,
            UUID.randomUUID(),
            completed = true,
            title = task.title,
        )
        updatePending(current.pendingIntents + (task.id to intent))
        mutableState.update {
            it.copy(
                inFlight = it.inFlight + task.id,
                rowErrors = it.rowErrors - task.id,
            )
        }
        execute(intent)
    }

    fun retry(taskId: UUID) {
        val current = mutableState.value
        val intent = current.pendingIntents[taskId] ?: return
        if (taskId in current.inFlight) return
        mutableState.update { it.copy(inFlight = it.inFlight + taskId, rowErrors = it.rowErrors - taskId) }
        execute(intent)
    }

    fun dismissRowError(taskId: UUID) {
        mutableState.update { it.copy(rowErrors = it.rowErrors - taskId) }
    }

    fun consumeActionResult(result: TodoActionResult) {
        if (mutableState.value.actionResult != result) return
        saved.remove<String>(KEY_ACTION_RESULT)
        mutableState.update { it.copy(actionResult = null) }
    }

    /** A committed restore replaces the task dataset, so old retries and feedback must be dropped. */
    fun invalidateAfterRestore() {
        mutationJobs.values.toList().forEach { it.cancel() }
        mutationJobs.clear()
        clearPendingIntents()
        saved.remove<String>(KEY_ACTION_RESULT)
        mutableState.value = TodayTasksActionState()
    }

    private fun execute(intent: TodoMutationIntent) {
        mutationJobs[intent.taskId]?.cancel()
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val committed = repository.setCompleted(
                    intent.taskId,
                    intent.expectedRevision,
                    intent.resultingRevision,
                    completed = intent.completed,
                )
                acceptCommitted(intent, committed)
            } catch (failure: Exception) {
                if (failure is CancellationException) {
                    mutableState.update { it.copy(inFlight = it.inFlight - intent.taskId) }
                    throw failure
                }
                val latest = runCatching { repository.get(intent.taskId) }
                val live = latest.getOrNull()
                when {
                    live != null && isCommitted(intent, live) -> acceptCommitted(intent, live)
                    (latest.isSuccess && live == null) || failure is TodoMissingException ->
                        failAndRemove(intent.taskId, TodoListRowError.MISSING)
                    (live != null && live.revision != intent.expectedRevision) || failure is TodoConflictException ->
                        failAndRemove(intent.taskId, TodoListRowError.CONFLICT)
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
        saved[KEY_ACTION_RESULT] = result.encodeActionResult()
        mutableState.update {
            it.copy(
                inFlight = it.inFlight - intent.taskId,
                rowErrors = it.rowErrors - intent.taskId,
                actionResult = result,
            )
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
                        live == null -> failAndRemove(taskId, TodoListRowError.MISSING)
                        isCommitted(intent, live) -> acceptCommitted(intent, live)
                        live.revision != intent.expectedRevision -> failAndRemove(taskId, TodoListRowError.CONFLICT)
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

    private fun failAndRemove(taskId: UUID, error: TodoListRowError) {
        removePending(taskId)
        mutableState.update {
            it.copy(inFlight = it.inFlight - taskId, rowErrors = it.rowErrors + (taskId to error))
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

    private fun clearActionResult() {
        saved.remove<String>(KEY_ACTION_RESULT)
        mutableState.update { it.copy(actionResult = null) }
    }

    private fun readPendingIntents(): Map<UUID, TodoMutationIntent> =
        saved.get<ArrayList<String>>(KEY_PENDING).orEmpty().mapNotNull(::decodeIntent).associateBy { it.taskId }

    private fun encodeIntent(intent: TodoMutationIntent): String {
        val title = Base64.getUrlEncoder().withoutPadding().encodeToString(intent.title.toByteArray(Charsets.UTF_8))
        return listOf(intent.taskId, intent.expectedRevision, intent.resultingRevision, intent.completed, title)
            .joinToString("|")
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

    private fun readActionResult(): TodoActionResult? = runCatching {
        val values = requireNotNull(saved.get<String>(KEY_ACTION_RESULT)).split('|')
        require(values.size == 3)
        TodoActionResult(UUID.fromString(values[0]), values[1].toBooleanStrict(), UUID.fromString(values[2]))
    }.getOrNull()

    private companion object {
        const val KEY_PENDING = "today_todo_pending"
        const val KEY_ACTION_RESULT = "today_todo_action_result"
    }
}
