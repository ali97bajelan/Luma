package com.luma.lifeos.ui.focus

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.luma.lifeos.domain.focus.FocusAction
import com.luma.lifeos.domain.focus.FocusCommand
import com.luma.lifeos.domain.focus.FocusHistoryRow
import com.luma.lifeos.domain.focus.FocusPage
import com.luma.lifeos.domain.focus.FocusRecoveryChoice
import com.luma.lifeos.domain.focus.FocusRepository
import com.luma.lifeos.domain.focus.FocusRun
import com.luma.lifeos.domain.focus.FocusSession
import com.luma.lifeos.domain.focus.FocusState
import com.luma.lifeos.domain.focus.FocusStatistics
import com.luma.lifeos.domain.focus.FocusTerminalReason
import com.luma.lifeos.domain.focus.FocusTimeSample
import com.luma.lifeos.domain.focus.FocusWriteContext
import com.luma.lifeos.domain.focus.FocusWriteResult
import com.luma.lifeos.domain.focus.compareFocusStatistics
import com.luma.lifeos.domain.review.reviewPeriod
import com.luma.lifeos.domain.time.TimeRange
import com.luma.lifeos.ui.theme.LifeOsTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FocusReportsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun todaySeparatesSavedTotalsFromTheOpenSessionAndOpensDayHistory() {
        var openedHistory = 0
        var openedFocus = 0
        val stats = FocusStatistics(25 * MINUTE, contributingSessions = 2, interruptedContributors = 1)

        compose.setContent {
            LifeOsTheme {
                TodayFocusCard(
                    report = FocusReportState.Ready(
                        FocusDayReport(TimeRange(ORIGIN, ORIGIN.plusSeconds(86_400)), stats),
                    ),
                    inProgressMillis = 30 * MINUTE,
                    inProgressState = FocusState.RUNNING,
                    onRetry = {},
                    onHistory = { openedHistory++ },
                    onFocus = { openedFocus++ },
                )
            }
        }

        compose.onNodeWithTag("today_focus_saved").assertTextContains("25:00", substring = true)
        compose.onNodeWithTag("today_focus_contributors").assertTextContains("2 sessions")
        compose.onNodeWithTag("today_focus_interrupted")
            .assertTextContains("1 interrupted session contributed.", substring = true)
        compose.onNodeWithTag("today_focus_in_progress").assertTextContains("30:00", substring = true)
        compose.onNodeWithTag("today_focus_history").performClick()
        compose.onNodeWithTag("today_focus_open").performClick()
        assertEquals(1, openedHistory)
        assertEquals(1, openedFocus)
    }

    @Test fun weeklyReportShowsPartialWeekAndDoesNotInventPercentForEmptyBaseline() {
        val zone = ZoneId.of("UTC")
        val today = LocalDate.parse("2026-10-28")
        val period = reviewPeriod(today, today, zone)
        val current = FocusStatistics(60_000, contributingSessions = 1, interruptedContributors = 0)
        val previous = FocusStatistics(0, contributingSessions = 0, interruptedContributors = 0)
        val comparison = compareFocusStatistics(current, previous)

        compose.setContent {
            LifeOsTheme {
                WeeklyFocusCard(
                    report = FocusReportState.Ready(
                        FocusWeekReport(
                            currentRange = TimeRange(period.current.startInstant(zone), period.current.endInstant(zone)),
                            previousRange = TimeRange(period.previous.startInstant(zone), period.previous.endInstant(zone)),
                            current = current,
                            previous = previous,
                            comparison = comparison,
                            days = (0L..6L).map { offset ->
                                val date = period.current.start.plusDays(offset)
                                FocusWeekDayReport(date, if (date <= period.current.through) current else null)
                            },
                        ),
                    ),
                    period = period,
                    onRetry = {},
                    onCurrentHistory = {},
                    onPreviousHistory = {},
                )
            }
        }

        compose.onNodeWithTag("review_focus_partial").assertIsDisplayed()
        compose.onNodeWithTag("review_focus_current").assertTextContains("01:00", substring = true)
        compose.onNodeWithTag("review_focus_previous").assertTextContains("00:00", substring = true)
        compose.onNodeWithTag("review_focus_comparison")
            .assertTextContains("Increase of 01:00 from a period with no recorded focus time", substring = true)
        compose.onNodeWithText("No focus time was recorded in the previous period, so no percentage is shown.")
            .assertIsDisplayed()
        compose.onNodeWithTag("review_focus_day_bar_0").assertIsDisplayed()
        compose.onNodeWithTag("review_focus_day_unavailable_6").assertIsDisplayed()
    }

    @Test fun historyShowsCreditedRunsAndOnlyDeletesAfterConfirmation() {
        val row = sampleHistoryRow()
        val repository = HistoryRepository(row)
        val zone = ZoneId.of("UTC")
        val range = TimeRange(ORIGIN, ORIGIN.plusSeconds(86_400))

        compose.setContent {
            LifeOsTheme {
                FocusHistoryRoute(repository, range, zone, onBack = {})
            }
        }

        compose.onNodeWithTag("focus_history_row_${row.session.id}").assertIsDisplayed()
        repository.generation = UUID.fromString("00000000-0000-0000-0000-000000000099")
        compose.onNodeWithTag("focus_history_detail").performClick()
        compose.onNodeWithText("Pause gaps are excluded. Each run below shows only time credited to the timer.")
            .assertIsDisplayed()
        compose.onNodeWithTag("focus_history_segment_0").assertTextContains("10:00", substring = true)
        compose.onNodeWithTag("focus_history_delete").performClick()
        compose.onNodeWithTag("focus_history_delete_dialog").assertIsDisplayed()
        compose.onNodeWithTag("focus_history_delete_cancel").performClick()
        assertEquals(0, repository.deleteCount.get())

        compose.onNodeWithTag("focus_history_delete").performClick()
        compose.onNodeWithTag("focus_history_delete_confirm").performClick()
        compose.waitUntil(5_000) { repository.deleteCount.get() == 1 }
        compose.onNodeWithTag("focus_history_empty").assertIsDisplayed()
        assertEquals(1, repository.deleteCount.get())
        assertEquals(GENERATION, repository.deleteContext?.databaseGeneration)
    }

    private class HistoryRepository(private val row: FocusHistoryRow) : FocusRepository {
        val deleteCount = AtomicInteger()
        var generation: UUID = GENERATION
        var deleteContext: FocusWriteContext? = null
        private var deleted = false

        override suspend fun currentGeneration(): UUID = generation
        override fun observeOpen(): Flow<FocusSession?> = emptyFlow()
        override suspend fun start(
            commandId: UUID,
            sessionId: UUID,
            sample: FocusTimeSample,
            context: FocusWriteContext,
        ): FocusWriteResult = error("Not used by history")

        override suspend fun apply(command: FocusCommand): FocusWriteResult = error("Not used by history")
        override suspend fun reconcile(sample: FocusTimeSample, context: FocusWriteContext): FocusWriteResult =
            error("Not used by history")

        override suspend fun resolveRecovery(
            sessionId: UUID,
            expectedRevision: Long,
            choice: FocusRecoveryChoice,
            sample: FocusTimeSample,
            context: FocusWriteContext,
            terminalReason: FocusTerminalReason,
        ): FocusWriteResult = error("Not used by history")

        override suspend fun checkpoint(
            sessionId: UUID,
            expectedRevision: Long,
            sample: FocusTimeSample,
            context: FocusWriteContext,
        ): FocusWriteResult = error("Not used by history")

        override fun observeStatistics(range: TimeRange): Flow<FocusStatistics> = flowOf(
            FocusStatistics(if (deleted) 0 else row.rangeContributionMillis,
                if (deleted) 0 else 1, 0),
        )

        override suspend fun history(range: TimeRange?, offset: Int, limit: Int): FocusPage {
            val rows = if (deleted) emptyList() else listOf(row).drop(offset).take(limit)
            return FocusPage(rows, offset, if (deleted) 0 else 1)
        }

        override suspend fun delete(
            sessionId: UUID,
            expectedRevision: Long,
            context: FocusWriteContext,
        ): FocusWriteResult {
            deleteCount.incrementAndGet()
            deleteContext = context
            deleted = true
            return FocusWriteResult.Applied(deletedSessionId = sessionId)
        }
    }

    private fun sampleHistoryRow(): FocusHistoryRow {
        val id = UUID.fromString("00000000-0000-0000-0000-000000000015")
        val run = FocusRun(
            id = UUID.fromString("00000000-0000-0000-0000-000000000016"),
            sessionId = id,
            ordinal = 1,
            startedAt = ORIGIN,
            endedAt = ORIGIN.plusMillis(10 * MINUTE),
            creditedMillis = 10 * MINUTE,
            rawStartedAt = ORIGIN,
            rawEndedAt = ORIGIN.plusMillis(10 * MINUTE),
        )
        val session = FocusSession(
            id = id,
            state = FocusState.FINISHED,
            revision = 1,
            startedAt = ORIGIN,
            endedAt = run.endedAt,
            createdAt = ORIGIN,
            updatedAt = run.endedAt!!,
            terminalReason = FocusTerminalReason.USER_STOP,
            recoveredOrUncertain = false,
            runs = listOf(run),
        )
        return FocusHistoryRow(session, fullDurationMillis = 10 * MINUTE, rangeContributionMillis = 5 * MINUTE)
    }

    private companion object {
        const val MINUTE = 60_000L
        val ORIGIN = Instant.parse("2026-10-28T12:00:00Z")
        val GENERATION = UUID.fromString("00000000-0000-0000-0000-000000000099")
    }
}
