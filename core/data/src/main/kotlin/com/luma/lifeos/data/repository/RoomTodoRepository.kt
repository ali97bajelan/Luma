package com.luma.lifeos.data.repository

import androidx.room.withTransaction
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.todo.TodoDao
import com.luma.lifeos.data.todo.toDomain
import com.luma.lifeos.data.todo.toEntity
import com.luma.lifeos.domain.todo.TodoConflictException
import com.luma.lifeos.domain.todo.TodoInput
import com.luma.lifeos.domain.todo.TodoMissingException
import com.luma.lifeos.domain.todo.TodoPage
import com.luma.lifeos.domain.todo.TodoRepository
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
    private val dao: TodoDao = database.todos()

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

    override suspend fun get(id: UUID): TodoTask? = dao.find(id.toString())?.toDomain()

    override suspend fun create(id: UUID, initialRevision: UUID, input: TodoInput): TodoTask {
        val normalized = normalizeTodoInput(input)
        return database.withTransaction {
            val key = id.toString()
            val existing = dao.find(key)
            if (existing != null) {
                val task = existing.toDomain()
                if (task.revision == initialRevision && task.matches(normalized)) return@withTransaction task
                throw TodoConflictException(id)
            }

            val entity = normalized.toEntity(id, initialRevision, clock.instant())
            dao.insert(entity)
            entity.toDomain()
        }
    }

    override suspend fun update(
        id: UUID,
        expectedRevision: UUID,
        nextRevision: UUID,
        input: TodoInput,
    ): TodoTask {
        val normalized = normalizeTodoInput(input)
        return database.withTransaction {
            val key = id.toString()
            val existing = dao.find(key) ?: throw TodoMissingException(id)
            val current = existing.toDomain()
            if (current.revision == nextRevision && current.matches(normalized)) return@withTransaction current
            if (current.revision != expectedRevision) throw TodoConflictException(id)
            require(nextRevision != expectedRevision) { "A task update must use a new revision token" }

            val updatedAt = maxOf(clock.millis(), existing.updatedAt)
            check(dao.update(
                id = key,
                expectedRevision = expectedRevision.toString(),
                nextRevision = nextRevision.toString(),
                title = normalized.title,
                note = normalized.note,
                dueDate = normalized.dueDate?.toString(),
                updatedAt = updatedAt,
            ) == 1) { "Task changed while its update was being applied" }
            requireNotNull(dao.find(key)).toDomain()
        }
    }

    override suspend fun setCompleted(
        id: UUID,
        expectedRevision: UUID,
        nextRevision: UUID,
        completed: Boolean,
    ): TodoTask = database.withTransaction {
        val key = id.toString()
        val existing = dao.find(key) ?: throw TodoMissingException(id)
        val current = existing.toDomain()
        if (current.revision == nextRevision && current.isCompleted == completed) return@withTransaction current
        if (current.revision != expectedRevision) throw TodoConflictException(id)
        if (current.isCompleted == completed) return@withTransaction current
        require(nextRevision != expectedRevision) { "A task status change must use a new revision token" }

        val now = clock.instant()
        val updatedAt = maxOf(now.toEpochMilli(), existing.updatedAt)
        val completedAt = if (completed) now.toEpochMilli() else null
        check(dao.setCompletion(
            id = key,
            expectedRevision = expectedRevision.toString(),
            nextRevision = nextRevision.toString(),
            completedAt = completedAt,
            updatedAt = updatedAt,
        ) == 1) { "Task changed while its completion was being applied" }
        requireNotNull(dao.find(key)).toDomain()
    }

    override suspend fun delete(id: UUID, expectedRevision: UUID) {
        database.withTransaction {
            val key = id.toString()
            val existing = dao.find(key) ?: return@withTransaction
            if (existing.revisionToken != expectedRevision.toString()) throw TodoConflictException(id)
            if (dao.delete(key, expectedRevision.toString()) != 1) throw TodoConflictException(id)
        }
    }

    private fun TodoTask.matches(input: TodoInput): Boolean =
        title == input.title && note == input.note && dueDate == input.dueDate
}
