package com.luma.lifeos.widget

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.R
import com.luma.lifeos.domain.widget.WidgetKind
import com.luma.lifeos.ui.theme.LifeOsTheme
import org.junit.Rule
import org.junit.Test

class WidgetConfigurationContentTest {
    @get:Rule val compose = createComposeRule()

    @Test fun saveAndCancelRemainVisibleAndPreviewFollowsUnsavedChoice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent {
            var details by remember { mutableStateOf(false) }
            LifeOsTheme {
                ConfigurationContent(WidgetKind.HABITS, details, true, false, false, false,
                    { details = it }, {}, {}, {})
            }
        }
        compose.onNodeWithText(context.getString(R.string.widget_config_save)).assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText(context.getString(R.string.widget_config_cancel)).assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText(context.getString(R.string.widget_mode_summary)).performScrollTo().assertIsDisplayed()
        captureEvidence()
        // Privacy remains default-off until the switch is explicitly changed.
        compose.onNodeWithText(context.getString(R.string.widget_preview_habit)).assertDoesNotExist()
        compose.onNode(androidx.compose.ui.test.isToggleable()).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.widget_mode_details)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.widget_config_save)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.widget_config_cancel)).assertIsDisplayed()
    }

    @Test fun saveStaysDisabledWhileLoading() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent {
            LifeOsTheme {
                ConfigurationContent(WidgetKind.TASKS, false, false, false, false, false,
                    {}, {}, {}, {})
            }
        }
        compose.onNodeWithText(context.getString(R.string.widget_config_save)).assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.widget_config_cancel)).assertIsDisplayed().assertIsEnabled()
    }
    private fun captureEvidence() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val config = context.resources.configuration
        val screenshot = instrumentation.uiAutomation.takeScreenshot() ?: return
        val file = java.io.File(context.cacheDir,
            "widget-config-${config.locales[0].language}-${config.orientation}-${config.fontScale}.png")
        file.outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
    }
}
