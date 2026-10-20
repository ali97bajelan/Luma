package com.luma.lifeos.widget

import android.content.Intent
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetIntentsTest {
    @Test fun emptyHabitActionHasAnAllowlistedNewHabitDestination() {
        assertEquals(WidgetDestination.NewHabit, decodeNavigation(widgetId = 61, destination = "new-habit"))
    }

    @Test fun eitherDataWidgetUsesTheSameDateFreeCaptureDestination() {
        assertEquals(WidgetDestination.QuickCapture, decodeNavigation(widgetId = 61, destination = "quick-capture"))
        assertEquals(WidgetDestination.QuickCapture, decodeNavigation(widgetId = 62, destination = "quick-capture"))
    }

    private fun decodeNavigation(widgetId: Int, destination: String): WidgetDestination? {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, MainActivity::class.java).apply {
            action = WidgetIntents.ACTION_NAVIGATION
            data = Uri.parse("luma-widget://navigate/navigation/$widgetId/$destination")
        }
        return WidgetIntents.decode(intent)
    }
}
