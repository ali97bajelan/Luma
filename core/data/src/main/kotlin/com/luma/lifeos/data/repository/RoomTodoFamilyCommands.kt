package com.luma.lifeos.data.repository

import androidx.room.withTransaction
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.todo.TodoCommandEntity
import com.luma.lifeos.data.todo.TodoCommandKind
import com.luma.lifeos.data.todo.TodoCommandMemberEntity
import com.luma.lifeos.data.todo.TodoCommandState
import com.luma.lifeos.data.todo.TodoEntity
import com.luma.lifeos.data.todo.toDomain
import com.luma.lifeos.data.todo.toEntity
import com.luma.lifeos.data.widget.encodeWidgetActionResult
import com.luma.lifeos.domain.widget.WidgetActionResult
import com.luma.lifeos.domain.widget.WidgetOutcome
import com.luma.lifeos.domain.todo.TodoCompletionPreview
import com.luma.lifeos.domain.todo.TodoCompletionReceipt
import com.luma.lifeos.domain.todo.TodoConflictException
import com.luma.lifeos.domain.todo.TodoConfirmationRequiredException
import com.luma.lifeos.domain.todo.TodoDeletePreview
import com.luma.lifeos.domain.todo.TodoFamilyAction
import com.luma.lifeos.domain.todo.TodoInput
import com.luma.lifeos.domain.todo.TodoMissingException
import com.luma.lifeos.domain.todo.TodoMoveDirection
import com.luma.lifeos.domain.todo.TodoTask
import com.luma.lifeos.domain.todo.normalizeTodoInput
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * Serializes every task-family mutation through the same Room database transaction. Preview and
 * receipt payloads contain only IDs, revisions and status timestamps, never task titles.
 */
