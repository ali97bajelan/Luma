package com.luma.lifeos.integration.usage

import com.luma.lifeos.domain.time.TimeRange
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class NativeUsageSessionsTest {
    private val range = TimeRange(Instant.ofEpochMilli(1000), Instant.ofEpochMilli(20_000))
    private fun event(time: Long, kind: NativeUsageEventKind, pkg: String? = "a", activity: String? = "Main") =
        NativeUsageEvent(time, kind, pkg, activity)
    @Test fun repeatedVisitsAndDuplicateResumesKeepOpeningTimes() {
        val sessions = reconstructUsageSessions(listOf(event(2000, NativeUsageEventKind.RESUME),
            event(2000, NativeUsageEventKind.RESUME), event(5000, NativeUsageEventKind.PAUSE),
            event(7000, NativeUsageEventKind.RESUME), event(9000, NativeUsageEventKind.PAUSE)), range)
        assertEquals(listOf(2000L, 7000L), sessions.map { it.start.toEpochMilli() })
        assertEquals(listOf(3000L, 2000L), sessions.map { it.durationMillis })
    }
    @Test fun concurrentActivitiesInOneAppAreNotDoubleCounted() {
        val sessions = reconstructUsageSessions(listOf(event(2000, NativeUsageEventKind.RESUME),
            event(3000, NativeUsageEventKind.RESUME, activity = "Detail"),
            event(4000, NativeUsageEventKind.PAUSE), event(6000, NativeUsageEventKind.PAUSE, activity = "Detail")), range)
        assertEquals(4000L, sessions.single().durationMillis)
    }
    @Test fun orphanClosesAndStartupNeverInventOpeningsOrEnds() {
        val sessions = reconstructUsageSessions(listOf(event(2000, NativeUsageEventKind.PAUSE),
            event(3000, NativeUsageEventKind.RESUME), event(5000, NativeUsageEventKind.STARTUP, null),
            event(6000, NativeUsageEventKind.PAUSE), event(7000, NativeUsageEventKind.RESUME)), range)
        assertEquals(7000L, sessions.single().start.toEpochMilli())
        assertEquals(20_000L, sessions.single().end.toEpochMilli()); assertTrue(sessions.single().ongoing)
    }
    @Test fun screenOffLockAndShutdownEndKnownForegroundVisits() {
        listOf(NativeUsageEventKind.SCREEN_OFF, NativeUsageEventKind.LOCK, NativeUsageEventKind.SHUTDOWN).forEach { kind ->
            val sessions = reconstructUsageSessions(listOf(event(2000, NativeUsageEventKind.RESUME), event(5000, kind, null)), range)
            assertEquals(3000L, sessions.single().durationMillis); assertFalse(sessions.single().ongoing)
        }
    }
    @Test fun sameTimestampActivityHandoffStaysOneVisitAndDifferentAppsMayOverlap() {
        val sessions = reconstructUsageSessions(listOf(event(2000, NativeUsageEventKind.RESUME),
            event(3000, NativeUsageEventKind.PAUSE), event(3000, NativeUsageEventKind.RESUME, activity = "Detail"),
            event(4000, NativeUsageEventKind.RESUME, "b"), event(5000, NativeUsageEventKind.PAUSE, activity = "Detail"),
            event(6000, NativeUsageEventKind.PAUSE, "b")), range)
        assertEquals(listOf("a", "b"), sessions.map { it.packageName })
        assertEquals(listOf(3000L, 2000L), sessions.map { it.durationMillis })
    }
    @Test fun sameClassPauseAndResumeAtOneTimestampDoNotLoseTheLaterInterval() {
        val sessions = reconstructUsageSessions(listOf(event(2000, NativeUsageEventKind.RESUME),
            event(3000, NativeUsageEventKind.PAUSE), event(3000, NativeUsageEventKind.RESUME),
            event(5000, NativeUsageEventKind.PAUSE)), range)
        assertEquals(3000L, sessions.sumOf { it.durationMillis })
        assertEquals(5000L, sessions.last().end.toEpochMilli())
    }
    @Test fun twoForegroundInstancesOfTheSameClassCountUntilTheLastPause() {
        val sessions = reconstructUsageSessions(listOf(event(2000, NativeUsageEventKind.RESUME),
            event(2500, NativeUsageEventKind.RESUME), event(3000, NativeUsageEventKind.PAUSE),
            event(5000, NativeUsageEventKind.PAUSE)), range)
        assertEquals(3000L, sessions.single().durationMillis)
    }
}
