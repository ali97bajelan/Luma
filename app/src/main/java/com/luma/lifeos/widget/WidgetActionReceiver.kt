package com.luma.lifeos.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.luma.lifeos.LumaApplication
import com.luma.lifeos.domain.widget.WidgetKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** A short, non-exported receiver that validates then hands all data work to WorkManager. */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = WidgetIntents.decodeAction(intent) ?: return
        val manager = AppWidgetManager.getInstance(context)
        val info = manager.getAppWidgetInfo(action.widgetId) ?: return
        if (info.provider.packageName != context.packageName ||
            WidgetRefreshCoordinator.kindForWidgetId(context, action.widgetId) == null
        ) return

        when (action) {
            is WidgetIntents.Action.Refresh -> WidgetRefreshCoordinator.request(context)
            is WidgetIntents.Action.Mutation -> enqueueMutation(context, action.widgetId, action.token)
            is WidgetIntents.Action.DismissFeedback -> {
                val data = Data.Builder()
                    .putInt(WidgetActionWorker.KEY_WIDGET_ID, action.widgetId)
                    .putBoolean(WidgetActionWorker.KEY_DISMISS_FEEDBACK, true)
                    .build()
                val work = OneTimeWorkRequestBuilder<WidgetActionWorker>()
                    .setInputData(data)
                    .build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    "luma-widget-dismiss-feedback:${action.widgetId}",
                    ExistingWorkPolicy.KEEP,
                    work,
                )
            }
            is WidgetIntents.Action.Collection -> when (val route = action.route) {
                is WidgetIntents.CollectionRoute.Navigate -> navigateToCollectionItem(
                    context, action.widgetId, route.itemId, route.date,
                )
                is WidgetIntents.CollectionRoute.Mutate -> enqueueMutation(
                    context, action.widgetId, route.token, route.itemId, route.date,
                )
            }
        }
    }

    private fun enqueueMutation(
        context: Context,
        widgetId: Int,
        token: UUID,
        itemId: UUID? = null,
        date: LocalDate? = null,
    ) {
        val data = Data.Builder()
            .putInt(WidgetActionWorker.KEY_WIDGET_ID, widgetId)
            .putString(WidgetActionWorker.KEY_TOKEN, token.toString())
        if (itemId != null && date != null) {
            data.putString(WidgetActionWorker.KEY_COLLECTION_ITEM_ID, itemId.toString())
            data.putString(WidgetActionWorker.KEY_COLLECTION_DATE, date.toString())
        }
        val work = OneTimeWorkRequestBuilder<WidgetActionWorker>()
            .setInputData(data.build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "luma-widget-action:$widgetId:$token",
            ExistingWorkPolicy.KEEP,
            work,
        )
    }

    private fun navigateToCollectionItem(context: Context, widgetId: Int, itemId: UUID, date: LocalDate) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as? LumaApplication ?: return@launch
                val kind = WidgetRefreshCoordinator.kindForWidgetId(context, widgetId) ?: return@launch
                val now = Instant.now()
                val deviceZone = ZoneId.systemDefault()
                val store = app.storage.widgets
                val snapshot = store.snapshot(widgetId, now, deviceZone, rowLimit = 0) ?: return@launch
                if (snapshot.instance.kind != kind || snapshot.date != date ||
                    !store.isEligibleCollectionItem(widgetId, itemId, now, deviceZone)
                ) {
                    WidgetRefreshCoordinator.request(context)
                    return@launch
                }
                val destination = when (kind) {
                    WidgetKind.HABITS -> WidgetDestination.HabitDetail(itemId, snapshot.date)
                    WidgetKind.TASKS -> WidgetDestination.Task(itemId)
                }
                WidgetIntents.navigation(context, widgetId, destination).send()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: PendingIntent.CanceledException) {
                WidgetRefreshCoordinator.request(context)
            } catch (_: Exception) {
                WidgetRefreshCoordinator.request(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
