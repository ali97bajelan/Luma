package com.luma.lifeos.ui.focus

import com.luma.lifeos.domain.focus.FocusMetadata
import com.luma.lifeos.domain.focus.FocusTag
import java.util.UUID

enum class FocusPrivateSurface { FOCUS, INSIGHTS, HISTORY, TODAY, REVIEW }

fun visibleFocusPrivateSurfaces(isFocusReadable: Boolean): Set<FocusPrivateSurface> =
    if (isFocusReadable) FocusPrivateSurface.entries.toSet() else emptySet()

fun focusMetadataForPresentation(
    metadata: FocusMetadata,
    isFocusReadable: Boolean,
): FocusMetadata = if (isFocusReadable) metadata else FocusMetadata()

data class FocusTagSuggestionsSnapshot(
    val databaseGeneration: UUID?,
    val tags: List<FocusTag>,
)

fun visibleFocusTagSuggestions(
    currentGeneration: UUID?,
    suggestions: FocusTagSuggestionsSnapshot,
): List<FocusTag> = if (
    currentGeneration != null && suggestions.databaseGeneration == currentGeneration
) suggestions.tags else emptyList()

data class FocusInsightsPresentation(
    val isVisible: Boolean,
    val state: FocusInsightsState,
    val tagSuggestions: List<FocusTag>,
)

fun focusInsightsPresentation(
    isFocusReadable: Boolean,
    selectedQuery: FocusInsightsQuery?,
    observed: FocusInsightsState,
    tagSuggestions: List<FocusTag>,
): FocusInsightsPresentation = if (isFocusReadable) {
    FocusInsightsPresentation(
        isVisible = true,
        state = visibleFocusInsightsState(selectedQuery, observed),
        tagSuggestions = tagSuggestions,
    )
} else {
    FocusInsightsPresentation(
        isVisible = false,
        state = FocusInsightsState.Unselected,
        tagSuggestions = emptyList(),
    )
}

fun focusHistoryStateForAccess(
    state: FocusHistoryUiState,
    isFocusReadable: Boolean,
): FocusHistoryUiState = if (isFocusReadable) state else clearFocusHistoryPrivateState(state)

fun focusHistoryStateForDatabaseReplacement(state: FocusHistoryUiState): FocusHistoryUiState =
    clearFocusHistoryPrivateState(state)
