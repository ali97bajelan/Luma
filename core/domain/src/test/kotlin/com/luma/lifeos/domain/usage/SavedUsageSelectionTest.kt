package com.luma.lifeos.domain.usage

import java.time.*
import org.junit.Test
import org.junit.Assert.*

class SavedUsageSelectionTest {
    private val date = LocalDate.parse("2026-10-14")
    private val zone = ZoneOffset.UTC
    private val partial = DailyUsageReport(date, zone, emptyList(), 0, history = UsageHistory.PARTIAL)
    @Test fun zeroAggregateSurvivesAnEmptyPartialSessionQuery() {
        val legacy = DailyUsageReport(date, zone, emptyList(), 0)
        assertEquals(legacy, selectSavedUsageReport(partial, legacy, true))
    }
    @Test fun recordedVisitsAndCompleteEmptyHistoryTakePrecedenceOverAggregates() {
        val start = date.atStartOfDay(zone).toInstant()
        val visits = partial.copy(apps = listOf(AppUsageTotal("reader", null, 1000)), totalMillis = 1000,
            sessions = listOf(UsageSession("reader", start, start.plusSeconds(1))))
        val legacy = partial.copy(apps = listOf(AppUsageTotal("reader", null, 9000)), totalMillis = 9000, history = UsageHistory.UNAVAILABLE)
        assertEquals(visits, selectSavedUsageReport(visits, legacy, true))
        assertEquals(UsageHistory.COMPLETE, selectSavedUsageReport(partial.copy(history = UsageHistory.COMPLETE), legacy, true)!!.history)
        assertEquals(0L, selectSavedUsageReport(partial.copy(history = UsageHistory.COMPLETE), legacy, true)!!.totalMillis)
    }
    @Test fun unavailableAndPartialQueryStatesRemainDistinct() {
        assertNull(selectSavedUsageReport(partial, null, false))
        assertEquals(partial, selectSavedUsageReport(partial, null, true))
    }
}
