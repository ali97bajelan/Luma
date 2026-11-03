package com.luma.lifeos.domain.focus

import com.luma.lifeos.domain.model.Tag
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.time.Instant

class FocusMetadataTest {
    @Test fun trimsAndPreservesLabels() {
        assertEquals(FocusStartInput("کار 😀", "Work"), normalizeFocusStartInput(FocusStartInput("  کار 😀  ", " Work ")))
        assertEquals(FocusStartInput("Study", null), normalizeFocusStartInput(FocusStartInput(" Study ", null)))
        assertEquals(Tag.normalize("Work"), Tag.normalize("work"))
        assertEquals(FocusMetadata(null, null), FocusMetadata())
        assertEquals("کار 😀", FocusTag(UUID.randomUUID(), "کار 😀").name)
    }

    @Test fun legacySessionHasNullMetadata() {
        val session = createFocusSession(UUID.randomUUID(), UUID.randomUUID(), FocusTimeSample(Instant.EPOCH, 0, "boot"))
        assertNull(session.metadata.name)
        assertNull(session.metadata.tag)
    }

    @Test fun validatesNameCodePoints() {
        assertEquals("😀".repeat(120), normalizeFocusStartInput(FocusStartInput("😀".repeat(120), null)).name)
        invalid(FocusStartInput("😀".repeat(121), null))
        invalid(FocusStartInput("  ", null))
        invalid(FocusStartInput("abc\n", null))
        invalid(FocusStartInput("a\u0000b", null))
    }

    @Test fun validatesTagUtf16Units() {
        assertEquals("😀".repeat(25), normalizeFocusStartInput(FocusStartInput("x", "😀".repeat(25))).tagName)
        invalid(FocusStartInput("x", "😀".repeat(25) + "a"))
        invalid(FocusStartInput("x", " "))
        invalid(FocusStartInput("x", "work\r"))
        invalid(FocusStartInput("x", "a\u0001b"))
    }

    private fun invalid(input: FocusStartInput) {
        try { normalizeFocusStartInput(input); fail("Expected invalid input") } catch (_: IllegalArgumentException) { }
    }
}
