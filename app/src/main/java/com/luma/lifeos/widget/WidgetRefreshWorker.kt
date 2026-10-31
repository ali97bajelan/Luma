package com.luma.lifeos.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.luma.lifeos.LumaApplication
import com.luma.lifeos.R
import com.luma.lifeos.domain.widget.WidgetInstance
import com.luma.lifeos.domain.widget.WidgetKind
import com.luma.lifeos.lock.ProtectedPreviewPolicy
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A periodic request only enqueues the serialized one-time renderer. */
class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (inputData.getBoolean(KEY_PERIODIC_TICK, false)) {
            WidgetRefreshCoordinator.request(applicationContext)
            return Result.success()
        }

        val app = applicationContext as? LumaApplication ?: return Result.failure()
        val kindName = inputData.getString(KEY_REMAP_KIND)
        val oldIds = inputData.getIntArray(KEY_REMAP_OLD_IDS) ?: intArrayOf()
        val newIds = inputData.getIntArray(KEY_REMAP_NEW_IDS) ?: intArrayOf()
        val removedIds = inputData.getIntArray(KEY_REMOVED_IDS) ?: intArrayOf()
        val remapKind = kindName?.let {
            try {
                WidgetKind.valueOf(it)
            } catch (_: IllegalArgumentException) {
                return Result.failure()
            }
        }
        if (kindName == null && (oldIds.isNotEmpty() || newIds.isNotEmpty())) return Result.failure()

        try {
            withContext(Dispatchers.IO) {
                val store = app.storage.widgets
                if (remapKind != null) {
                    store.remap(
                        oldIds.filter { it > 0 },
                        newIds.filter { it > 0 }.map { WidgetInstance(it, remapKind) },
                    )
                }
                for (id in removedIds.distinct()) store.remove(id)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Cleanup is idempotent; retry the same identifiers if storage is temporarily unavailable.
            return Result.retry()
        }

        WidgetRefreshCoordinator.reconcileSchedule(applicationContext, removedIds.toSet())
        val manager = AppWidgetManager.getInstance(applicationContext)
        val now = Instant.now()
        val deviceZone = ZoneId.systemDefault()
        val lockState = try {
            withContext(Dispatchers.IO) { app.localLockStore.read()?.policy to true }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null to false
        }
        for ((_, ids) in WidgetRefreshCoordinator.activeDataWidgetIds(applicationContext)) {
            ids.forEach { widgetId ->
                val kind = WidgetRefreshCoordinator.kindForWidgetId(applicationContext, widgetId)
                val protected = kind?.let {
                    ProtectedPreviewPolicy.redact(lockState.first,
                        ProtectedPreviewPolicy.widgetSection(it), lockState.second)
                } ?: !lockState.second
                val views = if (protected && kind != null) {
                    WidgetRenderer.render(applicationContext, widgetId, snapshot = null,
                        failure = false, options = manager.getAppWidgetOptions(widgetId), locked = true)
                } else try {
                    val snapshot = withContext(Dispatchers.IO) {
                        val store = app.storage.widgets
                        WidgetRenderer.snapshotForRendering(store, widgetId, now, deviceZone) ?: run {
                            val kind = WidgetRefreshCoordinator.kindForWidgetId(applicationContext, widgetId)
                                ?: return@withContext null
                            store.ensureConfigured(WidgetInstance(widgetId, kind))
                            WidgetRenderer.snapshotForRendering(store, widgetId, now, deviceZone)
                        }
                    }
                    WidgetRenderer.render(
                        applicationContext,
                        widgetId,
                        snapshot,
                        failure = false,
                        options = manager.getAppWidgetOptions(widgetId),
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    WidgetRenderer.render(applicationContext, widgetId, snapshot = null, failure = true,
                        options = manager.getAppWidgetOptions(widgetId))
                }
                val latestLockState = try {
                    withContext(Dispatchers.IO) { app.localLockStore.read()?.policy to true }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null to false
                }
                val latestProtected = kind?.let {
                    ProtectedPreviewPolicy.redact(
                        latestLockState.first,
                        ProtectedPreviewPolicy.widgetSection(it),
                        latestLockState.second,
                    )
                } ?: !latestLockState.second
                val safeViews = if (latestProtected && kind != null) {
                    WidgetRenderer.render(applicationContext, widgetId, snapshot = null,
                        failure = false, options = manager.getAppWidgetOptions(widgetId), locked = true)
                } else views
                manager.updateAppWidget(widgetId, safeViews)
                manager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_data_rows)
            }
        }
        return Result.success()
    }

    companion object {
        const val KEY_PERIODIC_TICK = "periodic_tick"
        const val KEY_REMOVED_IDS = "removed_ids"
        const val KEY_REMAP_KIND = "remap_kind"
        const val KEY_REMAP_OLD_IDS = "remap_old_ids"
        const val KEY_REMAP_NEW_IDS = "remap_new_ids"
    }
}
