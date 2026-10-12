package com.luma.lifeos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class MemoryCaptureTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun directCaptureRestoresDraftAppearsInReviewAndCanBeEditedAndDeleted() {
        val note = "Memory test ${System.nanoTime()}"
        compose.onNodeWithTag("quick_capture").performClick()
        compose.onNodeWithTag("capture_memory").performClick()
        awaitTag("memory_note")
        compose.onNodeWithTag("memory_note").performTextInput(note)
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
        awaitTag("memory_note")
        compose.onNodeWithTag("memory_note").performTextReplacement("$note edited")
        compose.onNodeWithTag("memory_save").performClick()
        awaitTag("screen_timeline")
        compose.onNodeWithTag("screen_timeline").performScrollToNode(hasText("$note edited"))
        compose.onNodeWithText("$note edited").performClick()
        awaitTag("memory_remove")
        compose.onNodeWithTag("memory_remove").performClick()
        compose.onNodeWithTag("memory_confirm_remove").performClick()
        awaitTag("screen_timeline")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("$note edited").fetchSemanticsNodes().isEmpty() }
    }
    @Test fun journalPromotionAndUnmarkingRetainOriginalBody() {
        val body = "Remember journal ${System.nanoTime()}"
        compose.onNodeWithTag("quick_capture").performClick()
        compose.onNodeWithTag("capture_journal").performClick()
        awaitTag("journal_body")
        compose.onNodeWithTag("journal_body").performTextInput(body)
        compose.onNodeWithTag("journal_save").performClick()
        awaitTag("screen_today"); awaitTag("today_summary")
        compose.onNodeWithTag("screen_today").performScrollToNode(hasText(body))
        compose.onNodeWithText(body).performClick()
        awaitTag("journal_detail_body")
        compose.onNodeWithTag("journal_memory").performClick()
        awaitTag("memory_original")
        compose.onNodeWithTag("memory_original").assertTextContains(body)
        compose.onNodeWithTag("memory_save").performClick()
        awaitTag("journal_detail_body")
        compose.onNodeWithTag("memory_status").assertIsDisplayed()
        compose.onNodeWithTag("journal_memory").performClick()
        awaitTag("memory_remove")
        compose.onNodeWithTag("memory_remove").performClick()
        compose.onNodeWithTag("memory_confirm_remove").performClick()
        awaitTag("journal_detail_body")
        compose.onNodeWithTag("journal_detail_body").assertTextEquals(body)
        compose.onNodeWithTag("memory_status").assertDoesNotExist()
        // Delete the source journal to leave the shared test database clean.
        compose.onNodeWithTag("journal_edit").performClick()
        awaitTag("journal_delete")
        compose.onNodeWithTag("journal_delete").performClick()
        compose.onNodeWithTag("journal_confirm_delete").performClick()
        awaitTag("screen_today")
    }
    private fun awaitTag(tag: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }
}
