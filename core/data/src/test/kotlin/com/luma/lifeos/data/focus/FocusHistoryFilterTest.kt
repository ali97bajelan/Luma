package com.luma.lifeos.data.focus

import com.luma.lifeos.domain.focus.FocusHistoryQuery
import com.luma.lifeos.domain.focus.FocusTagFilter
import com.luma.lifeos.domain.time.TimeRange
import java.time.Instant
import java.util.Locale
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusHistoryFilterTest {
    private val studyId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val workId = UUID.fromString("00000000-0000-0000-0000-000000000002")

    @Test fun allUntaggedAndTaggedFiltersMatchTheirCatalogIdentity() {
        assertTrue(matchesFocusHistoryFilter("Read", studyId.toString(), FocusHistoryQuery(null)))
        assertTrue(matchesFocusHistoryFilter("Read", null, FocusHistoryQuery(null, tag = FocusTagFilter.Untagged)))
        assertFalse(matchesFocusHistoryFilter("Read", workId.toString(), FocusHistoryQuery(null, tag = FocusTagFilter.Untagged)))
        assertTrue(
            matchesFocusHistoryFilter("Read", studyId.toString(), FocusHistoryQuery(null, tag = FocusTagFilter.Tagged(studyId))),
        )
        assertFalse(
            matchesFocusHistoryFilter("Read", workId.toString(), FocusHistoryQuery(null, tag = FocusTagFilter.Tagged(studyId))),
        )
    }

    @Test fun searchTrimsBothSidesAndUsesLocaleRootCaseInsensitiveSubstringMatching() {
        val oldLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale("tr", "TR"))
            assertTrue(matchesFocusHistoryFilter("  ISTANBUL notes  ", null, FocusHistoryQuery(null, "  istan ")))
        } finally {
            Locale.setDefault(oldLocale)
        }
    }

    @Test fun percentAndUnderscoreAreLiteralCharacters() {
        assertTrue(matchesFocusHistoryFilter("Task 100%_ready", null, FocusHistoryQuery(null, "%_")))
        assertFalse(matchesFocusHistoryFilter("Task 100%_ready", null, FocusHistoryQuery(null, "100__")))
    }

    @Test fun legacyNullNamesMatchOnlyAnEmptySearch() {
        assertTrue(matchesFocusHistoryFilter(null, null, FocusHistoryQuery(null)))
        assertFalse(matchesFocusHistoryFilter(null, null, FocusHistoryQuery(null, "Focus session")))
    }

    @Test fun nameAndTagPredicatesAreCombinedWithAnd() {
        val query = FocusHistoryQuery(null, "chapter", FocusTagFilter.Tagged(studyId))
        assertTrue(matchesFocusHistoryFilter("Read chapter 4", studyId.toString(), query))
        assertFalse(matchesFocusHistoryFilter("Read chapter 4", workId.toString(), query))
        assertFalse(matchesFocusHistoryFilter("Read notes", studyId.toString(), query))
    }

    @Test fun filtersAreCountedAndAppliedBeforeStablePageSlicingAcrossBatches() {
        data class Candidate(val order: Int, val name: String, val tag: String?)
        val candidates = (139 downTo 0).map { order ->
            val name = if (order % 23 == 0) "Break" else "Focus block $order"
            val tag = if (order < 120) studyId.toString() else workId.toString()
            Candidate(order, name, tag)
        }
        val range = TimeRange(Instant.EPOCH, Instant.EPOCH.plusSeconds(60))
        val query = FocusHistoryQuery(range, " focus ", FocusTagFilter.Tagged(studyId))
        val accumulator = FocusHistoryAccumulator<Candidate>(query, offset = 50, limit = 50)
        candidates.chunked(37).forEach { batch ->
            batch.forEach { accumulator.accept(it, it.name, it.tag) }
        }

        assertEquals(114, accumulator.total)
        assertEquals((66 downTo 15).filter { it % 23 != 0 }, accumulator.pageRows().map(Candidate::order))
        assertEquals(range, query.range)
    }
}
