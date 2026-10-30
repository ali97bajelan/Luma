package com.luma.lifeos

import android.app.Application
import android.content.SharedPreferences
import com.luma.lifeos.data.LumaStorage
import com.luma.lifeos.focus.AllowedFocusApps
import com.luma.lifeos.focus.AndroidFocusClock
import com.luma.lifeos.focus.FocusCoordinator
import com.luma.lifeos.focus.FocusPreferences
import com.luma.lifeos.focus.FocusRuntime
import com.luma.lifeos.ui.mood.FileMoodDraftStore
import com.luma.lifeos.domain.usage.UsageIntegration
import com.luma.lifeos.domain.usage.UsageSyncer
import com.luma.lifeos.integration.usage.AndroidUsageSource
import com.luma.lifeos.integration.usage.UsageWorkScheduler
import com.luma.lifeos.usage.UsageWorker
import com.luma.lifeos.ui.theme.THEME_KEY
import com.luma.lifeos.widget.WidgetRefreshCoordinator
import com.luma.lifeos.reminders.enqueueReminderReconciliation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class LumaApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val themePreferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == THEME_KEY || key == null) {
            WidgetRefreshCoordinator.request(this)
            WidgetRefreshCoordinator.refreshQuickCapture(this)
        }
    }

    // Construction is lazy; the database opens off the main thread on the first suspend/Flow query.
    val appDataReset: com.luma.lifeos.ui.privacy.AppDataReset by lazy {
        com.luma.lifeos.ui.privacy.AndroidAppDataReset(this, focusRuntime)
    }
    val storage: LumaStorage by lazy { LumaStorage(this) }
    val focusPreferences: FocusPreferences by lazy { FocusPreferences(this) }
    val allowedFocusApps: AllowedFocusApps by lazy { AllowedFocusApps(this) }
    val focusRuntime: FocusRuntime by lazy {
        FocusRuntime(
            coordinator = FocusCoordinator(storage.focus, AndroidFocusClock(this)),
            preferences = focusPreferences,
            allowedApps = allowedFocusApps,
            scope = applicationScope,
        )
    }
    val moodDraftStore: FileMoodDraftStore by lazy { FileMoodDraftStore(this) }
    val usage: UsageIntegration by lazy {
        UsageSyncer(AndroidUsageSource(this), storage.usage, UsageWorkScheduler(this, UsageWorker::class.java))
    }
    val health: com.luma.lifeos.domain.health.HealthIntegration by lazy {
        com.luma.lifeos.domain.health.HealthSyncer(com.luma.lifeos.integration.health.AndroidHealthSource(this), storage.health)
    }

    override fun onCreate() {
        super.onCreate()
        focusRuntime.initialize()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: android.app.Activity) = focusRuntime.onActivityResumed(activity)
            override fun onActivityPaused(activity: android.app.Activity) = focusRuntime.onActivityPaused(activity)
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) = Unit
            override fun onActivityStarted(activity: android.app.Activity) = Unit
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) = Unit
            override fun onActivityStopped(activity: android.app.Activity) = Unit
            override fun onActivityDestroyed(activity: android.app.Activity) = Unit
        })
        getSharedPreferences("app_theme", MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(themePreferenceListener)
        applicationScope.launch {
            storage.widgets.observeInvalidations().collect {
                WidgetRefreshCoordinator.request(this@LumaApplication)
                enqueueReminderReconciliation(this@LumaApplication)
            }
        }
        enqueueReminderReconciliation(this)
    }
}
