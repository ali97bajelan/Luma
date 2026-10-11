package com.luma.lifeos.domain

import com.luma.lifeos.domain.model.*
import java.time.Instant
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class PhotoTest {
    @Test fun rejectsUnsafePathsAndInvalidMetadata() {
        val id = UUID.randomUUID()
        for (path in listOf("", "/private/photo", "../photo", "photos/../photo", "photos/./photo", "photos//photo"))
            assertTrue(path, runCatching { PhotoAttachment(id, path) }.isFailure)
        assertTrue(runCatching { PhotoMetadata("text/plain", 4, 10, 20) }.isFailure)
        assertTrue(runCatching { PhotoMetadata("image/png", 0, 10, 20) }.isFailure)
        assertTrue(runCatching { PhotoMetadata("image/png", 4, 0, 20) }.isFailure)
        assertEquals("photos/legacy.jpg", PhotoAttachment(id, "photos/legacy.jpg").relativePath)
    }
    @Test fun snapshotsPreserveByDefaultAndRejectDuplicatesAndExcessiveDrafts() {
        val photo = PhotoAttachment(UUID.randomUUID(), "photos/a.img")
        assertNull(JournalInput("Body", Instant.EPOCH).photos)
        assertNull(MemoryInput("Note", Instant.EPOCH).photos)
        assertNull(MemoryMarkerInput().photos)
        assertEquals(emptyList<PhotoAttachment>(), JournalInput("Body", Instant.EPOCH, photos = emptyList()).photos)
        assertTrue(runCatching { MemoryMarkerInput(photos = listOf(photo, photo)) }.isFailure)
        assertTrue(runCatching { JournalInput("Body", Instant.EPOCH, photos = listOf(photo, photo.copy(id = UUID.randomUUID()))) }.isFailure)
        val photos = (1..21).map { PhotoAttachment(UUID.randomUUID(), "photos/$it.img") }
        assertTrue(runCatching { MemoryInput("Note", Instant.EPOCH, photos = photos) }.isFailure)
    }
}
