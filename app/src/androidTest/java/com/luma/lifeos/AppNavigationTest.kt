package com.luma.lifeos

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun launchesOnTodayAndNavigatesToEveryPrimaryRoute() {
        compose.onNodeWithTag("screen_today").assertIsDisplayed()
        listOf("timeline", "review", "settings", "today").forEach { route ->
            compose.onNodeWithTag("nav_$route").performClick()
            compose.onNodeWithTag("screen_$route").assertIsDisplayed()
        }
    }

    @Test fun recreationRestoresSelectedRoute() {
        compose.onNodeWithTag("nav_settings").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("screen_settings").assertIsDisplayed()
    }
}
