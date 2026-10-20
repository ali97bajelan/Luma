package com.luma.lifeos.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

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
            is WidgetIntents.Action.Mutation -> {
                val data = Data.Builder()
                    .putInt(WidgetActionWorker.KEY_WIDGET_ID, action.widgetId)
                    .putString(WidgetActionWorker.KEY_TOKEN, action.token.toString())
                    .build()
                val work = OneTimeWorkRequestBuilder<WidgetActionWorker>()
                    .setInputData(data)
                    .build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    "luma-widget-action:${action.widgetId}:${action.token}",
                    ExistingWorkPolicy.KEEP,
                    work,
                )
            }
        }
    }
}
