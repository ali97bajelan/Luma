package com.luma.lifeos.ui.focus

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
                    onStart = {},
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
                    onStart = {},
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
                )
            }
        }

        compose.onNodeWithTag("focus_pause").assertIsDisplayed()
        compose.onNodeWithTag("focus_stop").assertIsDisplayed()
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
                    onStart = {},
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
                )
            }
        }

        compose.onNodeWithTag("focus_retry_shortcut").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(retried) }
    }
}
