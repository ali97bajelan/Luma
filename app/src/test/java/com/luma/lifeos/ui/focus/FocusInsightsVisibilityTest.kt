package com.luma.lifeos.ui.focus

import com.luma.lifeos.domain.focus.FocusInsights
import com.luma.lifeos.domain.focus.FocusMetadata
import com.luma.lifeos.domain.focus.FocusPage
import com.luma.lifeos.domain.focus.FocusTag
import com.luma.lifeos.domain.focus.FocusTagFilter
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusInsightsVisibilityTest {
    @Test
    fun lockedFocusSuppressesEveryPrivateFocusSurface() {
        val surfaces = setOf(
            FocusPrivateSurface.FOCUS,
            FocusPrivateSurface.INSIGHTS,
            FocusPrivateSurface.HISTORY,
            FocusPrivateSurface.TODAY,
            FocusPrivateSurface.REVIEW,
        )
        assertTrue(visibleFocusPrivateSurfaces(isFocusReadable = true).containsAll(surfaces))
        assertEquals(emptySet<FocusPrivateSurface>(), visibleFocusPrivateSurfaces(isFocusReadable = false))
    }

    @Test
    fun relockHidesCachedInsightsAndTagLabels() {
        val query = FocusInsightsQuery(null, uuid(1))
        val ready = FocusInsightsState.Ready(query, FocusInsights.EMPTY)
        val tags = listOf(FocusTag(uuid(2), "Private label"))

        val visible = focusInsightsPresentation(true, query, ready, tags)
        val hidden = focusInsightsPresentation(false, query, ready, tags)

        assertEquals(ready, visible.state)
        assertEquals(tags, visible.tagSuggestions)
        assertFalse(hidden.isVisible)
        assertEquals(FocusInsightsState.Unselected, hidden.state)
        assertEquals(emptyList<FocusTag>(), hidden.tagSuggestions)
        assertEquals(
            FocusMetadata(),
            focusMetadataForPresentation(FocusMetadata("Private session", tags.single()), false),
        )
        assertEquals(
            FocusMetadata("Private session", tags.single()),
            focusMetadataForPresentation(FocusMetadata("Private session", tags.single()), true),
        )
    }

    @Test
    fun relockAndDatabaseReplacementClearCachedHistoryAndSearch() {
        val privateState = FocusHistoryUiState(
            filters = FocusHistoryFilterState("Private session", FocusTagFilter.Tagged(uuid(3)), 50),
            tags = listOf(FocusTag(uuid(3), "Private label")),
            loading = false,
            page = FocusPage(emptyList(), offset = 0, total = 1),
            databaseGeneration = uuid(4),
        )

        val locked = focusHistoryStateForAccess(privateState, isFocusReadable = false)
        val restored = focusHistoryStateForDatabaseReplacement(privateState)

        val cleared = FocusHistoryUiState(loading = false)
        assertEquals(cleared, locked)
        assertEquals(cleared, restored)
    }

    @Test
    fun relockClearsTheSavedSetupDraftButKeepsTheNonPrivatePeriodChoice() {
        val locked = reduceFocusSetupState(
            FocusSetupState(name = "Private session", tag = "Private label", period = FocusPeriod.THIS_WEEK),
            FocusSetupEvent.PrivateContentLocked,
        )

        assertEquals("", locked.name)
        assertEquals("", locked.tag)
        assertEquals(FocusPeriod.THIS_WEEK, locked.period)
    }

    @Test
    fun restoredGenerationNeverShowsAnOlderReadyInsight() {
        val range = com.luma.lifeos.domain.time.TimeRange(Instant.EPOCH, Instant.EPOCH.plusSeconds(60))
        val oldQuery = FocusInsightsQuery(range, uuid(5))
        val newQuery = oldQuery.copy(generation = uuid(6))

        assertEquals(
            FocusInsightsState.Loading(newQuery),
            focusInsightsPresentation(
                isFocusReadable = true,
                selectedQuery = newQuery,
                observed = FocusInsightsState.Ready(oldQuery, FocusInsights.EMPTY),
                tagSuggestions = emptyList(),
            ).state,
        )
    }

    @Test
    fun restoredGenerationNeverShowsOldTagSuggestions() {
        val generation = uuid(7)
        val tags = listOf(FocusTag(uuid(8), "Old database label"))
        val stale = FocusTagSuggestionsSnapshot(uuid(6), tags)
        val current = FocusTagSuggestionsSnapshot(generation, tags)

        assertEquals(emptyList<FocusTag>(), visibleFocusTagSuggestions(generation, stale))
        assertEquals(tags, visibleFocusTagSuggestions(generation, current))
        assertEquals(emptyList<FocusTag>(), visibleFocusTagSuggestions(null, current))
    }

    private fun uuid(value: Long) = UUID(0, value)
}
