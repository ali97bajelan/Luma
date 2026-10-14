package com.luma.lifeos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.JournalInput
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class JournalCaptureTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun journalBackAndEditStayVisibleAtEndOfLongEntry() {
        val id = EventId.new()
        val body = "A long remembered entry. ".repeat(390)
        val time = Instant.now()
        val storage = (compose.activity.application as LumaApplication).storage
        runBlocking { storage.journals.create(id, JournalInput(body, time, "Long entry")) }
        try {
            compose.onNodeWithTag("nav_timeline").performClick()
            awaitTag("event_$id")
            compose.onNodeWithTag("event_$id").performClick()
            awaitTag("journal_detail_content")
            compose.onNodeWithTag("journal_detail_content").performTouchInput {
                repeat(12) { swipeUp() }
            }
            compose.onNodeWithTag("journal_detail_body").assertIsDisplayed()
            compose.onNodeWithTag("journal_detail_back").assertIsDisplayed()
            compose.onNodeWithTag("journal_edit").assertIsDisplayed()
        } finally {
            runBlocking { storage.lifeEvents.delete(id) }
        }
    }

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
