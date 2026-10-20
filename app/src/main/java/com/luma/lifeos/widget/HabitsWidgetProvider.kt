package com.luma.lifeos.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import com.luma.lifeos.domain.widget.WidgetKind

/** Thin lifecycle bridge; all database reads and rendering run in the refresh worker. */
class HabitsWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
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
        WidgetRefreshCoordinator.onWidgetsRestored(context, WidgetKind.HABITS, oldWidgetIds, newWidgetIds)
    }

    override fun onEnabled(context: Context) {
        WidgetRefreshCoordinator.request(context)
    }
}
