package com.luma.lifeos.ui.focus

import com.luma.lifeos.domain.todo.TodoRowPage

/** Only the current request's page is rendered; query changes replace it while loading. */
internal data class FocusTaskPickerState(
    val query: String = "",
    val offset: Int = 0,
    val page: TodoRowPage? = null,
    val loading: Boolean = false,
    val error: Boolean = false,
    val requestId: Long = 0L,
) {
    val isEmpty: Boolean get() = !loading && !error && page?.items?.isEmpty() == true
    val hasPrevious: Boolean get() = offset > 0
    val hasNext: Boolean get() = page?.let { offset + it.items.size < it.total } == true
}
