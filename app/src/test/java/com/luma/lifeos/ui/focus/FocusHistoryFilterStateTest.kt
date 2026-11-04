package com.luma.lifeos.ui.focus

import com.luma.lifeos.domain.focus.FocusHistoryQuery
import com.luma.lifeos.domain.focus.FocusTagFilter
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusHistoryFilterStateTest {
    @Test fun searchAndTagChangesResetThePageOffset() {
        val initial = FocusHistoryFilterState(offset = 100)
        val searched = reduceFocusHistoryFilterState(initial, FocusHistoryFilterEvent.SearchChanged("chapter"))
        val tagged = reduceFocusHistoryFilterState(
            searched.copy(offset = 50),
            FocusHistoryFilterEvent.TagChanged(FocusTagFilter.Untagged),
        )

        assertEquals(0, searched.offset)
        assertEquals("chapter", searched.search)
        assertEquals(0, tagged.offset)
        assertEquals(FocusTagFilter.Untagged, tagged.tag)
    }

    @Test fun savedFiltersRestoreAndInvalidSavedTagFallsBackToAll() {
        val id = UUID.fromString("00000000-0000-0000-0000-000000000003")
        assertEquals(
            FocusHistoryFilterState("read", FocusTagFilter.Tagged(id), 50),
            restoreFocusHistoryFilterState("read", "tagged", id.toString(), 50),
        )
        assertEquals(
            FocusHistoryFilterState("read", FocusTagFilter.All, 0),
            restoreFocusHistoryFilterState("read", "tagged", "bad-id", -10),
        )
    }

    @Test fun databaseReplacementClearsPrivateFiltersAndPaging() {
        val changed = reduceFocusHistoryFilterState(
            FocusHistoryFilterState("private name", FocusTagFilter.Untagged, 100),
            FocusHistoryFilterEvent.DatabaseReplaced,
        )
        assertEquals(FocusHistoryFilterState(), changed)
    }

    @Test fun resultMustMatchQueryOffsetGenerationAndRequest() {
        val generation = UUID.fromString("00000000-0000-0000-0000-000000000004")
        val query = FocusHistoryQuery(null, "read", FocusTagFilter.All)
        val active = FocusHistoryLoadKey(query, 50, generation, 8)

        assertTrue(acceptsFocusHistoryResult(active, active))
        assertFalse(acceptsFocusHistoryResult(active.copy(query = query.copy(search = "work")), active))
        assertFalse(acceptsFocusHistoryResult(active.copy(offset = 0), active))
        assertFalse(acceptsFocusHistoryResult(active.copy(generation = UUID.randomUUID()), active))
        assertFalse(acceptsFocusHistoryResult(active.copy(requestId = 7), active))
    }
}
