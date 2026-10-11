package com.luma.lifeos

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertTextEquals
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
        listOf("timeline", "people", "review", "settings", "today").forEach { route ->
            compose.onNodeWithTag("nav_$route").performClick()
            compose.onNodeWithTag("screen_$route").assertIsDisplayed()
        }
    }

    @Test fun recreationRestoresSelectedRoute() {
        compose.onNodeWithTag("nav_settings").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("screen_settings").assertIsDisplayed()
    }

    @Test fun quickCaptureIsReachableFromEveryPrimaryRoute() {
        listOf("today", "timeline", "people", "review", "settings").forEach { route ->
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
        compose.onNodeWithTag("nav_settings").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("nav_today").performClick()
        compose.onNodeWithTag("day_selected").assertTextEquals(yesterday)
        compose.onNodeWithTag("day_today").performClick()
        compose.onNodeWithTag("day_selected").assertTextEquals(
            LocalDate.now().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)))
    }
}
