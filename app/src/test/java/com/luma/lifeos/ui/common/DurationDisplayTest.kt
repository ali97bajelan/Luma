package com.luma.lifeos.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DurationDisplayTest {
    @Test fun boundariesUseWholeSecondsThenWholeMinutes() {
        assertEquals(DurationDisplay.Zero, durationDisplay(0))
        assertEquals(DurationDisplay.UnderSecond, durationDisplay(1))
        assertEquals(DurationDisplay.UnderSecond, durationDisplay(999))
        assertEquals(DurationDisplay.Seconds(1), durationDisplay(1_000))
        assertEquals(DurationDisplay.Seconds(59), durationDisplay(59_999))
        assertEquals(DurationDisplay.Minutes(1), durationDisplay(60_000))
        assertEquals(DurationDisplay.Minutes(59), durationDisplay(3_599_999))
        assertEquals(DurationDisplay.HoursMinutes(1, 0), durationDisplay(3_600_000))
        assertEquals(DurationDisplay.HoursMinutes(1, 0), durationDisplay(3_600_001))
    }

    @Test fun longMaximumDoesNotOverflow() {
        assertEquals(DurationDisplay.HoursMinutes(Long.MAX_VALUE / 3_600_000,
            Long.MAX_VALUE / 60_000 % 60), durationDisplay(Long.MAX_VALUE))
    }

    @Test fun negativeRawDurationIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { durationDisplay(-1) }
    }
}
