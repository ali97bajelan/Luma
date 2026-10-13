package com.luma.lifeos

import android.app.Application
import com.luma.lifeos.data.LumaStorage
import com.luma.lifeos.domain.usage.UsageIntegration
import com.luma.lifeos.domain.usage.UsageSyncer
import com.luma.lifeos.integration.usage.AndroidUsageSource
import com.luma.lifeos.integration.usage.UsageWorkScheduler
import com.luma.lifeos.usage.UsageWorker

class LumaApplication : Application() {
    // Construction is lazy; the database opens off the main thread on the first suspend/Flow query.
    val appDataReset: com.luma.lifeos.ui.privacy.AppDataReset by lazy { com.luma.lifeos.ui.privacy.AndroidAppDataReset(this) }
    val storage: LumaStorage by lazy { LumaStorage(this) }
    val usage: UsageIntegration by lazy {
        UsageSyncer(AndroidUsageSource(this), storage.usage, UsageWorkScheduler(this, UsageWorker::class.java))
    }
    val health: com.luma.lifeos.domain.health.HealthIntegration by lazy {
        com.luma.lifeos.domain.health.HealthSyncer(com.luma.lifeos.integration.health.AndroidHealthSource(this), storage.health)
    }
}
