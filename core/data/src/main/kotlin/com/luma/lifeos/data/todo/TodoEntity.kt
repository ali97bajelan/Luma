package com.luma.lifeos.data.todo

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.luma.lifeos.domain.todo.TodoImportance
import com.luma.lifeos.domain.todo.TodoInput
import com.luma.lifeos.domain.todo.normalizeTodoInput
import java.time.LocalDate
import java.util.UUID

const val TODO_ROOT_FAMILY_REVISION = "00000000-0000-0000-0000-000000000000"

@Entity(
    tableName = "todo_task",
    foreignKeys = [
        ForeignKey(
            entity = TodoEntity::class,
            parentColumns = ["id"],
            childColumns = ["parent_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["completed_at", "due_date", "due_time_minutes", "created_at", "id"]),
        Index(value = ["completed_at", "id"]),
        Index(value = ["parent_id", "sibling_position"], unique = true),
        Index(value = ["parent_id", "completed_at", "sibling_position"]),
        Index(value = ["completed_at", "importance_code", "due_date", "due_time_minutes", "created_at", "id"]),
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
    @ColumnInfo(name = "due_time_minutes") val dueTimeMinutes: Int? = null,
    @ColumnInfo(name = "importance_code", defaultValue = "0") val importanceCode: Int = 0,
    @ColumnInfo(name = "parent_id") val parentId: String? = null,
    @ColumnInfo(name = "sibling_position") val siblingPosition: Long? = null,
    @ColumnInfo(
        name = "family_revision_token",
        defaultValue = "'$TODO_ROOT_FAMILY_REVISION'",
    )
    val familyRevisionToken: String = TODO_ROOT_FAMILY_REVISION,
) {
    init {
        require(id == canonicalUuid(id))
        require(revisionToken == canonicalUuid(revisionToken))
        require(familyRevisionToken == canonicalUuid(familyRevisionToken))
        require(parentId == null || parentId == canonicalUuid(parentId))
        require((parentId == null) == (siblingPosition == null)) {
            "Only a child task has a sibling position"
        }
        require(siblingPosition == null || siblingPosition >= 0L) {
            "Task sibling position cannot be negative"
        }
        val parsedDueDate = dueDate?.let { value ->
            LocalDate.parse(value).also { parsed -> require(parsed.toString() == value) }
        }
        require(dueTimeMinutes == null || dueTimeMinutes in 0..1439) {
            "Task due time minutes must be between 0 and 1439"
        }
        val dueTime = dueTimeMinutes?.let { java.time.LocalTime.of(it / 60, it % 60) }
        val normalized = normalizeTodoInput(
            TodoInput(title, note, parsedDueDate, dueTime, TodoImportance.fromCode(importanceCode)),
        )
        require(normalized.title == title && normalized.note == note)
        require(updatedAt >= createdAt)
    }
}

private fun canonicalUuid(value: String): String = UUID.fromString(value).toString()
