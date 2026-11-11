package com.luma.lifeos.ui.focus

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.luma.lifeos.domain.focus.FocusCommand
import com.luma.lifeos.domain.focus.FocusHistoryQuery
import com.luma.lifeos.domain.focus.FocusHistoryRow
import com.luma.lifeos.domain.focus.FocusGoalState
import com.luma.lifeos.domain.focus.FocusGoalVersion
import com.luma.lifeos.domain.focus.FocusProductContext
import com.luma.lifeos.domain.focus.FocusProductRepository
import com.luma.lifeos.domain.focus.FocusProductResult
import com.luma.lifeos.domain.focus.FocusTagCatalog
import com.luma.lifeos.domain.focus.FocusInsights
import com.luma.lifeos.domain.focus.FocusPage
import com.luma.lifeos.domain.focus.FocusRecoveryChoice
import com.luma.lifeos.domain.focus.FocusRepository
import com.luma.lifeos.domain.focus.FocusRun
import com.luma.lifeos.domain.focus.FocusSession
import com.luma.lifeos.domain.focus.FocusStartInput
import com.luma.lifeos.domain.focus.FocusState
import com.luma.lifeos.domain.focus.FocusStatistics
import com.luma.lifeos.domain.focus.FocusTag
import com.luma.lifeos.domain.focus.FocusTerminalReason
import com.luma.lifeos.domain.focus.FocusTimerConfig
import com.luma.lifeos.domain.focus.FocusTimeSample
import com.luma.lifeos.domain.focus.FocusWriteContext
import com.luma.lifeos.domain.focus.FocusWriteResult
import com.luma.lifeos.domain.focus.compareFocusStatistics
import com.luma.lifeos.domain.review.reviewPeriod
import com.luma.lifeos.domain.time.TimeRange
import com.luma.lifeos.focus.FocusRuntimeState
import com.luma.lifeos.focus.FocusScreenState
import com.luma.lifeos.ui.theme.LifeOsTheme
import java.time.DayOfWeek
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

    @Test fun pendingGoalIsShownAsScheduledUntilItsEffectiveMonday() {
        val effectiveMonday = LocalDate.of(2099, 1, 5)
        val goal = FocusGoalVersion(
            id = UUID.randomUUID(),
            revision = 1,
            effectiveMonday = effectiveMonday,
            zoneId = "UTC",
            targetMinutes = 300,
            preferredDays = setOf(DayOfWeek.MONDAY),
        )
        val state = FocusGoalState(
            enabled = true,
            settingsRevision = 1,
            visibleFromMonday = effectiveMonday,
            visibleFromZoneId = "UTC",
            versions = listOf(goal),
        )
        val repository = EmptyFocusProductRepository(GENERATION, goalState = state)

        compose.setContent {
            LifeOsTheme {
                FocusGoalSettings(repository)
            }
        }

        compose.onNodeWithTag("focus_settings_goals_toggle").assertTextContains("Starts Monday")
        compose.onNodeWithTag("focus_settings_goals_toggle").performClick()
        compose.onNodeWithTag("focus_goal_status").assertTextContains("Starts Monday")
        compose.onNodeWithTag("focus_goal_target").assertTextContains("300")
    }

    @Test fun protectedTagLabelsStayHiddenWhenTaskOriginAccessIsLocked() {
        val tag = FocusTag(UUID.randomUUID(), "Private tag")
        val repository = EmptyFocusProductRepository(
            GENERATION,
            tagCatalog = FocusTagCatalog(1, listOf(tag), databaseGeneration = GENERATION),
        )

        compose.setContent {
            LifeOsTheme {
                FocusTagManager(repository, canReadTaskOrigin = false)
            }
        }

        compose.onNodeWithTag("focus_settings_tags_toggle").assertTextContains("Unlock to review saved tags")
        compose.onNodeWithTag("focus_settings_tags_toggle").performClick()
        compose.onNodeWithTag("focus_tag_manager_unlock").assertIsDisplayed()
        compose.runOnIdle { assertEquals(false, repository.lastIncludeTaskOrigin) }
        assertEquals(
            0,
            compose.onAllNodesWithTag("focus_tag_name_${tag.id}").fetchSemanticsNodes().size,
        )
    }

    @Test fun tagCatalogConflictKeepsTheEditedNameVisibleAndRecoverable() {
        val tag = FocusTag(UUID.randomUUID(), "Work")
        val initialCatalog = FocusTagCatalog(1, listOf(tag), databaseGeneration = GENERATION)
        val conflictingCatalog = FocusTagCatalog(2, listOf(tag), databaseGeneration = GENERATION)
        val repository = EmptyFocusProductRepository(
            GENERATION,
            tagCatalog = initialCatalog,
            renameResult = FocusProductResult.TagCatalogConflict(conflictingCatalog),
        )

        compose.setContent {
            LifeOsTheme {
                FocusTagManager(repository)
            }
        }

        compose.onNodeWithTag("focus_settings_tags_toggle").performClick()
        compose.onNodeWithTag("focus_tag_name_${tag.id}").performTextInput(" updated")
        compose.onNodeWithTag("focus_tag_rename_${tag.id}").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("focus_tag_manager_error").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("focus_settings_tags_toggle").performClick()
        compose.onNodeWithTag("focus_settings_tags_toggle").assertTextContains("tag list changed")
        compose.onNodeWithTag("focus_settings_tags_toggle").performClick()
        compose.onNodeWithTag("focus_tag_name_${tag.id}").assertTextContains("Work updated")
    }

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
        compose.onNodeWithTag("today_focus_open").assertTextContains("Return to timer")
        compose.onNodeWithTag("today_focus_history").performClick()
        compose.onNodeWithTag("today_focus_open").performClick()
        assertEquals(1, openedHistory)
        assertEquals(1, openedFocus)
    }

    @Test fun weeklyReportSupportsSaturdayStartAndDoesNotInventPercentForEmptyBaseline() {
        val zone = ZoneId.of("UTC")
        val today = LocalDate.parse("2026-10-28")
        val period = reviewPeriod(today, today, zone, firstDayOfWeek = DayOfWeek.SATURDAY)
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

    @Test fun deletedCompletionDoesNotShowReflectionLoadError() {
        val row = sampleHistoryRow()
        val repository = HistoryRepository(row, initiallyDeleted = true)
        val productRepository = EmptyFocusProductRepository(GENERATION)
        var missingSessionId: UUID? = null

        compose.setContent {
            LifeOsTheme {
                FocusReflectionEditor(
                    session = row.session,
                    repository = repository,
                    productRepository = productRepository,
                    generation = GENERATION,
                    isFocusReadable = true,
                    showWhenEmpty = true,
                    onSessionMissing = { sessionId, _ -> missingSessionId = sessionId },
                )
            }
        }

        compose.waitUntil(5_000) { repository.terminalLookupCount > 0 }
        compose.onNodeWithTag("focus_reflection_load_retry").assertDoesNotExist()
        compose.runOnIdle { assertEquals(row.session.id, missingSessionId) }
    }

    @Test fun completionSummaryDistinguishesEarlyEndTargetAndInterrupted() {
        val earlyEnd = sampleHistoryRow().session.copy(timer = FocusTimerConfig.timed(25))
        renderCompletion(earlyEnd)
        compose.onNodeWithTag("focus_terminal_label").assertTextContains("Stopped before target")
        compose.onNodeWithTag("focus_saved_duration").assertTextContains("10:00")

        val targetReached = earlyEnd.copy(
            timer = FocusTimerConfig.timed(10),
            terminalReason = FocusTerminalReason.TARGET_REACHED,
        )
        renderCompletion(targetReached)
        compose.onNodeWithTag("focus_terminal_label").assertTextContains("Target reached")

        val interrupted = earlyEnd.copy(
            state = FocusState.INTERRUPTED,
            terminalReason = FocusTerminalReason.PROCESS_RESTART_GAP,
        )
        renderCompletion(interrupted)
        compose.onNodeWithTag("focus_terminal_label").assertTextContains("Interrupted")
        compose.onNodeWithText("Time after the last saved checkpoint was not included.").assertIsDisplayed()
    }

    @Test fun completionActionsKeepDonePrimaryAndBreakOptionsSecondary() {
        var doneCalls = 0
        var requestedBreakMinutes: Int? = null
        renderCompletion(
            session = sampleHistoryRow().session,
            onDone = { doneCalls++ },
            onStartBreak = { requestedBreakMinutes = it },
        )

        compose.onNodeWithTag("focus_done").assertIsDisplayed()
        compose.onNodeWithTag("focus_follow_up_group").assertIsDisplayed()
        compose.onNodeWithTag("focus_break_options_toggle").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(0, compose.onAllNodesWithTag("focus_start_five_minute_break").fetchSemanticsNodes().size)
        }
        compose.onNodeWithTag("focus_break_options_toggle").performClick()
        compose.onNodeWithTag("focus_start_five_minute_break").performClick()

        compose.runOnIdle {
            assertEquals(5, requestedBreakMinutes)
            assertEquals(0, doneCalls)
        }
        compose.onNodeWithTag("focus_done").performClick()
        compose.runOnIdle { assertEquals(1, doneCalls) }
    }

    @Test fun completionNavigationOffersCancelDiscardAndPreservesReflectionDraft() {
        val row = sampleHistoryRow()
        val repository = HistoryRepository(row)
        val productRepository = EmptyFocusProductRepository(GENERATION)
        var doneCalls = 0
        renderCompletion(
            session = row.session,
            repository = repository,
            productRepository = productRepository,
            onDone = { doneCalls++ },
        )
        compose.waitUntil(5_000) { repository.terminalLookupCount > 0 }

        compose.onNodeWithTag("focus_reflection_add").performClick()
        compose.onNodeWithTag("focus_reflection_next_step").performTextInput("draft next step")
        compose.onNodeWithTag("focus_done").performClick()
        compose.onNodeWithTag("focus_reflection_leave_dialog").assertIsDisplayed()
        compose.onNodeWithTag("focus_reflection_keep_editing").performClick()
        compose.onNodeWithTag("focus_reflection_next_step").assertTextContains("draft next step")

        compose.onNodeWithTag("focus_done").performClick()
        compose.onNodeWithTag("focus_reflection_discard_and_leave").performClick()
        compose.runOnIdle { assertEquals(1, doneCalls) }
        compose.onNodeWithTag("focus_reflection_add").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(0, compose.onAllNodesWithTag("focus_reflection_next_step").fetchSemanticsNodes().size)
        }
    }

    @Test fun failedReflectionSaveDoesNotLeaveCompletion() {
        val row = sampleHistoryRow()
        val repository = HistoryRepository(row)
        val productRepository = EmptyFocusProductRepository(GENERATION)
        var doneCalls = 0
        renderCompletion(
            session = row.session,
            repository = repository,
            productRepository = productRepository,
            onDone = { doneCalls++ },
        )
        compose.waitUntil(5_000) { repository.terminalLookupCount > 0 }

        compose.onNodeWithTag("focus_reflection_add").performClick()
        compose.onNodeWithTag("focus_reflection_next_step").performTextInput("keep this draft")
        compose.onNodeWithTag("focus_done").performClick()
        compose.onNodeWithTag("focus_reflection_save_and_leave").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("focus_reflection_save_error").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithTag("focus_reflection_editor").assertIsDisplayed()
        compose.onNodeWithTag("focus_reflection_next_step").assertTextContains("keep this draft")
        compose.runOnIdle { assertEquals(0, doneCalls) }
    }

    @Test fun failedTaskCompletionAndUnlinkKeepSeparateExplicitRetryActions() {
        val row = sampleHistoryRow()
        val taskId = UUID.fromString("00000000-0000-0000-0000-000000000101")
        val session = row.session.copy(taskOrigin = true, taskId = taskId)
        val state = FocusTaskCompletionState(
            sessionId = session.id,
            sessionRevision = session.revision,
            taskId = taskId,
            generation = GENERATION,
            completionPhase = FocusTaskCompletionPhase.ERROR,
            pendingCompletion = PendingFocusTaskCompletion(
                taskId = taskId,
                expectedRevision = UUID.fromString("00000000-0000-0000-0000-000000000102"),
                nextRevision = UUID.fromString("00000000-0000-0000-0000-000000000103"),
                generation = GENERATION,
            ),
            unlinkPhase = FocusTaskUnlinkPhase.ERROR,
            pendingUnlink = PendingFocusTaskUnlink(
                commandId = UUID.fromString("00000000-0000-0000-0000-000000000104"),
                sessionId = session.id,
                taskId = taskId,
                expectedRevision = session.revision,
                generation = GENERATION,
            ),
        )
        var completionRetries = 0
        var unlinkRetries = 0

        compose.setContent {
            LifeOsTheme {
                FocusTaskCompletionAction(
                    session = session,
                    generation = GENERATION,
                    state = state,
                    canAccessTaskActions = true,
                    onSessionChanged = { _, _, _, _ -> },
                    onMarkComplete = { _, _, _, _ -> error("Retry action expected") },
                    onRetryCompletion = { _, _, _, _ -> completionRetries++ },
                    onUnlinkTask = { _, _, _, _ -> error("Retry action expected") },
                    onRetryUnlink = { _, _, _, _ -> unlinkRetries++ },
                    onChanged = {},
                )
            }
        }

        compose.onNodeWithTag("focus_task_completion_error").assertIsDisplayed()
        compose.onNodeWithTag("focus_task_unlink_error").assertIsDisplayed()
        compose.onNodeWithTag("focus_task_mark_complete").performClick()
        compose.onNodeWithTag("focus_task_unlink").performClick()
        compose.runOnIdle {
            assertEquals(1, completionRetries)
            assertEquals(1, unlinkRetries)
        }

        compose.setContent {
            LifeOsTheme {
                FocusTaskCompletionAction(
                    session = session,
                    generation = GENERATION,
                    state = state.copy(
                        completionPhase = FocusTaskCompletionPhase.SAVING,
                        unlinkPhase = FocusTaskUnlinkPhase.SAVING,
                    ),
                    canAccessTaskActions = true,
                    onSessionChanged = { _, _, _, _ -> },
                    onMarkComplete = { _, _, _, _ -> },
                    onRetryCompletion = { _, _, _, _ -> },
                    onUnlinkTask = { _, _, _, _ -> },
                    onRetryUnlink = { _, _, _, _ -> },
                    onChanged = {},
                )
            }
        }
        compose.onNodeWithTag("focus_task_completion_saving").assertIsDisplayed()
        compose.onNodeWithTag("focus_task_unlink_saving").assertIsDisplayed()

        compose.setContent {
            LifeOsTheme {
                FocusTaskCompletionAction(
                    session = session,
                    generation = GENERATION,
                    state = state,
                    canAccessTaskActions = false,
                    onSessionChanged = { _, _, _, _ -> },
                    onMarkComplete = { _, _, _, _ -> },
                    onRetryCompletion = { _, _, _, _ -> },
                    onUnlinkTask = { _, _, _, _ -> },
                    onRetryUnlink = { _, _, _, _ -> },
                    onChanged = {},
                )
            }
        }
        compose.runOnIdle {
            assertEquals(0, compose.onAllNodesWithTag("focus_task_mark_complete").fetchSemanticsNodes().size)
            assertEquals(0, compose.onAllNodesWithTag("focus_task_unlink").fetchSemanticsNodes().size)
        }
    }

    @Test fun completionRepeatUsesTheSavedSessionWithoutStartingOne() {
        val session = sampleHistoryRow().session
        var repeatedSession: UUID? = null
        var starts = 0
        renderCompletion(
            session = session,
            onRepeat = { repeatedSession = it },
            onStartAnother = { starts++ },
        )

        compose.onNodeWithTag("focus_repeat").performClick()
        compose.runOnIdle {
            assertEquals(session.id, repeatedSession)
            assertEquals(0, starts)
        }
    }

    @Test fun historyShowsCreditedRunsAndOnlyDeletesAfterConfirmation() {
        val row = sampleHistoryRow()
        val repository = HistoryRepository(row)
        val zone = ZoneId.of("UTC")
        val range = TimeRange(ORIGIN, ORIGIN.plusSeconds(86_400))

        compose.setContent {
            LifeOsTheme {
                FocusHistoryRoute(
                    repository = repository,
                    range = range,
                    zone = zone,
                    isFocusReadable = true,
                    privateContentAccessFlow = flowOf(true),
                    taskContentAccessAllowed = false,
                    taskContentAccessFlow = flowOf(false),
                    databaseGenerationFlow = flowOf(GENERATION),
                    onBack = {},
                )
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

    private class HistoryRepository(
        private val row: FocusHistoryRow,
        initiallyDeleted: Boolean = false,
    ) : FocusRepository {
        val deleteCount = AtomicInteger()
        var generation: UUID = GENERATION
        var deleteContext: FocusWriteContext? = null
        private var deleted = initiallyDeleted
        var terminalLookupCount = 0
            private set

        override suspend fun terminalSession(sessionId: UUID): FocusSession? {
            terminalLookupCount++
            return row.session.takeIf { !deleted && it.id == sessionId }
        }

        override suspend fun currentGeneration(): UUID = generation
        override fun observeOpen(): Flow<FocusSession?> = emptyFlow()
        override suspend fun start(
            commandId: UUID,
            sessionId: UUID,
            input: FocusStartInput,
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

        override fun observeInsights(range: TimeRange?): Flow<FocusInsights> = flowOf(FocusInsights.EMPTY)
        override fun observeTagSuggestions(): Flow<List<FocusTag>> = flowOf(emptyList())
        override fun observeTags(): Flow<List<FocusTag>> = flowOf(emptyList())

        override suspend fun history(
            query: FocusHistoryQuery,
            offset: Int,
            limit: Int,
        ): FocusPage = history(query.range, offset, limit)

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

    private class EmptyFocusProductRepository(
        private val generation: UUID,
        private val goalState: FocusGoalState = FocusGoalState.DISABLED,
        private val tagCatalog: FocusTagCatalog = FocusTagCatalog(0, emptyList(), databaseGeneration = generation),
        private val renameResult: FocusProductResult = FocusProductResult.Unavailable(),
    ) : FocusProductRepository {
        var lastIncludeTaskOrigin: Boolean? = null
            private set

        override suspend fun currentGeneration(): UUID = generation
        override fun observeGoals() = flowOf(goalState)
        override fun observeTaskFocusMillis(taskId: UUID) = flowOf(0L)
        override suspend fun unlinkTask(
            sessionId: UUID,
            expectedRevision: Long,
            context: FocusProductContext,
        ): FocusProductResult = error("Not used by this test")
        override suspend fun saveReflection(
            sessionId: UUID,
            expectedRevision: Long,
            reflection: com.luma.lifeos.domain.focus.FocusReflection,
            context: FocusProductContext,
        ): FocusProductResult = error("Not used by this test")
        override fun observeTagCatalog() = flowOf(tagCatalog)
        override fun observeTagCatalog(includeTaskOrigin: Boolean): Flow<FocusTagCatalog> {
            lastIncludeTaskOrigin = includeTaskOrigin
            return flowOf(if (includeTaskOrigin) tagCatalog else tagCatalog.copy(tags = emptyList()))
        }
        override suspend fun updateSessionMetadata(
            sessionId: UUID,
            expectedRevision: Long,
            name: String?,
            tagName: String?,
            expectedCatalogRevision: Long,
            context: FocusProductContext,
        ): FocusProductResult = error("Not used by this test")
        override suspend fun renameTag(
            tagId: UUID,
            name: String,
            expectedCatalogRevision: Long,
            context: FocusProductContext,
        ): FocusProductResult = renameResult
        override suspend fun mergeTags(
            sourceTagId: UUID,
            targetTagId: UUID,
            expectedCatalogRevision: Long,
            context: FocusProductContext,
        ): FocusProductResult = error("Not used by this test")
        override suspend fun scheduleGoal(
            goal: FocusGoalVersion,
            expectedSettingsRevision: Long,
            context: FocusProductContext,
        ): FocusProductResult = error("Not used by this test")
        override suspend fun setGoalsEnabled(
            enabled: Boolean,
            expectedSettingsRevision: Long,
            context: FocusProductContext,
        ): FocusProductResult = error("Not used by this test")
    }

    private fun renderCompletion(
        session: FocusSession,
        repository: FocusRepository? = null,
        productRepository: FocusProductRepository? = null,
        onDone: () -> Unit = {},
        onStartAnother: () -> Unit = {},
        onRepeat: (UUID) -> Unit = {},
        onStartBreak: (Int) -> Unit = {},
        onHistory: () -> Unit = {},
    ) {
        compose.setContent {
            LifeOsTheme {
                FocusCompletionContent(
                    state = FocusScreenState(
                        runtime = FocusRuntimeState(
                            initialized = true,
                            loading = false,
                            session = session,
                            databaseGeneration = GENERATION,
                        ),
                        focusContentReadable = repository != null,
                    ),
                    session = session,
                    focusRepository = repository,
                    focusProductRepository = productRepository,
                    taskContentReadable = true,
                    interrupted = session.state == FocusState.INTERRUPTED,
                    hasPendingDestination = false,
                    onDone = onDone,
                    onStartAnother = onStartAnother,
                    onRepeatSession = onRepeat,
                    onStartBreak = onStartBreak,
                    onEndBreak = {},
                    onRetry = {},
                    onEmergencyExit = {},
                    onOpenPendingDestination = {},
                    onOpenHistory = onHistory,
                    onTerminalSessionMissing = { _, _ -> },
                    onTaskSessionChanged = { _, _, _, _ -> },
                    onMarkTaskComplete = { _, _, _, _ -> },
                    onRetryTaskCompletion = { _, _, _, _ -> },
                    onUnlinkTask = { _, _, _, _ -> },
                    onRetryTaskUnlink = { _, _, _, _ -> },
                )
            }
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
