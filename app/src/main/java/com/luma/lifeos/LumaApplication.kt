package com.luma.lifeos

import android.app.Application
import android.content.SharedPreferences
import com.luma.lifeos.data.LumaStorage
import com.luma.lifeos.ui.mood.FileMoodDraftStore
import com.luma.lifeos.domain.usage.UsageIntegration
import com.luma.lifeos.domain.usage.UsageSyncer
import com.luma.lifeos.integration.usage.AndroidUsageSource
import com.luma.lifeos.integration.usage.UsageWorkScheduler
import com.luma.lifeos.usage.UsageWorker
import com.luma.lifeos.ui.theme.THEME_KEY
import com.luma.lifeos.widget.WidgetRefreshCoordinator
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
    val appDataReset: com.luma.lifeos.ui.privacy.AppDataReset by lazy { com.luma.lifeos.ui.privacy.AndroidAppDataReset(this) }
    val storage: LumaStorage by lazy { LumaStorage(this) }
    val moodDraftStore: FileMoodDraftStore by lazy { FileMoodDraftStore(this) }
    val usage: UsageIntegration by lazy {
        UsageSyncer(AndroidUsageSource(this), storage.usage, UsageWorkScheduler(this, UsageWorker::class.java))
    }
    val health: com.luma.lifeos.domain.health.HealthIntegration by lazy {
        com.luma.lifeos.domain.health.HealthSyncer(com.luma.lifeos.integration.health.AndroidHealthSource(this), storage.health)
    }

    override fun onCreate() {
        super.onCreate()
        getSharedPreferences("app_theme", MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(themePreferenceListener)
        applicationScope.launch {
            storage.widgets.observeInvalidations().collect {
                WidgetRefreshCoordinator.request(this@LumaApplication)
            }
        }
    }
}
