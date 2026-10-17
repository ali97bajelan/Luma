package com.luma.lifeos.domain.habit

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class HabitTimeTest {
    private val tehran = ZoneId.of("Asia/Tehran")
    private val today = LocalDate.of(2026, 10, 17)
    private val now = Instant.parse("2026-10-17T09:00:00Z")

    @Test fun unknownOccurrenceStaysNullAndNextEffectiveDateUsesTrackingZone() {
        assertNull(habitLogTime(today.minusDays(1), null, tehran, now))
        assertEquals(today.plusDays(1), nextHabitEffectiveDate(now, tehran))
    }

    @Test fun futureDatesAndInstantsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            habitLogTime(today.plusDays(1), null, tehran, now)
        }
        assertThrows(IllegalArgumentException::class.java) {
            habitLogTime(today, LocalTime.of(16, 0), tehran, now)
        }
    }

    @Test fun daylightSavingGapIsRejected() {
        val berlin = ZoneId.of("Europe/Berlin")
        val afterGapDate = Instant.parse("2026-03-30T12:00:00Z")

        assertThrows(IllegalArgumentException::class.java) {
            habitLogTime(LocalDate.of(2026, 3, 29), LocalTime.of(2, 30), berlin, afterGapDate)
        }
    }

    @Test fun overlapDefaultsToEarlierOffsetAndAcceptsAnExplicitValidOffset() {
        val berlin = ZoneId.of("Europe/Berlin")
        val overlapDate = LocalDate.of(2026, 10, 25)
        val afterOverlapDate = Instant.parse("2026-10-26T12:00:00Z")
        val localTime = LocalTime.of(2, 30)

        assertEquals(
            Instant.parse("2026-10-25T00:30:00Z"),
            habitLogTime(overlapDate, localTime, berlin, afterOverlapDate),
        )
        assertEquals(
            Instant.parse("2026-10-25T01:30:00Z"),
            habitLogTime(overlapDate, localTime, berlin, afterOverlapDate, ZoneOffset.ofHours(1)),
        )
    }

    @Test fun occurrenceDateMustMatchItsZoneAndExplicitInvalidOffsetsAreRejected() {
        val berlin = ZoneId.of("Europe/Berlin")
        val afterOverlapDate = Instant.parse("2026-10-26T12:00:00Z")
        val overlapDate = LocalDate.of(2026, 10, 25)

        assertThrows(IllegalArgumentException::class.java) {
            habitLogTime(overlapDate, LocalTime.of(2, 30), berlin, afterOverlapDate, ZoneOffset.UTC)
        }
        assertThrows(IllegalArgumentException::class.java) {
            HabitLogInput(
                habitId = java.util.UUID.randomUUID(),
                date = today,
                zone = tehran,
                occurredAt = Instant.parse("2026-10-16T12:00:00Z"),
            )
        }
    }
}
