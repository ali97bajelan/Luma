package com.luma.lifeos.domain.todo

import java.time.LocalDate
import java.time.LocalTime
import java.util.Comparator

enum class TodoField {
    TITLE,
    NOTE,
    DUE_DATE,
    DUE_TIME,
}

enum class TodoValidationReason {
    REQUIRED,
    LINE_BREAKS_NOT_ALLOWED,
    TOO_LONG,
    OUT_OF_RANGE,
}

class TodoValidationException(
    val field: TodoField,
    val reason: TodoValidationReason,
) : IllegalArgumentException("Invalid task ${field.name.lowercase()}: ${reason.name.lowercase()}")

fun normalizeTodoInput(input: TodoInput): TodoInput {
    if (TodoTextLimits.containsLineBreak(input.title)) {
        throw TodoValidationException(TodoField.TITLE, TodoValidationReason.LINE_BREAKS_NOT_ALLOWED)
    }

    val title = input.title.trim()
    if (title.isEmpty()) {
        throw TodoValidationException(TodoField.TITLE, TodoValidationReason.REQUIRED)
    }
    if (TodoTextLimits.codePointCount(title) > TodoTextLimits.TITLE) {
        throw TodoValidationException(TodoField.TITLE, TodoValidationReason.TOO_LONG)
    }

    val note = input.note?.trim()?.takeUnless(String::isEmpty)
    if (note != null && TodoTextLimits.codePointCount(note) > TodoTextLimits.NOTE) {
        throw TodoValidationException(TodoField.NOTE, TodoValidationReason.TOO_LONG)
    }

    val dueDate = input.dueDate
    if (dueDate != null && dueDate.year !in TodoTextLimits.MIN_YEAR..TodoTextLimits.MAX_YEAR) {
        throw TodoValidationException(TodoField.DUE_DATE, TodoValidationReason.OUT_OF_RANGE)
    }

    val dueTime = input.dueTime
    if (dueTime != null && dueDate == null) {
        throw TodoValidationException(TodoField.DUE_DATE, TodoValidationReason.REQUIRED)
    }
    if (dueTime != null && (dueTime.second != 0 || dueTime.nano != 0)) {
        throw TodoValidationException(TodoField.DUE_TIME, TodoValidationReason.OUT_OF_RANGE)
    }

    return TodoInput(title = title, note = note, dueDate = dueDate, dueTime = dueTime)
}

fun todoBucket(task: TodoTask, today: LocalDate): TodoBucket = when {
    task.dueDate == null -> TodoBucket.NO_DATE
    task.dueDate.isBefore(today) -> TodoBucket.OVERDUE
    task.dueDate == today -> TodoBucket.TODAY
    else -> TodoBucket.UPCOMING
}

fun isTodoTodayEligible(task: TodoTask, today: LocalDate): Boolean =
    !task.isCompleted && task.dueDate?.isAfter(today) == false

val openTodoTaskComparator: Comparator<TodoTask> = Comparator { first, second ->
    compareDueDates(first.dueDate, second.dueDate)
        .takeIf { it != 0 }
        ?: compareDueTimes(first.dueTime, second.dueTime).takeIf { it != 0 }
        ?: first.createdAt.compareTo(second.createdAt).takeIf { it != 0 }
        ?: first.id.toString().compareTo(second.id.toString())
}

val completedTodoTaskComparator: Comparator<TodoTask> = Comparator { first, second ->
    compareValues(second.completedAt, first.completedAt)
        .takeIf { it != 0 }
        ?: first.id.toString().compareTo(second.id.toString())
}

private fun compareDueDates(first: LocalDate?, second: LocalDate?): Int = when {
    first == null && second == null -> 0
    first == null -> 1
    second == null -> -1
    else -> first.compareTo(second)
}

private fun compareDueTimes(first: LocalTime?, second: LocalTime?): Int = when {
    first == null && second == null -> 0
    first == null -> 1
    second == null -> -1
    else -> first.compareTo(second)
}