internal class RoomTodoFamilyCommands(
    private val database: LumaDatabase,
    private val clock: Clock,
) {
    private val tasks = database.todos()
    private val commands = database.todoCommands()

    suspend fun update(
        id: UUID,
        expectedRevision: UUID,
        nextRevision: UUID,
        input: TodoInput,
    ): TodoTask {
        val normalized = normalizeTodoInput(input)
        return database.withTransaction {
            val key = id.toString()
            val existing = tasks.find(key) ?: throw TodoMissingException(id)
            val current = existing.toDomain()
            if (current.revision == nextRevision && current.matches(normalized)) return@withTransaction current
            if (current.revision != expectedRevision) throw TodoConflictException(id)
            require(nextRevision != expectedRevision) { "A task update must use a new revision token" }

            val (_, family) = loadFamily(existing)
            val familyRevision = existing.familyRevisionToken
            val nextFamilyRevision = UUID.randomUUID().toString()
            check(tasks.update(
                id = key,
                expectedRevision = expectedRevision.toString(),
                nextRevision = nextRevision.toString(),
                title = normalized.title,
                note = normalized.note,
                dueDate = normalized.dueDate?.toString(),
                dueTimeMinutes = normalized.dueTime?.let { it.hour * 60 + it.minute },
                importanceCode = normalized.importance.code,
                updatedAt = monotonicMillis(existing),
            ) == 1) { "Task changed while its update was being applied" }
            rotateFamily(existing.parentId ?: existing.id, familyRevision, nextFamilyRevision, family.size)
            database.taskReminders().onTaskRevisionChanged(
                key,
                nextRevision.toString(),
                UUID.randomUUID().toString(),
            )
            requireNotNull(tasks.find(key)).toDomain()
        }
    }

    suspend fun createChild(
        id: UUID,
        initialRevision: UUID,
        parentId: UUID,
        input: TodoInput,
    ): TodoTask {
        val normalized = normalizeTodoInput(input)
        return database.withTransaction {
            val parent = tasks.find(parentId.toString()) ?: throw TodoMissingException(parentId)
            if (parent.parentId != null || parent.completedAt != null) throw TodoConflictException(parentId)

            val key = id.toString()
            val existing = tasks.find(key)
            if (existing != null) {
                val task = existing.toDomain()
                if (task.parentId == parentId && task.revision == initialRevision && task.matches(normalized)) {
                    return@withTransaction task
                }
                throw TodoConflictException(id)
            }

            val family = tasks.family(parent.id)
            validateFamily(parent, family)
            val lastPosition = family.asSequence()
                .filter { it.parentId != null }
                .maxOfOrNull { requireNotNull(it.siblingPosition) } ?: -1L
            val position = Math.addExact(lastPosition, 1L)
            val nextFamilyRevision = UUID.randomUUID().toString()
            val child = normalized.toEntity(
                id = id,
                revision = initialRevision,
                now = clock.instant(),
                parentId = parentId,
                siblingPosition = position,
                familyRevision = UUID.fromString(parent.familyRevisionToken),
            )
            tasks.insert(child)
            rotateFamily(parent.id, parent.familyRevisionToken, nextFamilyRevision, family.size + 1)
            requireNotNull(tasks.find(key)).toDomain()
        }
    }

    suspend fun moveChild(
        id: UUID,
        expectedRevision: UUID,
        nextRevision: UUID,
        direction: TodoMoveDirection,
    ) {
        database.withTransaction {
            val key = id.toString()
            val child = tasks.find(key) ?: throw TodoMissingException(id)
            val parentId = child.parentId?.let(UUID::fromString) ?: throw TodoConflictException(id)
            if (child.revisionToken == nextRevision.toString()) return@withTransaction
            if (child.revisionToken != expectedRevision.toString()) throw TodoConflictException(id)
            val (root, family) = loadFamily(child)
            val ordered = family.filter { it.parentId == root.id }.sortedWith(
                compareBy<TodoEntity> { it.siblingPosition }.thenBy { it.id },
            )
            val index = ordered.indexOfFirst { it.id == child.id }
            if (index < 0) throw TodoConflictException(id)
            val siblingIndex = when (direction) {
                TodoMoveDirection.UP -> index - 1
                TodoMoveDirection.DOWN -> index + 1
            }
            if (siblingIndex !in ordered.indices) return@withTransaction
            require(nextRevision != expectedRevision) { "A child move must use a new revision token" }

            val sibling = ordered[siblingIndex]
            val childPosition = requireNotNull(child.siblingPosition)
            val siblingPosition = requireNotNull(sibling.siblingPosition)
            val temporaryPosition = Math.addExact(
                ordered.maxOf { requireNotNull(it.siblingPosition) },
                1L,
            )
            val now = clock.instant()
            val childNextRevision = nextRevision.toString()
            val siblingNextRevision = UUID.randomUUID().toString()

            check(tasks.updateSiblingPosition(
                id = child.id,
                expectedRevision = child.revisionToken,
                nextRevision = childNextRevision,
                position = temporaryPosition,
                updatedAt = maxOf(now.toEpochMilli(), child.updatedAt),
            ) == 1) { "Task changed while its sibling position was being updated" }
            check(tasks.updateSiblingPosition(
                id = sibling.id,
                expectedRevision = sibling.revisionToken,
                nextRevision = siblingNextRevision,
                position = childPosition,
                updatedAt = maxOf(now.toEpochMilli(), sibling.updatedAt),
            ) == 1) { "Sibling changed while its position was being updated" }
            check(tasks.updateSiblingPosition(
                id = child.id,
                expectedRevision = childNextRevision,
                nextRevision = childNextRevision,
                position = siblingPosition,
                updatedAt = maxOf(now.toEpochMilli(), child.updatedAt),
            ) == 1) { "Task changed while its sibling position was being finalized" }

            rotateFamily(root.id, root.familyRevisionToken, UUID.randomUUID().toString(), family.size)
            database.taskReminders().onTaskRevisionChanged(
                child.id,
                childNextRevision,
                UUID.randomUUID().toString(),
            )
            database.taskReminders().onTaskRevisionChanged(
                sibling.id,
                siblingNextRevision,
                UUID.randomUUID().toString(),
            )
            check(parentId.toString() == child.parentId) { "Sibling move crossed task families" }
        }
    }

    suspend fun setCompleted(
        id: UUID,
        expectedRevision: UUID,
        nextRevision: UUID,
        completed: Boolean,
    ): TodoTask = database.withTransaction {
        val key = id.toString()
        val existing = tasks.find(key) ?: throw TodoMissingException(id)
        val current = existing.toDomain()
        if (current.revision == nextRevision && current.isCompleted == completed) return@withTransaction current
        if (current.revision != expectedRevision) throw TodoConflictException(id)
        if (current.isCompleted == completed) return@withTransaction current

        val (root, family) = loadFamily(existing)
        if (completed && existing.parentId == null) {
            val openChildren = family.count { it.parentId == existing.id && it.completedAt == null }
            if (openChildren > 0) {
                throw TodoConfirmationRequiredException(
                    id,
                    TodoFamilyAction.COMPLETE_FAMILY,
                    openChildren + 1,
                )
            }
        }
        if (!completed && existing.parentId != null && root.completedAt != null) {
            throw TodoConfirmationRequiredException(
                id,
                TodoFamilyAction.REOPEN_PARENT_WITH_CHILD,
                2,
            )
        }
        require(nextRevision != expectedRevision) { "A task status change must use a new revision token" }

        val now = clock.instant()
        updateCompletionRow(
            existing,
            completedAt = if (completed) now.toEpochMilli() else null,
            nextRevision = nextRevision,
            now = now,
        )
        rotateFamily(root.id, root.familyRevisionToken, UUID.randomUUID().toString(), family.size)
        database.taskReminders().onTaskRevisionChanged(
            key,
            nextRevision.toString(),
            UUID.randomUUID().toString(),
        )
        requireNotNull(tasks.find(key)).toDomain()
    }

    suspend fun delete(id: UUID, expectedRevision: UUID) {
        database.withTransaction {
            val key = id.toString()
            val existing = tasks.find(key) ?: return@withTransaction
            if (existing.revisionToken != expectedRevision.toString()) throw TodoConflictException(id)
            val (root, family) = loadFamily(existing)
            if (existing.parentId == null && family.size > 1) {
                throw TodoConfirmationRequiredException(
                    id,
                    TodoFamilyAction.DELETE_FAMILY,
                    family.size,
                )
            }

            invalidateWidgetCommands(listOf(existing.id))
            unlinkFocusSessions(listOf(existing.id))
            if (tasks.delete(key, expectedRevision.toString()) != 1) throw TodoConflictException(id)
            if (existing.parentId != null) {
                rotateFamily(
                    root.id,
                    root.familyRevisionToken,
                    UUID.randomUUID().toString(),
                    family.size - 1,
                )
            }
        }
    }

    suspend fun prepareCompletion(id: UUID, completed: Boolean): TodoCompletionPreview {
        return database.withTransaction {
            pruneJournal()
            val target = tasks.find(id.toString()) ?: throw TodoMissingException(id)
            val (root, family) = loadFamily(target)
            val openChildren = family.count { it.parentId == root.id && it.completedAt == null }
            val reopensParent = !completed && target.parentId != null && root.completedAt != null
            val willChange = when {
                (target.completedAt != null) == completed && !reopensParent -> emptySet()
                reopensParent -> setOf(target.id, root.id)
                completed && target.parentId == null ->
                    buildSet {
                        add(root.id)
                        family.filter { it.parentId == root.id && it.completedAt == null }.forEach { add(it.id) }
                    }
                else -> setOf(target.id)
            }
            val confirmationRequired =
                (completed && target.parentId == null && openChildren > 0) || reopensParent
            val planId = UUID.randomUUID().toString()
            val now = clock.millis()
            commands.insert(
                TodoCommandEntity(
                    id = planId,
                    kind = TodoCommandKind.COMPLETION,
                    state = TodoCommandState.PREPARED,
                    targetId = target.id,
                    rootId = root.id,
                    completed = completed,
                    familyRevisionBefore = root.familyRevisionToken,
                    familyRevisionAfter = null,
                    processToken = ProcessSession.token,
                    commandId = null,
                    sourceCommandId = null,
                    childCount = family.size - 1,
                    changedTaskCount = willChange.size,
                    openChildCount = if (completed && target.parentId == null) openChildren else 0,
                    reopensParent = reopensParent,
                    confirmationRequired = confirmationRequired,
                    undoAvailable = false,
                    pinned = true,
                    createdAt = now,
                    terminalAt = null,
                ),
            )
            commands.insertMembers(family.map { member ->
                TodoCommandMemberEntity(
                    commandId = planId,
                    taskId = member.id,
                    expectedRevision = member.revisionToken,
                    previousCompletedAt = member.completedAt,
                    willChange = member.id in willChange,
                )
            })
            TodoCompletionPreview(
                planId = UUID.fromString(planId),
                targetId = id,
                completed = completed,
                changedTaskCount = willChange.size,
                openChildCount = if (completed && target.parentId == null) openChildren else 0,
                reopensParent = reopensParent,
                confirmationRequired = confirmationRequired,
            )
        }
    }

    suspend fun applyCompletion(planId: UUID, commandId: UUID): TodoCompletionReceipt {
        try {
            return database.withTransaction {
                pruneJournal()
                val commandIdText = commandId.toString()
                val previous = commands.findByCommandId(commandIdText)
                if (previous != null) {
                    if (previous.id != planId.toString() ||
                        previous.kind != TodoCommandKind.COMPLETION ||
                        previous.state != TodoCommandState.APPLIED
                    ) {
                        throw TodoConflictException(UUID.fromString(previous.targetId))
                    }
                    val root = tasks.find(previous.rootId)
                    if (root?.familyRevisionToken != previous.familyRevisionAfter) {
                        throw TodoConflictException(UUID.fromString(previous.targetId))
                    }
                    val refreshed = previous.copy(
                        processToken = ProcessSession.token,
                        pinned = true,
                        terminalAt = clock.millis(),
                    )
                    check(commands.update(refreshed) == 1) { "Completion receipt changed during retry" }
                    return@withTransaction refreshed.toCompletionReceipt()
                }

                val command = commands.find(planId.toString())
                    ?: throw TodoConflictException(planId)
                if (command.kind != TodoCommandKind.COMPLETION ||
                    command.state != TodoCommandState.PREPARED ||
                    command.commandId != null
                ) {
                    throw TodoConflictException(UUID.fromString(command.targetId))
                }
                val family = revalidatePrepared(command)
                val affected = commands.members(command.id).filter { it.willChange }
                val now = clock.instant()
                val completed = requireNotNull(command.completed)
                val ordered = if (completed) {
                    affected.sortedBy { if (it.taskId == command.rootId) 1 else 0 }
                } else {
                    affected.sortedBy { if (it.taskId == command.rootId) 0 else 1 }
                }
                val resultMembers = mutableListOf<TodoCommandMemberEntity>()
                for (member in ordered) {
                    val entity = requireNotNull(tasks.find(member.taskId))
                    val revision = UUID.randomUUID()
                    val completedAt = if (completed) now.toEpochMilli() else null
                    updateCompletionRow(entity, completedAt, revision, now)
                    database.taskReminders().onTaskRevisionChanged(
                        entity.id,
                        revision.toString(),
                        UUID.randomUUID().toString(),
                    )
                    resultMembers += member.copy(
                        resultingRevision = revision.toString(),
                        resultingCompletedAt = completedAt,
                    )
                }

                val nextFamilyRevision = if (affected.isEmpty()) {
                    command.familyRevisionBefore
                } else {
                    UUID.randomUUID().toString().also {
                        rotateFamily(command.rootId, command.familyRevisionBefore, it, family.size)
                    }
                }
                if (resultMembers.isNotEmpty()) {
                    check(commands.updateMembers(resultMembers) == resultMembers.size) {
                        "Completion command snapshot changed during commit"
                    }
                }
                val updated = command.copy(
                    state = TodoCommandState.APPLIED,
                    familyRevisionAfter = nextFamilyRevision,
                    processToken = ProcessSession.token,
                    commandId = commandIdText,
                    changedTaskCount = affected.size,
                    undoAvailable = affected.isNotEmpty(),
                    terminalAt = clock.millis(),
                )
                check(commands.update(updated) == 1) { "Completion receipt changed during commit" }
                updated.toCompletionReceipt()
            }
        } catch (stale: StaleTodoCommandPreview) {
            markStale(stale.commandId)
            throw TodoConflictException(stale.targetId)
        }
    }

    suspend fun undoCompletion(commandId: UUID, undoCommandId: UUID): TodoCompletionReceipt {
        try {
            return database.withTransaction {
                pruneJournal()
                val undoKey = undoCommandId.toString()
                val priorUndo = commands.findByCommandId(undoKey)
                if (priorUndo != null) {
                    if (priorUndo.kind != TodoCommandKind.UNDO ||
                        priorUndo.sourceCommandId != commandId.toString() ||
                        priorUndo.state != TodoCommandState.APPLIED
                    ) {
                        throw TodoConflictException(UUID.fromString(priorUndo.targetId))
                    }
                    val currentRoot = tasks.find(priorUndo.rootId)
                    if (currentRoot?.familyRevisionToken != priorUndo.familyRevisionAfter) {
                        throw TodoConflictException(UUID.fromString(priorUndo.targetId))
                    }
                    val refreshed = priorUndo.copy(
                        processToken = ProcessSession.token,
                        pinned = true,
                        terminalAt = clock.millis(),
                    )
                    check(commands.update(refreshed) == 1) { "Undo receipt changed during retry" }
                    return@withTransaction refreshed.toCompletionReceipt()
                }

                val original = commands.findByCommandId(commandId.toString())
                    ?: throw TodoConflictException(commandId)
                if (original.kind != TodoCommandKind.COMPLETION ||
                    original.state != TodoCommandState.APPLIED ||
                    !original.undoAvailable
                ) {
                    throw TodoConflictException(UUID.fromString(original.targetId))
                }
                val root = tasks.find(original.rootId)
                val family = tasks.family(original.rootId)
                val snapshots = commands.members(original.id)
                if (root == null ||
                    root.familyRevisionToken != original.familyRevisionAfter ||
                    family.size != snapshots.size ||
                    !matchesCommittedSnapshot(family, snapshots)
                ) {
                    throw UndoFamilyConflict(original.id, UUID.fromString(original.targetId))
                }

                val changed = snapshots.filter { it.resultingRevision != null }
                if (changed.isEmpty()) {
                    throw UndoFamilyConflict(original.id, UUID.fromString(original.targetId))
                }

                val now = clock.instant()
                val restoreOpen = changed.filter { it.previousCompletedAt == null }
                    .sortedBy { if (it.taskId == original.rootId) 0 else 1 }
                val restoreCompleted = changed.filter { it.previousCompletedAt != null }
                    .sortedBy { if (it.taskId == original.rootId) 1 else 0 }
                for (member in restoreOpen + restoreCompleted) {
                    val entity = requireNotNull(tasks.find(member.taskId))
                    val revision = UUID.randomUUID()
                    updateCompletionRow(entity, member.previousCompletedAt, revision, now)
                    database.taskReminders().onTaskRevisionChanged(
                        entity.id,
                        revision.toString(),
                        UUID.randomUUID().toString(),
                    )
                }
                val nextFamilyRevision = UUID.randomUUID().toString()
                rotateFamily(original.rootId, root.familyRevisionToken, nextFamilyRevision, family.size)

                val nowMillis = clock.millis()
                val undoReceipt = TodoCommandEntity(
                    id = undoKey,
                    kind = TodoCommandKind.UNDO,
                    state = TodoCommandState.APPLIED,
                    targetId = original.targetId,
                    rootId = original.rootId,
                    completed = original.completed?.not(),
                    familyRevisionBefore = requireNotNull(original.familyRevisionAfter),
                    familyRevisionAfter = nextFamilyRevision,
                    processToken = ProcessSession.token,
                    commandId = undoKey,
                    sourceCommandId = commandId.toString(),
                    childCount = original.childCount,
                    changedTaskCount = changed.size,
                    openChildCount = 0,
                    reopensParent = false,
                    confirmationRequired = false,
                    undoAvailable = false,
                    pinned = true,
                    createdAt = nowMillis,
                    terminalAt = nowMillis,
                )
                commands.insert(undoReceipt)
                check(commands.update(
                    original.copy(
                        state = TodoCommandState.UNDONE,
                        undoAvailable = false,
                        terminalAt = nowMillis,
                    ),
                ) == 1) { "Completion receipt changed while Undo was applied" }
                undoReceipt.toCompletionReceipt()
            }
        } catch (conflict: UndoFamilyConflict) {
            database.withTransaction {
                commands.find(conflict.commandId)?.let { original ->
                    if (original.state == TodoCommandState.APPLIED) {
                        commands.update(original.copy(undoAvailable = false, terminalAt = clock.millis()))
                    }
                }
            }
            throw TodoConflictException(conflict.targetId)
        }
    }

    suspend fun completionReceipt(commandId: UUID): TodoCompletionReceipt? =
        database.withTransaction {
            pruneJournal()
            val command = commands.findByCommandId(commandId.toString()) ?: return@withTransaction null
            if (command.kind != TodoCommandKind.COMPLETION ||
                command.state !in setOf(TodoCommandState.APPLIED, TodoCommandState.UNDONE)
            ) {
                return@withTransaction null
            }
            if (command.state == TodoCommandState.APPLIED) {
                val root = tasks.find(command.rootId)
                if (root?.familyRevisionToken != command.familyRevisionAfter) {
                    commands.update(command.copy(undoAvailable = false, terminalAt = clock.millis()))
                }
            }
            val refreshed = requireNotNull(commands.find(command.id)).copy(
                processToken = ProcessSession.token,
                pinned = true,
                terminalAt = clock.millis(),
            )
            check(commands.update(refreshed) == 1) { "Completion receipt changed during reconciliation" }
            refreshed.toCompletionReceipt()
        }

    suspend fun prepareDelete(id: UUID): TodoDeletePreview =
        database.withTransaction {
            pruneJournal()
            val target = tasks.find(id.toString()) ?: throw TodoMissingException(id)
            val (root, family) = loadFamily(target)
            val deleteFamily = target.parentId == null
            val childCount = if (deleteFamily) family.count { it.parentId == root.id } else 0
            val planId = UUID.randomUUID().toString()
            commands.insert(
                TodoCommandEntity(
                    id = planId,
                    kind = TodoCommandKind.DELETE,
                    state = TodoCommandState.PREPARED,
                    targetId = target.id,
                    rootId = root.id,
                    completed = null,
                    familyRevisionBefore = root.familyRevisionToken,
                    familyRevisionAfter = null,
                    processToken = ProcessSession.token,
                    commandId = null,
                    sourceCommandId = null,
                    childCount = childCount,
                    changedTaskCount = if (deleteFamily) family.size else 1,
                    openChildCount = 0,
                    reopensParent = false,
                    confirmationRequired = deleteFamily && childCount > 0,
                    undoAvailable = false,
                    pinned = true,
                    createdAt = clock.millis(),
                    terminalAt = null,
                ),
            )
            commands.insertMembers(family.map { member ->
                TodoCommandMemberEntity(
                    commandId = planId,
                    taskId = member.id,
                    expectedRevision = member.revisionToken,
                    previousCompletedAt = member.completedAt,
                    willChange = member.id == target.id || (deleteFamily && member.parentId == root.id),
                )
            })
            TodoDeletePreview(
                planId = UUID.fromString(planId),
                targetId = id,
                childCount = childCount,
            )
        }

    suspend fun applyDelete(planId: UUID, commandId: UUID) {
        try {
            database.withTransaction {
                pruneJournal()
                val commandIdText = commandId.toString()
                val prior = commands.findByCommandId(commandIdText)
                if (prior != null) {
                    if (prior.id != planId.toString() ||
                        prior.kind != TodoCommandKind.DELETE ||
                        prior.state != TodoCommandState.APPLIED
                    ) {
                        throw TodoConflictException(UUID.fromString(prior.targetId))
                    }
                    val currentRoot = tasks.find(prior.rootId)
                    val unchangedAfterDelete = if (prior.familyRevisionAfter == null) {
                        currentRoot == null
                    } else {
                        currentRoot?.familyRevisionToken == prior.familyRevisionAfter
                    }
                    if (!unchangedAfterDelete) {
                        throw TodoConflictException(UUID.fromString(prior.targetId))
                    }
                    check(commands.update(
                        prior.copy(
                            processToken = ProcessSession.token,
                            pinned = true,
                            terminalAt = clock.millis(),
                        ),
                    ) == 1) { "Delete receipt changed during retry" }
                    return@withTransaction
                }
                val command = commands.find(planId.toString())
                    ?: throw TodoConflictException(planId)
                if (command.kind != TodoCommandKind.DELETE ||
                    command.state != TodoCommandState.PREPARED ||
                    command.commandId != null
                ) {
                    throw TodoConflictException(UUID.fromString(command.targetId))
                }
                val family = revalidatePrepared(command)
                val target = tasks.find(command.targetId)
                    ?: throw StaleTodoCommandPreview(command.id, UUID.fromString(command.targetId))
                val deleted = commands.members(command.id).filter { it.willChange }
                val deleteFamily = target.parentId == null
                val deletedIds = deleted.map { it.taskId }
                invalidateWidgetCommands(deletedIds)
                unlinkFocusSessions(deletedIds)
                if (tasks.delete(target.id, target.revisionToken) != 1) {
                    throw StaleTodoCommandPreview(command.id, UUID.fromString(command.targetId))
                }

                val familyRevisionAfter = if (deleteFamily) {
                    null
                } else {
                    UUID.randomUUID().toString().also {
                        rotateFamily(command.rootId, command.familyRevisionBefore, it, family.size - 1)
                    }
                }
                val updated = command.copy(
                    state = TodoCommandState.APPLIED,
                    familyRevisionAfter = familyRevisionAfter,
                    processToken = ProcessSession.token,
                    commandId = commandIdText,
                    changedTaskCount = deleted.size,
                    undoAvailable = false,
                    terminalAt = clock.millis(),
                )
                check(commands.update(updated) == 1) { "Delete receipt changed during commit" }
            }
        } catch (stale: StaleTodoCommandPreview) {
            markStale(stale.commandId)
            throw TodoConflictException(stale.targetId)
        }
    }

    suspend fun releaseCommandHandle(id: UUID) {
        database.withTransaction {
            commands.release(id.toString(), clock.millis())
        }
    }

    private suspend fun revalidatePrepared(command: TodoCommandEntity): List<TodoEntity> {
        if (command.processToken != ProcessSession.token) {
            throw StaleTodoCommandPreview(command.id, UUID.fromString(command.targetId))
        }
        val root = tasks.find(command.rootId)
            ?: throw StaleTodoCommandPreview(command.id, UUID.fromString(command.targetId))
        val family = tasks.family(command.rootId)
        val members = commands.members(command.id)
        if (root.parentId != null ||
            root.familyRevisionToken != command.familyRevisionBefore ||
            family.size != members.size ||
            family.map { it.id }.toSet() != members.map { it.taskId }.toSet() ||
            family.any { current ->
                members.firstOrNull { it.taskId == current.id }?.expectedRevision != current.revisionToken ||
                    current.familyRevisionToken != command.familyRevisionBefore
            }
        ) {
            throw StaleTodoCommandPreview(command.id, UUID.fromString(command.targetId))
        }
        return family
    }

    private suspend fun loadFamily(target: TodoEntity): Pair<TodoEntity, List<TodoEntity>> {
        val rootId = target.parentId ?: target.id
        val root = tasks.find(rootId) ?: throw TodoConflictException(UUID.fromString(rootId))
        val family = tasks.family(rootId)
        validateFamily(root, family)
        if (family.none { it.id == target.id }) throw TodoConflictException(UUID.fromString(target.id))
        return root to family
    }

    private fun validateFamily(root: TodoEntity, family: List<TodoEntity>) {
        if (root.parentId != null || family.none { it.id == root.id } ||
            family.any { it.familyRevisionToken != root.familyRevisionToken }
        ) {
            throw TodoConflictException(UUID.fromString(root.id))
        }
    }

    private suspend fun rotateFamily(
        rootId: String,
        expectedRevision: String,
        nextRevision: String,
        expectedSize: Int,
    ) {
        if (expectedRevision == nextRevision) {
            throw IllegalArgumentException("A family mutation must use a new family revision token")
        }
        val changed = tasks.rotateFamilyRevision(rootId, expectedRevision, nextRevision)
        if (changed != expectedSize) throw TodoConflictException(UUID.fromString(rootId))
    }

    private suspend fun updateCompletionRow(
        entity: TodoEntity,
        completedAt: Long?,
        nextRevision: UUID,
        now: Instant,
    ) {
        check(nextRevision.toString() != entity.revisionToken) {
            "A task status change must use a new revision token"
        }
        check(tasks.setCompletion(
            id = entity.id,
            expectedRevision = entity.revisionToken,
            nextRevision = nextRevision.toString(),
            completedAt = completedAt,
            updatedAt = maxOf(now.toEpochMilli(), entity.updatedAt),
        ) == 1) { "Task changed while its completion was being applied" }
    }

    private suspend fun invalidateWidgetCommands(taskIds: List<String>) {
        val outcome = encodeWidgetActionResult(WidgetActionResult(WidgetOutcome.STALE))
        val widgets = database.widgets()
        for (taskId in taskIds) {
            widgets.clearFeedbackForTaskCommands(taskId)
            widgets.invalidateReadyTaskCommands(taskId, outcome)
        }
    }

    private suspend fun unlinkFocusSessions(taskIds: List<String>) {
        val focusDao = database.focus()
        val now = clock.instant()
        for (taskId in taskIds) {
            for (session in focusDao.sessionsForTask(taskId)) {
                val nextSessionRevision = Math.addExact(session.revision, 1L)
                val updatedAt = maxOf(
                    now,
                    Instant.ofEpochSecond(session.updatedAtSeconds, session.updatedAtNanos.toLong()),
                )
                check(focusDao.updateSession(
                    session.copy(
                        taskId = null,
                        revision = nextSessionRevision,
                        updatedAtSeconds = updatedAt.epochSecond,
                        updatedAtNanos = updatedAt.nano,
                    ),
                ) == 1) { "Focus session changed while its task link was being removed" }

                if (session.openSlot != null && session.state == "RUNNING") {
                    val runtime = focusDao.runtime()
                        ?: throw IllegalStateException("Running focus session has no runtime")
                    check(runtime.sessionId == session.id && runtime.revision == session.revision) {
                        "Focus session and runtime revisions diverged"
                    }
                    check(focusDao.updateRuntime(runtime.copy(revision = nextSessionRevision)) == 1) {
                        "Focus runtime changed while its task link was being removed"
                    }
                }
            }
        }
    }

    private suspend fun pruneJournal() {
        val now = clock.millis()
        commands.reconcileForeignHandles(ProcessSession.token, now)
        commands.pruneTerminal(now - JOURNAL_RETENTION_MILLIS)
    }

    private suspend fun markStale(commandId: String) {
        database.withTransaction {
            commands.markStale(commandId, clock.millis())
        }
    }

    private fun TodoCommandEntity.toCompletionReceipt(): TodoCompletionReceipt =
        TodoCompletionReceipt(
            commandId = UUID.fromString(requireNotNull(commandId)),
            targetId = UUID.fromString(targetId),
            completed = requireNotNull(completed),
            changedTaskCount = changedTaskCount,
            undoAvailable = undoAvailable,
        )

    private fun TodoTask.matches(input: TodoInput): Boolean =
        title == input.title && note == input.note && dueDate == input.dueDate &&
            dueTime == input.dueTime && importance == input.importance

    private fun monotonicMillis(entity: TodoEntity): Long =
        maxOf(clock.millis(), entity.updatedAt)

    private fun matchesCommittedSnapshot(
        family: List<TodoEntity>,
        snapshots: List<TodoCommandMemberEntity>,
    ): Boolean {
        if (family.map { it.id }.toSet() != snapshots.map { it.taskId }.toSet()) return false
        return family.all { entity ->
            val member = snapshots.firstOrNull { it.taskId == entity.id } ?: return@all false
            if (member.resultingRevision != null) {
                entity.revisionToken == member.resultingRevision &&
                    entity.completedAt == member.resultingCompletedAt
            } else {
                entity.revisionToken == member.expectedRevision &&
                    entity.completedAt == member.previousCompletedAt
            }
        }
    }

    private class StaleTodoCommandPreview(
        val commandId: String,
        val targetId: UUID,
    ) : RuntimeException()

    private class UndoFamilyConflict(
        val commandId: String,
        val targetId: UUID,
    ) : RuntimeException()

    private object ProcessSession {
        val token: String = UUID.randomUUID().toString()
    }

    private companion object {
        const val JOURNAL_RETENTION_MILLIS = 7L * 24L * 60L * 60L * 1000L
    }
}
