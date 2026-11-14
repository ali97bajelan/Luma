package com.luma.lifeos.data.todo

import com.luma.lifeos.domain.todo.TodoChildProgress
import com.luma.lifeos.domain.todo.TodoImportance
import com.luma.lifeos.domain.todo.TodoInput
import com.luma.lifeos.domain.todo.TodoParentContext
import com.luma.lifeos.domain.todo.TodoTask
import com.luma.lifeos.domain.todo.TodoTaskRow
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

internal fun TodoEntity.toDomain(): TodoTask = TodoTask(
    id = UUID.fromString(id),
    title = title,
    note = note,
    dueDate = dueDate?.let(LocalDate::parse),
    dueTime = dueTimeMinutes?.let { LocalTime.of(it / 60, it % 60) },
    completedAt = completedAt?.let(Instant::ofEpochMilli),
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    revision = UUID.fromString(revisionToken),
    importance = TodoImportance.fromCode(importanceCode),
    parentId = parentId?.let(UUID::fromString),
    siblingPosition = siblingPosition,
    familyRevision = UUID.fromString(familyRevisionToken),
)

internal fun TodoInput.toEntity(
    id: UUID,
    revision: UUID,
    now: Instant,
    parentId: UUID? = null,
    siblingPosition: Long? = null,
    familyRevision: UUID = UUID.randomUUID(),
): TodoEntity = TodoEntity(
    id = id.toString(),
    title = title,
    note = note,
    dueDate = dueDate?.toString(),
    completedAt = null,
    createdAt = now.toEpochMilli(),
    updatedAt = now.toEpochMilli(),
    revisionToken = revision.toString(),
    dueTimeMinutes = dueTime?.let { it.hour * 60 + it.minute },
    importanceCode = importance.code,
    parentId = parentId?.toString(),
    siblingPosition = siblingPosition,
    familyRevisionToken = familyRevision.toString(),
)

internal fun TodoFamilyPageRow.toDomainOrNull(): TodoTaskRow? {
    val entity = toEntityOrNull() ?: return null
    val task = entity.toDomain()
    val parent = entity.parentId?.let { parentId ->
        TodoParentContext(
            id = UUID.fromString(parentId),
            title = requireNotNull(parentTitle) { "Child row is missing parent context" },
        )
    }
    val progress = if (task.parentId == null && (childTotal ?: 0) > 0) {
        TodoChildProgress(
            completed = requireNotNull(childCompleted),
            total = requireNotNull(childTotal),
        )
    } else {
        null
    }
    return TodoTaskRow(task = task, parent = parent, progress = progress)
}
