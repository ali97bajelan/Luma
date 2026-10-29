package com.luma.lifeos.ui.focus

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.luma.lifeos.MainActivity
import com.luma.lifeos.widget.WidgetIntents
import org.junit.Rule
import org.junit.Test

class FocusLifecycleTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun recreationSystemBackPauseResumeAndStopKeepOneFocusSession() {
        awaitTag("today_focus")
        compose.onNodeWithTag("today_focus").performClick()
        startOrRestartSession()

        compose.activityRule.scenario.recreate()
        awaitTag("focus_pause")
        compose.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        awaitTag("focus_back_dialog")
        compose.onNodeWithTag("focus_back_pause").performClick()
        awaitTag("today_focus")
        compose.onNodeWithTag("today_focus").assertIsDisplayed().performClick()
        awaitTag("focus_resume")
        compose.onNodeWithTag("focus_resume").performClick()
        awaitTag("focus_pause")
        compose.onNodeWithTag("focus_stop").performClick()
        awaitTag("focus_finished")
        compose.onNodeWithTag("focus_done").performClick()
        awaitTag("today_focus")
    }

    @Test fun moreEntryAndWidgetIntentWaitForAnExplicitPostPauseChoice() {
        awaitTag("nav_more")
        compose.onNodeWithTag("nav_more").performClick()
        awaitTag("more_focus")
        compose.onNodeWithTag("more_focus").performClick()
        startOrRestartSession()

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
        compose.onNodeWithTag("focus_pause").performClick()
        awaitTag("focus_resume")
        compose.onNodeWithTag("focus_open_pending_destination").assertIsDisplayed().performClick()
        awaitTag("screen_today")

        compose.onNodeWithTag("today_focus").performClick()
        awaitTag("focus_resume")
        compose.onNodeWithTag("focus_resume").performClick()
        awaitTag("focus_pause")
        compose.onNodeWithTag("focus_stop").performClick()
        awaitTag("focus_finished")
        compose.onNodeWithTag("focus_done").performClick()
    }

    private fun awaitTag(tag: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(tag).assertIsDisplayed()
    }

    private fun startOrRestartSession() {
        compose.waitUntil(10_000) {
            listOf("focus_start", "focus_start_another", "focus_finished", "focus_interrupted")
                .any { compose.onAllNodesWithTag(it).fetchSemanticsNodes().isNotEmpty() }
        }
        when {
            compose.onAllNodesWithTag("focus_start").fetchSemanticsNodes().isNotEmpty() ->
                compose.onNodeWithTag("focus_start").performClick()
            else -> compose.onNodeWithTag("focus_start_another").performClick()
        }
        awaitTag("focus_pause")
    }
}
