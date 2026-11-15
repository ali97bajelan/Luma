package com.luma.lifeos.domain.todo

/** Ordering available to task list queries. Completed rows retain their completion-time order. */
enum class TodoSort {
    DATE,
    IMPORTANCE,
}

/**
 * Status, importance, ordering, and optional title query for Tasks. A null importance filter means
 * All; [TodoImportance.NONE] selects only tasks that have not been classified. A nonblank title
 * query switches the list to independent matching tasks, including children.
 */
data class TodoListQuery(
    val completed: Boolean,
    val importance: TodoImportance? = null,
    val sort: TodoSort = TodoSort.DATE,
    val titleSearch: String? = null,
)
