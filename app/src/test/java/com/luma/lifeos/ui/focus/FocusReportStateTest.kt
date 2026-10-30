package com.luma.lifeos.ui.focus

import com.luma.lifeos.domain.focus.FocusCommand
import com.luma.lifeos.domain.focus.FocusRecoveryChoice
import com.luma.lifeos.domain.focus.FocusRepository
import com.luma.lifeos.domain.focus.FocusSession
import com.luma.lifeos.domain.focus.FocusStatistics
import com.luma.lifeos.domain.focus.FocusTerminalReason
import com.luma.lifeos.domain.focus.FocusTimeSample
import com.luma.lifeos.domain.focus.FocusWriteContext
import com.luma.lifeos.domain.focus.FocusWriteResult
import com.luma.lifeos.domain.time.TimeRange
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusReportStateTest {
    @Test fun dayReportCarriesTheExactSelectedRangeAndSavedStatistics() = runBlocking {
        val range = TimeRange(
            Instant.parse("2026-10-28T00:00:00Z"),
            Instant.parse("2026-10-29T00:00:00Z"),
        )
        val statistics = FocusStatistics(1_500_000, contributingSessions = 1, interruptedContributors = 0)
        val repository = ReportRepository { observed ->
            assertEquals(range, observed)
            flowOf(statistics)
        }

        val states = FocusReportSource(repository).day(range).toList()

        assertEquals(FocusReportState.Loading, states.first())
        val ready = states.last() as FocusReportState.Ready<FocusDayReport>
        assertEquals(range, ready.value.range)
        assertEquals(statistics, ready.value.statistics)
    }

    @Test fun weeklyReportComparesExactCurrentAndPreviousRanges() = runBlocking {
        val current = TimeRange(Instant.parse("2026-10-26T00:00:00Z"), Instant.parse("2026-10-29T00:00:00Z"))
        val previous = TimeRange(Instant.parse("2026-10-19T00:00:00Z"), Instant.parse("2026-10-22T00:00:00Z"))
        val currentStats = FocusStatistics(5_400_000, contributingSessions = 3, interruptedContributors = 1)
        val previousStats = FocusStatistics(3_600_000, contributingSessions = 2, interruptedContributors = 0)
        val requested = mutableListOf<TimeRange>()
        val repository = ReportRepository { range ->
            requested += range
            flowOf(if (range == current) currentStats else previousStats)
        }

        val states = FocusReportSource(repository).week(current, previous).toList()

        assertEquals(listOf(current, previous), requested)
        val ready = states.last() as FocusReportState.Ready<FocusWeekReport>
        assertEquals(current, ready.value.currentRange)
        assertEquals(previous, ready.value.previousRange)
        assertEquals(currentStats, ready.value.current)
        assertEquals(previousStats, ready.value.previous)
        assertEquals(BigDecimal("50.0"), ready.value.comparison.percentageChange)
    }

    @Test fun reportFailureIsCardLocalAndCancellationRemainsCancellation() = runBlocking {
        val range = TimeRange(Instant.EPOCH, Instant.EPOCH.plusSeconds(60))
        val failed = ReportRepository { flow { throw IllegalStateException("storage") } }
        assertEquals(
            listOf(FocusReportState.Loading, FocusReportState.Error),
            FocusReportSource(failed).day(range).toList(),
        )

        val cancelled = ReportRepository { flow { throw CancellationException("date changed") } }
        val failure = runCatching { FocusReportSource(cancelled).day(range).toList() }.exceptionOrNull()
        assertTrue(failure is CancellationException)
    }

    @Test fun localDayRangesKeepDaylightSavingBoundaries() {
        val zone = ZoneId.of("America/New_York")
        val shortDay = TimeRange.forDay(LocalDate.parse("2026-03-08"), zone)
        val longDay = TimeRange.forDay(LocalDate.parse("2026-11-01"), zone)

        assertEquals(Duration.ofHours(23), Duration.between(shortDay.start, shortDay.endExclusive))
        assertEquals(Duration.ofHours(25), Duration.between(longDay.start, longDay.endExclusive))
    }

    private class ReportRepository(
        private val statistics: (TimeRange) -> Flow<FocusStatistics>,
    ) : FocusRepository {
        override suspend fun currentGeneration(): UUID = error("Not used by report streams")
        override fun observeOpen(): Flow<FocusSession?> = emptyFlow()
        override suspend fun start(
            commandId: UUID,
            sessionId: UUID,
            sample: FocusTimeSample,
            context: FocusWriteContext,
        ): FocusWriteResult = error("Not used by report streams")

        override suspend fun apply(command: FocusCommand): FocusWriteResult = error("Not used by report streams")
        override suspend fun reconcile(sample: FocusTimeSample, context: FocusWriteContext): FocusWriteResult =
            error("Not used by report streams")

        override suspend fun resolveRecovery(
            sessionId: UUID,
            expectedRevision: Long,
            choice: FocusRecoveryChoice,
            sample: FocusTimeSample,
            context: FocusWriteContext,
            terminalReason: FocusTerminalReason,
        ): FocusWriteResult = error("Not used by report streams")

        override suspend fun checkpoint(
            sessionId: UUID,
            expectedRevision: Long,
            sample: FocusTimeSample,
            context: FocusWriteContext,
        ): FocusWriteResult = error("Not used by report streams")

        override fun observeStatistics(range: TimeRange): Flow<FocusStatistics> = statistics(range)
        override suspend fun history(range: TimeRange?, offset: Int, limit: Int) =
            error("Not used by report streams")

        override suspend fun delete(
            sessionId: UUID,
            expectedRevision: Long,
            context: FocusWriteContext,
        ): FocusWriteResult = error("Not used by report streams")
    }
}
