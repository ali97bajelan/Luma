package com.luma.lifeos

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

class MoodCaptureTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun capturesMultipleEmotionsRestoresDraftAndEditsAndDeletesFromTimeline() {
        val note = "Mood capture test ${System.nanoTime()}"
        compose.onNodeWithTag("quick_capture").performClick()
        compose.onNodeWithTag("capture_mood").performClick()
        awaitTag("mood_score_4")
        compose.onNodeWithTag("mood_score_4").performClick()
        compose.onNodeWithTag("mood_emotion_Calm").performScrollTo().performClick()
        compose.onNodeWithTag("mood_emotion_Happy").performScrollTo().performClick()
        compose.onNodeWithTag("mood_more").performScrollTo().performClick()
        compose.onNodeWithTag("mood_energy_2").performScrollTo().performClick()
        compose.onNodeWithTag("mood_stress_5").performScrollTo().performClick()
        compose.onNodeWithTag("mood_note").performScrollTo().performTextInput(note)
        compose.activityRule.scenario.recreate()
        awaitTag("mood_score_4")
        compose.onNodeWithTag("mood_score_4").assertIsSelected()
        compose.onNodeWithTag("mood_save").performClick()
        awaitTag("screen_today")
        awaitText(note, "screen_today")
        compose.onNodeWithTag("nav_timeline").performClick()
        awaitText(note, "screen_timeline")
        compose.onNodeWithText(note).performClick()
        awaitTag("mood_score_2")
        compose.onNodeWithTag("mood_score_2").performClick()
        compose.onNodeWithTag("mood_save").performClick()
        awaitTag("screen_timeline")
        awaitText(note, "screen_timeline")
        compose.onNodeWithText(note).performClick()
        awaitTag("mood_score_2")
        compose.onNodeWithTag("mood_score_2").assertIsSelected()
        compose.onNodeWithTag("mood_delete").performClick()
        compose.onNodeWithTag("mood_confirm_delete").performClick()
        awaitTag("screen_timeline")
        compose.waitUntil(5_000) { compose.onAllNodesWithText(note).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("screen_timeline").assertIsDisplayed()
    }

    private fun awaitText(text: String, feed: String) {
        awaitTag("today_summary")
        compose.onNodeWithTag(feed).performScrollToNode(hasText(text))
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    private fun awaitTag(tag: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }
}
