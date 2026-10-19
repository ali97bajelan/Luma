package com.luma.lifeos.todo

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.luma.lifeos.MainActivity
import com.luma.lifeos.R
import com.luma.lifeos.language.AppLanguage
import com.luma.lifeos.language.AppLanguages
import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import androidx.compose.ui.test.SemanticsMatcher

class TodayTasksFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun useEnglish() = selectLanguage(AppLanguage.ENGLISH)

    @After fun restoreEnglish() = selectLanguage(AppLanguage.ENGLISH)

    @Test fun contextualAddUsesTodayAndCompletionCanBeUndoneFromToday() {
        compose.onNodeWithTag("nav_today").performClick()
        awaitTag("screen_today")
        compose.onNodeWithTag("day_today").performClick()
        compose.onNodeWithTag("screen_today").performScrollToNode(hasTestTag("today_tasks_card"))
        awaitTag("today_tasks_card")
        awaitTag("today_tasks_empty")
        compose.onNodeWithTag("today_tasks_all").assertExists()
        compose.onNodeWithTag("today_tasks_add").assertExists()
        compose.onNodeWithTag("today_tasks_add").performClick()
        awaitTag("screen_todo_editor")

        val dueText = compose.onNodeWithTag("todo_editor_due_date_value")
            .fetchSemanticsNode().config[SemanticsProperties.Text].joinToString(" ") { it.text }
        assertNotEquals(compose.activity.getString(R.string.todo_no_date), dueText)
        val title = "Today task ${System.nanoTime()}"
        compose.onNodeWithTag("todo_editor_title").performTextInput(title)
        compose.onNodeWithTag("todo_editor_save").performClick()
        awaitTag("screen_today")
        compose.onNodeWithTag("screen_today").performScrollToNode(hasTestTag("today_tasks_card"))
        compose.onNodeWithText(title).assertExists()
        compose.onNodeWithTag("today_tasks_count").assertExists()

        tapTaskCheckbox(title, R.string.todo_mark_complete)
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag("screen_today").fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithText(compose.activity.getString(R.string.todo_undo)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(compose.activity.getString(R.string.todo_undo)).performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithText(title).performClick()
        awaitTag("screen_todo_editor")
        compose.onNodeWithTag("todo_editor_delete").performClick()
        compose.onNodeWithTag("todo_editor_confirm_delete").performClick()
        awaitTag("screen_today")
    }

    @Test fun taskCardIsHiddenForHistoricalDaysAndReturnsForCurrentDay() {
        compose.onNodeWithTag("nav_today").performClick()
        awaitTag("screen_today")
        compose.onNodeWithTag("day_today").performClick()
        compose.onNodeWithTag("screen_today").performScrollToNode(hasTestTag("today_tasks_card"))
        compose.onNodeWithTag("today_tasks_card").assertExists()

        compose.onNodeWithTag("day_next").performClick()
        compose.onNodeWithTag("screen_today").performScrollToNode(hasTestTag("today_recent_entries"))
        compose.onNodeWithTag("today_tasks_card").assertDoesNotExist()

        compose.onNodeWithTag("day_previous").performClick()
        compose.onNodeWithTag("screen_today").performScrollToNode(hasTestTag("today_tasks_card"))
        compose.onNodeWithTag("today_tasks_card").assertExists()

        compose.onNodeWithTag("day_previous").performClick()
        compose.onNodeWithTag("screen_today").performScrollToNode(hasTestTag("today_recent_entries"))
        compose.onNodeWithTag("today_tasks_card").assertDoesNotExist()

        compose.onNodeWithTag("day_today").performClick()
        compose.onNodeWithTag("screen_today").performScrollToNode(hasTestTag("today_tasks_card"))
        compose.onNodeWithTag("today_tasks_card").assertExists()
    }

    private fun tapTaskCheckbox(title: String, actionResource: Int) {
        val action = compose.activity.getString(actionResource)
        val matching = compose.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().single { node ->
            val description = node.config[SemanticsProperties.ContentDescription].joinToString(" ")
            description.contains(title) && description.contains(action)
        }
        compose.onNodeWithTag(matching.config[SemanticsProperties.TestTag], useUnmergedTree = true).performClick()
    }

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
