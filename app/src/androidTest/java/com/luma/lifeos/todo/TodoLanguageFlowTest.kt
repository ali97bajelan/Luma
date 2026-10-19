package com.luma.lifeos.todo

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.luma.lifeos.MainActivity
import com.luma.lifeos.R
import com.luma.lifeos.language.AppLanguage
import com.luma.lifeos.language.AppLanguages
import com.luma.lifeos.language.localizedDate
import java.time.LocalDate
import java.time.format.FormatStyle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TodoLanguageFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun useEnglish() = selectLanguage(AppLanguage.ENGLISH)

    @After fun restoreEnglish() = selectLanguage(AppLanguage.ENGLISH)

    @Test fun dirtyDraftAndPoliteValidationSurviveEnglishPersianFrenchRecreation() {
        val title = "T07 draft ${System.nanoTime()}"
        val note = "Persian فارسی · Français · English"
        val dueDate = LocalDate.now()

        compose.onNodeWithTag("nav_more").performClick()
        compose.onNodeWithTag("more_tasks").performClick()
        compose.onNodeWithTag("todo_list_add").performClick()
        compose.onNodeWithTag("todo_editor_title").performTextInput(title)
        compose.onNodeWithTag("todo_editor_note").performTextInput(note)
        compose.onNodeWithTag("todo_editor_change_due_date").performClick()
        compose.onNodeWithTag("todo_editor_select_date").performClick()

        for (language in listOf(AppLanguage.PERSIAN, AppLanguage.FRENCH, AppLanguage.ENGLISH)) {
            selectLanguage(language)
            awaitEditor()

            compose.onNodeWithTag("todo_editor_title").assertTextContains(title)
            compose.onNodeWithTag("todo_editor_note").assertTextContains(note)
            val localizedDueDate = dueDate.format(localizedDate(FormatStyle.LONG))
            compose.onNodeWithTag("todo_editor_due_date_value").assertTextContains(localizedDueDate, substring = true)
            compose.onNodeWithTag("todo_editor_save").assertTextContains(activityString(R.string.save))

            compose.onNodeWithTag("todo_editor_title").performTextClearance()
            compose.onNodeWithTag("todo_editor_save").performClick()
            val required = activityString(R.string.todo_title_required)
            compose.onNode(
                hasText(required) and SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite,
                ),
                useUnmergedTree = true,
            ).assertExists()
            compose.onNodeWithTag("todo_editor_title").assert(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.Error),
            )
            compose.onNodeWithTag("todo_editor_title").performTextInput(title)
        }

        compose.activityRule.scenario.onActivity { assertEquals(AppLanguage.ENGLISH, AppLanguages.current(it)) }
        compose.onNodeWithTag("todo_editor_title").assertTextContains(title)
    }

    private fun selectLanguage(language: AppLanguage) {
        var previousActivityIdentity = 0
        var previousResourceLanguage = ""
        compose.activityRule.scenario.onActivity { activity ->
            previousActivityIdentity = System.identityHashCode(activity)
            previousResourceLanguage = activity.resources.configuration.locales[0].language
            assertTrue(AppLanguages.select(activity, language))
        }
        compose.waitUntil(15_000) {
            var ready = false
            compose.activityRule.scenario.onActivity { activity ->
                val resourceLanguage = activity.resources.configuration.locales[0].language
                val expectedRecreation = previousResourceLanguage == language.tag ||
                    System.identityHashCode(activity) != previousActivityIdentity
                ready = AppLanguages.current(activity) == language &&
                    resourceLanguage == language.tag && expectedRecreation
            }
            ready
        }
        compose.waitForIdle()
    }

    private fun awaitEditor() {
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag("screen_todo_editor").fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
    }

    private fun activityString(resource: Int): String {
        var value = ""
        compose.activityRule.scenario.onActivity { value = it.getString(resource) }
        return value
    }
}
