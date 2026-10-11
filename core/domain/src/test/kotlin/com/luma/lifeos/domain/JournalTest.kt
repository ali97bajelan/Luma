package com.luma.lifeos.domain

import com.luma.lifeos.domain.model.*
import java.time.Instant
import java.util.Locale
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class JournalTest {
    @Test fun blankBodyAndBlankTagsAreRejected() {
        assertTrue(runCatching { JournalInput(" \n ", Instant.EPOCH) }.isFailure)
        assertTrue(runCatching { JournalInput("Body", Instant.EPOCH, tagNames = setOf(" ")) }.isFailure)
        assertTrue(runCatching { JournalDetails(null, " ") }.isFailure)
    }
    @Test fun oversizedJournalTitleAndTagInputsAreRejected() {
        assertTrue(runCatching { JournalInput("x".repeat(50_001), Instant.EPOCH) }.isFailure)
        assertTrue(runCatching { JournalInput("Body", Instant.EPOCH, "x".repeat(201)) }.isFailure)
        assertTrue(runCatching { JournalInput("Body", Instant.EPOCH, tagNames = setOf("x".repeat(51))) }.isFailure)
        assertTrue(runCatching { JournalInput("Body", Instant.EPOCH, tagNames = (1..51).map { "tag$it" }.toSet()) }.isFailure)
        assertEquals(50_000, JournalInput("x".repeat(50_000), Instant.EPOCH).body.length)
    }
    @Test fun tagIdentityIgnoresCaseAndSurroundingWhitespaceInEveryLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"))
            assertEquals("ideas", Tag.normalize("  IDEAS  "))
        } finally { Locale.setDefault(previous) }
    }
    @Test fun journalRetainsBodyFormattingAndCannotBeAttachedToMood() {
        val body = "  First\n\nSecond  "
        assertEquals(body, JournalInput(body, Instant.EPOCH).body)
        val event = LifeEvent(EventId.new(), EventType.MOOD, Instant.EPOCH, Instant.EPOCH)
        assertTrue(runCatching { LifeEventRecord(event, journal = JournalDetails(null, body)) }.isFailure)
        assertTrue(runCatching { Tag(UUID.randomUUID(), " ") }.isFailure)
    }
}
