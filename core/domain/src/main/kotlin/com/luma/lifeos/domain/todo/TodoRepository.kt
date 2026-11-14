package com.luma.lifeos.domain.todo

import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/** A write could not be applied because the row no longer matches the operation's revision. */
class TodoConflictException(val taskId: UUID) : IllegalStateException("Task revision conflict: $taskId")

/** An update or completion change targeted a task that no longer exists. */
class TodoMissingException(val taskId: UUID) : NoSuchElementException("Task does not exist: $taskId")

/** A legacy single-row operation cannot safely apply the requested family-wide change. */
class TodoConfirmationRequiredException(
    val taskId: UUID,
    val action: TodoFamilyAction,
    val affectedTaskCount: Int,
) : IllegalStateException("Task family confirmation required for $taskId ($action)") {
    init {
        require(affectedTaskCount > 0) { "A confirmation must affect at least one task" }
    }
}

private fun unsupportedTodoFamilyOperation(): Nothing =
    throw UnsupportedOperationException("This TodoRepository implementation does not support task families")

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
 *
 * Family implementations own a separate root family revision, rotated by every member
 * content/status/order/membership mutation. A child content edit compares its own task revision;
 * previews additionally capture the family revision. Command UUIDs make completion, Undo and
 * deletion retries idempotent, and a same-state completion preview is a no-op with no Undo.
 * Legacy implementations fail closed for family APIs until they provide the transactional
 * contract; they must never fall back to a single-row mutation for a family-wide change.
 */
interface TodoRepository {
    fun observeList(completed: Boolean, limit: Int = TODO_DEFAULT_PAGE_SIZE): Flow<TodoPage>
    fun observeToday(date: LocalDate, limit: Int = TODO_DEFAULT_TODAY_LIMIT): Flow<TodoPage>

    /**
     * Observe roots for an unfiltered query, or independent matching tasks when importance is set.
     * Implementations must filter, sort and count before applying the bounded page. This default
     * fails closed until the implementation provides the family-aware query.
     */
    fun observeRows(
        query: TodoListQuery,
        limit: Int = TODO_DEFAULT_PAGE_SIZE,
    ): Flow<TodoRowPage> = unsupportedTodoFamilyOperation()

    /** Observe every child status in sibling order; total covers the full family. */
    fun observeChildren(parentId: UUID, limit: Int = TODO_DEFAULT_PAGE_SIZE): Flow<TodoRowPage> =
        unsupportedTodoFamilyOperation()

    /** Observe independently eligible tasks for the date with parent context and a bounded prefix. */
    fun observeTodayRows(date: LocalDate, limit: Int = TODO_DEFAULT_PAGE_SIZE): Flow<TodoRowPage> =
        unsupportedTodoFamilyOperation()

    /** Literal open-title search with batched parent context and stable bounded paging. */
    suspend fun searchOpenRows(
        query: String,
        offset: Int,
        limit: Int = TODO_DEFAULT_PAGE_SIZE,
    ): TodoRowPage = unsupportedTodoFamilyOperation()

    /** Literal title search over open tasks, returned in stable 50-row pages. */
    suspend fun searchOpen(query: String, offset: Int, limit: Int = TODO_DEFAULT_PAGE_SIZE): TodoPage
    /** Read-only lookup; transaction-sensitive actions must revalidate task state at write time. */
    suspend fun get(id: UUID): TodoTask?
    suspend fun create(id: UUID, initialRevision: UUID, input: TodoInput): TodoTask

    /**
     * Create a real child after validating the open root, root depth, and append position inside the
     * family transaction. The child receives independent content fields and the root family guard.
     */
    suspend fun createChild(
        id: UUID,
        initialRevision: UUID,
        parentId: UUID,
        input: TodoInput,
    ): TodoTask = unsupportedTodoFamilyOperation()

    /**
     * Swap one child with its adjacent sibling after revision validation. An edge move is a no-op;
     * a real swap changes both child revisions and the shared family guard.
     */
    suspend fun moveChild(
        id: UUID,
        expectedRevision: UUID,
        nextRevision: UUID,
        direction: TodoMoveDirection,
    ): Unit = unsupportedTodoFamilyOperation()

    suspend fun update(id: UUID, expectedRevision: UUID, nextRevision: UUID, input: TodoInput): TodoTask
    suspend fun setCompleted(
        id: UUID,
        expectedRevision: UUID,
        nextRevision: UUID,
        completed: Boolean,
    ): TodoTask
    suspend fun delete(id: UUID, expectedRevision: UUID)

    /**
     * Snapshot the family revision and exact rows that a completion/reopen action may change.
     * Same-state requests produce a no-op preview with no affected rows.
     */
    suspend fun prepareCompletion(id: UUID, completed: Boolean): TodoCompletionPreview =
        unsupportedTodoFamilyOperation()

    /**
     * Apply a previously confirmed completion preview with a retained idempotent command identity.
     * A reused ID with a different payload or stale family snapshot conflicts without mutation.
     */
    suspend fun applyCompletion(planId: UUID, commandId: UUID): TodoCompletionReceipt =
        unsupportedTodoFamilyOperation()

    /** Atomically undo only the rows changed by the named completion command. */
    suspend fun undoCompletion(commandId: UUID, undoCommandId: UUID): TodoCompletionReceipt =
        unsupportedTodoFamilyOperation()

    /** Read a committed completion receipt after process recreation without replaying the command. */
    suspend fun completionReceipt(commandId: UUID): TodoCompletionReceipt? =
        unsupportedTodoFamilyOperation()

    /** Snapshot the full family before a destructive delete confirmation. */
    suspend fun prepareDelete(id: UUID): TodoDeletePreview = unsupportedTodoFamilyOperation()

    /** Delete the exact confirmed family snapshot atomically. */
    suspend fun applyDelete(planId: UUID, commandId: UUID): Unit = unsupportedTodoFamilyOperation()

    /** Release a no-longer-referenced preview, retry or Undo handle. */
    suspend fun releaseCommandHandle(id: UUID): Unit = unsupportedTodoFamilyOperation()
}
