package com.luma.lifeos.ui.focus

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.luma.lifeos.focus.AllowedFocusApps
import com.luma.lifeos.ui.theme.LifeOsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FocusAllowedAppsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun searchRetainsDuplicateLabelIdentityAndAppChoiceIsExplicit() {
        val apps = listOf(
            AllowedFocusApps.App("org.example.reader.alpha", "Reader"),
            AllowedFocusApps.App("net.example.reader.beta", "reader"),
            AllowedFocusApps.App("com.example.calendar", "Calendar"),
        )
        var changed: Pair<String, Boolean>? = null

        compose.setContent {
            LifeOsTheme {
                FocusSettingsScreen(
                    apps = apps,
                    selectedPackages = emptySet(),
                    isRunning = false,
                    loading = false,
                    discoveryFailed = false,
                    writeFailed = false,
                    onRefresh = {},
                    onToggle = { app, allowed -> changed = app.packageName to allowed },
                    onBack = {},
                )
            }
        }

        compose.onNodeWithTag("focus_settings_scope").assertIsDisplayed()
        compose.onNodeWithTag("focus_settings_empty_selection").assertIsDisplayed()
        compose.onNodeWithText("Reader · org.example.reader.alpha").assertIsDisplayed()
        compose.onNodeWithText("reader · net.example.reader.beta").assertIsDisplayed()
        compose.onNodeWithTag("focus_settings_search").performTextInput("com.example.calendar")
        compose.onNodeWithTag("focus_settings_app_com.example.calendar").assertIsDisplayed()
        compose.onNodeWithTag("focus_settings_app_org.example.reader.alpha").assertDoesNotExist()
        compose.onNodeWithTag("focus_settings_app_com.example.calendar").performClick()

        compose.runOnIdle { assertEquals("com.example.calendar" to true, changed) }
    }

    @Test fun staleSelectionsCanBeRemovedAndRunningSessionDisablesEdits() {
        val stalePackage = "org.example.removed"
        var changed: Pair<String, Boolean>? = null
        compose.setContent {
            LifeOsTheme {
                FocusSettingsScreen(
                    apps = listOf(AllowedFocusApps.App("com.example.calendar", "Calendar")),
                    selectedPackages = setOf("com.example.calendar", stalePackage),
                    isRunning = true,
                    loading = false,
                    discoveryFailed = false,
                    writeFailed = false,
                    onRefresh = {},
                    onToggle = { app, allowed -> changed = app.packageName to allowed },
                    onBack = {},
                )
            }
        }

        compose.onNodeWithTag("focus_settings_running").assertIsDisplayed()
        compose.onNodeWithTag("focus_settings_app_com.example.calendar").assertIsNotEnabled()
        compose.onNodeWithTag("focus_settings_unavailable_$stalePackage").assertIsDisplayed()
        compose.onNodeWithTag("focus_settings_remove_$stalePackage").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(null, changed) }
    }
}
