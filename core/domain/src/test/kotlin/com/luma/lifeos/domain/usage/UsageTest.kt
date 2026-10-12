package com.luma.lifeos.domain.usage

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class UsageTest {
    private val start = Instant.parse("2026-10-11T00:00:00Z")
    private fun bucket(pkg: String = "org.example.app", end: Instant = start.plusSeconds(3600), duration: Long = 1_000) =
        UsageBucket(pkg, start, end, duration, "Example")
    @Test fun growingBucketsKeepTheirIdentityAndRejectImpossibleValues() {
        assertEquals(bucket().sourceReference, bucket(end = start.plusSeconds(7200), duration = 2000).sourceReference)
        assertNotEquals(bucket().sourceReference, bucket("org.example.other").sourceReference)
        assertTrue(runCatching { bucket(duration = -1) }.isFailure)
        assertTrue(runCatching { bucket(duration = 3_600_001) }.isFailure)
        assertTrue(runCatching { bucket(end = start) }.isFailure)
        assertTrue(runCatching { bucket(" ") }.isFailure)
    }
    @Test fun dailyReportUsesReportedBucketStartAndDoesNotInventEmptyDays() {
        val day = LocalDate.parse("2026-10-11"); val zone = ZoneId.of("UTC")
        assertNull(dailyUsageReport(emptyList(), day, zone))
        val report = dailyUsageReport(listOf(bucket(duration = 5_125), bucket("org.example.other", duration = 2_000),
            bucket().copy(start = start.minusSeconds(86_400), end = start)), day, zone)!!
        assertEquals(7_125L, report.totalMillis)
        assertEquals(listOf(5_125L, 2_000L), report.apps.map { it.durationMillis })
        assertEquals(0L, dailyUsageReport(listOf(bucket(duration = 0)), day, zone)!!.totalMillis)
    }
    @Test fun duplicateObservationsAreNotAddedAndConflictingBucketsAreRejected() {
        val day = LocalDate.parse("2026-10-11"); val zone = ZoneId.of("UTC")
        assertEquals(1_000L, dailyUsageReport(listOf(bucket(), bucket()), day, zone)!!.totalMillis)
        assertTrue(runCatching { dailyUsageReport(listOf(bucket(), bucket(duration = 2000)), day, zone) }.isFailure)
    }
    @Test fun unusedAppsAreExcludedEvenWhenAndroidReportsNonEmptyBounds() {
        val day = LocalDate.parse("2026-10-11"); val zone = ZoneId.of("UTC")
        val report = dailyUsageReport(listOf(bucket(duration = 0), bucket("org.example.used", duration = 2000)), day, zone)!!
        assertEquals(listOf("org.example.used"), report.apps.map { it.packageName })
        assertTrue(dailyUsageReport(listOf(bucket(duration = 0)), day, zone)!!.apps.isEmpty())
    }
    @Test fun shiftedNativeIntervalsAreRejectedWhileGrowingEndsAndAdjacentDaysAreAllowed() {
        val old = bucket()
        assertUnambiguousUsage(listOf(old.copy(end = old.end.plusSeconds(60))), listOf(old))
        assertUnambiguousUsage(listOf(old.copy(start = start.plusSeconds(86_400), end = start.plusSeconds(90_000))), listOf(old))
        val shifted = old.copy(start = old.start.plusSeconds(5), end = old.end.plusSeconds(5))
        assertTrue(runCatching { assertUnambiguousUsage(listOf(shifted), listOf(old)) }.exceptionOrNull() is UsageIntervalChangedException)
    }
}
