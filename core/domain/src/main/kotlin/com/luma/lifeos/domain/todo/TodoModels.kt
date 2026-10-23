package com.luma.lifeos.domain.todo

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

const val TODO_DEFAULT_PAGE_SIZE = 50
const val TODO_DEFAULT_TODAY_LIMIT = 3

data class TodoTask(
    val id: UUID,
    val title: String,
    val note: String?,
    val dueDate: LocalDate?,
    val completedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val revision: UUID,
    val dueTime: LocalTime? = null,
) {
    init {
        require(title == title.trim() && title.isNotEmpty()) { "Task title must be non-blank and trimmed" }
        require(!TodoTextLimits.containsLineBreak(title)) { "Task title must be a single line" }
        require(TodoTextLimits.codePointCount(title) <= TodoTextLimits.TITLE) { "Task title is too long" }
        require(note == null || (note == note.trim() && note.isNotEmpty())) {
            "Task note must be absent or non-blank and trimmed"
        }
        require(note == null || TodoTextLimits.codePointCount(note) <= TodoTextLimits.NOTE) {
            "Task note is too long"
        }
        require(dueDate == null || dueDate.year in TodoTextLimits.MIN_YEAR..TodoTextLimits.MAX_YEAR) {
            "Task due date must be in years ${TodoTextLimits.MIN_YEAR}–${TodoTextLimits.MAX_YEAR}"
        }
        require(dueTime == null || dueDate != null) { "Task due time requires a due date" }
        require(dueTime == null || (dueTime.second == 0 && dueTime.nano == 0)) {
            "Task due time must have minute precision"
        }
        require(updatedAt >= createdAt) { "Task updated-at cannot precede created-at" }
    }

    val isCompleted: Boolean
        get() = completedAt != null
}

data class TodoInput(
    val title: String,
    val note: String? = null,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
)

data class TodoPage(val items: List<TodoTask>, val total: Int) {
    init {
        require(total >= 0) { "Task total cannot be negative" }
        require(items.size <= total) { "Task page cannot exceed its total" }
        require(items.map(TodoTask::id).distinct().size == items.size) { "Task page contains duplicate tasks" }
    }
}

enum class TodoBucket {
    OVERDUE,
    TODAY,
    UPCOMING,
    NO_DATE,
}
