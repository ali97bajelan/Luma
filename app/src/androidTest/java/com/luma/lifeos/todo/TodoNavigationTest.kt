package com.luma.lifeos.todo

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.luma.lifeos.MainActivity
import com.luma.lifeos.R
import com.luma.lifeos.language.AppLanguage
import com.luma.lifeos.language.AppLanguages
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TodoNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun useEnglish() = selectLanguage(AppLanguage.ENGLISH)

    @After fun restoreEnglish() = selectLanguage(AppLanguage.ENGLISH)

    @Test fun historicalQuickCaptureTaskStaysUndatedAndReturnsToItsTimelineOrigin() {
        compose.onNodeWithTag("nav_today").performClick()
        awaitTag("screen_today")
        compose.onNodeWithTag("day_today").performClick()
        compose.onNodeWithTag("day_previous").performClick()
        val expectedTimelineDate = selectedDateText()

        compose.onNodeWithTag("nav_timeline").performClick()
        awaitTag("screen_timeline")
        compose.onNodeWithTag("quick_capture").performClick()
        compose.onNodeWithTag("capture_mood").assertIsDisplayed()
        compose.onNodeWithTag("capture_journal").assertIsDisplayed()
        compose.onNodeWithTag("capture_memory").assertIsDisplayed()
        compose.onNodeWithTag("capture_close").assertIsDisplayed()
        compose.onNodeWithTag("capture_on_browsed_date").performClick()
        compose.onNodeWithTag("capture_task").performClick()
        awaitTag("screen_todo_editor")
        compose.onNodeWithTag("todo_editor_due_date_value")
            .assertTextContains(compose.activity.getString(R.string.todo_no_date))

        val title = "Capture task ${System.nanoTime()}"
        compose.onNodeWithTag("todo_editor_title").performTextInput(title)
        compose.onNodeWithTag("todo_editor_save").performClick()
        awaitTag("screen_timeline")

        assertEquals(expectedTimelineDate, selectedDateText())
        compose.onNodeWithTag("nav_timeline").assertIsSelected()
        listOf("nav_today", "nav_timeline", "nav_habits", "nav_review", "nav_more").forEach {
            compose.onNodeWithTag(it).assertExists()
        }

        compose.onNodeWithTag("nav_more").performClick()
        compose.onNodeWithTag("more_tasks").performClick()
        compose.onNodeWithText(title).performClick()
        awaitTag("screen_todo_editor")
        compose.onNodeWithTag("todo_editor_status").performClick()
        awaitTag("screen_todo_list")
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText(compose.activity.getString(R.string.todo_undo)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(compose.activity.getString(R.string.todo_undo)).performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText(compose.activity.getString(R.string.todo_undo)).fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText(title).performClick()
        awaitTag("screen_todo_editor")
        compose.onNodeWithTag("todo_editor_delete").performClick()
        compose.onNodeWithTag("todo_editor_confirm_delete").performClick()
        awaitTag("screen_todo_list")
        compose.onNodeWithTag("todo_list_back").performClick()
        awaitTag("nav_timeline")
        compose.onNodeWithTag("nav_timeline").performClick()
        awaitTag("screen_timeline")
        assertEquals(expectedTimelineDate, selectedDateText())
    }

    private fun selectedDateText(): String = compose.onNodeWithTag("day_selected", useUnmergedTree = true)
        .fetchSemanticsNode().config[SemanticsProperties.Text].joinToString(separator = "") { it.text }

    private fun selectLanguage(language: AppLanguage) {
        compose.activityRule.scenario.onActivity { assertTrue(AppLanguages.select(it, language)) }
        compose.waitUntil(15_000) {
            var selected = false
            compose.activityRule.scenario.onActivity { selected = AppLanguages.current(it) == language }
            selected
        }
    }

    private fun awaitTag(tag: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }
}
