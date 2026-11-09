package com.luma.lifeos.ui.focus

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.luma.lifeos.domain.focus.FocusAction
import com.luma.lifeos.domain.focus.FocusState
import com.luma.lifeos.domain.focus.FocusTimeSample
import com.luma.lifeos.domain.focus.createFocusSession
import com.luma.lifeos.focus.AllowedFocusApps
import com.luma.lifeos.focus.FocusOperationFailure
import com.luma.lifeos.focus.FocusRuntimeState
import com.luma.lifeos.focus.FocusScreenState
import com.luma.lifeos.ui.theme.LifeOsTheme
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FocusScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun systemBackPromptProvidesReachablePauseAndStopActions() {
        var leaveAction: FocusAction? = null
        val session = createFocusSession(
            sessionId = UUID.fromString("20000000-0000-0000-0000-000000000001"),
            commandId = UUID.fromString("20000000-0000-0000-0000-000000000002"),
            sample = FocusTimeSample(Instant.parse("2026-10-30T12:00:00Z"), 10_000, "boot-a"),
        )

        compose.setContent {
            LifeOsTheme {
                FocusScreen(
                    state = FocusScreenState(
                        runtime = FocusRuntimeState(initialized = true, loading = false, session = session),
                        backPromptVisible = true,
                    ),
                    allowedApps = emptyList(),
                    hasPendingDestination = false,
                    onBack = {},
                    onShowBackPrompt = {},
                    onDismissBackPrompt = {},
                    onStart = { _ -> },
                    onNameChange = {},
                    onTagChange = {},
                    onPeriodChange = {},
                    onRetryInsights = {},
                    onAction = {},
                    onLeaveAfterAction = { leaveAction = it },
                    onResolveRecovery = {},
                    onRetry = {},
                    onRetryInitialization = {},
                    onEmergencyExit = {},
                    onDismissEmergencyExit = {},
                    onConfirmEmergencyExit = {},
                    onLaunchApp = {},
                    onOpenPendingDestination = {},
                    onOpenHistory = {},
                )
            }
        }

        compose.onNodeWithTag("focus_back_dialog").assertIsDisplayed()
        compose.onNodeWithTag("focus_back_pause").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(FocusAction.PAUSE, leaveAction) }
    }

    @Test fun healthyRunningScreenAlwaysOffersEmergencyExit() {
        var requested = false
        val session = createFocusSession(
            sessionId = UUID.fromString("20000000-0000-0000-0000-000000000003"),
            commandId = UUID.fromString("20000000-0000-0000-0000-000000000004"),
            sample = FocusTimeSample(Instant.parse("2026-10-30T12:00:00Z"), 10_000, "boot-a"),
        )

        compose.setContent {
            LifeOsTheme {
                FocusScreen(
                    state = FocusScreenState(
                        runtime = FocusRuntimeState(initialized = true, loading = false, session = session),
                    ),
                    allowedApps = emptyList(),
                    hasPendingDestination = false,
                    onBack = {},
                    onShowBackPrompt = {},
                    onDismissBackPrompt = {},
                    onStart = { _ -> },
                    onNameChange = {},
                    onTagChange = {},
                    onPeriodChange = {},
                    onRetryInsights = {},
                    onAction = {},
                    onLeaveAfterAction = {},
                    onResolveRecovery = {},
                    onRetry = {},
                    onRetryInitialization = {},
                    onEmergencyExit = { requested = true },
                    onDismissEmergencyExit = {},
                    onConfirmEmergencyExit = {},
                    onLaunchApp = {},
                    onOpenPendingDestination = {},
                    onOpenHistory = {},
                )
            }
        }

        compose.onNodeWithTag("focus_pause").assertIsDisplayed()
        compose.onNodeWithTag("focus_stop").assertIsDisplayed()
        compose.onNodeWithTag("focus_history_open").assertDoesNotExist()
        compose.onNodeWithTag("focus_settings_open").assertDoesNotExist()
        compose.onNodeWithTag("focus_help_toggle").assertIsDisplayed().performClick()
        compose.onNodeWithTag("focus_emergency_exit_help").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(requested) }
    }

    @Test fun recoveryScreenHidesHistoryAndSettingsDestinations() {
        val session = createFocusSession(
            sessionId = UUID.fromString("20000000-0000-0000-0000-000000000009"),
            commandId = UUID.fromString("20000000-0000-0000-0000-000000000010"),
            sample = FocusTimeSample(Instant.parse("2026-10-30T12:00:00Z"), 10_000, "boot-a"),
        )

        compose.setContent {
            LifeOsTheme {
                FocusScreen(
                    state = FocusScreenState(
                        runtime = FocusRuntimeState(
                            initialized = true,
                            loading = false,
                            session = session,
                            recoveryRequired = true,
                        ),
                    ),
                    allowedApps = emptyList(),
                    hasPendingDestination = false,
                    onBack = {},
                    onShowBackPrompt = {},
                    onDismissBackPrompt = {},
                    onStart = { _ -> },
                    onNameChange = {},
                    onTagChange = {},
                    onPeriodChange = {},
                    onRetryInsights = {},
                    onAction = {},
                    onLeaveAfterAction = {},
                    onResolveRecovery = {},
                    onRetry = {},
                    onRetryInitialization = {},
                    onEmergencyExit = {},
                    onDismissEmergencyExit = {},
                    onConfirmEmergencyExit = {},
                    onLaunchApp = {},
                    onOpenPendingDestination = {},
                    onOpenHistory = {},
                )
            }
        }

        compose.onNodeWithTag("focus_recovery_continue").assertIsDisplayed()
        compose.onNodeWithTag("focus_emergency_exit").assertIsDisplayed()
        compose.onNodeWithTag("focus_history_open").assertDoesNotExist()
        compose.onNodeWithTag("focus_settings_open").assertDoesNotExist()
    }

    @Test fun unavailableShortcutOffersRetryWithoutLeavingTheRunningScreen() {
        var retried = false
        val session = createFocusSession(
            sessionId = UUID.fromString("20000000-0000-0000-0000-000000000005"),
            commandId = UUID.fromString("20000000-0000-0000-0000-000000000006"),
            sample = FocusTimeSample(Instant.parse("2026-10-30T12:00:00Z"), 10_000, "boot-a"),
        )

        compose.setContent {
            LifeOsTheme {
                FocusScreen(
                    state = FocusScreenState(
                        runtime = FocusRuntimeState(initialized = true, loading = false, session = session),
                        shortcutUnavailable = true,
                    ),
                    allowedApps = emptyList(),
                    hasPendingDestination = false,
                    onBack = {},
                    onShowBackPrompt = {},
                    onDismissBackPrompt = {},
                    onStart = { _ -> },
                    onNameChange = {},
                    onTagChange = {},
                    onPeriodChange = {},
                    onRetryInsights = {},
                    onAction = {},
                    onLeaveAfterAction = {},
                    onResolveRecovery = {},
                    onRetry = { retried = true },
                    onRetryInitialization = {},
                    onEmergencyExit = {},
                    onDismissEmergencyExit = {},
                    onConfirmEmergencyExit = {},
                    onLaunchApp = {},
                    onOpenPendingDestination = {},
                    onOpenHistory = {},
                )
            }
        }

        compose.onNodeWithTag("focus_retry_shortcut").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(retried) }
    }

    @Test fun runningScreenPrioritizesLabeledTimerAndOmitsMissingTag() {
        val session = createFocusSession(
            sessionId = UUID.fromString("20000000-0000-0000-0000-000000000011"),
            commandId = UUID.fromString("20000000-0000-0000-0000-000000000012"),
            sample = FocusTimeSample(Instant.parse("2026-10-30T12:00:00Z"), 10_000, "boot-a"),
        )
        renderFocusScreen(
            FocusScreenState(
                runtime = FocusRuntimeState(
                    initialized = true,
                    loading = false,
                    session = session,
                    displayedMillis = 60_000,
                ),
            ),
        )

        compose.onNodeWithTag("focus_running_label").assertIsDisplayed()
        compose.onNodeWithTag("focus_session_name").assertIsDisplayed()
        compose.onNodeWithTag("focus_session_tag").assertDoesNotExist()
        compose.onNodeWithText("Time focused").assertIsDisplayed()
        compose.onNodeWithTag("focus_elapsed").assertIsDisplayed()
        compose.onNodeWithText("Pause").assertIsDisplayed()
        compose.onNodeWithText("End and save").assertIsDisplayed()
    }

    @Test fun busyRunningControlsCannotSubmitAnotherAction() {
        val session = createFocusSession(
            sessionId = UUID.fromString("20000000-0000-0000-0000-000000000013"),
            commandId = UUID.fromString("20000000-0000-0000-0000-000000000014"),
            sample = FocusTimeSample(Instant.parse("2026-10-30T12:00:00Z"), 10_000, "boot-a"),
        )
        renderFocusScreen(
            FocusScreenState(
                runtime = FocusRuntimeState(initialized = true, loading = false, session = session),
                busy = true,
            ),
        )

        compose.onNodeWithTag("focus_pause").assertIsNotEnabled()
        compose.onNodeWithTag("focus_stop").assertIsNotEnabled()
    }

    @Test fun shortcutsStayCollapsedAndOnlyDisambiguateDuplicateLabels() {
        val session = createFocusSession(
            sessionId = UUID.fromString("20000000-0000-0000-0000-000000000015"),
            commandId = UUID.fromString("20000000-0000-0000-0000-000000000016"),
            sample = FocusTimeSample(Instant.parse("2026-10-30T12:00:00Z"), 10_000, "boot-a"),
        )
        val apps = listOf(
            AllowedFocusApps.App("com.example.reader.one", "Reader"),
            AllowedFocusApps.App("com.example.reader.two", "Reader"),
        )
        renderFocusScreen(
            state = FocusScreenState(runtime = FocusRuntimeState(
                initialized = true,
                loading = false,
                session = session,
            )),
            allowedApps = apps,
        )

        compose.onNodeWithTag("focus_shortcuts_toggle").assertIsDisplayed()
        compose.onNodeWithTag("focus_app_com.example.reader.one").assertDoesNotExist()
        compose.onNodeWithTag("focus_shortcuts_toggle").performClick()
        compose.onNodeWithText("Reader · com.example.reader.one").assertIsDisplayed()
        compose.onNodeWithText("Reader · com.example.reader.two").assertIsDisplayed()
    }

    @Test fun pausedShortcutFailureNamesAppAndOffersRetryAndManageWithoutTimerAction() {
        val session = createFocusSession(
            sessionId = UUID.fromString("20000000-0000-0000-0000-000000000017"),
            commandId = UUID.fromString("20000000-0000-0000-0000-000000000018"),
            sample = FocusTimeSample(Instant.parse("2026-10-30T12:00:00Z"), 10_000, "boot-a"),
        ).copy(state = FocusState.PAUSED)
        var retryCalls = 0
        var manageCalls = 0
        val timerActions = mutableListOf<FocusAction>()
        renderFocusScreen(
            state = FocusScreenState(
                runtime = FocusRuntimeState(initialized = true, loading = false, session = session),
                shortcutUnavailable = true,
                shortcutLabel = "Reader",
            ),
            onRetry = { retryCalls++ },
            onAction = { timerActions += it },
            onOpenSettings = { manageCalls++ },
        )

        compose.onNodeWithText("Could not open Reader.").assertIsDisplayed()
        compose.onNodeWithText("Paused — the timer is not counting.").assertIsDisplayed()
        compose.onNodeWithText("You can leave while paused. Resume from Today or More.").assertIsDisplayed()
        compose.onNodeWithTag("focus_retry_shortcut").assertIsDisplayed().performClick()
        compose.onNodeWithTag("focus_manage_shortcuts").assertIsDisplayed().performClick()
        compose.onNodeWithTag("focus_emergency_exit").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, retryCalls)
            assertEquals(1, manageCalls)
            assertTrue(timerActions.isEmpty())
        }
    }

    @Test fun checkpointFailureExplainsLastSavedTimeAndOffersRetry() {
        val session = createFocusSession(
            sessionId = UUID.fromString("20000000-0000-0000-0000-000000000021"),
            commandId = UUID.fromString("20000000-0000-0000-0000-000000000022"),
            sample = FocusTimeSample(Instant.parse("2026-10-30T12:00:00Z"), 10_000, "boot-a"),
        )
        var retried = false
        renderFocusScreen(
            state = FocusScreenState(
                runtime = FocusRuntimeState(
                    initialized = true,
                    loading = false,
                    session = session,
                    checkpointFailed = true,
                ),
                operationFailed = true,
                operationFailure = FocusOperationFailure.CHECKPOINT,
            ),
            onRetry = { retried = true },
        )

        compose.onNodeWithText(
            "Recent focus time could not be saved. The session remains at its last checkpoint; retry to save the same interval.",
        ).assertIsDisplayed()
        compose.onNodeWithTag("focus_retry").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(retried) }
    }

    @Test fun restoreFailureKeepsRetryAndEmergencyExitAvailable() {
        var retried = false
        var emergencyExitRequested = false
        renderFocusScreen(
            state = FocusScreenState(
                runtime = FocusRuntimeState(initialized = false, loading = false, loadFailed = true),
            ),
            onRetryInitialization = { retried = true },
            onEmergencyExit = { emergencyExitRequested = true },
        )

        compose.onNodeWithTag("focus_retry_load").assertIsDisplayed().performClick()
        compose.onNodeWithTag("focus_emergency_exit").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertTrue(retried)
            assertTrue(emergencyExitRequested)
        }
    }

    @Test fun failedEndExplainsSavedStateAndKeepsScopedRetry() {
        val session = createFocusSession(
            sessionId = UUID.fromString("20000000-0000-0000-0000-000000000019"),
            commandId = UUID.fromString("20000000-0000-0000-0000-000000000020"),
            sample = FocusTimeSample(Instant.parse("2026-10-30T12:00:00Z"), 10_000, "boot-a"),
        )
        var retried = false
        renderFocusScreen(
            state = FocusScreenState(
                runtime = FocusRuntimeState(initialized = true, loading = false, session = session),
                operationFailed = true,
                operationFailure = FocusOperationFailure.END,
            ),
            onRetry = { retried = true },
        )

        compose.onNodeWithText(
            "End and save could not be confirmed. Your last saved focus time remains available; retry the same action.",
        ).assertIsDisplayed()
        compose.onNodeWithTag("focus_help_toggle").assertIsDisplayed().performClick()
        compose.onNodeWithText(
            "Use the error below to retry the same action. Emergency exit is shown with the error.",
        ).assertIsDisplayed()
        compose.onNodeWithTag("focus_emergency_exit").assertIsDisplayed()
        compose.onNodeWithTag("focus_retry").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(retried) }
    }

    @Test fun idleFocusScreenCanOpenHistoryAndSettings() {
        var requested = false
        var settingsRequested = false
        compose.setContent {
            LifeOsTheme {
                FocusScreen(
                    state = FocusScreenState(runtime = FocusRuntimeState(initialized = true, loading = false)),
                    allowedApps = emptyList(),
                    hasPendingDestination = false,
                    onBack = {},
                    onShowBackPrompt = {},
                    onDismissBackPrompt = {},
                    onStart = { _ -> },
                    onNameChange = {},
                    onTagChange = {},
                    onPeriodChange = {},
                    onRetryInsights = {},
                    onAction = {},
                    onLeaveAfterAction = {},
                    onResolveRecovery = {},
                    onRetry = {},
                    onRetryInitialization = {},
                    onEmergencyExit = {},
                    onDismissEmergencyExit = {},
                    onConfirmEmergencyExit = {},
                    onLaunchApp = {},
                    onOpenPendingDestination = {},
                    onOpenHistory = { requested = true },
                    onOpenSettings = { settingsRequested = true },
                )
            }
        }

        compose.onNodeWithTag("focus_history_open").assertIsDisplayed().performClick()
        compose.onNodeWithTag("focus_settings_open").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertTrue(requested)
            assertTrue(settingsRequested)
        }
    }

    @Test fun leaveActionsHaveDistinctBounds() {
        val actions = mutableListOf<FocusAction>()
        renderBackPrompt(onLeaveAfterAction = { actions += it })

        val actionTags = listOf("focus_back_pause", "focus_back_stop", "focus_back_stay")
        val bounds = actionTags.map { tag ->
            compose.onNodeWithTag(tag)
                .assertIsDisplayed()
                .assertHeightIsAtLeast(48.dp)
                .fetchSemanticsNode()
                .boundsInRoot
        }
        bounds.zipWithNext().forEach { (first, second) ->
            assertTrue("Adjacent leave actions overlap", first.bottom <= second.top)
        }

        compose.onNodeWithTag("focus_back_pause").performClick()
        compose.runOnIdle { assertEquals(listOf(FocusAction.PAUSE), actions) }
    }

    @Test fun dismissLeaveKeepsRunning() {
        var dismissed = false
        val actions = mutableListOf<FocusAction>()
        renderBackPrompt(
            onDismissBackPrompt = { dismissed = true },
            onLeaveAfterAction = { actions += it },
        )

        compose.onNodeWithTag("focus_back_stay").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertTrue(dismissed)
            assertTrue(actions.isEmpty())
        }
        compose.onNodeWithTag("focus_pause").assertIsDisplayed()
    }

    @Test fun leaveWaitsForSuccessfulAction() {
        val actions = mutableListOf<FocusAction>()
        var backCalls = 0
        renderBackPrompt(
            onBack = { backCalls++ },
            onLeaveAfterAction = { actions += it },
            operationFailed = true,
        )

        compose.onNodeWithTag("focus_back_stop").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(listOf(FocusAction.STOP), actions)
            assertEquals(0, backCalls)
        }
        compose.onNodeWithTag("focus_back_dialog").assertIsDisplayed()
        compose.onNodeWithTag("focus_write_error").assertIsDisplayed()
        compose.onNodeWithTag("focus_emergency_exit").assertIsDisplayed()
    }

    private fun renderFocusScreen(
        state: FocusScreenState,
        allowedApps: List<AllowedFocusApps.App> = emptyList(),
        hasPendingDestination: Boolean = false,
        onAction: (FocusAction) -> Unit = {},
        onRetry: () -> Unit = {},
        onRetryInitialization: () -> Unit = {},
        onEmergencyExit: () -> Unit = {},
        onLaunchApp: (String) -> Unit = {},
        onOpenSettings: () -> Unit = {},
    ) {
        compose.setContent {
            LifeOsTheme {
                FocusScreen(
                    state = state,
                    allowedApps = allowedApps,
                    hasPendingDestination = hasPendingDestination,
                    onBack = {},
                    onShowBackPrompt = {},
                    onDismissBackPrompt = {},
                    onStart = {},
                    onNameChange = {},
                    onTagChange = {},
                    onPeriodChange = {},
                    onRetryInsights = {},
                    onAction = onAction,
                    onLeaveAfterAction = {},
                    onResolveRecovery = {},
                    onRetry = onRetry,
                    onRetryInitialization = onRetryInitialization,
                    onEmergencyExit = onEmergencyExit,
                    onDismissEmergencyExit = {},
                    onConfirmEmergencyExit = {},
                    onLaunchApp = onLaunchApp,
                    onOpenPendingDestination = {},
                    onOpenHistory = {},
                    onOpenSettings = onOpenSettings,
                )
            }
        }
    }

    private fun renderBackPrompt(
        onBack: () -> Unit = {},
        onDismissBackPrompt: () -> Unit = {},
        onLeaveAfterAction: (FocusAction) -> Unit = {},
        operationFailed: Boolean = false,
    ) {
        val session = createFocusSession(
            sessionId = UUID.fromString("20000000-0000-0000-0000-000000000007"),
            commandId = UUID.fromString("20000000-0000-0000-0000-000000000008"),
            sample = FocusTimeSample(Instant.parse("2026-10-30T12:00:00Z"), 10_000, "boot-a"),
        )

        compose.setContent {
            LifeOsTheme {
                FocusScreen(
                    state = FocusScreenState(
                        runtime = FocusRuntimeState(initialized = true, loading = false, session = session),
                        operationFailed = operationFailed,
                        backPromptVisible = true,
                    ),
                    allowedApps = emptyList(),
                    hasPendingDestination = false,
                    onBack = onBack,
                    onShowBackPrompt = {},
                    onDismissBackPrompt = onDismissBackPrompt,
                    onStart = { _ -> },
                    onNameChange = {},
                    onTagChange = {},
                    onPeriodChange = {},
                    onRetryInsights = {},
                    onAction = {},
                    onLeaveAfterAction = onLeaveAfterAction,
                    onResolveRecovery = {},
                    onRetry = {},
                    onRetryInitialization = {},
                    onEmergencyExit = {},
                    onDismissEmergencyExit = {},
                    onConfirmEmergencyExit = {},
                    onLaunchApp = {},
                    onOpenPendingDestination = {},
                    onOpenHistory = {},
                )
            }
        }
    }
}
