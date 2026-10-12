package com.luma.lifeos.domain.search

import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class SearchQueryTest {
    @Test fun civilDatesUseDstBoundariesAndAllowOpenEnds() {
        val query = SearchQuery(from = LocalDate.parse("2026-03-08"), through = LocalDate.parse("2026-03-08"))
        val bounds = query.bounds(ZoneId.of("America/New_York"))
        assertEquals("2026-03-08T05:00:00Z", bounds.start.toString())
        assertEquals("2026-03-09T04:00:00Z", bounds.endExclusive.toString())
        assertNull(SearchQuery().bounds(ZoneId.of("UTC")).start)
        assertNull(SearchQuery(from = LocalDate.parse("2026-10-12")).bounds(ZoneId.of("UTC")).endExclusive)
    }
    @Test fun rejectsInvertedDatesAndOversizedInputs() {
        assertTrue(runCatching { SearchQuery(from = LocalDate.parse("2026-10-12"), through = LocalDate.parse("2026-10-11")) }.isFailure)
        assertTrue(runCatching { SearchQuery(text = "a".repeat(201)) }.isFailure)
        assertTrue(runCatching { SearchQuery(tagIds = (0..100).map { UUID(0, it.toLong()) }.toSet()) }.isFailure)
    }
    @Test fun maximumDateHasNoExclusiveUpperBoundAndSkippedDayHasAnEmptyRange() {
        assertNull(SearchQuery(through = LocalDate.MAX).bounds(ZoneId.of("UTC")).endExclusive)
        val bounds = SearchQuery(from = LocalDate.parse("2011-12-30"), through = LocalDate.parse("2011-12-30"))
            .bounds(ZoneId.of("Pacific/Apia"))
        assertEquals(bounds.start, bounds.endExclusive)
    }
}
