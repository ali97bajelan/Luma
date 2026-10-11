package com.luma.lifeos.domain

import com.luma.lifeos.domain.model.MoodInput
import com.luma.lifeos.domain.time.moodInstant
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class MoodTest {
    private val now = Instant.parse("2026-10-11T08:00:00Z")

    @Test fun moodAndOptionalScalesRejectOutOfRangeValues() {
        for (value in listOf(0, 6)) {
            assertThrows(IllegalArgumentException::class.java) { MoodInput(value, now) }
            assertThrows(IllegalArgumentException::class.java) { MoodInput(3, now, energy = value) }
            assertThrows(IllegalArgumentException::class.java) { MoodInput(3, now, stress = value) }
        }
        assertEquals(null, MoodInput(3, now).energy)
        assertEquals(5, MoodInput(1, now, energy = 5, stress = 1).energy)
    }

    @Test fun rejectsNonexistentLocalTimeInsteadOfMovingItForward() {
        assertThrows(IllegalArgumentException::class.java) {
            moodInstant(LocalDateTime.parse("2026-03-08T02:30:00"), ZoneId.of("America/New_York"))
        }
    }

    @Test fun retainsTheOriginalOffsetWhenEditingAnOverlappingLocalTime() {
        val local = LocalDateTime.parse("2026-11-01T01:30:00")
        val zone = ZoneId.of("America/New_York")
        assertEquals(Instant.parse("2026-11-01T05:30:00Z"), moodInstant(local, zone))
        assertEquals(Instant.parse("2026-11-01T06:30:00Z"),
            moodInstant(local, zone, Instant.parse("2026-11-01T06:15:00Z")))
    }
}
