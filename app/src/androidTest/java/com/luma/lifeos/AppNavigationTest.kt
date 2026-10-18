package com.luma.lifeos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun launchesOnTodayAndNavigatesToEveryPrimaryRoute() {
        compose.onNodeWithTag("screen_today").assertIsDisplayed()
        listOf("timeline", "habits", "review", "more", "today").forEach { route ->
            compose.onNodeWithTag("nav_$route").performClick()
            compose.onNodeWithTag("screen_$route").assertIsDisplayed()
        }
        compose.onNodeWithTag("nav_people").assertDoesNotExist()
        compose.onNodeWithTag("nav_settings").assertDoesNotExist()
    }

    @Test fun moreOpensPeoplePlacesAndSettingsWithBackToMore() {
        compose.onNodeWithTag("nav_more").performClick()
        compose.onNodeWithTag("screen_more").assertIsDisplayed()
        compose.onNodeWithTag("more_people_places").performClick()
        compose.onNodeWithTag("screen_people").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("screen_people").assertIsDisplayed()
        compose.onNodeWithTag("catalog_people").assertIsSelected()
        compose.onNodeWithTag("people_back").assertIsDisplayed().performClick()
        compose.onNodeWithTag("screen_more").assertIsDisplayed()

        compose.onNodeWithTag("more_settings").performClick()
        compose.onNodeWithTag("screen_settings").assertIsDisplayed()
        compose.onNodeWithTag("settings_hub_back").assertIsDisplayed().performClick()
        compose.onNodeWithTag("screen_more").assertIsDisplayed()
    }

    @Test fun todayHabitPreviewOpensThePrimaryHabitsDestination() {
        compose.onNodeWithTag("today_habits_card").assertIsDisplayed()
        compose.onNodeWithTag("habits_view_all").performClick()
        compose.onNodeWithTag("screen_habits").assertIsDisplayed()
    }

    @Test fun recreationRestoresSelectedRoute() {
        compose.onNodeWithTag("nav_more").performClick()
        compose.onNodeWithTag("more_settings").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("screen_settings").assertIsDisplayed()
        compose.onNodeWithTag("settings_hub_back").performClick()
        compose.onNodeWithTag("screen_more").assertIsDisplayed()
    }

    @Test fun quickCaptureIsReachableFromEveryPrimaryRoute() {
        listOf("today", "timeline", "habits", "review", "more").forEach { route ->
            compose.onNodeWithTag("nav_$route").performClick()
            compose.onNodeWithTag("quick_capture").assertIsDisplayed().performClick()
            compose.onNodeWithTag("capture_sheet").assertIsDisplayed()
            compose.onNodeWithTag("capture_close").performClick()
        }
    }

    @Test fun selectedDaySurvivesNavigationAndRecreation() {
        val yesterday = LocalDate.now().minusDays(1)
            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        compose.onNodeWithTag("day_previous").performClick()
        compose.onNodeWithTag("nav_habits").performClick()
        compose.onNodeWithTag("day_selected", useUnmergedTree = true).assertTextEquals(yesterday)
        compose.onNodeWithTag("nav_more").performClick()
        compose.onNodeWithTag("more_settings").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("settings_hub_back").performClick()
        compose.onNodeWithTag("nav_more").assertIsSelected()
        compose.onNodeWithTag("nav_today").performClick()
        compose.onNodeWithTag("day_selected", useUnmergedTree = true).assertTextEquals(yesterday)
        compose.onNodeWithTag("day_today").performClick()
        compose.onNodeWithTag("day_selected", useUnmergedTree = true).assertTextEquals(
            LocalDate.now().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)))
    }
}
