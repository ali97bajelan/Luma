package com.luma.lifeos.domain.widget

import java.time.Instant
import java.time.LocalDate
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
    ) : WidgetRow
}

enum class WidgetOutcome { SAVED, UNDONE, STALE, CONFLICT, MISSING, INVALID, FAILED }

data class WidgetActionResult(
    val outcome: WidgetOutcome,
    val retryToken: UUID? = null,
    val undoToken: UUID? = null,
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
