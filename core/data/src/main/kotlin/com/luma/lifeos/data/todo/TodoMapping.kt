package com.luma.lifeos.data.todo

import com.luma.lifeos.domain.todo.TodoInput
import com.luma.lifeos.domain.todo.TodoTask
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

internal fun TodoEntity.toDomain(): TodoTask {
    val taskId = UUID.fromString(id)
    val revision = UUID.fromString(revisionToken)
    return TodoTask(
        id = taskId,
        title = title,
        note = note,
        dueDate = dueDate?.let { LocalDate.parse(it) },
        dueTime = dueTimeMinutes?.let { LocalTime.of(it / 60, it % 60) },
        completedAt = completedAt?.let(Instant::ofEpochMilli),
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt),
        revision = revision,
        importance = com.luma.lifeos.domain.todo.TodoImportance.NONE,
        parentId = null,
        siblingPosition = null,
        familyRevision = taskId,
    )
}

internal fun TodoInput.toEntity(
    id: UUID,
    revision: UUID,
    now: Instant,
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
)
