package com.luma.lifeos.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.luma.lifeos.LumaApplication
import com.luma.lifeos.domain.widget.WidgetActionResult
import com.luma.lifeos.domain.widget.WidgetOutcome
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Executes only the persisted command addressed by the widget ID and opaque token. */
class WidgetActionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val widgetId = inputData.getInt(KEY_WIDGET_ID, 0).takeIf { it > 0 } ?: return Result.failure()
        val app = applicationContext as? LumaApplication ?: return Result.failure()
        if (inputData.getBoolean(KEY_DISMISS_FEEDBACK, false)) {
            val expectedToken = inputData.getString(KEY_EXPECTED_UNDO_TOKEN)?.let {
                runCatching { UUID.fromString(it) }.getOrNull() ?: return Result.failure()
            }
            try {
                withContext(Dispatchers.IO) { app.storage.widgets.dismissFeedback(widgetId, expectedToken) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return Result.retry()
            }
            WidgetRefreshCoordinator.request(applicationContext)
            return Result.success()
        }
        val tokenString = inputData.getString(KEY_TOKEN) ?: return Result.failure()
        val token = try {
            UUID.fromString(tokenString).takeIf { it.toString() == tokenString } ?: return Result.failure()
        } catch (_: IllegalArgumentException) {
            return Result.failure()
        }
        val collectionItemIdRaw = inputData.getString(KEY_COLLECTION_ITEM_ID)
        val collectionDateRaw = inputData.getString(KEY_COLLECTION_DATE)
        if ((collectionItemIdRaw == null) != (collectionDateRaw == null)) return Result.failure()
        val collectionItemId = collectionItemIdRaw?.let { raw ->
            try {
                UUID.fromString(raw).takeIf { it.toString() == raw } ?: return Result.failure()
            } catch (_: IllegalArgumentException) {
                return Result.failure()
            }
        }
        val collectionDate = collectionDateRaw?.let { raw ->
            runCatching { LocalDate.parse(raw).takeIf { it.toString() == raw } }.getOrNull()
                ?: return Result.failure()
        }
        val now = Instant.now()
        val deviceZone = ZoneId.systemDefault()
        val result = try {
            withContext(Dispatchers.IO) {
                val store = app.storage.widgets
                if (collectionItemId != null && collectionDate != null &&
                    !store.isValidCollectionMutation(widgetId, collectionItemId, token, collectionDate)
                ) {
                    WidgetActionResult(WidgetOutcome.INVALID)
                } else {
                    store.execute(widgetId, token, now, deviceZone)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Keep and retry the original identity; execute is transactionally replay-safe.
            WidgetRefreshCoordinator.request(applicationContext)
            return Result.retry()
        }

        WidgetRefreshCoordinator.request(applicationContext)
        if (result.outcome == WidgetOutcome.SAVED && result.undoToken != null) {
            val dismiss = OneTimeWorkRequestBuilder<WidgetActionWorker>()
                .setInitialDelay(3, TimeUnit.SECONDS)
                .setInputData(Data.Builder()
                    .putInt(KEY_WIDGET_ID, widgetId)
                    .putBoolean(KEY_DISMISS_FEEDBACK, true)
                    .putString(KEY_EXPECTED_UNDO_TOKEN, result.undoToken.toString())
                    .build())
                .build()
            WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                "luma-widget-auto-dismiss:$widgetId:${result.undoToken}", ExistingWorkPolicy.KEEP, dismiss,
            )
        }
        return if (result.outcome == WidgetOutcome.FAILED && result.retryToken == token) {
            Result.retry()
        } else {
            Result.success()
        }
    }

    companion object {
        const val KEY_WIDGET_ID = "widget_id"
        const val KEY_TOKEN = "token"
        const val KEY_DISMISS_FEEDBACK = "dismiss_feedback"
        const val KEY_EXPECTED_UNDO_TOKEN = "expected_undo_token"
        const val KEY_COLLECTION_ITEM_ID = "collection_item_id"
        const val KEY_COLLECTION_DATE = "collection_date"
    }
}
