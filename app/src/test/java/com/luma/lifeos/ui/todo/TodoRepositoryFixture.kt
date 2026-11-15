package com.luma.lifeos.ui.todo

import com.luma.lifeos.domain.todo.TodoChildProgress
import com.luma.lifeos.domain.todo.TodoCompletionPreview
import com.luma.lifeos.domain.todo.TodoCompletionReceipt
import com.luma.lifeos.domain.todo.TodoConflictException
import com.luma.lifeos.domain.todo.TodoDeletePreview
import com.luma.lifeos.domain.todo.TodoImportance
import com.luma.lifeos.domain.todo.TodoInput
import com.luma.lifeos.domain.todo.TodoListQuery
import com.luma.lifeos.domain.todo.TodoMissingException
import com.luma.lifeos.domain.todo.TodoPage
import com.luma.lifeos.domain.todo.TodoParentContext
import com.luma.lifeos.domain.todo.TodoRepository
import com.luma.lifeos.domain.todo.TodoRowPage
import com.luma.lifeos.domain.todo.TodoTask
import com.luma.lifeos.domain.todo.TodoTaskRow
import com.luma.lifeos.domain.todo.completedTodoTaskComparator
import com.luma.lifeos.domain.todo.isTodoTodayEligible
import com.luma.lifeos.domain.todo.normalizeTodoInput
import com.luma.lifeos.domain.todo.openTodoTaskComparator
import com.luma.lifeos.domain.todo.todoTaskComparator
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
    private val completionPlans = mutableMapOf<UUID, CompletionPlan>()
    private val completionReceipts = mutableMapOf<UUID, TodoCompletionReceipt>()
    private val completionUndoSnapshots = mutableMapOf<UUID, CompletionUndoSnapshot>()
    private val undoReceipts = mutableMapOf<UUID, TodoCompletionReceipt>()
    private val deletePlans = mutableMapOf<UUID, Pair<UUID, Map<UUID, TodoTask>>>()

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

    override fun observeRows(query: TodoListQuery, limit: Int): Flow<TodoRowPage> {
        listRequests += ListRequest(query.completed, limit)
        return flow {
            if (listFailures > 0) {
                listFailures--
                throw IllegalStateException("list read failed")
            }
            emitAll(rows.map { current ->
                val candidates = current.values.filter { task ->
                    (query.importance != null || task.parentId == null) &&
                        task.isCompleted == query.completed &&
                        (query.importance == null || task.importance == query.importance)
                }.sortedWith(todoTaskComparator(query))
                val allChildren = current.values.groupBy(TodoTask::parentId)
                val pageItems = candidates.take(limit).map { task ->
                    val parent = task.parentId?.let(current::get)?.let { TodoParentContext(it.id, it.title) }
                    val children = allChildren[task.id].orEmpty()
                    val progress = children.takeIf { it.isNotEmpty() }?.let { family ->
                        TodoChildProgress(family.count(TodoTask::isCompleted), family.size)
                    }
                    TodoTaskRow(task, parent, progress)
                }
                TodoRowPage(pageItems, candidates.size)
            })
        }
    }

    override fun observeChildren(parentId: UUID, limit: Int): Flow<TodoRowPage> = flow {
        emitAll(rows.map { current ->
            val parent = current[parentId]
            val children = current.values.filter { it.parentId == parentId }
                .sortedBy { it.siblingPosition }
            val progress = children.takeIf { it.isNotEmpty() }?.let { family ->
                TodoChildProgress(family.count(TodoTask::isCompleted), family.size)
            }
            TodoRowPage(
                items = children.take(limit).map { child ->
                    TodoTaskRow(child, parent?.let { TodoParentContext(it.id, it.title) }, progress = null)
                },
                total = children.size,
                familyProgress = progress,
            )
        })
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

    override fun observeTodayRows(date: LocalDate, limit: Int): Flow<TodoRowPage> {
        todayRequests += TodayRequest(date, limit)
        return flow {
            if (todayFailures > 0) {
                todayFailures--
                throw IllegalStateException("today read failed")
            }
            emitAll(rows.map { current ->
                val matching = current.values.filter { isTodoTodayEligible(it, date) }
                    .sortedWith(openTodoTaskComparator)
                val allChildren = current.values.groupBy(TodoTask::parentId)
                val pageItems = matching.take(limit).map { task ->
                    val parent = task.parentId?.let(current::get)?.let { TodoParentContext(it.id, it.title) }
                    val children = allChildren[task.id].orEmpty()
                    val progress = children.takeIf { it.isNotEmpty() }?.let { family ->
                        TodoChildProgress(family.count(TodoTask::isCompleted), family.size)
                    }
                    TodoTaskRow(task, parent, progress)
                }
                TodoRowPage(pageItems, matching.size)
            })
        }
    }

    override suspend fun searchOpen(query: String, offset: Int, limit: Int): TodoPage {
        val matching = rows.value.values.filter { !it.isCompleted && it.title.contains(query, ignoreCase = true) }
            .sortedWith(openTodoTaskComparator)
        return TodoPage(matching.drop(offset).take(limit), matching.size)
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
            importance = normalized.importance,
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
            importance = normalized.importance,
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

    override suspend fun prepareCompletion(id: UUID, completed: Boolean): TodoCompletionPreview {
        val target = rows.value[id] ?: throw TodoMissingException(id)
        val root = target.parentId?.let(rows.value::get) ?: target
        val children = rows.value.values.filter { it.parentId == root.id }
        val openChildren = children.filterNot(TodoTask::isCompleted)
        val reopensParent = !completed && target.parentId != null && root.isCompleted
        val changed = when {
            completed && target.parentId == null -> buildList {
                if (!target.isCompleted) add(target)
                addAll(openChildren)
            }
            completed && !target.isCompleted -> listOf(target)
            !completed && target.isCompleted -> buildList {
                add(target)
                if (reopensParent) add(root)
            }
            else -> emptyList()
        }.distinctBy(TodoTask::id)
        val planId = UUID.randomUUID()
        completionPlans[planId] = CompletionPlan(
            planId = planId,
            targetId = id,
            completed = completed,
            previous = changed.associateBy(TodoTask::id),
            familyRevision = root.familyRevision,
        )
        val confirmationRequired = openChildren.isNotEmpty() && completed && target.parentId == null || reopensParent
        return TodoCompletionPreview(
            planId = planId,
            targetId = id,
            completed = completed,
            changedTaskCount = changed.size,
            openChildCount = openChildren.size,
            reopensParent = reopensParent,
            confirmationRequired = confirmationRequired,
        )
    }

    override suspend fun applyCompletion(planId: UUID, commandId: UUID): TodoCompletionReceipt {
        completionReceipts[commandId]?.let { return it }
        failNextCompletionBeforeCommit?.let { failure ->
            failNextCompletionBeforeCommit = null
            throw failure
        }
        val plan = completionPlans[planId] ?: throw TodoConflictException(UUID(0L, 0L))
        if (plan.previous.any { (taskId, previous) ->
                val current = rows.value[taskId]
                current?.revision != previous.revision || current.familyRevision != plan.familyRevision
            }
        ) throw TodoConflictException(plan.targetId)

        val now = Instant.parse("2026-01-01T00:00:01Z")
        val targetBefore = plan.previous[plan.targetId]
        completionCalls += CompletionCall(
            plan.targetId,
            targetBefore?.revision ?: UUID.randomUUID(),
            UUID.randomUUID(),
            plan.completed,
        )
        val nextRows = rows.value.toMutableMap()
        val after = mutableMapOf<UUID, TodoTask>()
        val familyRevision = UUID.randomUUID()
        plan.previous.forEach { (taskId, previous) ->
            val updated = previous.copy(
                completedAt = if (plan.completed) now else null,
                updatedAt = previous.updatedAt.plusSeconds(1),
                revision = UUID.randomUUID(),
                familyRevision = familyRevision,
            )
            nextRows[taskId] = updated
            after[taskId] = updated
        }
        rows.value = nextRows
        val receipt = TodoCompletionReceipt(
            commandId = commandId,
            targetId = plan.targetId,
            completed = plan.completed,
            changedTaskCount = after.size,
            undoAvailable = plan.completed && after.isNotEmpty(),
        )
        completionReceipts[commandId] = receipt
        completionUndoSnapshots[commandId] = CompletionUndoSnapshot(plan.previous, after, plan.targetId)
        failNextCompletionAfterCommit?.let { failure ->
            failNextCompletionAfterCommit = null
            throw failure
        }
        return receipt
    }

    override suspend fun completionReceipt(commandId: UUID): TodoCompletionReceipt? = completionReceipts[commandId]

    override suspend fun undoCompletion(commandId: UUID, undoCommandId: UUID): TodoCompletionReceipt {
        undoReceipts[undoCommandId]?.let { return it }
        val snapshot = completionUndoSnapshots[commandId] ?: throw TodoConflictException(UUID(0L, 0L))
        if (snapshot.after.any { (taskId, after) -> rows.value[taskId]?.revision != after.revision }) {
            throw TodoConflictException(snapshot.targetId)
        }
        val nextRows = rows.value.toMutableMap()
        val familyRevision = UUID.randomUUID()
        completionCalls += CompletionCall(
            snapshot.targetId,
            snapshot.after[snapshot.targetId]?.revision ?: UUID.randomUUID(),
            UUID.randomUUID(),
            false,
        )
        snapshot.previous.forEach { (taskId, previous) ->
            nextRows[taskId] = previous.copy(
                updatedAt = previous.updatedAt.plusSeconds(2),
                revision = UUID.randomUUID(),
                familyRevision = familyRevision,
            )
        }
        rows.value = nextRows
        return TodoCompletionReceipt(
            commandId = undoCommandId,
            targetId = snapshot.targetId,
            completed = false,
            changedTaskCount = snapshot.previous.size,
            undoAvailable = false,
        ).also { undoReceipts[undoCommandId] = it }
    }

    override suspend fun prepareDelete(id: UUID): TodoDeletePreview {
        val target = rows.value[id] ?: throw TodoMissingException(id)
        val rootId = target.parentId ?: target.id
        val family = rows.value.values.filter { it.id == rootId || it.parentId == rootId }.associateBy(TodoTask::id)
        val planId = UUID.randomUUID()
        deletePlans[planId] = rootId to family
        return TodoDeletePreview(planId, id, family.size - 1)
    }

    override suspend fun applyDelete(planId: UUID, commandId: UUID) {
        val (rootId, snapshot) = deletePlans[planId] ?: throw TodoConflictException(UUID(0L, 0L))
        if (snapshot.any { (taskId, task) -> rows.value[taskId]?.revision != task.revision }) {
            throw TodoConflictException(rootId)
        }
        rows.value = rows.value - snapshot.keys
    }

    override suspend fun releaseCommandHandle(id: UUID) = Unit

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
    private data class CompletionPlan(
        val planId: UUID,
        val targetId: UUID,
        val completed: Boolean,
        val previous: Map<UUID, TodoTask>,
        val familyRevision: UUID,
    )
    private data class CompletionUndoSnapshot(
        val previous: Map<UUID, TodoTask>,
        val after: Map<UUID, TodoTask>,
        val targetId: UUID,
    )

    private fun TodoTask.input() = TodoInput(title, note, dueDate, dueTime, importance)
}

internal fun todoFixtureTask(
    title: String,
    id: UUID = UUID.randomUUID(),
    dueDate: LocalDate? = LocalDate.parse("2026-10-20"),
    dueTime: LocalTime? = null,
    completedAt: Instant? = null,
    createdAt: Instant = Instant.parse("2026-01-01T00:00:00Z"),
    revision: UUID = UUID.randomUUID(),
    importance: TodoImportance = TodoImportance.NONE,
    parentId: UUID? = null,
    siblingPosition: Long? = null,
    familyRevision: UUID = id,
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
    importance = importance,
    parentId = parentId,
    siblingPosition = siblingPosition,
    familyRevision = familyRevision,
)
