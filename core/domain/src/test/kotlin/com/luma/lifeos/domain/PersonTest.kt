package com.luma.lifeos.domain

import com.luma.lifeos.domain.model.*
import java.time.Instant
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class PersonTest {
    @Test fun personInputRejectsBlankNameAndOversizedDraftText() {
        assertTrue(runCatching { PersonInput(" \n ") }.isFailure)
        assertTrue(runCatching { PersonInput("x".repeat(201)) }.isFailure)
        assertTrue(runCatching { PersonInput("Name", "x".repeat(10_001)) }.isFailure)
        assertEquals(200, PersonInput("x".repeat(200), "x".repeat(10_000)).name.length)
        assertTrue(runCatching { Person(UUID.randomUUID(), " ") }.isFailure)
    }
    @Test fun capturesDistinguishPreserveFromExplicitClearAndBoundSavedSelections() {
        assertNull(MoodInput(3, Instant.EPOCH).personIds)
        assertNull(JournalInput("Body", Instant.EPOCH).personIds)
        assertNull(MemoryInput("Note", Instant.EPOCH).personIds)
        assertNull(MemoryMarkerInput().personIds)
        assertEquals(emptySet<UUID>(), JournalInput("Body", Instant.EPOCH, personIds = emptySet()).personIds)
        val ids = (1..101).map { UUID.randomUUID() }.toSet()
        assertTrue(runCatching { MoodInput(3, Instant.EPOCH, personIds = ids) }.isFailure)
        assertTrue(runCatching { JournalInput("Body", Instant.EPOCH, personIds = ids) }.isFailure)
        assertTrue(runCatching { MemoryInput("Note", Instant.EPOCH, personIds = ids) }.isFailure)
        assertTrue(runCatching { MemoryMarkerInput(personIds = ids) }.isFailure)
    }
}
