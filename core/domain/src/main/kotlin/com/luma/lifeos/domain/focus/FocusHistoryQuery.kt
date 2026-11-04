package com.luma.lifeos.domain.focus

import com.luma.lifeos.domain.time.TimeRange
import java.util.UUID

sealed interface FocusTagFilter {
    data object All : FocusTagFilter
    data object Untagged : FocusTagFilter
    data class Tagged(val id: UUID) : FocusTagFilter
}

/** History filters are independent of the selected insights period. */
data class FocusHistoryQuery(
    val range: TimeRange?,
    val search: String = "",
    val tag: FocusTagFilter = FocusTagFilter.All,
)
