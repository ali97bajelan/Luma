package com.luma.lifeos.ui.common

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureTimestampRowTest {
    private val original = LocalDateTime.of(2026, 10, 14, 8, 12, 37, 123_000_000)

    @Test fun changingOnlyDateKeepsSecondsAndMilliseconds() {
        val selectedDate = LocalDate.of(2026, 10, 15)

        val selected = captureTimestampForSelection(original, selectedDate, original.hour, original.minute)

        assertEquals(LocalDateTime.of(2026, 10, 15, 8, 12, 37, 123_000_000), selected)
    }

    @Test fun selectingSameVisibleMinuteKeepsSecondsAndMilliseconds() {
        val selected = captureTimestampForSelection(original, original.toLocalDate(), 8, 12)

        assertEquals(original, selected)
    }

    @Test fun deliberatelyChangingTimeUsesMinutePrecision() {
        val selected = captureTimestampForSelection(original, original.toLocalDate(), 8, 13)

        assertEquals(LocalDateTime.of(2026, 10, 14, 8, 13), selected)
    }
}
