package com.luma.lifeos.domain.usage

import java.time.*
import org.junit.Assert.*
import org.junit.Test

class UsageSessionsTest {
    private val day = LocalDate.parse("2026-10-11")
    private val midnight = Instant.parse("2026-10-11T00:00:00Z")
    private fun visit(pkg: String, start: Long, end: Long) =
        UsageSession(pkg, midnight.plusSeconds(start), midnight.plusSeconds(end), "App $pkg")

    @Test fun repeatedVisitsAreChronologicalAndClippedAtBothMidnights() {
        val report = sessionUsageReport(listOf(visit("a", 60, 180), visit("a", -60, 60),
            visit("b", 86_340, 86_520), visit("a", 60, 180)), day, ZoneOffset.UTC, UsageHistory.COMPLETE)
        assertEquals(listOf(0L, 60L, 86_340L), report.sessions.map { Duration.between(midnight, it.start).seconds })
        assertEquals(listOf(60_000L, 120_000L, 60_000L), report.sessions.map { it.durationMillis })
        assertEquals(listOf("a", "b"), report.apps.map { it.packageName })
        assertEquals(listOf(180_000L, 60_000L), report.apps.map { it.durationMillis })
        assertEquals(240_000L, report.totalMillis)
    }
    @Test fun samePackageOverlapsAreUnionedAndOngoingIdentityDoesNotChange() {
        val first = visit("a", 0, 120)
        assertEquals(first.identity, first.copy(end = midnight.plusSeconds(180), ongoing = true).identity)
        val report = sessionUsageReport(listOf(first, visit("a", 60, 180)), day, ZoneOffset.UTC, UsageHistory.PARTIAL)
        assertEquals(180_000L, report.totalMillis)
        assertEquals(UsageHistory.PARTIAL, report.history)
    }
    @Test fun civilDayUsesDstBoundariesAndExcludesVisitsEndingAtStart() {
        val date = LocalDate.parse("2026-11-01"); val zone = ZoneId.of("America/New_York")
        val report = sessionUsageReport(listOf(UsageSession("a", Instant.parse("2026-11-01T04:00:00Z"),
            Instant.parse("2026-11-02T05:00:00Z")), UsageSession("b", Instant.parse("2026-11-01T03:00:00Z"),
            Instant.parse("2026-11-01T04:00:00Z"))), date, zone, UsageHistory.COMPLETE)
        assertEquals(90_000_000L, report.totalMillis)
        assertEquals(listOf("a"), report.apps.map { it.packageName })
    }
    @Test fun missingHistoryAndEmptyWindowsDoNotInventVisits() {
        val report = sessionUsageReport(emptyList(), day, ZoneOffset.UTC, UsageHistory.PARTIAL)
        assertTrue(report.apps.isEmpty()); assertTrue(report.sessions.isEmpty())
        assertEquals(UsageHistory.PARTIAL, report.history)
        assertTrue(runCatching { visit("a", 60, 60) }.isFailure)
    }
    @Test fun skippedCivilDayHasNoUsageEvenWhenAVisitSpansTheDateChange() {
        val report = sessionUsageReport(listOf(UsageSession("a", Instant.parse("2011-12-29T00:00:00Z"),
            Instant.parse("2011-12-31T23:00:00Z"))), LocalDate.parse("2011-12-30"), ZoneId.of("Pacific/Apia"), UsageHistory.PARTIAL)
        assertTrue(report.sessions.isEmpty()); assertEquals(0L, report.totalMillis)
    }
}
