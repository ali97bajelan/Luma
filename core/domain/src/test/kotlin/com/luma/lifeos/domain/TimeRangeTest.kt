package com.luma.lifeos.domain

import com.luma.lifeos.domain.time.TimeRange
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class TimeRangeTest {
    @Test fun springDayIs23HoursAndAutumnDayIs25Hours() {
        val zone = ZoneId.of("America/New_York")
        val spring = TimeRange.forDay(LocalDate.of(2026, 3, 8), zone)
        val autumn = TimeRange.forDay(LocalDate.of(2026, 11, 1), zone)
        assertEquals(23L, Duration.between(spring.start, spring.endExclusive).toHours())
        assertEquals(25L, Duration.between(autumn.start, autumn.endExclusive).toHours())
    }

    @Test fun localMidnightHonorsTehranOffset() {
        val day = TimeRange.forDay(LocalDate.of(2026, 10, 10), ZoneId.of("Asia/Tehran"))
        assertEquals(Instant.parse("2026-10-09T20:30:00Z"), day.start)
        assertEquals(Instant.parse("2026-10-10T20:30:00Z"), day.endExclusive)
    }

    @Test fun boundariesAssignAnEventToExactlyOneDay() {
        val day = TimeRange.forDay(LocalDate.of(2026, 10, 10), ZoneId.of("UTC"))
        assertTrue(day.start in day)
        assertTrue(day.endExclusive.minusNanos(1) in day)
        assertFalse(day.endExclusive in day)
        assertFalse(day.start.minusNanos(1) in day)
    }

    @Test fun rejectsEmptyAndReversedRanges() {
        val now = Instant.parse("2026-10-10T00:00:00Z")
        assertThrows(IllegalArgumentException::class.java) { TimeRange(now, now) }
        assertThrows(IllegalArgumentException::class.java) { TimeRange(now, now.minusSeconds(1)) }
    }
}
