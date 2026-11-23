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
            awaitTag("event_detail_content")
            compose.onNodeWithTag("event_detail_content").performTouchInput {
                repeat(12) { swipeUp() }
            }
            compose.onNodeWithTag("event_detail_body").assertIsDisplayed()
            compose.onNodeWithTag("event_detail_back").assertIsDisplayed()
            compose.onNodeWithTag("event_edit").assertIsDisplayed()
        } finally {
            runBlocking { storage.lifeEvents.delete(id) }
        }
    }

    @Test fun directMemoryCaptureRestoresDraftOpensDetailAndCanBeEditedAndDeleted() {
        val note = "Memory test ${System.nanoTime()}"
        compose.onNodeWithTag("today_capture_memory").performClick()
        awaitTag("memory_note")
        compose.onNodeWithTag("memory_note").performTextInput(note)
        compose.activityRule.scenario.recreate()
        awaitTag("memory_note")
        compose.onNodeWithTag("memory_note").assertTextContains(note)
        compose.onNodeWithTag("memory_save").performClick()
        awaitTag("screen_today")
        compose.onNodeWithTag("nav_timeline").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("timeline_loading").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("screen_timeline").performScrollToNode(hasText(note))
        compose.onNodeWithText(note).performClick()
        awaitTag("event_detail_body")
        compose.onNodeWithTag("event_detail_body").assertTextEquals(note)
        compose.onNodeWithTag("event_edit").performClick()
        awaitTag("memory_note")
        compose.onNodeWithTag("memory_note").performTextReplacement("$note edited")
        compose.onNodeWithTag("memory_save").performClick()
        awaitTag("event_detail_body")
        compose.onNodeWithTag("event_detail_body").assertTextEquals("$note edited")
        compose.onNodeWithTag("event_edit").performClick()
        awaitTag("memory_remove")
        compose.onNodeWithTag("memory_remove").performClick()
        compose.onNodeWithTag("memory_confirm_remove").performClick()
        awaitTag("screen_timeline")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("$note edited").fetchSemanticsNodes().isEmpty() }
    }
    @Test fun navigatingBackRequiresExplicitDiscardForChangedMemory() {
        compose.onNodeWithTag("today_capture_memory").performClick()
        awaitTag("memory_note")
        compose.onNodeWithTag("memory_note").performTextInput("Unsaved")
        compose.activityRule.scenario.recreate()
        awaitTag("memory_note")
        compose.onNodeWithTag("memory_note").assertTextContains("Unsaved")
        compose.onNodeWithTag("memory_cancel").performClick()
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithTag("memory_note").assertTextContains("Unsaved")
        compose.onNodeWithTag("memory_cancel").performClick()
        compose.onNodeWithText("Discard").performClick()
        awaitTag("screen_today")
    }
    private fun awaitTag(tag: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }
}
