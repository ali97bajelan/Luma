package com.luma.lifeos.data.repository

import androidx.room.withTransaction
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.todo.toDomain
import com.luma.lifeos.data.todo.toEntity
import com.luma.lifeos.data.todo.toDomainOrNull
import com.luma.lifeos.domain.todo.TODO_DEFAULT_PAGE_SIZE
import com.luma.lifeos.domain.todo.TodoInput
import com.luma.lifeos.domain.todo.TodoListQuery
import com.luma.lifeos.domain.todo.TodoPage
import com.luma.lifeos.domain.todo.TodoRepository
import com.luma.lifeos.domain.todo.TodoRowPage
import com.luma.lifeos.domain.todo.TodoSort
import com.luma.lifeos.domain.todo.TodoTask
import com.luma.lifeos.domain.todo.normalizeTodoInput
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomTodoRepository(
    private val database: LumaDatabase,
    private val clock: Clock = Clock.systemUTC(),
) : TodoRepository {
    private val dao = database.todos()
    private val familyCommands = RoomTodoFamilyCommands(database, clock)

    override fun observeList(completed: Boolean, limit: Int): Flow<TodoPage> {
        require(limit > 0) { "Task page size must be positive" }
        val rows = if (completed) dao.observeCompleted(limit) else dao.observeOpen(limit)
        return rows.map { pageRows ->
            TodoPage(
                items = pageRows.mapNotNull { it.toEntityOrNull()?.toDomain() },
                total = pageRows.firstOrNull()?.totalCount ?: 0,
            )
        }
    }

    override fun observeList(query: TodoListQuery, limit: Int): Flow<TodoPage> {
        require(limit > 0) { "Task page size must be positive" }
        return dao.observeFiltered(
            completed = query.completed,
            importanceCode = query.importance?.code,
            sortByImportance = query.sort == TodoSort.IMPORTANCE,
            limit = limit,
        ).map { pageRows ->
            TodoPage(
                items = pageRows.mapNotNull { it.toEntityOrNull()?.toDomain() },
                total = pageRows.firstOrNull()?.totalCount ?: 0,
            )
        }
    }

    override fun observeRows(query: TodoListQuery, limit: Int): Flow<TodoRowPage> {
        require(limit > 0) { "Task page size must be positive" }
        return dao.observeRows(
            completed = query.completed,
            importanceCode = query.importance?.code,
            sortByImportance = query.sort == TodoSort.IMPORTANCE,
            limit = minOf(limit, TODO_DEFAULT_PAGE_SIZE),
        ).map { rows ->
            TodoRowPage(
                items = rows.mapNotNull { it.toDomainOrNull() },
                total = rows.firstOrNull()?.totalCount ?: 0,
            )
        }
    }

    override fun observeChildren(parentId: UUID, limit: Int): Flow<TodoRowPage> {
        require(limit > 0) { "Task page size must be positive" }
        return dao.observeChildren(parentId.toString(), minOf(limit, TODO_DEFAULT_PAGE_SIZE))
            .map { rows ->
                TodoRowPage(
                    items = rows.mapNotNull { it.toDomainOrNull() },
                    total = rows.firstOrNull()?.totalCount ?: 0,
                )
            }
    }

    override fun observeToday(date: LocalDate, limit: Int): Flow<TodoPage> {
        require(limit > 0) { "Task page size must be positive" }
        require(date.year in 1..9999) { "Task date must be in years 0001–9999" }
        return dao.observeToday(date.toString(), limit).map { pageRows ->
            TodoPage(
                items = pageRows.mapNotNull { it.toEntityOrNull()?.toDomain() },
                total = pageRows.firstOrNull()?.totalCount ?: 0,
            )
        }
    }

    override fun observeTodayRows(date: LocalDate, limit: Int): Flow<TodoRowPage> {
        require(limit > 0) { "Task page size must be positive" }
        require(date.year in 1..9999) { "Task date must be in years 0001–9999" }
        return dao.observeTodayRows(date.toString(), minOf(limit, TODO_DEFAULT_PAGE_SIZE))
            .map { rows ->
                TodoRowPage(
                    items = rows.mapNotNull { it.toDomainOrNull() },
                    total = rows.firstOrNull()?.totalCount ?: 0,
                )
            }
    }

    override suspend fun searchOpen(query: String, offset: Int, limit: Int): TodoPage {
        require(offset >= 0) { "Task search offset cannot be negative" }
        require(limit > 0) { "Task search page size must be positive" }
        val escapedQuery = query.trim().escapeLikeWildcards()
        val boundedLimit = minOf(limit, TODO_DEFAULT_PAGE_SIZE)
        return database.withTransaction {
            val total = dao.countOpenSearch(escapedQuery)
            val items = dao.searchOpenPage(escapedQuery, offset, boundedLimit)
                .mapNotNull { it.toEntityOrNull()?.toDomain() }
            TodoPage(items = items, total = total)
        }
    }

    override suspend fun searchOpenRows(query: String, offset: Int, limit: Int): TodoRowPage {
        require(offset >= 0) { "Task search offset cannot be negative" }
        require(limit > 0) { "Task search page size must be positive" }
        val escapedQuery = query.trim().escapeLikeWildcards()
        val boundedLimit = minOf(limit, TODO_DEFAULT_PAGE_SIZE)
        return database.withTransaction {
            val total = dao.countOpenSearchRows(escapedQuery)
            val items = dao.searchOpenRowsPage(escapedQuery, offset, boundedLimit)
                .mapNotNull { it.toDomainOrNull() }
            TodoRowPage(items = items, total = total)
        }
    }

    override suspend fun get(id: UUID): TodoTask? = dao.find(id.toString())?.toDomain()

    override suspend fun create(id: UUID, initialRevision: UUID, input: TodoInput): TodoTask {
        val normalized = normalizeTodoInput(input)
        return database.withTransaction {
            val key = id.toString()
            val existing = dao.find(key)
            if (existing != null) {
                val task = existing.toDomain()
                if (task.parentId == null && task.revision == initialRevision && task.matches(normalized)) {
                    return@withTransaction task
                }
                throw com.luma.lifeos.domain.todo.TodoConflictException(id)
            }

            val entity = normalized.toEntity(id, initialRevision, clock.instant())
            dao.insert(entity)
            entity.toDomain()
        }
    }

    override suspend fun createChild(
        id: UUID,
        initialRevision: UUID,
        parentId: UUID,
        input: TodoInput,
    ): TodoTask = familyCommands.createChild(id, initialRevision, parentId, input)

    override suspend fun moveChild(
        id: UUID,
        expectedRevision: UUID,
        nextRevision: UUID,
        direction: com.luma.lifeos.domain.todo.TodoMoveDirection,
    ) = familyCommands.moveChild(id, expectedRevision, nextRevision, direction)

    override suspend fun update(
        id: UUID,
        expectedRevision: UUID,
        nextRevision: UUID,
        input: TodoInput,
    ): TodoTask = familyCommands.update(id, expectedRevision, nextRevision, input)

    override suspend fun setCompleted(
        id: UUID,
        expectedRevision: UUID,
        nextRevision: UUID,
        completed: Boolean,
    ): TodoTask = familyCommands.setCompleted(id, expectedRevision, nextRevision, completed)

    override suspend fun delete(id: UUID, expectedRevision: UUID) {
        familyCommands.delete(id, expectedRevision)
    }

    override suspend fun prepareCompletion(id: UUID, completed: Boolean) =
        familyCommands.prepareCompletion(id, completed)

    override suspend fun applyCompletion(planId: UUID, commandId: UUID) =
        familyCommands.applyCompletion(planId, commandId)

    override suspend fun undoCompletion(commandId: UUID, undoCommandId: UUID) =
        familyCommands.undoCompletion(commandId, undoCommandId)

    override suspend fun completionReceipt(commandId: UUID) =
        familyCommands.completionReceipt(commandId)

    override suspend fun prepareDelete(id: UUID) = familyCommands.prepareDelete(id)

    override suspend fun applyDelete(planId: UUID, commandId: UUID) {
        familyCommands.applyDelete(planId, commandId)
    }

    override suspend fun releaseCommandHandle(id: UUID) {
        familyCommands.releaseCommandHandle(id)
    }

    private fun String.escapeLikeWildcards(): String = buildString(length) {
        for (character in this@escapeLikeWildcards) {
            if (character == '\\' || character == '%' || character == '_') append('\\')
            append(character)
        }
    }

    private fun TodoTask.matches(input: TodoInput): Boolean =
        title == input.title && note == input.note && dueDate == input.dueDate &&
            dueTime == input.dueTime && importance == input.importance
}
