package com.luma.lifeos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luma.lifeos.ui.LifeOsApp
import com.luma.lifeos.ui.theme.LifeOsTheme
import com.luma.lifeos.widget.WidgetIntents
import com.luma.lifeos.widget.WidgetLaunchViewModel
import com.luma.lifeos.widget.WidgetRefreshCoordinator
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.luma.lifeos.reminders.ReminderOpenRequest
import com.luma.lifeos.reminders.ReminderPreferences
import com.luma.lifeos.reminders.reminderOpenRequest
import com.luma.lifeos.reminders.enqueueReminderReconciliation

class MainActivity : ComponentActivity() {
    private lateinit var widgetLaunches: WidgetLaunchViewModel
    private var reminderLaunch by mutableStateOf<ReminderOpenRequest?>(null)

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.luma.lifeos.language.AppLanguages.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetLaunches = ViewModelProvider(this)[WidgetLaunchViewModel::class.java]
        if (savedInstanceState == null) enqueueWidgetLaunch(intent)
        if (savedInstanceState == null) reminderLaunch = reminderOpenRequest(intent)
        enableEdgeToEdge()
        val luma = application as LumaApplication
        val storage = luma.storage
        setContent {
            val widgetLaunch = widgetLaunches.pending.collectAsStateWithLifecycle().value
            LifeOsTheme {
                LifeOsApp(
                    storage.lifeEvents,
                    storage.moods,
                    storage.journals,
                    storage.memories,
                    storage.people,
                    storage.places,
                    storage.photos,
                    storage.timeline,
                    luma.usage,
                    storage.summaries,
                    luma.health,
                    storage.search,
                    storage.reviews,
                    storage.backup,
                    luma.appDataReset,
                    storage.habits,
                    storage.todos,
                    widgetStore = storage.widgets,
                    moodDraftStore = luma.moodDraftStore,
                    calendar = storage.calendar,
                    habitReminders = storage.habitReminders,
                    reminderPreferences = ReminderPreferences(this@MainActivity),
                    focusRuntime = luma.focusRuntime,
                    focusRepository = storage.focus,
                    focusPreferences = luma.focusPreferences,
                    allowedFocusApps = luma.allowedFocusApps,
                    widgetLaunch = widgetLaunch,
                    onWidgetLaunchConsumed = widgetLaunches::acknowledge,
                    reminderLaunch = reminderLaunch,
                    onReminderLaunchConsumed = { reminderLaunch = null },
                )
            }
        }
    }

    public override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        enqueueWidgetLaunch(intent)
        reminderLaunch = reminderOpenRequest(intent)
    }

    override fun onStart() {
        super.onStart()
        WidgetRefreshCoordinator.request(this)
        WidgetRefreshCoordinator.refreshQuickCapture(this)
        enqueueReminderReconciliation(this)
    }

    private fun enqueueWidgetLaunch(intent: android.content.Intent) {
        WidgetIntents.decode(intent)?.let(widgetLaunches::enqueue)
    }
}
