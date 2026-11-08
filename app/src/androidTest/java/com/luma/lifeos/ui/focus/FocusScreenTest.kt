package com.luma.lifeos.ui.focus

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.luma.lifeos.domain.focus.FocusAction
import com.luma.lifeos.domain.focus.FocusTimeSample
import com.luma.lifeos.domain.focus.createFocusSession
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
        compose.onNodeWithTag("focus_emergency_exit").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(requested) }
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

    @Test fun idleFocusScreenCanOpenAllSessionHistory() {
        var requested = false
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
                )
            }
        }

        compose.onNodeWithTag("focus_history_open").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(requested) }
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
