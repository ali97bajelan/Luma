package com.luma.lifeos.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.luma.lifeos.domain.widget.WidgetKind
import java.util.concurrent.TimeUnit

/** Owns the single persistent refresh schedule and the serialized render chain. */
object WidgetRefreshCoordinator {
    const val PERIODIC_WORK_NAME = "luma-widget-periodic-refresh"
    const val REFRESH_WORK_NAME = "luma-widget-refresh-chain"
    private const val PERIOD_MINUTES = 30L
    private const val QUICK_CAPTURE_PROVIDER = ".widget.QuickCaptureWidgetProvider"

    fun request(context: Context) {
        val appContext = context.applicationContext
        reconcileSchedule(appContext)
        enqueueRefresh(appContext, Data.Builder().build())
    }

    /** Called from provider deletion callbacks; cleanup and rendering stay on WorkManager threads. */
    fun onWidgetsDeleted(context: Context, deletedIds: IntArray) {
        if (deletedIds.isEmpty()) return
        val appContext = context.applicationContext
        reconcileSchedule(appContext, deletedIds.toSet())
        val data = Data.Builder()
            .putIntArray(WidgetRefreshWorker.KEY_REMOVED_IDS, deletedIds.distinct().toIntArray())
            .build()
        enqueueRefresh(appContext, data, hasDeferredState = true)
    }

    /** Host-restored IDs receive fresh, hidden-detail runtime rows; old IDs lose every token. */
    fun onWidgetsRestored(
        context: Context,
        kind: WidgetKind,
        oldIds: IntArray,
        newIds: IntArray,
    ) {
        val appContext = context.applicationContext
        reconcileSchedule(appContext)
        val data = Data.Builder()
            .putString(WidgetRefreshWorker.KEY_REMAP_KIND, kind.name)
            .putIntArray(WidgetRefreshWorker.KEY_REMAP_OLD_IDS, oldIds.distinct().toIntArray())
            .putIntArray(WidgetRefreshWorker.KEY_REMAP_NEW_IDS, newIds.distinct().toIntArray())
            .build()
        enqueueRefresh(appContext, data, hasDeferredState = oldIds.isNotEmpty() || newIds.isNotEmpty())
    }

    /** Must stay cheap: provider/system receivers call this on their main thread. */
    fun reconcileSchedule(context: Context, excludingIds: Set<Int> = emptySet()) {
        val appContext = context.applicationContext
        val hasDataWidget = activeDataWidgetIds(appContext).values.any { ids ->
            ids.any { it !in excludingIds }
        }
        val workManager = WorkManager.getInstance(appContext)
        if (!hasDataWidget) {
            workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
            return
        }

        val periodic = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(PERIOD_MINUTES, TimeUnit.MINUTES)
            .setInputData(Data.Builder().putBoolean(WidgetRefreshWorker.KEY_PERIODIC_TICK, true).build())
            .build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodic,
        )
    }

    /** Quick capture is static and never starts or reads the data-widget refresh pipeline. */
    fun refreshQuickCapture(context: Context) {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        val provider = ComponentName(appContext.packageName, appContext.packageName + QUICK_CAPTURE_PROVIDER)
        val ids = manager.getAppWidgetIds(provider)
        if (ids.isEmpty()) return
        val update = Intent(appContext, QuickCaptureWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
        // The provider itself owns the RemoteViews implementation; this explicit internal
        // broadcast asks it to rebuild against the selected app language and theme.
        update.component = provider
        appContext.sendBroadcast(update)
    }

    internal fun activeDataWidgetIds(context: Context): Map<WidgetKind, IntArray> {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        return WidgetKind.entries.associateWith { kind ->
            val providerName = when (kind) {
                WidgetKind.HABITS -> ".widget.HabitsWidgetProvider"
                WidgetKind.TASKS -> ".widget.TasksWidgetProvider"
            }
            manager.getAppWidgetIds(
                ComponentName(appContext.packageName, appContext.packageName + providerName),
            )
        }
    }

    internal fun kindForWidgetId(context: Context, widgetId: Int): WidgetKind? {
        val provider = AppWidgetManager.getInstance(context).getAppWidgetInfo(widgetId)?.provider?.className ?: return null
        return when {
            provider.endsWith(".HabitsWidgetProvider") -> WidgetKind.HABITS
            provider.endsWith(".TasksWidgetProvider") -> WidgetKind.TASKS
            else -> null
        }
    }

    private fun enqueueRefresh(context: Context, data: Data, hasDeferredState: Boolean = false) {
        val hasActiveWidgets = activeDataWidgetIds(context).values.any { it.isNotEmpty() }
        if (!hasActiveWidgets && !hasDeferredState) return
        val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
            .setInputData(data)
            .build()
        WorkManager.getInstance(context).beginUniqueWork(
            REFRESH_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        ).enqueue()
    }
}
