package com.luma.lifeos.data.todo

import com.luma.lifeos.domain.todo.TodoInput
import com.luma.lifeos.domain.todo.TodoTask
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

internal fun TodoEntity.toDomain(): TodoTask = TodoTask(
    id = UUID.fromString(id),
    title = title,
    note = note,
    dueDate = dueDate?.let { LocalDate.parse(it) },
    completedAt = completedAt?.let(Instant::ofEpochMilli),
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    revision = UUID.fromString(revisionToken),
)

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
)
