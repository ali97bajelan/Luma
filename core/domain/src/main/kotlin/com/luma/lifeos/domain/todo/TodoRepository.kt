package com.luma.lifeos.domain.todo

import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/** A write could not be applied because the row no longer matches the operation's revision. */
class TodoConflictException(val taskId: UUID) : IllegalStateException("Task revision conflict: $taskId")

/** An update or completion change targeted a task that no longer exists. */
class TodoMissingException(val taskId: UUID) : NoSuchElementException("Task does not exist: $taskId")

/**
 * Task reads and revision-checked writes.
 *
 * A successful create stores `initialRevision`. Repeating that create is idempotent only while the
 * existing row still has that revision and the normalized input matches; any other row with the
 * same ID conflicts. Update and completion writes atomically compare `expectedRevision` and, when
 * they change the row, store `nextRevision`. Retrying an already committed operation succeeds if
 * the row has `nextRevision` and the submitted values match. A stale operation otherwise
 * conflicts. A same-state completion request using the current revision is a no-op: it preserves
 * the completion timestamp and revision.
 *
 * [get] returns null for a missing ID. [update] and [setCompleted] throw
 * [TodoMissingException] for a missing ID; [delete] treats a missing ID as success. Delete of an
 * existing row compares its revision atomically and conflicts if it changed.
 */
interface TodoRepository {
    fun observeList(completed: Boolean, limit: Int = TODO_DEFAULT_PAGE_SIZE): Flow<TodoPage>
    fun observeToday(date: LocalDate, limit: Int = TODO_DEFAULT_TODAY_LIMIT): Flow<TodoPage>
    suspend fun get(id: UUID): TodoTask?
    suspend fun create(id: UUID, initialRevision: UUID, input: TodoInput): TodoTask
    suspend fun update(id: UUID, expectedRevision: UUID, nextRevision: UUID, input: TodoInput): TodoTask
    suspend fun setCompleted(
        id: UUID,
        expectedRevision: UUID,
        nextRevision: UUID,
        completed: Boolean,
    ): TodoTask
    suspend fun delete(id: UUID, expectedRevision: UUID)
}
