package com.luma.lifeos.integration.usage

import android.content.Context
import androidx.work.*
import com.luma.lifeos.domain.usage.UsageScheduler
import java.util.concurrent.TimeUnit

class UsageWorkScheduler(context: Context, private val worker: Class<out ListenableWorker>) : UsageScheduler {
    private val manager = WorkManager.getInstance(context.applicationContext)
    override fun schedule() {
        val request = PeriodicWorkRequest.Builder(worker, 6, TimeUnit.HOURS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
    override fun cancel() { manager.cancelUniqueWork(WORK_NAME) }
    companion object { const val WORK_NAME = "luma-usage-import" }
}
