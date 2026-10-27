package com.luma.lifeos.reminders

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.R
import com.luma.lifeos.ui.settings.ReminderSettingsRoute
import com.luma.lifeos.ui.theme.LifeOsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ReminderNotificationAccessTest {
    @get:Rule val compose = createComposeRule()

    @Test fun settingsShowsCurrentAccessWithoutRequestingItOnOpen() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val accessBeforeOpening = notificationPermissionGranted(context)

        compose.setContent {
            LifeOsTheme {
                ReminderSettingsRoute(ReminderPreferences(context), onBack = {})
            }
        }

        compose.onNodeWithTag("reminder_notification_access_request")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(
            if (accessBeforeOpening) R.string.reminder_notifications_allowed else R.string.reminder_status_blocked,
        )).assertExists()
        compose.runOnIdle { assertEquals(accessBeforeOpening, notificationPermissionGranted(context)) }
    }
}
