package com.luma.lifeos.data.focus

import com.luma.lifeos.domain.focus.FocusHistoryQuery
import com.luma.lifeos.domain.focus.FocusTagFilter
import java.util.Locale

/** Applies literal name/tag predicates after the repository has selected the period candidates. */
internal fun matchesFocusHistoryFilter(
    name: String?,
    tagId: String?,
    query: FocusHistoryQuery,
): Boolean {
    val needle = query.search.trim().lowercase(Locale.ROOT)
    if (needle.isNotEmpty() && name?.trim()?.lowercase(Locale.ROOT)?.contains(needle) != true) return false
    return when (val filter = query.tag) {
        FocusTagFilter.All -> true
        FocusTagFilter.Untagged -> tagId == null
        is FocusTagFilter.Tagged -> tagId == filter.id.toString()
    }
}

internal class FocusHistoryAccumulator<T>(
    private val query: FocusHistoryQuery,
    private val offset: Int,
    private val limit: Int,
) {
    private val selected = ArrayList<T>(limit)
    var total: Int = 0
        private set

    init {
        require(offset >= 0)
        require(limit > 0)
    }

    fun accept(candidate: T, name: String?, tagId: String?) {
        if (!matchesFocusHistoryFilter(name, tagId, query)) return
        if (total >= offset && selected.size < limit) selected += candidate
        total = Math.incrementExact(total)
    }

    fun pageRows(): List<T> = selected.toList()
}
