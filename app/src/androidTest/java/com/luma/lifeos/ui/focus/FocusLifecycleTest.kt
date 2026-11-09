package com.luma.lifeos.ui.focus

import android.content.Intent
import android.net.Uri
import com.luma.lifeos.LumaApplication
import com.luma.lifeos.focus.AndroidFocusClock
import com.luma.lifeos.focus.FocusBreakState
import com.luma.lifeos.domain.focus.FocusBreakWriteResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.luma.lifeos.MainActivity
import com.luma.lifeos.widget.WidgetIntents
import org.junit.Rule
import org.junit.Test

class FocusLifecycleTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun recreationSystemBackPauseResumeAndStopKeepOneFocusSession() {
        awaitTag("today_focus_open")
        compose.onNodeWithTag("today_focus_open").performClick()
        startOrRestartSession()

        compose.activityRule.scenario.recreate()
        awaitTag("focus_pause")
        compose.onNodeWithTag("focus_settings_open").assertDoesNotExist()
        compose.onNodeWithTag("focus_history_open").assertDoesNotExist()
        compose.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        awaitTag("focus_back_dialog")
        compose.onNodeWithTag("focus_back_pause").performClick()
        awaitTag("today_focus_open")
        compose.onNodeWithTag("today_focus_open").assertTextEquals(
            compose.activity.getString(com.luma.lifeos.R.string.today_focus_return_paused),
        )
        compose.onNodeWithTag("today_focus_open").assertIsDisplayed().performClick()
        awaitTag("focus_resume")
        compose.onNodeWithTag("focus_resume").performClick()
        awaitTag("focus_pause")
        compose.onNodeWithTag("focus_stop").performClick()
        awaitTag("focus_finished")
        compose.onNodeWithTag("focus_done").performClick()
        awaitTag("today_focus_open")
    }

    @Test fun moreEntryAndWidgetIntentWaitForAnExplicitPostPauseChoice() {
        awaitTag("nav_more")
        compose.onNodeWithTag("nav_more").performClick()
        awaitTag("more_focus")
        compose.onNodeWithTag("more_focus").performClick()
        startOrRestartSession()
        compose.onNodeWithTag("focus_settings_open").assertDoesNotExist()

        val externalWidgetIntent = Intent(compose.activity, MainActivity::class.java).apply {
            action = WidgetIntents.ACTION_NAVIGATION
            data = Uri.parse("luma-widget://navigate/navigation/42/today")
        }
        compose.activityRule.scenario.onActivity { activity ->
            val originalIntent = activity.intent
            activity.onNewIntent(externalWidgetIntent)
            activity.setIntent(originalIntent)
        }
        awaitTag("focus_open_pending_destination")
        compose.onNodeWithTag("focus_settings_open").assertDoesNotExist()
        compose.onNodeWithTag("focus_history_open").assertDoesNotExist()
        compose.onNodeWithTag("focus_pause").performClick()
        awaitTag("focus_resume")
        compose.onNodeWithTag("focus_open_pending_destination").assertIsDisplayed().performClick()
        awaitTag("screen_today")

        awaitTag("today_focus_open")
        compose.onNodeWithTag("today_focus_open").assertTextEquals(
            compose.activity.getString(com.luma.lifeos.R.string.today_focus_return_paused),
        )
        compose.onNodeWithTag("today_focus_open").performClick()
        awaitTag("focus_resume")
        compose.onNodeWithTag("focus_resume").performClick()
        awaitTag("focus_pause")
        compose.onNodeWithTag("focus_stop").performClick()
        awaitTag("focus_finished")
        compose.onNodeWithTag("focus_done").performClick()
    }

    @Test fun repeatedQuickCaptureWidgetIntentsStayBehindTheRunningGuard() {
        awaitTag("nav_more")
        compose.onNodeWithTag("nav_more").performClick()
        awaitTag("more_focus")
        compose.onNodeWithTag("more_focus").performClick()
        startOrRestartSession()

        repeat(2) {
            val quickCaptureIntent = Intent(compose.activity, MainActivity::class.java).apply {
                action = WidgetIntents.ACTION_NAVIGATION
                data = Uri.parse("luma-widget://navigate/navigation/42/quick-capture")
            }
            compose.activityRule.scenario.onActivity { activity ->
                val originalIntent = activity.intent
                activity.onNewIntent(quickCaptureIntent)
                activity.setIntent(originalIntent)
            }
            awaitTag("focus_open_pending_destination")
        }

        compose.onNodeWithTag("capture_sheet").assertDoesNotExist()
        compose.onNodeWithTag("focus_stop").performClick()
        awaitTag("focus_finished")
        compose.onNodeWithTag("focus_done").performClick()
    }

    @Test fun activeBreakContinuesAfterDoneAndCanBeEndedOnReturn() {
        clearBreak()
        openTerminalAndStartBreak()
        try {
            compose.onNodeWithTag("focus_done").performClick()
            awaitTag("today_focus_open")
            compose.onNodeWithTag("today_focus_open").performClick()
            awaitTag("focus_break_heading")
            awaitTag("focus_break_countdown")
            compose.onNodeWithTag("focus_break_leave_note").assertIsDisplayed()

            compose.onNodeWithTag("focus_end_break").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("focus_break_heading").fetchSemanticsNodes().isEmpty() &&
                    compose.onAllNodesWithTag("focus_finished").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("focus_saved_duration").assertIsDisplayed()
            compose.onNodeWithTag("focus_done").performClick()
            awaitTag("today_focus_open")
        } finally {
            clearBreak()
        }
    }

    @Test fun customBreakKeepsInvalidMinutesForCorrection() {
        clearBreak()
        openTerminal()
        try {
            compose.onNodeWithTag("focus_break_options_toggle").performClick()
            compose.onNodeWithTag("focus_customize_break").performClick()
            awaitTag("focus_custom_break_dialog")

            compose.onNodeWithTag("focus_custom_break_start").performClick()
            compose.onNodeWithTag("focus_custom_break_dialog").assertIsDisplayed()
            compose.onNodeWithTag("focus_break_minutes").performTextInput("0")
            compose.onNodeWithTag("focus_custom_break_start").performClick()
            compose.onNodeWithTag("focus_custom_break_dialog").assertIsDisplayed()
            compose.onNodeWithTag("focus_break_countdown").assertDoesNotExist()

            compose.onNodeWithTag("focus_break_minutes").performTextClearance()
            compose.onNodeWithTag("focus_break_minutes").performTextInput("61")
            compose.onNodeWithTag("focus_custom_break_start").performClick()
            compose.onNodeWithTag("focus_custom_break_dialog").assertIsDisplayed()
            compose.onNodeWithTag("focus_break_countdown").assertDoesNotExist()

            compose.onNodeWithTag("focus_break_minutes").performTextClearance()
            compose.onNodeWithTag("focus_break_minutes").performTextInput("1")
            compose.onNodeWithTag("focus_custom_break_start").performClick()
            awaitTag("focus_break_countdown")
        } finally {
            clearBreak()
        }
    }

    @Test fun expiredBreakOffersDoneAndPreviousSettingsWithoutStartingFocus() {
        clearBreak()
        openTerminal()
        compose.onNodeWithTag("focus_done").performClick()
        awaitTag("today_focus_open")
        seedExpiredBreak()

        try {
            compose.onNodeWithTag("today_focus_open").performClick()
            awaitTag("focus_break_expired")
            compose.onNodeWithTag("focus_done").assertIsDisplayed()
            compose.onNodeWithTag("focus_break_use_previous_settings").assertIsDisplayed()
            assertNull(runBlocking { (compose.activity.application as LumaApplication).storage.focus.observeOpen().first() })

            compose.onNodeWithTag("focus_break_use_previous_settings").performClick()
            awaitTag("focus_name")
            compose.onNodeWithTag("focus_pause").assertDoesNotExist()
            assertNull(runBlocking { (compose.activity.application as LumaApplication).storage.focus.observeOpen().first() })
        } finally {
            clearBreak()
        }
    }

    @Test fun startingFocusDuringBreakRequiresConfirmationBeforeReplacingIt() {
        clearBreak()
        openTerminalAndStartBreak()
        try {
            compose.onNodeWithTag("focus_start_another").performClick()
            awaitTag("focus_name")
            compose.onNodeWithTag("focus_name").performTextClearance()
            compose.onNodeWithTag("focus_name").performTextInput("After break")
            compose.onNodeWithTag("focus_start").performClick()
            awaitTag("focus_break_replace_dialog")
            assertNotNull(runBlocking { (compose.activity.application as LumaApplication).storage.focus.currentBreak() })

            compose.onNodeWithTag("focus_break_replace_cancel").performClick()
            compose.onNodeWithTag("focus_break_countdown").assertIsDisplayed()
            compose.onNodeWithTag("focus_start").performClick()
            awaitTag("focus_break_replace_dialog")
            compose.onNodeWithTag("focus_break_replace_confirm").performClick()
            awaitTag("focus_pause")
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("focus_break_countdown").fetchSemanticsNodes().isEmpty()
            }
            assertNull(runBlocking { (compose.activity.application as LumaApplication).storage.focus.currentBreak() })
            compose.onNodeWithTag("focus_stop").performClick()
            awaitTag("focus_finished")
        } finally {
            clearBreak()
        }
    }

    private fun openTerminalAndStartBreak() {
        openTerminal()
        compose.onNodeWithTag("focus_break_options_toggle").performClick()
        compose.onNodeWithTag("focus_start_five_minute_break").performClick()
        awaitTag("focus_break_countdown")
    }

    private fun openTerminal() {
        awaitTag("today_focus_open")
        compose.onNodeWithTag("today_focus_open").performClick()
        startOrRestartSession()
        if (compose.onAllNodesWithTag("focus_pause").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("focus_stop").performClick()
        }
        awaitTag("focus_finished")
    }

    private fun clearBreak() {
        val app = compose.activity.application as LumaApplication
        runBlocking {
            val repository = app.storage.focus
            repository.currentBreak()?.let { record ->
                repository.endBreak(record.revision, record.databaseGeneration)
            }
            app.focusBreakRuntime.state.first { it == FocusBreakState.Idle }
        }
    }

    private fun seedExpiredBreak() {
        val app = compose.activity.application as LumaApplication
        runBlocking {
            val repository = app.storage.focus
            val sample = AndroidFocusClock(compose.activity).sample()
            val durationMillis = 60_000L
            val result = repository.startBreak(
                durationMillis = durationMillis,
                startedElapsedMillis = (sample.elapsedMillis - durationMillis).coerceAtLeast(0L),
                bootId = sample.bootId,
                expectedGeneration = repository.currentGeneration(),
            )
            assertTrue(result is FocusBreakWriteResult.Applied)
            val record = requireNotNull(repository.currentBreak())
            app.focusBreakRuntime.expireFromAlarm(
                record.databaseGeneration,
                record.revision,
                record.startedElapsedMillis + record.durationMillis,
            )
            assertTrue(app.focusBreakRuntime.state.value is FocusBreakState.Expired)
        }
    }

    private fun awaitTag(tag: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(tag).assertIsDisplayed()
    }

    private fun startOrRestartSession() {
        compose.waitUntil(10_000) {
            listOf("focus_pause", "focus_name", "focus_start_another")
                .any { compose.onAllNodesWithTag(it).fetchSemanticsNodes().isNotEmpty() }
        }
        if (compose.onAllNodesWithTag("focus_pause").fetchSemanticsNodes().isNotEmpty()) return
        if (compose.onAllNodesWithTag("focus_start_another").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("focus_start_another").performClick()
        }
        awaitTag("focus_name")
        compose.onNodeWithTag("focus_name").performTextClearance()
        compose.onNodeWithTag("focus_name").performTextInput("Lifecycle focus")
        compose.onNodeWithTag("focus_start").performClick()
        awaitTag("focus_pause")
    }
}
