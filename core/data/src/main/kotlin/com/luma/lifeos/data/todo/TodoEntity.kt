package com.luma.lifeos.data.todo

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.luma.lifeos.domain.todo.TodoInput
import com.luma.lifeos.domain.todo.normalizeTodoInput
import java.time.LocalDate
import java.util.UUID

@Entity(
    tableName = "todo_task",
    indices = [
        Index(value = ["completed_at", "due_date", "created_at", "id"]),
        Index(value = ["completed_at", "id"]),
    ],
)
data class TodoEntity(
    @PrimaryKey val id: String,
    val title: String,
    val note: String?,
    @ColumnInfo(name = "due_date") val dueDate: String?,
    @ColumnInfo(name = "completed_at") val completedAt: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "revision_token") val revisionToken: String,
) {
    init {
        require(id == canonicalUuid(id))
        require(revisionToken == canonicalUuid(revisionToken))
        val parsedDueDate = dueDate?.let { value ->
            LocalDate.parse(value).also { parsed -> require(parsed.toString() == value) }
        }
        val normalized = normalizeTodoInput(TodoInput(title, note, parsedDueDate))
        require(normalized.title == title && normalized.note == note)
        require(updatedAt >= createdAt)
    }
}

private fun canonicalUuid(value: String): String = UUID.fromString(value).toString()
