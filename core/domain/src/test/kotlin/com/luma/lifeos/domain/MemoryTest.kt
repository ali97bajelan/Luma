package com.luma.lifeos.domain

import com.luma.lifeos.domain.model.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class MemoryTest {
    @Test fun directMemoryRequiresTextAndValidImportanceAndBoundedDrafts() {
        assertTrue(runCatching { MemoryInput(" ", Instant.EPOCH) }.isFailure)
        assertTrue(runCatching { MemoryInput("Text", Instant.EPOCH, importance = 0) }.isFailure)
        assertTrue(runCatching { MemoryMarkerInput(importance = 6) }.isFailure)
        assertTrue(runCatching { MemoryDetails(importance = -1) }.isFailure)
        assertTrue(runCatching { MemoryInput("x".repeat(10_001), Instant.EPOCH) }.isFailure)
        assertTrue(runCatching { MemoryInput("Text", Instant.EPOCH, title = "x".repeat(201)) }.isFailure)
        assertTrue(runCatching { MemoryMarkerInput(note = "x".repeat(10_001)) }.isFailure)
        assertEquals(10_000, MemoryInput("x".repeat(10_000), Instant.EPOCH, importance = 5).note.length)
    }
    @Test fun onlyCompleteManualMoodAndJournalSupportPromotion() {
        fun event(type: EventType) = LifeEvent(EventId.new(), type, Instant.EPOCH, Instant.EPOCH)
        assertTrue(LifeEventRecord(event(EventType.JOURNAL), journal = JournalDetails(null, "Body")).canPromoteToMemory)
        assertTrue(LifeEventRecord(event(EventType.MOOD), MoodDetails(3)).canPromoteToMemory)
        assertFalse(LifeEventRecord(event(EventType.JOURNAL)).canPromoteToMemory)
        assertFalse(LifeEventRecord(event(EventType.MEMORY), memory = MemoryDetails(note = "Direct")).canPromoteToMemory)
        val imported = event(EventType.MOOD).copy(source = EventSource.HEALTH_CONNECT, sourceReference = "source")
        assertFalse(LifeEventRecord(imported, MoodDetails(3)).canPromoteToMemory)
        val marked = LifeEventRecord(event(EventType.JOURNAL), journal = JournalDetails(null, "Body"), memory = MemoryDetails())
        assertTrue(marked.canPromoteToMemory) // Existing markers remain editable.
        assertNotNull(marked.memory)
    }
}
