package com.luma.lifeos.domain.focus

import com.luma.lifeos.domain.time.TimeRange
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CancellationException
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusInsightsTest {
    @Test fun clipsEachTagAndLongestSessionToTheSelectedPeriod() {
        val zone = ZoneId.of("Asia/Tehran")
        val yesterday = TimeRange.forDay(LocalDate.parse("2026-10-27"), zone)
        val today = TimeRange.forDay(LocalDate.parse("2026-10-28"), zone)
        val study = FocusTag(uuid(1), "Study")
        val work = FocusTag(uuid(2), "Work")
        val studySession = terminal(
            id = uuid(10),
            runId = uuid(110),
            start = yesterday.endExclusive.minus(Duration.ofMinutes(10)),
            millis = 30 * MINUTE,
            metadata = FocusMetadata("Read chapter 4", study),
        )
        val workSession = terminal(
            id = uuid(11),
            runId = uuid(111),
            start = today.start.plus(Duration.ofHours(1)),
            millis = 25 * MINUTE,
            metadata = FocusMetadata("Plan launch", work),
        )

        val result = focusInsights(listOf(studySession, workSession), today)

        assertEquals(45 * MINUTE, result.totalMillis)
        assertEquals(2, result.sessions)
        assertEquals(22 * MINUTE + 30_000, result.averageMillis)
        assertEquals(0, result.interruptedContributors)
        assertEquals(listOf(work, study), result.tags.map { it.tag })
        assertEquals(listOf(25 * MINUTE, 20 * MINUTE), result.tags.map { it.totalMillis })
        assertEquals(listOf(1, 1), result.tags.map { it.sessions })
        assertEquals(listOf(BigDecimal("55.6"), BigDecimal("44.4")), result.tags.map { it.percentage })
        assertEquals(workSession, result.longest?.session)
        assertEquals(25 * MINUTE, result.longest?.contributionMillis)
        assertEquals(45 * MINUTE, result.tags.sumOf(FocusTagSummary::totalMillis))
    }

    @Test fun groupsUntaggedSessionsAndCountsPausedRunsAsOneSession() {
        val start = Instant.parse("2026-10-28T09:00:00Z")
        val id = uuid(20)
        val pausedSession = terminal(
            id = id,
            runs = listOf(
                run(id, uuid(120), start, 10 * MINUTE, 1),
                run(id, uuid(121), start.plus(Duration.ofMinutes(40)), 5 * MINUTE, 2),
            ),
            metadata = FocusMetadata("Deep work", null),
        )
        val range = TimeRange(start, start.plus(Duration.ofHours(2)))

        val result = focusInsights(listOf(pausedSession), range)

        assertEquals(15 * MINUTE, result.totalMillis)
        assertEquals(1, result.sessions)
        assertEquals(15 * MINUTE, result.averageMillis)
        assertEquals(15 * MINUTE, result.tags.single().totalMillis)
        assertNull(result.tags.single().tag)
        assertEquals(BigDecimal("100.0"), result.tags.single().percentage)
        assertEquals(15 * MINUTE, result.longest?.contributionMillis)
    }

    @Test fun interruptedPositiveCreditCountsButZeroCreditDoesNot() {
        val start = Instant.parse("2026-10-28T09:00:00Z")
        val interrupted = terminal(
            id = uuid(30), runId = uuid(130), start = start, millis = 4 * MINUTE,
            state = FocusState.INTERRUPTED,
        )
        val zero = terminal(id = uuid(31), runId = uuid(131), start = start, millis = 0)
        val range = TimeRange(start, start.plus(Duration.ofHours(1)))

        val result = focusInsights(listOf(interrupted, zero), range)
        val empty = focusInsights(emptyList(), range)

        assertEquals(4 * MINUTE, result.totalMillis)
        assertEquals(1, result.sessions)
        assertEquals(1, result.interruptedContributors)
        assertEquals(interrupted.id, result.longest?.session?.id)
        assertEquals(FocusInsights(0, 0, 0, 0, emptyList(), null), empty)
        assertEquals(empty, focusInsights(listOf(zero), range))
    }

    @Test fun halfOpenBoundaryExcludesRunsEndingAtStartOrStartingAtEnd() {
        val start = Instant.parse("2026-10-28T10:00:00Z")
        val range = TimeRange(start, start.plus(Duration.ofHours(1)))
        val before = terminal(
            id = uuid(40), runId = uuid(140), start = start.minus(Duration.ofMinutes(10)), millis = 10 * MINUTE,
        )
        val after = terminal(
            id = uuid(41), runId = uuid(141), start = range.endExclusive, millis = 10 * MINUTE,
        )

        assertEquals(FocusInsights(0, 0, 0, 0, emptyList(), null), focusInsights(listOf(before, after), range))
    }

    @Test fun localMidnightAndDaylightSavingRangesConserveWholeRunCredit() {
        val tehran = ZoneId.of("Asia/Tehran")
        val tehranDate = LocalDate.parse("2026-10-28")
        val today = TimeRange.forDay(tehranDate, tehran)
        val midnightSession = terminal(
            id = uuid(50), runId = uuid(150), start = today.start.minus(Duration.ofMinutes(10)), millis = 30 * MINUTE,
        )
        assertEquals(20 * MINUTE, focusInsights(listOf(midnightSession), today).totalMillis)

        val newYork = ZoneId.of("America/New_York")
        for ((index, case) in listOf(
            LocalDate.parse("2026-03-08") to 23L,
            LocalDate.parse("2026-11-01") to 25L,
        ).withIndex()) {
            val (date, hours) = case
            val day = TimeRange.forDay(date, newYork)
            val duration = Duration.between(day.start, day.endExclusive).toMillis()
            val session = terminal(
                id = uuid(51 + index), runId = uuid(151 + index), start = day.start, millis = duration,
            )
            assertEquals(hours * 60 * 60_000L, focusInsights(listOf(session), day).totalMillis)
            assertEquals(duration, focusInsights(listOf(session), null).totalMillis)
        }
    }

    @Test fun allTimeUsesFullCreditedDurationAndLongestTiesPreferLatestThenLargestId() {
        val start = Instant.parse("2026-10-28T10:00:00Z")
        val earlier = terminal(id = uuid(60), runId = uuid(160), start = start, millis = 5 * MINUTE)
        val laterSmallId = terminal(
            id = uuid(61), runId = uuid(161), start = start.plusSeconds(60), millis = 5 * MINUTE,
        )
        val laterLargeId = terminal(
            id = uuid(62), runId = uuid(162), start = start.plusSeconds(60), millis = 5 * MINUTE,
        )

        val result = focusInsights(listOf(earlier, laterSmallId, laterLargeId), range = null)

        assertEquals(15 * MINUTE, result.totalMillis)
        assertEquals(3, result.sessions)
        assertEquals(5 * MINUTE, result.averageMillis)
        assertEquals(laterLargeId.id, result.longest?.session?.id)
        assertEquals(5 * MINUTE, result.longest?.contributionMillis)
    }

    @Test fun tagRowsUseOneDecimalHalfUpAndSortEqualTotalsDeterministically() {
        val start = Instant.parse("2026-10-28T10:00:00Z")
        val lowerName = FocusTag(uuid(70), "alpha")
        val upperName = FocusTag(uuid(71), "Beta")
        val alpha = terminal(
            id = uuid(72), runId = uuid(172), start = start, millis = 1,
            metadata = FocusMetadata("One", lowerName),
        )
        val beta = terminal(
            id = uuid(73), runId = uuid(173), start = start, millis = 79,
            metadata = FocusMetadata("Seventy nine", upperName),
        )
        val range = TimeRange(start, start.plusSeconds(1))
        val percentages = focusInsights(listOf(alpha, beta), range)

        assertEquals(listOf(BigDecimal("98.8"), BigDecimal("1.3")), percentages.tags.map { it.percentage })
        assertEquals(500, focusInsights(
            listOf(
                terminal(id = uuid(74), runId = uuid(174), start = start, millis = 501),
                terminal(id = uuid(75), runId = uuid(175), start = start, millis = 500),
            ), range,
        ).averageMillis)

        val tied = listOf("zulu", "Beta", "alpha").mapIndexed { index, name ->
            val tag = FocusTag(uuid(80 + index), name)
            terminal(
                id = uuid(90 + index), runId = uuid(190 + index), start = start, millis = 10,
                metadata = FocusMetadata("Session $index", tag),
            )
        }
        assertEquals(listOf("alpha", "Beta", "zulu"), focusInsights(tied, range).tags.map { it.tag?.name })
    }

    @Test fun duplicateIdenticalSnapshotsDeduplicateAndConflictsOrOpenSessionsReject() {
        val start = Instant.parse("2026-10-28T10:00:00Z")
        val one = terminal(id = uuid(100), runId = uuid(200), start = start, millis = 30_000)
        val range = TimeRange(start, start.plusSeconds(60))

        assertEquals(1, focusInsights(listOf(one, one), range).sessions)
        assertThrows(IllegalArgumentException::class.java) {
            focusInsights(listOf(one, one.copy(metadata = FocusMetadata("Changed label"))), range)
        }
        val open = createFocusSession(uuid(101), uuid(201), FocusTimeSample(start, 0, "boot"))
        assertThrows(IllegalArgumentException::class.java) { focusInsights(listOf(open), range) }
    }

    @Test fun totalsRejectLongOverflowInsteadOfWrapping() {
        val start = Instant.EPOCH
        val range = TimeRange(start, Instant.MAX)
        val halfPlusOne = Long.MAX_VALUE / 2 + 1
        val one = terminal(id = uuid(110), runId = uuid(210), start = start, millis = halfPlusOne)
        val two = terminal(id = uuid(111), runId = uuid(211), start = start, millis = halfPlusOne)

        assertThrows(ArithmeticException::class.java) { focusInsights(listOf(one, two), range) }
    }

    @Test fun nonTerminalSnapshotIsRejectedBeforeItCanLookLikeEmptyData() {
        val open = createFocusSession(uuid(120), uuid(220), FocusTimeSample(Instant.EPOCH, 0, "boot"))
        assertTrue(runCatching { focusInsights(listOf(open), null) }.isFailure)
    }

    @Test fun largeAggregationChecksCancellationWhileProcessingTheSnapshot() {
        val start = Instant.parse("2026-10-28T10:00:00Z")
        val sessions = (0 until 128).map { index ->
            terminal(id = uuid(1_000 + index), runId = uuid(2_000 + index), start = start, millis = 1_000)
        }
        var checkpoints = 0

        assertThrows(CancellationException::class.java) {
            focusInsightsCancellable(sessions, null) {
                checkpoints++
                if (checkpoints == 2) throw CancellationException("query replaced")
            }
        }
        assertEquals(2, checkpoints)
    }

    private fun terminal(
        id: UUID,
        runId: UUID,
        start: Instant,
        millis: Long,
        metadata: FocusMetadata = FocusMetadata(),
        state: FocusState = FocusState.FINISHED,
    ) = terminal(id, listOf(run(id, runId, start, millis, 1)), metadata, state)

    private fun terminal(
        id: UUID,
        runs: List<FocusRun>,
        metadata: FocusMetadata = FocusMetadata(),
        state: FocusState = FocusState.FINISHED,
    ): FocusSession {
        val endedAt = runs.maxOf { requireNotNull(it.endedAt) }
        return FocusSession(
            id = id,
            state = state,
            revision = 1,
            startedAt = runs.first().startedAt,
            endedAt = endedAt,
            createdAt = runs.first().startedAt,
            updatedAt = endedAt,
            terminalReason = if (state == FocusState.FINISHED) FocusTerminalReason.USER_STOP else FocusTerminalReason.BOOT_CHANGED,
            recoveredOrUncertain = state == FocusState.INTERRUPTED,
            runs = runs,
            metadata = metadata,
        )
    }

    private fun run(id: UUID, runId: UUID, start: Instant, millis: Long, ordinal: Int) = FocusRun(
        id = runId,
        sessionId = id,
        ordinal = ordinal,
        startedAt = start,
        endedAt = start.plusMillis(millis),
        creditedMillis = millis,
        rawStartedAt = start,
        rawEndedAt = start.plusMillis(millis),
    )

    private fun uuid(value: Int) = UUID(0, value.toLong())

    private companion object { const val MINUTE = 60_000L }
}
