package com.luma.lifeos.domain

import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.EventSource
import com.luma.lifeos.domain.model.EventType
import com.luma.lifeos.domain.model.LifeEvent
import com.luma.lifeos.domain.model.SourceIdentity
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class LifeEventTest {
    private val now = Instant.parse("2026-10-10T08:00:00Z")
    private fun event(id: String = "00000000-0000-0000-0000-000000000001") = LifeEvent(
        id = EventId.parse(id), type = EventType.JOURNAL, occurredAt = now, createdAt = now,
    )

    @Test fun manualRecordIsEditableAndHasNoUpstreamIdentity() {
        assertTrue(event().isEditable)
        assertNull(event().sourceIdentity)
    }

    @Test fun importedRecordHasStableIdentityAndIsNotManuallyEditable() {
        val imported = event().copy(source = EventSource.HEALTH_CONNECT, sourceReference = "steps-42")
        assertFalse(imported.isEditable)
        assertEquals(SourceIdentity(EventSource.HEALTH_CONNECT, "steps-42"), imported.sourceIdentity)
    }

    @Test fun rejectsEndBeforeOccurrence() {
        assertThrows(IllegalArgumentException::class.java) { event().copy(endedAt = now.minusSeconds(1)) }
    }

    @Test fun rejectsUpdateBeforeCreation() {
        assertThrows(IllegalArgumentException::class.java) { event().copy(updatedAt = now.minusSeconds(1)) }
    }

    @Test fun permitsBackdatedAndFutureOccurrenceIndependentOfAuditTimestamps() {
        assertEquals(now.minusSeconds(3600), event().copy(occurredAt = now.minusSeconds(3600)).occurredAt)
        assertEquals(now.plusSeconds(3600), event().copy(occurredAt = now.plusSeconds(3600)).occurredAt)
    }

    @Test fun rejectsMissingAndBlankImportedIdentities() {
        assertThrows(IllegalArgumentException::class.java) { event().copy(source = EventSource.USAGE_STATS) }
        assertThrows(IllegalArgumentException::class.java) {
            event().copy(source = EventSource.USAGE_STATS, sourceReference = " ")
        }
    }

    @Test fun rejectsUpstreamIdentityOnManualRecord() {
        assertThrows(IllegalArgumentException::class.java) { event().copy(sourceReference = "remote") }
        assertThrows(IllegalArgumentException::class.java) { SourceIdentity(EventSource.MANUAL, "remote") }
    }

    @Test fun equalTimestampOrderingIsStable() {
        val a = event()
        val b = event("00000000-0000-0000-0000-000000000002")
        val earlier = b.copy(occurredAt = now.minusSeconds(1))
        assertEquals(listOf(earlier, a, b), listOf(b, a, earlier).sortedWith(LifeEvent.chronologicalOrder))
    }

    @Test fun uuidRoundTripsAndRejectsInvalidOrNoncanonicalValues() {
        val id = EventId.new()
        assertEquals(id, EventId.parse(id.toString()))
        assertThrows(IllegalArgumentException::class.java) { EventId.parse("not-an-id") }
        assertThrows(IllegalArgumentException::class.java) { EventId.parse("1-1-1-1-1") }
    }
}
