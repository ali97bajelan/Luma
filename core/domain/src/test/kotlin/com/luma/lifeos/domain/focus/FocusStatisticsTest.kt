package com.luma.lifeos.domain.focus

import com.luma.lifeos.domain.time.TimeRange
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusStatisticsTest {
    @Test fun sessionAExcludesPausedTimeAndCountsOnePositiveContributor() {
        val start = Instant.parse("2026-10-28T09:00:00Z")
        val sessionId = uuid(1)
        val first = run(sessionId, 2, 1, start, 20 * 60_000L)
        val second = run(sessionId, 3, 2, start.plusSeconds(30 * 60L), 5 * 60_000L)
        val session = terminal(sessionId, FocusState.FINISHED, listOf(first, second), start.plusSeconds(35 * 60L))
        val range = TimeRange(start, start.plusSeconds(60 * 60L))

        assertEquals(FocusStatistics(1_500_000L, 1, 0), focusStatistics(listOf(session), range))
        assertEquals(1_500_000L, focusRangeContributionMillis(session, range))
    }

    @Test fun tehranMidnightSplitsSessionBIntoTenAndTwentyMinutes() {
        val zone = ZoneId.of("Asia/Tehran")
        val sessionId = uuid(10)
        val start = Instant.parse("2026-10-27T20:20:00Z") // 23:50 in Tehran
        val session = terminal(
            sessionId,
            FocusState.FINISHED,
            listOf(run(sessionId, 11, 1, start, 30 * 60_000L)),
            start.plusSeconds(30 * 60L),
        )
        val oct27 = TimeRange.forDay(LocalDate.parse("2026-10-27"), zone)
        val oct28 = TimeRange.forDay(LocalDate.parse("2026-10-28"), zone)
        val combined = TimeRange(oct27.start, oct28.endExclusive)

        assertEquals(600_000L, focusStatistics(listOf(session), oct27).totalMillis)
        assertEquals(1_200_000L, focusStatistics(listOf(session), oct28).totalMillis)
        assertEquals(FocusStatistics(1_800_000L, 1, 0), focusStatistics(listOf(session), combined))
        assertEquals(1, focusStatistics(listOf(session), oct27).contributingSessions)
        assertEquals(1, focusStatistics(listOf(session), oct28).contributingSessions)
    }

    @Test fun dstDaysUseLocalStartBoundariesAndSumToFullRun() {
        val zone = ZoneId.of("America/New_York")
        val cases = listOf(
            LocalDate.parse("2026-03-08") to 23L,
            LocalDate.parse("2026-11-01") to 25L,
        )
        for ((index, pair) in cases.withIndex()) {
            val (date, hours) = pair
            val range = TimeRange.forDay(date, zone)
            val sessionId = uuid(20 + index)
            val session = terminal(
                sessionId,
                FocusState.FINISHED,
                listOf(run(sessionId, 30 + index, 1, range.start, hours * 60 * 60_000L)),
                range.endExclusive,
            )
            assertEquals(hours * 60 * 60_000L, focusStatistics(listOf(session), range).totalMillis)
            assertEquals(hours * 60 * 60_000L, focusRangeContributionMillis(session, range))
        }
    }

    @Test fun halfOpenRangesExcludeExactEndpointsAndSplitAcrossWeekBoundary() {
        val monday = Instant.parse("2026-10-26T00:00:00Z")
        val previousMonday = monday.minusSeconds(7 * 24 * 60 * 60L)
        val nextMonday = monday.plusSeconds(7 * 24 * 60 * 60L)
        val sessionId = uuid(40)
        val runStart = nextMonday.minusSeconds(10 * 60L)
        val session = terminal(
            sessionId,
            FocusState.FINISHED,
            listOf(run(sessionId, 41, 1, runStart, 30 * 60_000L)),
            nextMonday.plusSeconds(20 * 60L),
        )
        val previousWeek = TimeRange(previousMonday, monday)
        val currentWeek = TimeRange(monday, nextMonday)
        val nextWeek = TimeRange(nextMonday, nextMonday.plusSeconds(7 * 24 * 60 * 60L))

        assertEquals(0L, focusStatistics(listOf(session), previousWeek).totalMillis)
        assertEquals(10 * 60_000L, focusStatistics(listOf(session), currentWeek).totalMillis)
        assertEquals(20 * 60_000L, focusStatistics(listOf(session), nextWeek).totalMillis)
        assertEquals(30 * 60_000L, listOf(previousWeek, currentWeek, nextWeek).sumOf {
            focusRangeContributionMillis(session, it)
        })
    }

    @Test fun zeroTimeDoesNotContributeButPositiveInterruptedTimeDoes() {
        val start = Instant.parse("2026-10-28T00:00:00Z")
        val zeroId = uuid(50)
        val zero = terminal(zeroId, FocusState.FINISHED, listOf(run(zeroId, 51, 1, start, 0)), start)
        val interruptedId = uuid(52)
        val interrupted = terminal(
            interruptedId,
            FocusState.INTERRUPTED,
            listOf(run(interruptedId, 53, 1, start, 59_999)),
            start.plusMillis(59_999),
            reason = FocusTerminalReason.BOOT_CHANGED,
        )

        assertEquals(FocusStatistics(59_999L, 1, 1), focusStatistics(
            listOf(zero, interrupted), TimeRange(start, start.plusSeconds(60)),
        ))
        assertEquals(0, focusStatistics(listOf(zero), TimeRange(start, start.plusSeconds(60))).contributingSessions)
    }

    @Test fun repeatedIdenticalSessionAndRunRowsAreDeduplicated() {
        val start = Instant.parse("2026-10-28T10:00:00Z")
        val sessionId = uuid(60)
        val row = run(sessionId, 61, 1, start, 30_000)
        val session = terminal(sessionId, FocusState.FINISHED, listOf(row, row), start.plusMillis(30_000))
        val range = TimeRange(start, start.plusSeconds(60))

        assertEquals(FocusStatistics(30_000, 1, 0), focusStatistics(listOf(session, session), range))
    }

    @Test fun conflictingSnapshotsAndOpenSessionsAreRejected() {
        val start = Instant.parse("2026-10-28T10:00:00Z")
        val sessionId = uuid(70)
        val baseRun = run(sessionId, 71, 1, start, 30_000)
        val session = terminal(sessionId, FocusState.FINISHED, listOf(baseRun), start.plusMillis(30_000))
        val conflict = terminal(
            sessionId,
            FocusState.FINISHED,
            listOf(run(sessionId, 71, 1, start, 31_000)),
            start.plusMillis(31_000),
        )
        val range = TimeRange(start, start.plusSeconds(60))

        assertThrows(IllegalArgumentException::class.java) { focusStatistics(listOf(session, conflict), range) }
        val open = createFocusSession(uuid(72), uuid(73), FocusTimeSample(start, 0, "boot-A"))
        assertThrows(IllegalArgumentException::class.java) { focusStatistics(listOf(open), range) }
    }

    @Test fun reviewComparisonUsesRawMillisecondTotalsAndHalfUpOneDecimalRounding() {
        val ninetyMinutes = stats(90 * 60_000L, 3)
        val sixtyMinutes = stats(60 * 60_000L, 3)
        val increase = compareFocusStatistics(ninetyMinutes, sixtyMinutes)
        assertEquals(30 * 60_000L, increase.deltaMillis)
        assertEquals(BigDecimal("50.0"), increase.percentageChange)
        assertEquals(FocusComparisonStatus.CHANGE, increase.status)

        val roundedUp = compareFocusStatistics(stats(4_050, 1), stats(4_000, 1))
        assertEquals(BigDecimal("1.3"), roundedUp.percentageChange)
        val roundedDown = compareFocusStatistics(stats(3_950, 1), stats(4_000, 1))
        assertEquals(BigDecimal("-1.3"), roundedDown.percentageChange)

        val equal = compareFocusStatistics(stats(30_000, 1), stats(30_000, 1))
        assertEquals(FocusComparisonStatus.NO_CHANGE, equal.status)
        assertEquals(BigDecimal("0.0"), equal.percentageChange)
    }

    @Test fun emptyComparisonBaselineOmitsPercentAndDistinguishesBothZero() {
        val noPrevious = compareFocusStatistics(stats(1, 1), FocusStatistics(0, 0, 0))
        assertEquals(FocusComparisonStatus.PREVIOUS_PERIOD_EMPTY, noPrevious.status)
        assertEquals(1L, noPrevious.deltaMillis)
        assertEquals(null, noPrevious.percentageChange)

        val bothZero = compareFocusStatistics(FocusStatistics(0, 0, 0), FocusStatistics(0, 0, 0))
        assertEquals(FocusComparisonStatus.NO_CHANGE, bothZero.status)
        assertEquals(null, bothZero.percentageChange)

        val decline = compareFocusStatistics(FocusStatistics(0, 0, 0), stats(60_000, 1))
        assertEquals(-60_000L, decline.deltaMillis)
        assertEquals(BigDecimal("-100.0"), decline.percentageChange)
    }

    @Test fun statisticsRejectLongOverflowInsteadOfWrapping() {
        val start = Instant.EPOCH
        val range = TimeRange(start, Instant.MAX)
        val firstId = uuid(80)
        val secondId = uuid(82)
        val first = terminal(
            firstId, FocusState.FINISHED,
            listOf(run(firstId, 81, 1, start, Long.MAX_VALUE / 2 + 1)),
            start.plusMillis(Long.MAX_VALUE / 2 + 1),
        )
        val second = terminal(
            secondId, FocusState.FINISHED,
            listOf(run(secondId, 83, 1, start, Long.MAX_VALUE / 2 + 1)),
            start.plusMillis(Long.MAX_VALUE / 2 + 1),
        )

        assertThrows(ArithmeticException::class.java) { focusStatistics(listOf(first, second), range) }
    }

    private fun stats(total: Long, count: Int) = FocusStatistics(total, count, 0)

    private fun terminal(
        id: UUID,
        state: FocusState,
        runs: List<FocusRun>,
        endedAt: Instant,
        reason: FocusTerminalReason = if (state == FocusState.FINISHED) {
            FocusTerminalReason.USER_STOP
        } else {
            FocusTerminalReason.BOOT_CHANGED
        },
    ) = FocusSession(
        id = id,
        state = state,
        revision = 1,
        startedAt = runs.first().rawStartedAt,
        endedAt = endedAt,
        createdAt = runs.first().rawStartedAt,
        updatedAt = endedAt,
        terminalReason = reason,
        recoveredOrUncertain = state == FocusState.INTERRUPTED,
        runs = runs,
    )

    private fun run(sessionId: UUID, runId: Int, ordinal: Int, start: Instant, durationMillis: Long) = FocusRun(
        id = uuid(runId),
        sessionId = sessionId,
        ordinal = ordinal,
        startedAt = start,
        endedAt = start.plusMillis(durationMillis),
        creditedMillis = durationMillis,
        rawStartedAt = start,
        rawEndedAt = start.plusMillis(durationMillis),
    )

    private fun uuid(value: Int): UUID = UUID(0, value.toLong())
}
