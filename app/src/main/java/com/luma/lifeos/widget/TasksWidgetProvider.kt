package com.luma.lifeos.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import com.luma.lifeos.domain.widget.WidgetKind

/** Thin lifecycle bridge; task reads, actions and rendering run through the shared worker pipeline. */
class TasksWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id ->
            manager.updateAppWidget(id, WidgetRenderer.render(context, id, null, false,
                manager.getAppWidgetOptions(id), loadingKind = WidgetKind.TASKS))
        }
        WidgetRefreshCoordinator.request(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        WidgetRefreshCoordinator.request(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        WidgetRefreshCoordinator.onWidgetsDeleted(context, appWidgetIds)
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        WidgetRefreshCoordinator.onWidgetsRestored(context, WidgetKind.TASKS, oldWidgetIds, newWidgetIds)
    }

    override fun onEnabled(context: Context) {
        WidgetRefreshCoordinator.request(context)
    }
}
