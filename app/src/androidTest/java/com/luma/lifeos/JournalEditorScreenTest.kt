package com.luma.lifeos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
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

    @Test fun openingTagsBeforeLoadCompletesKeepsTheSectionExpanded() {
        val state = androidx.compose.runtime.mutableStateOf(
            JournalEditorState(JournalDraft(1_791_720_000_000L, body = "Entry"), ready = false),
        )
        compose.setContent {
            LifeOsTheme {
                JournalEditorScreen(
                    state = state.value,
                    onCancel = {}, onSave = {}, onDelete = {}, onRetry = {},
                    onTitle = {}, onBody = {}, onAddTag = { true }, onRemoveTag = {},
                    onTime = { _: LocalDateTime, _: ZoneId -> true },
                )
            }
        }

        compose.onNodeWithTag("journal_tags_toggle").performClick()
        compose.onNodeWithTag("journal_tag_input").assertExists()
        state.value = state.value.copy(ready = true)
        compose.waitForIdle()
        compose.onNodeWithTag("journal_tag_input").assertExists()
    }

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
        compose.onNodeWithTag("journal_tags_toggle").performClick()
        compose.onNodeWithTag("journal_tag_input").performTextInput("Unaccepted")
        compose.onNodeWithTag("journal_title").performScrollTo()
        compose.onNodeWithTag("journal_save").performClick()

        assertEquals(0, saves.get())
        compose.onNodeWithTag("screen_journal").assertExists()
        compose.onNodeWithTag("journal_tag_input").assertTextContains("Unaccepted").assertIsDisplayed()
        compose.onNodeWithText("Use up to 50 characters and no more than 50 tags. Your tag text is retained.").assertExists()
        compose.onNodeWithTag("journal_tag_input").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
    }
}
