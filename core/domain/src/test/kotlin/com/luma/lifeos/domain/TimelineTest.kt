package com.luma.lifeos.domain

import com.luma.lifeos.domain.model.*
import java.time.Instant
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class TimelineTest {
    private val time = Instant.parse("2026-10-11T08:00:00Z")
    @Test fun signalsRejectInvalidValuesRatherThanPresentingFabricatedMeasurements() {
        assertTrue(runCatching { ActivityDetails(UUID.randomUUID(), "steps", Double.NaN, "count", EventSource.HEALTH_CONNECT) }.isFailure)
        assertTrue(runCatching { ActivityDetails(UUID.randomUUID(), "steps", -1.0, "count", EventSource.HEALTH_CONNECT) }.isFailure)
        assertTrue(runCatching { ActivityDetails(UUID.randomUUID(), " ", 0.0, "count", EventSource.MANUAL) }.isFailure)
        assertTrue(runCatching { AppUsageDetails("app", time, time.plusSeconds(5), 5_001) }.isFailure)
        assertTrue(runCatching { AppUsageDetails(" ", time, time.plusSeconds(5), 0) }.isFailure)
        assertTrue(runCatching { AppUsageDetails("app", time, time, 0) }.isFailure)
        assertEquals(0L, AppUsageDetails("app", time, time.plusSeconds(5), 0).durationMillis)
    }
    @Test fun committedPageRejectsInconsistentCountsAndOffsets() {
        val record = LifeEventRecord(LifeEvent(EventId.new(), EventType.ACTIVITY, time, time))
        assertTrue(runCatching { TimelinePage(listOf(record), 0, 0) }.isFailure)
        assertTrue(runCatching { TimelinePage(emptyList(), -1, 1) }.isFailure)
        assertEquals(emptyList<LifeEventRecord>(), TimelinePage(emptyList(), 5, 3).records)
    }
    @Test fun onlyCompleteSupportedManualRecordsOfferEditing() {
        fun record(type: EventType, imported: Boolean = false) = LifeEventRecord(LifeEvent(EventId.new(), type, time, time,
            source = if (imported) EventSource.HEALTH_CONNECT else EventSource.MANUAL,
            sourceReference = if (imported) "source" else null))
        assertFalse(record(EventType.MOOD).canEditContent)
        assertTrue(record(EventType.MOOD).copy(mood = MoodDetails(3)).canEditContent)
        assertFalse(record(EventType.MOOD, true).copy(mood = MoodDetails(3)).canEditContent)
        assertTrue(record(EventType.JOURNAL).copy(journal = JournalDetails(null, "Body")).canEditContent)
        assertTrue(record(EventType.MEMORY).copy(memory = MemoryDetails()).canEditContent)
        assertFalse(record(EventType.ACTIVITY).canEditContent)
        assertFalse(record(EventType.APP_USAGE).canEditContent)
    }
}
