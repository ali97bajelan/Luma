package com.luma.lifeos.ui.todo

import com.luma.lifeos.domain.todo.TodoConflictException
import com.luma.lifeos.domain.todo.TodoInput
import com.luma.lifeos.domain.todo.TodoMissingException
import com.luma.lifeos.domain.todo.TodoPage
import com.luma.lifeos.domain.todo.TodoRepository
import com.luma.lifeos.domain.todo.TodoTask
import com.luma.lifeos.domain.todo.completedTodoTaskComparator
import com.luma.lifeos.domain.todo.isTodoTodayEligible
import com.luma.lifeos.domain.todo.normalizeTodoInput
import com.luma.lifeos.domain.todo.openTodoTaskComparator
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** Small observable repository used by the UI state tests. */
internal class TodoRepositoryFixture(initial: List<TodoTask> = emptyList()) : TodoRepository {
    private val rows = MutableStateFlow(initial.associateBy(TodoTask::id))

    val tasks: List<TodoTask> get() = rows.value.values.toList()
    val createCalls = mutableListOf<CreateCall>()
    val updateCalls = mutableListOf<UpdateCall>()
    val completionCalls = mutableListOf<CompletionCall>()
    val deleteCalls = mutableListOf<Pair<UUID, UUID>>()
    val getCalls = mutableListOf<UUID>()
    val listRequests = mutableListOf<ListRequest>()
    val todayRequests = mutableListOf<TodayRequest>()

    var listFailures = 0
    var todayFailures = 0
    var getFailure: Exception? = null
    var failNextCreateBeforeCommit: Exception? = null
    var failNextCreateAfterCommit: Exception? = null
    var failNextUpdateBeforeCommit: Exception? = null
    var failNextUpdateAfterCommit: Exception? = null
    var failNextCompletionBeforeCommit: Exception? = null
    var failNextCompletionAfterCommit: Exception? = null
    var failNextDeleteBeforeCommit: Exception? = null
    var createStarted: CompletableDeferred<Unit>? = null
    var releaseCreate: CompletableDeferred<Unit>? = null

    override fun observeList(completed: Boolean, limit: Int): Flow<TodoPage> {
        listRequests += ListRequest(completed, limit)
        return flow {
            if (listFailures > 0) {
                listFailures--
                throw IllegalStateException("list read failed")
            }
            emitAll(rows.map { current ->
                val matching = current.values.filter { it.isCompleted == completed }
                    .sortedWith(if (completed) completedTodoTaskComparator else openTodoTaskComparator)
                TodoPage(matching.take(limit), matching.size)
            })
        }
    }

    override fun observeToday(date: LocalDate, limit: Int): Flow<TodoPage> {
        todayRequests += TodayRequest(date, limit)
        return flow {
            if (todayFailures > 0) {
                todayFailures--
                throw IllegalStateException("today read failed")
            }
            emitAll(rows.map { current ->
                val matching = current.values.filter { isTodoTodayEligible(it, date) }.sortedWith(openTodoTaskComparator)
                TodoPage(matching.take(limit), matching.size)
            })
        }
    }

    override suspend fun get(id: UUID): TodoTask? {
        getCalls += id
        getFailure?.let { failure ->
            getFailure = null
            throw failure
        }
        return rows.value[id]
    }

    override suspend fun create(id: UUID, initialRevision: UUID, input: TodoInput): TodoTask {
        createCalls += CreateCall(id, initialRevision, input)
        createStarted?.complete(Unit)
        releaseCreate?.await()
        failNextCreateBeforeCommit?.let { failure ->
            failNextCreateBeforeCommit = null
            throw failure
        }
        val normalized = normalizeTodoInput(input)
        val existing = rows.value[id]
        if (existing != null) {
            if (existing.revision == initialRevision && existing.input() == normalized) return existing
            throw TodoConflictException(id)
        }
        val created = TodoTask(
            id = id,
            title = normalized.title,
            note = normalized.note,
            dueDate = normalized.dueDate,
            dueTime = normalized.dueTime,
            completedAt = null,
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
            revision = initialRevision,
        )
        rows.value = rows.value + (id to created)
        failNextCreateAfterCommit?.let { failure ->
            failNextCreateAfterCommit = null
            throw failure
        }
        return created
    }

