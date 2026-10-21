package com.luma.lifeos.widget

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.R
import com.luma.lifeos.domain.widget.WidgetKind
import com.luma.lifeos.ui.theme.LifeOsTheme
import org.junit.Rule
import org.junit.Test

class WidgetConfigurationContentTest {
    @get:Rule val compose = createComposeRule()

    @Test fun saveAndCancelRemainVisibleAndPreviewAlwaysShowsNamesAndActions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent {
            LifeOsTheme {
                ConfigurationContent(WidgetKind.HABITS, true, false, false, false, {}, {}, {})
            }
        }
        compose.onNodeWithText(context.getString(R.string.widget_config_save)).assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText(context.getString(R.string.widget_config_cancel)).assertIsDisplayed().assertIsEnabled()
        compose.onNode(isToggleable()).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.widget_preview_habit_second)).performScrollTo().assertIsDisplayed()
        captureEvidence()
        compose.onNodeWithText(context.getString(R.string.widget_capture)).assertExists()
        compose.onNodeWithText(context.getString(R.string.widget_preview_habit_action)).assertExists()
        compose.onNodeWithText(context.getString(R.string.widget_preview_habit)).assertExists()
        compose.onNodeWithText(context.getString(R.string.widget_preview_habit_second)).assertExists()
        compose.onNodeWithText(context.getString(R.string.widget_scroll_for_more)).assertExists()
        compose.onNodeWithText(context.getString(R.string.widget_config_scroll_resize)).assertExists()
        compose.onNodeWithText(context.getString(R.string.widget_config_save)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.widget_config_cancel)).assertIsDisplayed()
    }

    @Test fun saveStaysDisabledWhileLoading() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent {
            LifeOsTheme {
                ConfigurationContent(WidgetKind.TASKS, false, false, false, false, {}, {}, {})
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
