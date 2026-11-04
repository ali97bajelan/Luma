package com.luma.lifeos.ui.focus

import com.luma.lifeos.domain.focus.FocusPage
import com.luma.lifeos.domain.focus.FocusTag
import java.util.UUID

data class FocusHistoryUiState(
    val filters: FocusHistoryFilterState = FocusHistoryFilterState(),
    val tags: List<FocusTag> = emptyList(),
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val page: FocusPage? = null,
    val databaseGeneration: UUID? = null,
    val deletingSessionId: UUID? = null,
    val deletionFailed: Boolean = false,
)

fun clearFocusHistoryPrivateState(state: FocusHistoryUiState): FocusHistoryUiState = state.copy(
    filters = FocusHistoryFilterState(),
    tags = emptyList(),
    loading = false,
    loadFailed = false,
    page = null,
    databaseGeneration = null,
    deletingSessionId = null,
    deletionFailed = false,
)
