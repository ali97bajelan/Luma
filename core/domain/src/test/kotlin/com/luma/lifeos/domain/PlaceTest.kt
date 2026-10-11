package com.luma.lifeos.domain

import com.luma.lifeos.domain.model.*
import java.time.Instant
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class PlaceTest {
    @Test fun rejectsBlankAndOversizedPlaceDrafts() {
        assertTrue(runCatching { PlaceInput(" \n ") }.isFailure)
        assertTrue(runCatching { PlaceInput("x".repeat(201)) }.isFailure)
        assertTrue(runCatching { PlaceInput("Cafe", "x".repeat(10_001)) }.isFailure)
        assertEquals(200, PlaceInput("x".repeat(200), "x".repeat(10_000)).name.length)
        assertTrue(runCatching { Place(UUID.randomUUID(), " ") }.isFailure)
    }
    @Test fun requiresPairedValidCoordinatesOnStoredPlaces() {
        val id = UUID.randomUUID()
        assertTrue(runCatching { Place(id, "Cafe", latitude = 20.0) }.isFailure)
        assertTrue(runCatching { Place(id, "Cafe", latitude = 91.0, longitude = 0.0) }.isFailure)
        assertTrue(runCatching { Place(id, "Cafe", latitude = 0.0, longitude = Double.NaN) }.isFailure)
        assertEquals(-180.0, Place(id, "Cafe", latitude = -90.0, longitude = -180.0).longitude!!, 0.0)
    }
    @Test fun absentSelectionPreservesWhileExplicitNoneClearsInEveryCaptureContract() {
        assertNull(MoodInput(3, Instant.EPOCH).placeSelection)
        assertNull(JournalInput("Body", Instant.EPOCH).placeSelection)
        assertNull(MemoryInput("Note", Instant.EPOCH).placeSelection)
        assertNull(MemoryMarkerInput().placeSelection)
        assertNull(JournalInput("Body", Instant.EPOCH, placeSelection = PlaceSelection(null)).placeSelection!!.id)
        val id = UUID.randomUUID()
        assertEquals(id, MemoryMarkerInput(placeSelection = PlaceSelection(id)).placeSelection!!.id)
    }
}
