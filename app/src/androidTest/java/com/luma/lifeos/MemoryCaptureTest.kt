package com.luma.lifeos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.JournalInput
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class MemoryCaptureTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun directCaptureRestoresDraftAppearsInReviewAndCanBeEditedAndDeleted() {
        val note = "Memory test ${System.nanoTime()}"
        compose.onNodeWithTag("today_capture_memory").performClick()
        awaitTag("memory_note")
        compose.onNodeWithTag("memory_note").performTextInput(note)
        awaitEnabled("memory_save")
        awaitStateDescription("memory_importance_toggle", R.string.review_details_collapsed)
        compose.onNodeWithTag("memory_importance_toggle").performScrollTo().performClick()
        awaitStateDescription("memory_importance_toggle", R.string.review_details_expanded)
        awaitTag("memory_importance_5")
        compose.onNodeWithTag("memory_importance_5").performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        awaitTag("memory_note")
        compose.onNodeWithTag("memory_note").assertTextContains(note)
        compose.onNodeWithTag("memory_save").performClick()
        awaitTag("screen_today")
        compose.onNodeWithTag("nav_review").performClick()
        awaitTag("memory_review_list")
        compose.onNodeWithTag("memory_review_list").performScrollToNode(hasText(note))
        compose.onNodeWithText(note).assertIsDisplayed()
        compose.onNodeWithTag("nav_timeline").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("timeline_loading").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("screen_timeline").performScrollToNode(hasText(note))
        compose.onNodeWithText(note).performClick()
        awaitTag("event_edit")
        compose.onNodeWithTag("event_edit").performClick()
        awaitTag("memory_note")
        compose.onNodeWithTag("memory_note").performTextReplacement("$note edited")
        compose.onNodeWithTag("memory_save").performClick()
        awaitTag("screen_event_detail")
        compose.onNodeWithTag("event_detail_back").performClick()
        awaitTag("screen_timeline")
        compose.onNodeWithTag("screen_timeline").performScrollToNode(hasText("$note edited"))
        compose.onNodeWithText("$note edited").performClick()
        awaitTag("event_edit")
        compose.onNodeWithTag("event_edit").performClick()
        awaitEnabled("memory_remove")
        compose.onNodeWithTag("memory_remove").performClick()
        awaitTag("memory_confirm_remove")
        compose.onNodeWithTag("memory_confirm_remove").performClick()
        awaitTag("screen_timeline")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("$note edited").fetchSemanticsNodes().isEmpty() }
    }
    @Test fun journalPromotionAndUnmarkingRetainOriginalBody() {
        val id = EventId.new()
        val body = "Remember journal ${System.nanoTime()}"
        val storage = (compose.activity.application as LumaApplication).storage
        runBlocking { storage.journals.create(id, JournalInput(body, Instant.now())) }
        try {
            compose.onNodeWithTag("nav_timeline").performClick()
            awaitTag("timeline_counts")
            compose.onNodeWithTag("screen_timeline").performScrollToNode(hasTestTag("event_$id"))
            compose.onNodeWithTag("event_$id").performClick()
            awaitTag("event_detail_body")
            compose.onNodeWithTag("event_promote").performClick()
            awaitTag("memory_original")
            compose.onNodeWithTag("memory_original").assertTextContains(body)
            compose.onNodeWithTag("capture_timestamp_change").assertIsNotEnabled()
            compose.onNodeWithTag("memory_save").performClick()
            awaitTag("event_detail_body")
            compose.onNodeWithTag("memory_status").assertIsDisplayed()
            compose.onNodeWithTag("event_promote").performClick()
            awaitEnabled("memory_remove")
            compose.onNodeWithTag("memory_remove").performClick()
            awaitTag("memory_confirm_remove")
            compose.onNodeWithTag("memory_confirm_remove").performClick()
            awaitTag("event_detail_body")
            compose.onNodeWithTag("event_detail_body").assertTextEquals(body)
            compose.onNodeWithTag("memory_status").assertDoesNotExist()
        } finally {
            runBlocking { storage.lifeEvents.delete(id) }
        }
    }
    private fun awaitTag(tag: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun awaitEnabled(tag: String) {
        compose.waitUntil(10_000) {
            try {
                compose.onNodeWithTag(tag).assertIsEnabled()
                true
            } catch (_: AssertionError) {
                false
            }
        }
    }

    private fun awaitStateDescription(tag: String, stateResource: Int) {
        val expected = compose.activity.getString(stateResource)
        compose.waitUntil(10_000) {
            try {
                compose.onNodeWithTag(tag).assert(
                    SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, expected),
                )
                true
            } catch (_: AssertionError) {
                false
            }
        }
    }


}
