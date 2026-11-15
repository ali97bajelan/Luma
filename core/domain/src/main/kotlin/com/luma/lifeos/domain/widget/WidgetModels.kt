package com.luma.lifeos.domain.widget

import com.luma.lifeos.domain.todo.TodoChildProgress
import com.luma.lifeos.domain.todo.TodoImportance
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

enum class WidgetKind { HABITS, TASKS }

data class WidgetInstance(
    val id: Int,
    val kind: WidgetKind,
)

sealed interface WidgetRow {
    data class Habit(
        val id: UUID,
        val title: String,
        val count: Int,
        val target: Int,
        val skipped: Boolean,
        val actionToken: UUID?,
    ) : WidgetRow

    data class Task(
        val id: UUID,
        val title: String,
        val dueDate: LocalDate,
        val actionToken: UUID?,
        val dueTime: LocalTime? = null,
        val parentTitle: String? = null,
        val importance: TodoImportance = TodoImportance.NONE,
        val childProgress: TodoChildProgress? = null,
    ) : WidgetRow
}

enum class WidgetOutcome { SAVED, UNDONE, STALE, CONFLICT, CONFIRMATION_REQUIRED, MISSING, INVALID, FAILED }

data class WidgetActionResult(
    val outcome: WidgetOutcome,
    val retryToken: UUID? = null,
    val undoToken: UUID? = null,
    /** Task-specific route target for actions that need an in-app family confirmation. */
    val taskId: UUID? = null,
)

data class WidgetSnapshot(
    val instance: WidgetInstance,
    val date: LocalDate,
    val zone: ZoneId,
    val total: Int,
    val completed: Int?,
    val hasAnyItems: Boolean,
    val rows: List<WidgetRow>,
    val feedback: WidgetActionResult?,
    val updatedAt: Instant,
)
