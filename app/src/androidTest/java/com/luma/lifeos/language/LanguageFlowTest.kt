package com.luma.lifeos.language

import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.luma.lifeos.MainActivity
import com.luma.lifeos.R
import java.time.LocalDate
import java.time.format.FormatStyle
import java.text.NumberFormat
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test

/** Run on a disposable installation: these cases save a journal and change its app language. */
class LanguageFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun english() = select(AppLanguage.ENGLISH)
    @After fun restoreEnglish() = select(AppLanguage.ENGLISH)

    @Test fun settingsSwitchesAllLanguagesAndPreservesSelectedDateThroughRecreation() {
        val selected = LocalDate.now().minusDays(1)
        compose.onNodeWithTag("day_previous").performClick()
        openLanguageSettings()
        for ((language, more) in listOf(AppLanguage.PERSIAN to "بیشتر", AppLanguage.FRENCH to "Plus", AppLanguage.ENGLISH to "More")) {
            compose.onNodeWithTag("language_${language.tag}").performScrollTo().performClick()
            awaitLanguage(language)
            compose.onNodeWithTag("screen_settings_language").assertIsDisplayed()
            compose.onNodeWithTag("nav_more").assertDoesNotExist()
            compose.activityRule.scenario.onActivity { activity ->
                assertEquals(if (language == AppLanguage.PERSIAN) Configuration.SCREENLAYOUT_LAYOUTDIR_RTL else Configuration.SCREENLAYOUT_LAYOUTDIR_LTR,
                    activity.resources.configuration.screenLayout and Configuration.SCREENLAYOUT_LAYOUTDIR_MASK)
                assertEquals(language, AppLanguages.current(activity))
            }
            compose.activityRule.scenario.recreate()
            awaitLanguage(language)
            compose.onNodeWithTag("screen_settings_language").assertIsDisplayed()
            compose.onNodeWithTag("settings_back").performClick()
            compose.onNodeWithTag("screen_settings").assertIsDisplayed()
            compose.onNodeWithTag("settings_hub_back").performClick()
            compose.onNodeWithTag("nav_more").assertContentDescriptionEquals(more)
            compose.onNodeWithTag("screen_more").assertIsDisplayed()
            compose.onNodeWithTag("nav_today").performClick()
            compose.onNodeWithTag("day_selected", useUnmergedTree = true)
                .assertTextEquals(selected.format(localizedDate(FormatStyle.MEDIUM)))
            for (route in listOf("timeline", "habits", "review", "more")) {
                compose.onNodeWithTag("nav_$route").performClick()
                compose.onNodeWithTag("screen_$route").assertIsDisplayed()
                compose.onNodeWithTag("quick_capture").performClick()
                compose.onNodeWithTag("capture_sheet").assertIsDisplayed()
                compose.onNodeWithTag("capture_close").performClick()
            }
            if (language != AppLanguage.ENGLISH) openLanguageSettings()
        }
    }

    @Test fun journalDraftTextTagsAndDateSurviveLanguageChangeThenSaveAndDetail() {
        val body = "یادداشت / Mon texte / My words ${System.nanoTime()}"
        compose.onNodeWithTag("quick_capture").performClick()
        compose.onNodeWithTag("capture_journal").performClick()
        awaitTag("journal_body")
        compose.onNodeWithTag("journal_title").performTextInput("عنوان / Titre")
        compose.onNodeWithTag("journal_body").performTextInput(body)
        compose.onNodeWithTag("journal_tag_input").performScrollTo().performTextInput("من / Moi")
        compose.onNodeWithTag("journal_add_tag").performScrollTo().performClick()
        val date = LocalDate.now()
        for (language in listOf(AppLanguage.PERSIAN, AppLanguage.FRENCH, AppLanguage.ENGLISH)) {
            select(language)
            awaitTag("journal_body")
            compose.onNodeWithTag("journal_body").assertTextContains(body)
            compose.onNodeWithTag("journal_title").assertTextContains("عنوان / Titre")
            compose.onNodeWithTag("journal_save").assertTextContains(text(R.string.save))
            compose.onNodeWithTag("mood_date").performScrollTo()
                .assertTextContains(date.format(localizedDate(FormatStyle.MEDIUM)))
            compose.onNodeWithTag("journal_cancel").performClick()
            compose.onNodeWithText(text(R.string.keep_editing)).assertIsDisplayed().performClick()
        }
        compose.onNodeWithTag("journal_save").performClick()
        awaitTag("screen_today")
        select(AppLanguage.PERSIAN)
        compose.onNodeWithTag("nav_timeline").performClick()
        compose.onNodeWithTag("screen_timeline").performScrollToNode(hasText(body))
        compose.onNodeWithText(body).performClick()
        awaitTag("journal_detail_body")
        compose.onNodeWithTag("journal_detail_body").assertTextEquals(body)
        compose.onNodeWithText("من / Moi").assertExists()
        compose.onNodeWithTag("journal_edit").performClick()
        awaitTag("journal_body")
        compose.onNodeWithTag("journal_body").assertTextContains(body)
    }

    @Test fun persianResourcesFormatPluralsNumbersAccessibilityAndNativePickers() {
        select(AppLanguage.PERSIAN)
        compose.activityRule.scenario.onActivity { activity ->
            assertEquals("۱ رویداد در این روز", activity.resources.getQuantityString(R.plurals.daily_events, 1, 1))
            assertEquals("۲ رویداد در این روز", activity.resources.getQuantityString(R.plurals.daily_events, 2, 2))
            assertTrue(activity.getString(R.string.mood_scale_description, "انرژی", 4).contains("۴"))
        }
        compose.onNodeWithTag("day_picker").performClick()
        compose.onNodeWithTag("date_picker_dialog").assertIsDisplayed()
        compose.onNode(hasText(text(R.string.cancel)) and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithTag("quick_capture").performClick()
        compose.onNodeWithTag("capture_mood").performClick()
        awaitTag("mood_more")
        compose.onNodeWithTag("mood_more").performClick()
        val description = text(R.string.mood_scale_description, text(R.string.mood_energy), 4)
        compose.onNodeWithContentDescription(description).assertExists()
        compose.onNodeWithTag("mood_time").performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.select_time)).assertIsDisplayed()
        compose.onNode(hasText(text(R.string.cancel)) and hasAnyAncestor(isDialog())).performClick()
    }

    @Test fun habitListLoadsPersianFrenchAndEnglishLabelsAfterLanguageChanges() {
        select(AppLanguage.PERSIAN)
        compose.onNodeWithTag("today_habits_card").assertIsDisplayed()
        compose.onNodeWithTag("habits_view_all").performClick()
        compose.onNodeWithTag("screen_habits").assertIsDisplayed()
        compose.onNodeWithTag("habit_create").performClick()
        awaitTag("screen_habit_editor")
        compose.onNodeWithTag("habit_name").performTextInput("Read")
        compose.onNodeWithTag("habit_save").performClick()
        awaitTag("screen_habits")

        val expectedTitles = listOf(
            AppLanguage.PERSIAN to "عادت‌ها",
            AppLanguage.FRENCH to "Habitudes",
            AppLanguage.ENGLISH to "Habits",
        )
        var activeLanguage = AppLanguage.PERSIAN
        for ((language, title) in expectedTitles) {
            if (language != activeLanguage) {
                select(language)
                activeLanguage = language
            }
            awaitTag("screen_habits")
            compose.onNodeWithTag("screen_habits").assertIsDisplayed()
            assertTrue(compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty())
            val numberFormat = NumberFormat.getIntegerInstance()
            val progress = text(R.string.habit_progress, numberFormat.format(0), numberFormat.format(1),
                quantityText(R.plurals.habit_unit_default, 1))
            assertTrue(compose.onAllNodesWithText(progress).fetchSemanticsNodes().isNotEmpty())
        }
    }

    private fun text(id: Int, vararg args: Any): String {
        var value = ""
        compose.activityRule.scenario.onActivity { value = it.getString(id, *args) }
        return value
    }
    private fun quantityText(id: Int, quantity: Int): String {
        var value = ""
        compose.activityRule.scenario.onActivity { value = it.resources.getQuantityString(id, quantity) }
        return value
    }
    private fun select(language: AppLanguage) {
        compose.activityRule.scenario.onActivity { assertTrue(AppLanguages.select(it, language)) }
        awaitLanguage(language)
    }
    private fun openLanguageSettings() {
        compose.onNodeWithTag("nav_more").performClick()
        compose.onNodeWithTag("more_settings").performClick()
        compose.onNodeWithTag("settings_language").performClick()
    }
    private fun awaitLanguage(language: AppLanguage) {
        compose.waitUntil(15_000) {
            var ready = false
            compose.activityRule.scenario.onActivity { ready = it.resources.configuration.locales[0].language == language.tag }
            ready
        }
        compose.waitForIdle()
    }
    private fun awaitTag(tag: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }
}
