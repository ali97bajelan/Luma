package com.luma.lifeos.domain.summary

import com.luma.lifeos.domain.model.*
import java.math.BigDecimal
import java.time.*
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class DailySummaryTest {
    private val date = LocalDate.parse("2026-10-11")
    private val start = Instant.parse("2026-10-11T00:00:00Z")
    private fun record(n: Int, time: Instant = start) = SummaryRecord(EventId.parse("00000000-0000-0000-0000-${n.toString().padStart(12, '0')}"), time)
    private fun activity(n: Int, value: Double, unit: String = "count", source: EventSource = EventSource.HEALTH_CONNECT) =
        ActivityDetails(UUID(0, n.toLong()), "steps", value, unit, source)
    @Test fun mixedMetricsMatchStoredValuesAndPromotionsKeepOneEvent() {
        val records = listOf(record(1).copy(mood = MoodSample(2, energy = 1)),
            record(2).copy(mood = MoodSample(5, stress = 4), memory = true),
            record(3).copy(journal = true, memory = true), record(4).copy(memory = true),
            record(5).copy(usage = AppUsageDetails("org.example", start, start.plusSeconds(3600), 5_125, "Example")),
            record(6).copy(activities = listOf(activity(1, 1200.0), activity(2, 300.0))))
        val result = calculateDailySummary(date, ZoneOffset.UTC, records)
        assertEquals(5, result.records); assertEquals(1, result.journals); assertEquals(3, result.memories)
        assertEquals(ScaleTotal(2, 7), result.mood!!.score); assertEquals(3.5, result.mood!!.score.average, 0.0)
        assertEquals(ScaleTotal(1, 1), result.mood!!.energy); assertEquals(ScaleTotal(1, 4), result.mood!!.stress)
        assertEquals(5_125L, result.usage!!.totalMillis); assertEquals("Example", result.usage!!.apps.single().appLabel)
        assertEquals("1500", result.activities!!.single().total.toPlainString())
    }
    @Test fun missingSignalsAreUnavailableWhileMeasuredZeroIsRetained() {
        val empty = calculateDailySummary(date, ZoneOffset.UTC, listOf(record(1)))
        assertEquals(1, empty.records); assertEquals(0, empty.journals); assertEquals(0, empty.memories)
        assertNull(empty.mood); assertNull(empty.usage); assertNull(empty.activities)
        val zero = calculateDailySummary(date, ZoneOffset.UTC, listOf(record(1).copy(
            activities = listOf(activity(1, 0.0)), usage = AppUsageDetails("org.example", start, start.plusSeconds(60), 0))))
        assertEquals(0L, zero.usage!!.totalMillis); assertEquals(BigDecimal.ZERO, zero.activities!!.single().total)
        assertTrue(zero.usage!!.apps.isEmpty())
    }
    @Test fun screenTimeDoesNotCountAsEventsWhileItsMetricsRemainAvailable() {
        val usage = record(1).copy(usage = AppUsageDetails("org.example", start, start.plusSeconds(3600), 5_125))
        val result = calculateDailySummary(date, ZoneOffset.UTC, listOf(usage))
        assertEquals(0, result.records); assertEquals(5_125L, result.usage!!.totalMillis)
        assertEquals(1, calculateDailySummary(date, ZoneOffset.UTC, listOf(usage, record(2).copy(journal = true))).records)
    }
    @Test fun orderingAndDuplicateSnapshotsCannotChangeTheMetrics() {
        val rows = listOf(record(1).copy(activities = listOf(activity(2, 0.2), activity(1, 0.1))),
            record(2).copy(journal = true), record(3).copy(mood = MoodSample(4)))
        val expected = calculateDailySummary(date, ZoneOffset.UTC, rows)
        assertEquals("0.3", expected.activities!!.single().total.toPlainString())
        assertEquals(expected, calculateDailySummary(date, ZoneOffset.UTC, rows.reversed() + rows.first()))
        assertTrue(runCatching { calculateDailySummary(date, ZoneOffset.UTC, rows + rows.first().copy(memory = true)) }.isFailure)
    }
    @Test fun activityTypesUnitsAndProvidersRemainSeparate() {
        val rows = listOf(record(1).copy(activities = listOf(activity(1, 10.0), activity(2, 20.0, source = EventSource.MANUAL),
            activity(3, 3.0, unit = "km"), activity(4, 5.0).copy(type = "sleep", unit = "minutes"))))
        val result = calculateDailySummary(date, ZoneOffset.UTC, rows).activities!!
        assertEquals(4, result.size)
        assertEquals(listOf("5", "10", "20", "3"), result.map { it.total.toPlainString() })
        assertEquals(listOf("sleep", "steps", "steps", "steps"), result.map { it.type })
        assertTrue(runCatching { calculateDailySummary(date, ZoneOffset.UTC, listOf(record(1).copy(activities = listOf(activity(1, 1.0), activity(1, 2.0))))) }.isFailure)
    }
    @Test fun usageDatesFollowNativeStartsEvenWhenTheParentOccursElsewhere() {
        val outside = record(1, start.minusSeconds(86_400)).copy(usage = AppUsageDetails("org.example", start, start.plusSeconds(3600), 5_125))
        val result = calculateDailySummary(date, ZoneOffset.UTC, listOf(outside, record(2, start.plusSeconds(86_400))))
        assertEquals(0, result.records); assertEquals(5_125L, result.usage!!.totalMillis)
        assertNull(calculateDailySummary(date.minusDays(1), ZoneOffset.UTC, listOf(outside)).usage)
        assertTrue(runCatching { calculateDailySummary(date, ZoneOffset.UTC, listOf(outside, outside.copy(id = record(3).id,
            usage = outside.usage!!.copy(durationMillis = 6_000)))) }.isFailure)
    }
    @Test fun civilBoundsIncludeDstTransitionsAndExcludeTheNextMidnight() {
        val day = LocalDate.parse("2026-03-08"); val zone = ZoneId.of("America/New_York")
        val rows = listOf(record(1, Instant.parse("2026-03-08T04:59:59Z")), record(2, Instant.parse("2026-03-08T05:00:00Z")),
            record(3, Instant.parse("2026-03-09T03:59:59Z")), record(4, Instant.parse("2026-03-09T04:00:00Z")))
        assertEquals(2, calculateDailySummary(day, zone, rows).records)
        val skipped = calculateDailySummary(LocalDate.parse("2011-12-30"), ZoneId.of("Pacific/Apia"), rows)
        assertEquals(0, skipped.records); assertNull(skipped.usage)
    }
}
