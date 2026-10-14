package com.luma.lifeos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import org.junit.Rule
import org.junit.Test

class JournalCaptureTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun captureRestoresDraftAndOpensDetailThenEditsAndDeletes() {
        val body = "Journal test ${System.nanoTime()}"
        compose.onNodeWithTag("quick_capture").performClick()
        compose.onNodeWithTag("capture_journal").performClick()
        awaitTag("journal_body")
        compose.onNodeWithTag("journal_body").performTextInput(body)
        compose.onNodeWithTag("journal_title").performTextInput("A journal")
        compose.onNodeWithTag("journal_tag_input").performScrollTo().performTextInput("Ideas")
        compose.onNodeWithTag("journal_add_tag").performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        awaitTag("journal_body")
        compose.onNodeWithTag("journal_body").assertTextContains(body)
        compose.onNodeWithTag("journal_save").performClick()
        awaitTag("screen_today")
        compose.onNodeWithTag("nav_timeline").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("timeline_loading").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("screen_timeline").performScrollToNode(hasText(body))
        compose.onNodeWithText(body).performClick()
        awaitTag("journal_detail_body")
        compose.onNodeWithTag("journal_detail_body").assertTextEquals(body)
        compose.onNodeWithText("Ideas").assertExists()
        compose.onNodeWithTag("journal_edit").performClick()
        awaitTag("journal_body")
        compose.onNodeWithTag("journal_body").performTextReplacement("$body edited")
        compose.onNodeWithTag("journal_save").performClick()
        awaitTag("journal_detail_body")
        compose.onNodeWithTag("journal_detail_body").assertTextEquals("$body edited")
        compose.onNodeWithTag("journal_edit").performClick()
        awaitTag("journal_delete")
        compose.onNodeWithTag("journal_delete").performClick()
        compose.onNodeWithTag("journal_confirm_delete").performClick()
        awaitTag("screen_timeline")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("$body edited").fetchSemanticsNodes().isEmpty() }
    }
    @Test fun navigatingBackRequiresExplicitDiscardForChangedText() {
        compose.onNodeWithTag("quick_capture").performClick()
        compose.onNodeWithTag("capture_journal").performClick()
        awaitTag("journal_body")
        compose.onNodeWithTag("journal_body").performTextInput("Unsaved")
        compose.onNodeWithTag("journal_body").performTextReplacement("x".repeat(50_001))
        compose.onNodeWithTag("journal_body").assertTextContains("Unsaved")
        compose.onNodeWithTag("journal_body").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
        compose.activityRule.scenario.recreate()
        awaitTag("journal_body")
        compose.onNodeWithTag("journal_body").assertTextContains("Unsaved")
        compose.onNodeWithTag("journal_cancel").performClick()
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithTag("journal_body").assertTextContains("Unsaved")
        compose.onNodeWithTag("journal_cancel").performClick()
        compose.onNodeWithText("Discard").performClick()
        awaitTag("screen_today")
    }
    private fun awaitTag(tag: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }
}
