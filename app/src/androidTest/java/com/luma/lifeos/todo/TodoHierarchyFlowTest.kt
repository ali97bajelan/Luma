package com.luma.lifeos.todo

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.luma.lifeos.MainActivity
import com.luma.lifeos.R
import com.luma.lifeos.language.AppLanguage
import com.luma.lifeos.language.AppLanguages
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TodoHierarchyFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun useEnglish() = selectEnglish()

    @After
    fun restoreEnglish() = selectEnglish()

    @Test
    fun savedParentCreatesIndependentChildAndReturnsThroughParentContext() {
        val parentTitle = "TI12 parent ${System.nanoTime()}"
        val childTitle = "TI12 child ${System.nanoTime()}"

        compose.onNodeWithTag("nav_more").performClick()
        compose.onNodeWithTag("more_tasks").performClick()
        awaitTag("screen_todo_list")
        compose.onNodeWithTag("todo_list_add").performClick()
        awaitTag("screen_todo_editor")
        compose.onNodeWithTag("todo_editor_title").performTextInput(parentTitle)
        compose.onNodeWithTag("todo_editor_save").performClick()
        awaitTag("screen_todo_list")

        compose.onNodeWithText(parentTitle).performClick()
        awaitTag("screen_todo_editor")
        compose.onNodeWithTag("todo_editor_add_subtask").performScrollTo().performClick()
        awaitTag("todo_editor_open_parent")
        compose.onNodeWithTag("todo_editor_parent_context").assertTextContains(parentTitle)
        compose.onNodeWithTag("todo_editor_due_date_value")
            .assertTextContains(compose.activity.getString(R.string.todo_no_date))

        compose.onNodeWithTag("todo_editor_title").performTextInput(childTitle)
        compose.onNodeWithTag("todo_importance_high").performScrollTo().performClick()
        compose.onNodeWithTag("todo_importance_high").assertIsSelected()
        compose.onNodeWithTag("todo_editor_due_date_value")
            .assertTextContains(compose.activity.getString(R.string.todo_no_date))
        compose.onNodeWithTag("todo_editor_save").performScrollTo().performClick()

        val progressLabel = compose.activity.getString(R.string.todo_subtask_progress, 0, 1)
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag("todo_editor_subtask_progress").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("todo_editor_subtask_progress").assertTextContains(progressLabel)
        compose.onNodeWithText(compose.activity.getString(R.string.todo_open_subtask))
            .performScrollTo()
            .performClick()

        awaitTag("todo_editor_open_parent")
        compose.onNodeWithTag("todo_editor_title").assertTextContains(childTitle)
        compose.onNodeWithTag("todo_editor_parent_context").assertTextContains(parentTitle)
        compose.onNodeWithTag("todo_importance_high").assertIsSelected()
        compose.onNodeWithTag("todo_editor_due_date_value")
            .assertTextContains(compose.activity.getString(R.string.todo_no_date))

        compose.onNodeWithTag("todo_editor_open_parent").performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag("todo_editor_subtask_progress").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("todo_editor_title").assertTextContains(parentTitle)
        compose.onNodeWithText(childTitle).assertExists()

        compose.onNodeWithTag("todo_editor_status").performScrollTo().performClick()
        awaitTag("todo_family_action_confirmation")
        compose.onNodeWithText(
            compose.activity.getString(R.string.todo_family_complete_body, 1),
        ).assertExists()
        compose.onNodeWithTag("todo_family_action_cancel").performClick()

        compose.onNodeWithTag("todo_editor_status").performScrollTo().performClick()
        awaitTag("todo_family_action_confirmation")
        compose.onNodeWithTag("todo_family_action_confirm").performClick()
        awaitTag("screen_todo_list")
        compose.onNodeWithTag("todo_list_completed_tab").performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText(parentTitle).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(parentTitle).performClick()
        awaitTag("screen_todo_editor")
        compose.waitUntil(15_000) {
            runCatching {
                compose.onNodeWithTag("todo_editor_subtask_progress")
                    .assertTextContains(compose.activity.getString(R.string.todo_subtask_progress, 1, 1))
            }.isSuccess
        }

        compose.onNodeWithTag("todo_editor_delete").performScrollTo().performClick()
        awaitTag("todo_family_action_confirmation")
        compose.onNodeWithText(
            compose.activity.getString(R.string.todo_family_delete_body, parentTitle, 1),
        ).assertExists()
        compose.onNodeWithTag("todo_family_action_cancel").performClick()
        compose.onNodeWithTag("todo_editor_title").assertTextContains(parentTitle)
    }

    private fun selectEnglish() {
        compose.activityRule.scenario.onActivity { assertTrue(AppLanguages.select(it, AppLanguage.ENGLISH)) }
        compose.waitUntil(15_000) {
            var selected = false
            compose.activityRule.scenario.onActivity { selected = AppLanguages.current(it) == AppLanguage.ENGLISH }
            selected
        }
    }

    private fun awaitTag(tag: String) {
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
    }
}
