package com.luma.lifeos.widget

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.R
import com.luma.lifeos.ui.theme.LifeOsTheme
import com.luma.lifeos.ui.widgets.WidgetSettingsRoute
import org.junit.Rule
import org.junit.Test

class WidgetSettingsRouteTest {
    @get:Rule val compose = createComposeRule()

    @Test fun discoveryPromotesDataWidgetsAndExplainsSharedCapture() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent {
            LifeOsTheme {
                WidgetSettingsRoute(widgetStore = null, onBack = {})
            }
        }

        compose.onNodeWithText(context.getString(R.string.widget_settings_intro)).assertExists()
        compose.onNodeWithText(context.getString(R.string.widget_settings_capture_on_both)).assertExists()
        compose.onNodeWithText(context.getString(R.string.widget_habits_title)).assertExists()
        compose.onNodeWithText(context.getString(R.string.widget_tasks_title)).assertExists()
        compose.onNodeWithText(context.getString(R.string.widget_quick_capture_name)).assertDoesNotExist()
    }
}
