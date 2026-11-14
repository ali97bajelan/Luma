package com.luma.lifeos.domain.todo

/** Ordering available to task list queries. Completed rows retain their completion-time order. */
enum class TodoSort {
    DATE,
    IMPORTANCE,
}

/**
 * Status and importance query for Tasks. A null importance filter means All; [TodoImportance.NONE]
 * selects only tasks that have not been classified.
 */
data class TodoListQuery(
    val completed: Boolean,
    val importance: TodoImportance? = null,
    val sort: TodoSort = TodoSort.DATE,
)
