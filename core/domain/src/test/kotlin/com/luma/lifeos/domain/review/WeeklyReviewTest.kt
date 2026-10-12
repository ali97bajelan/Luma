package com.luma.lifeos.domain.review

import com.luma.lifeos.domain.model.*
import com.luma.lifeos.domain.summary.MoodSample
import com.luma.lifeos.domain.usage.*
import java.time.*
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class WeeklyReviewTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.parse("2026-10-14")
    private val period get() = reviewPeriod(today, today, zone)
    private fun row(n: Int, date: String = "2026-10-12", score: Int? = null) = ReviewRecord(
        EventId(UUID(0, n.toLong())), LocalDate.parse(date).atTime(12, 0).toInstant(ZoneOffset.UTC),
        mood = score?.let { MoodSample(it) })

    @Test fun currentWeekComparesOnlyMatchingWeekdaysAndRejectsFutureWeeks() {
        val p = period
        assertEquals(LocalDate.parse("2026-10-12"), p.current.start)
        assertEquals(LocalDate.parse("2026-10-14"), p.current.through)
        assertEquals(LocalDate.parse("2026-10-05"), p.previous.start)
        assertEquals(LocalDate.parse("2026-10-07"), p.previous.through)
        assertTrue(p.partial)
        val monday = reviewPeriod(LocalDate.parse("2026-10-12"), LocalDate.parse("2026-10-12"), zone)
        assertEquals(monday.current.start, monday.current.through)
        assertEquals(monday.previous.start, monday.previous.through)
        assertTrue(runCatching { reviewPeriod(today.plusWeeks(1), today, zone) }.isFailure)
    }
    @Test fun completedWeeksUseCivilBoundsIncludingDstAndSkippedDates() {
        val p = reviewPeriod(LocalDate.parse("2026-03-08"), today, ZoneId.of("America/New_York"))
        assertFalse(p.partial)
        assertEquals(Instant.parse("2026-03-02T05:00:00Z"), p.current.startInstant(p.zone))
        assertEquals(Instant.parse("2026-03-09T04:00:00Z"), p.current.endInstant(p.zone))
        assertEquals(7, p.current.dates.size)
        val skipped = reviewPeriod(LocalDate.parse("2011-12-30"), today, ZoneId.of("Pacific/Apia"))
        val result = calculateWeeklyReview(skipped, emptyList(), emptyList())
        assertEquals(7, result.current.moodDays.size)
        assertEquals(0, result.current.records)
        assertNull(result.current.mood)
    }
    @Test fun unequalDailySamplesUseWeightedAveragesAndPromotionCountsOneEntry() {
        val rows = listOf(row(1, score = 5), row(2, score = 5), row(3, "2026-10-13", 3).copy(memory = true),
            row(4).copy(journal = true, memory = true), row(5, "2026-10-05", 2), row(6, "2026-10-15", 1))
        val report = calculateWeeklyReview(period, rows, emptyList())
        assertEquals(4, report.current.records)
        assertEquals(1, report.current.journals)
        assertEquals(2, report.current.memories)
        assertEquals(13L, report.current.mood!!.score.sum)
        assertEquals(13.0 / 3, report.current.mood!!.score.average, 0.00001)
        assertEquals(1, report.previous.records)
        assertEquals(2.0, report.previous.mood!!.score.average, 0.0)
        assertEquals(3, report.current.moodDays.size)
        assertEquals(2, report.current.moodDays[0].mood!!.score.samples)
        assertNull(report.current.moodDays[2].mood)
    }
    @Test fun catalogIdentityAndEmotionFrequenciesAreDeterministic() {
        val a = ReviewName(UUID(0, 1), "Same")
        val b = ReviewName(UUID(0, 2), "Same")
        val rows = listOf(row(1, score = 3).copy(emotions = listOf(b, a), people = listOf(a, b), place = a),
            row(2, score = 4).copy(emotions = listOf(a), people = listOf(a), place = a))
        val report = calculateWeeklyReview(period, rows, emptyList())
        assertEquals(listOf(2, 1), report.current.people.map { it.records })
        assertEquals(listOf(a, b), report.current.emotions.map { it.name })
        assertEquals(2, report.current.places.single().records)
        assertEquals(report, calculateWeeklyReview(period, rows.reversed(), emptyList()))
        assertEquals(report, calculateWeeklyReview(period, rows + rows.first(), emptyList()))
        assertTrue(runCatching { calculateWeeklyReview(period, rows + rows.first().copy(memory = true), emptyList()) }.isFailure)
    }
    @Test fun absentScalesAndActivityStayUnavailableWhileRecordedZeroSurvives() {
        val activity = ActivityDetails(UUID(0, 7), "steps", 0.0, "count", EventSource.HEALTH_CONNECT)
        val report = calculateWeeklyReview(period, listOf(row(1, score = 4), row(2).copy(activities = listOf(activity))), emptyList())
        assertNull(report.current.mood!!.energy)
        assertNull(report.current.mood!!.stress)
        assertNull(report.current.usage)
        assertEquals("0", report.current.activities.single().total.total.toPlainString())
        assertEquals(1, report.current.activities.single().dates)
        assertTrue(report.previous.activities.isEmpty())
    }
    @Test fun activityGroupsKeepUnitsProvidersAndDateCoverageSeparate() {
        fun activity(n: Long, type: String, unit: String, value: Double, source: EventSource) = ActivityDetails(UUID(0, n), type, value, unit, source)
        val report = calculateWeeklyReview(period, listOf(row(1).copy(activities = listOf(
            activity(1, "distance", "km", 0.1, EventSource.MANUAL), activity(2, "distance", "m", 100.0, EventSource.MANUAL))),
            row(2, "2026-10-13").copy(activities = listOf(activity(3, "distance", "km", 0.2, EventSource.MANUAL))),
            row(3).copy(activities = listOf(activity(4, "distance", "km", 2.0, EventSource.HEALTH_CONNECT)))), emptyList())
        assertEquals(3, report.current.activities.size)
        val km = report.current.activities.single { it.total.source == EventSource.MANUAL && it.total.unit == "km" }
        assertEquals("0.3", km.total.total.toPlainString())
        assertEquals(2, km.dates)
    }
    @Test fun weeklyUsageSumsOnlySelectedDatesAndRetainsHistoryCoverage() {
        val first = DailyUsageReport(LocalDate.parse("2026-10-12"), zone,
            listOf(AppUsageTotal("reader", "Reader", 1500)), 1500, history = UsageHistory.PARTIAL)
        val second = DailyUsageReport(LocalDate.parse("2026-10-13"), zone,
            listOf(AppUsageTotal("reader", null, 2500)), 2500, history = UsageHistory.UNAVAILABLE)
        val zero = DailyUsageReport(LocalDate.parse("2026-10-14"), zone, emptyList(), 0, history = UsageHistory.COMPLETE)
        val report = calculateWeeklyReview(period, listOf(row(1)), listOf(second, first, zero))
        assertEquals(1, report.current.records)
        assertEquals(4000L, report.current.usage!!.totalMillis)
        assertEquals(3, report.current.usage!!.days.size)
        assertEquals(1, report.current.usage!!.completeDates)
        assertEquals("Reader", report.current.usage!!.apps.single().appLabel)
        assertEquals(report, calculateWeeklyReview(period, listOf(row(1)), listOf(zero, first, second)))
        assertTrue(runCatching { calculateWeeklyReview(period, emptyList(), listOf(first, first)) }.isFailure)
    }
    @Test fun partialEmptyUsageWindowDoesNotEstablishMeasuredZero() {
        val partial = DailyUsageReport(today, zone, emptyList(), 0, history = UsageHistory.PARTIAL)
        assertNull(calculateWeeklyReview(period, emptyList(), listOf(partial)).current.usage!!.totalMillis)
    }
}