    override suspend fun update(id: UUID, expectedRevision: UUID, nextRevision: UUID, input: TodoInput): TodoTask {
        updateCalls += UpdateCall(id, expectedRevision, nextRevision, input)
        failNextUpdateBeforeCommit?.let { failure ->
            failNextUpdateBeforeCommit = null
            throw failure
        }
        val current = rows.value[id] ?: throw TodoMissingException(id)
        val normalized = normalizeTodoInput(input)
        if (current.revision == nextRevision && current.input() == normalized) return current
        if (current.revision != expectedRevision) throw TodoConflictException(id)
        val updated = current.copy(
            title = normalized.title,
            note = normalized.note,
            dueDate = normalized.dueDate,
            dueTime = normalized.dueTime,
            updatedAt = current.updatedAt.plusSeconds(1),
            revision = nextRevision,
        )
        rows.value = rows.value + (id to updated)
        failNextUpdateAfterCommit?.let { failure ->
            failNextUpdateAfterCommit = null
            throw failure
        }
        return updated
    }

    override suspend fun setCompleted(
        id: UUID,
        expectedRevision: UUID,
        nextRevision: UUID,
        completed: Boolean,
    ): TodoTask {
        completionCalls += CompletionCall(id, expectedRevision, nextRevision, completed)
        failNextCompletionBeforeCommit?.let { failure ->
            failNextCompletionBeforeCommit = null
            throw failure
        }
        val current = rows.value[id] ?: throw TodoMissingException(id)
        if (current.revision == nextRevision && current.isCompleted == completed) return current
        if (current.revision != expectedRevision) throw TodoConflictException(id)
        if (current.isCompleted == completed) return current
        val updated = current.copy(
            completedAt = if (completed) Instant.parse("2026-01-01T00:00:01Z") else null,
            updatedAt = current.updatedAt.plusSeconds(1),
            revision = nextRevision,
        )
        rows.value = rows.value + (id to updated)
        failNextCompletionAfterCommit?.let { failure ->
            failNextCompletionAfterCommit = null
            throw failure
        }
        return updated
    }

    override suspend fun delete(id: UUID, expectedRevision: UUID) {
        deleteCalls += id to expectedRevision
        failNextDeleteBeforeCommit?.let { failure ->
            failNextDeleteBeforeCommit = null
            throw failure
        }
        val current = rows.value[id] ?: return
        if (current.revision != expectedRevision) throw TodoConflictException(id)
        rows.value = rows.value - id
    }

    fun replace(task: TodoTask) {
        rows.value = rows.value + (task.id to task)
    }

    fun remove(taskId: UUID) {
        rows.value = rows.value - taskId
    }

    data class CreateCall(val id: UUID, val initialRevision: UUID, val input: TodoInput)
    data class UpdateCall(val id: UUID, val expectedRevision: UUID, val nextRevision: UUID, val input: TodoInput)
    data class CompletionCall(val id: UUID, val expectedRevision: UUID, val nextRevision: UUID, val completed: Boolean)
    data class ListRequest(val completed: Boolean, val limit: Int)
    data class TodayRequest(val date: LocalDate, val limit: Int)

    private fun TodoTask.input() = TodoInput(title, note, dueDate, dueTime)
}

internal fun todoFixtureTask(
    title: String,
    id: UUID = UUID.randomUUID(),
    dueDate: LocalDate? = LocalDate.parse("2026-10-20"),
    dueTime: LocalTime? = null,
    completedAt: Instant? = null,
    createdAt: Instant = Instant.parse("2026-01-01T00:00:00Z"),
    revision: UUID = UUID.randomUUID(),
) = TodoTask(
    id = id,
    title = title,
    note = null,
    dueDate = dueDate,
    completedAt = completedAt,
    createdAt = createdAt,
    updatedAt = createdAt,
    revision = revision,
    dueTime = dueTime,
)
