package com.luma.lifeos.domain.focus

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FocusGoalsTest {
    @Test fun focusWeekDistributionAcceptsConfiguredSaturdayToFridayWeek() {
        val distribution = focusWeekDistribution(
            mapOf(
                LocalDate.parse("2026-10-24") to 15L,
                LocalDate.parse("2026-10-25") to 25L,
                LocalDate.parse("2026-10-26") to 0L,
                LocalDate.parse("2026-10-30") to 30L,
            ),
            firstDayOfWeek = DayOfWeek.SATURDAY,
        )

        assertEquals(FocusWeekDistribution(activeDays = 3, highestDate = LocalDate.parse("2026-10-30")), distribution)
    }

    @Test fun focusWeekDistributionRejectsDatesAcrossConfiguredWeekBoundary() {
        assertThrows(IllegalArgumentException::class.java) {
            focusWeekDistribution(
                mapOf(
                    LocalDate.parse("2026-10-30") to 10L,
                    LocalDate.parse("2026-10-31") to 20L,
                ),
                firstDayOfWeek = DayOfWeek.SATURDAY,
            )
        }
    }
}
