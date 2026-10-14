package com.luma.lifeos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.luma.lifeos.ui.journal.JournalDraft
import com.luma.lifeos.ui.journal.JournalEditorScreen
import com.luma.lifeos.ui.journal.JournalEditorState
import com.luma.lifeos.ui.theme.LifeOsTheme
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class JournalEditorScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun rejectedPendingTagKeepsEditorOpenRetainsInputAndShowsFieldError() {
        val saves = AtomicInteger()
        compose.setContent {
            LifeOsTheme {
                JournalEditorScreen(
                    state = JournalEditorState(JournalDraft(1_791_720_000_000L, body = "Entry"), ready = true),
                    onCancel = {}, onSave = { saves.incrementAndGet() }, onDelete = {}, onRetry = {},
                    onTitle = {}, onBody = {}, onAddTag = { false }, onRemoveTag = {},
                    onTime = { _: LocalDateTime, _: ZoneId -> true },
                )
            }
        }
        compose.onNodeWithTag("journal_tag_input").performTextInput("Unaccepted")
        compose.onNodeWithTag("journal_title").performScrollTo()
        compose.onNodeWithTag("journal_save").performClick()

        assertEquals(0, saves.get())
        compose.onNodeWithTag("screen_journal").assertExists()
        compose.onNodeWithTag("journal_tag_input").assertTextContains("Unaccepted").assertIsDisplayed()
        compose.onNodeWithText("Use up to 50 characters and no more than 50 tags. Your tag text is retained.").assertExists()
    }
}
