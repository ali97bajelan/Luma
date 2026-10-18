package com.luma.lifeos.settings

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.ui.settings.SettingsRoute
import com.luma.lifeos.ui.theme.LifeOsTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ThemeSettingsTest {
    @get:Rule val compose = createComposeRule()
    private val preferences get() = InstrumentationRegistry.getInstrumentation().targetContext
        .getSharedPreferences("app_theme", Context.MODE_PRIVATE)

    @Before fun clearPreference() { preferences.edit().clear().commit() }
    @After fun cleanup() { preferences.edit().clear().commit() }

    // Catches a missing settings control, a preference that fails to update the root theme,
    // and a choice held only in transient UI state.
    @Test fun themeChangesImmediatelyAndSurvivesRestoration() {
        val restoration = StateRestorationTester(compose)
        var systemDark by mutableStateOf(false)
        restoration.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (systemDark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                LifeOsTheme {
                    SettingsRoute(false, false, false, {}, {}, {}, {})
                    Text(if (MaterialTheme.colorScheme.background.luminance() < 0.5f) "dark" else "light",
                        modifier = Modifier.testTag("theme_probe"))
                }
            }
        }
        compose.onNodeWithTag("theme_system").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("theme_probe").assertTextEquals("light")
        compose.onNodeWithTag("theme_dark").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("theme_probe").assertTextEquals("dark")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("theme_dark").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("theme_probe").assertTextEquals("dark")
        compose.runOnIdle { systemDark = true }
        compose.onNodeWithTag("theme_light").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("theme_probe").assertTextEquals("light")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("theme_light").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("theme_probe").assertTextEquals("light")
        compose.onNodeWithTag("theme_system").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("theme_probe").assertTextEquals("dark")
        compose.runOnIdle { systemDark = false }
        compose.onNodeWithTag("theme_probe").assertTextEquals("light")
    }
}
