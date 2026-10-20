package com.luma.lifeos.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.luma.lifeos.LumaApplication
import com.luma.lifeos.domain.widget.WidgetOutcome
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Executes only the persisted command addressed by the widget ID and opaque token. */
class WidgetActionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val widgetId = inputData.getInt(KEY_WIDGET_ID, 0).takeIf { it > 0 } ?: return Result.failure()
        val tokenString = inputData.getString(KEY_TOKEN) ?: return Result.failure()
        val token = try {
            UUID.fromString(tokenString).takeIf { it.toString() == tokenString } ?: return Result.failure()
        } catch (_: IllegalArgumentException) {
            return Result.failure()
        }
        val app = applicationContext as? LumaApplication ?: return Result.failure()

        val result = try {
            withContext(Dispatchers.IO) {
                app.storage.widgets.execute(widgetId, token, Instant.now(), ZoneId.systemDefault())
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Keep and retry the original identity; execute is transactionally replay-safe.
            WidgetRefreshCoordinator.request(applicationContext)
            return Result.retry()
        }

        WidgetRefreshCoordinator.request(applicationContext)
        return if (result.outcome == WidgetOutcome.FAILED && result.retryToken == token) {
            Result.retry()
        } else {
            Result.success()
        }
    }

    companion object {
        const val KEY_WIDGET_ID = "widget_id"
        const val KEY_TOKEN = "token"
    }
}
