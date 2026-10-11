package com.luma.lifeos.usage

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.luma.lifeos.LumaApplication
import com.luma.lifeos.domain.usage.UsageSyncOutcome
import kotlinx.coroutines.CancellationException

class UsageWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val outcome = try { (applicationContext as LumaApplication).usage.importNow() }
        catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            UsageSyncOutcome.ERROR
        }
        return if (shouldRetryUsage(outcome, runAttemptCount)) Result.retry() else Result.success()
    }
}
fun shouldRetryUsage(outcome: UsageSyncOutcome, attempt: Int): Boolean =
    outcome in setOf(UsageSyncOutcome.ERROR, UsageSyncOutcome.LOCKED) && attempt < 3
