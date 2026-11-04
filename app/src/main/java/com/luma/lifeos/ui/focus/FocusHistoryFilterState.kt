package com.luma.lifeos.ui.focus

import com.luma.lifeos.domain.focus.FocusHistoryQuery
import com.luma.lifeos.domain.focus.FOCUS_HISTORY_PAGE_SIZE
import com.luma.lifeos.domain.focus.FocusTagFilter
import com.luma.lifeos.domain.time.TimeRange
import java.util.UUID

data class FocusHistoryFilterState(
    val search: String = "",
    val tag: FocusTagFilter = FocusTagFilter.All,
    val offset: Int = 0,
)

sealed interface FocusHistoryFilterEvent {
    data class SearchChanged(val search: String) : FocusHistoryFilterEvent
    data class TagChanged(val tag: FocusTagFilter) : FocusHistoryFilterEvent
    data class PageChanged(val offset: Int) : FocusHistoryFilterEvent
    data object DatabaseReplaced : FocusHistoryFilterEvent
}

fun reduceFocusHistoryFilterState(
    state: FocusHistoryFilterState,
    event: FocusHistoryFilterEvent,
): FocusHistoryFilterState = when (event) {
    is FocusHistoryFilterEvent.SearchChanged -> state.copy(search = event.search, offset = 0)
    is FocusHistoryFilterEvent.TagChanged -> state.copy(tag = event.tag, offset = 0)
    is FocusHistoryFilterEvent.PageChanged -> {
        require(event.offset >= 0 && event.offset % FOCUS_HISTORY_PAGE_SIZE == 0)
        state.copy(offset = event.offset)
    }
    FocusHistoryFilterEvent.DatabaseReplaced -> FocusHistoryFilterState()
}

fun restoreFocusHistoryFilterState(
    search: String?,
    tagKind: String?,
    tagId: String?,
    offset: Int?,
): FocusHistoryFilterState {
    val tag = when (tagKind) {
        "untagged" -> FocusTagFilter.Untagged
        "tagged" -> runCatching { tagId?.let { FocusTagFilter.Tagged(UUID.fromString(it)) } }.getOrNull()
            ?: FocusTagFilter.All
        else -> FocusTagFilter.All
    }
    val safeOffset = offset?.takeIf { it >= 0 && it % FOCUS_HISTORY_PAGE_SIZE == 0 } ?: 0
    return FocusHistoryFilterState(search.orEmpty(), tag, safeOffset)
}

fun FocusHistoryFilterState.query(range: TimeRange?): FocusHistoryQuery =
    FocusHistoryQuery(range = range, search = search, tag = tag)

data class FocusHistoryLoadKey(
    val query: FocusHistoryQuery,
    val offset: Int,
    val generation: UUID,
    val requestId: Long,
)

fun acceptsFocusHistoryResult(result: FocusHistoryLoadKey, active: FocusHistoryLoadKey): Boolean = result == active
