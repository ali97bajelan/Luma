package com.luma.lifeos.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Build
import android.util.SizeF
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.luma.lifeos.LumaApplication
import com.luma.lifeos.domain.widget.WidgetKind
import com.luma.lifeos.lock.ProtectedPreviewPolicy
import com.luma.lifeos.domain.widget.WidgetRow
import com.luma.lifeos.domain.widget.WidgetSnapshot
import com.luma.lifeos.domain.widget.WidgetStore
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** Paged, per-widget rows. Collection actions carry record/token identity, never adapter position. */
internal class WidgetCollectionFactory(
    private val context: Context,
    serviceIntent: android.content.Intent,
) : RemoteViewsService.RemoteViewsFactory {
    private val identity = WidgetCollectionService.parseIdentity(serviceIntent)
    private val widgetId = identity?.first ?: 0
    private val kind = identity?.second
    private var snapshotNow: Instant = Instant.now()
    private var deviceZone: ZoneId = ZoneId.systemDefault()
    private var metadata: WidgetSnapshot? = null
    private var pageStart = -1
    private var pageSnapshot: WidgetSnapshot? = null
    private val stableIds = mutableMapOf<UUID, Long>()
    private val idOwners = mutableMapOf<Long, UUID>()

    override fun onCreate() = refreshMetadata()

    @Synchronized
    override fun onDataSetChanged() {
        pageStart = -1
        pageSnapshot = null
        refreshMetadata()
    }

    @Synchronized
    override fun onDestroy() {
        metadata = null
        pageSnapshot = null
        stableIds.clear()
        idOwners.clear()
    }

    @Synchronized
    override fun getCount(): Int = currentMetadata()?.let { snapshot ->
        if (snapshot.instance.kind == kind) snapshot.total.coerceAtLeast(0) else 0
    } ?: 0

    @Synchronized
    override fun getViewAt(position: Int): RemoteViews? {
        val row = rowAt(position) ?: return null
        val snapshot = pageSnapshot ?: return null
        return WidgetRenderer.collectionRow(context, snapshot, row, widthDp())
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = if (kind == WidgetKind.HABITS) 4 else 1

    @Synchronized
    override fun getItemId(position: Int): Long = rowAt(position)?.let { row ->
        val id = when (row) {
            is WidgetRow.Habit -> row.id
            is WidgetRow.Task -> row.id
        }
        stableItemId(id)
    } ?: -1L

    override fun hasStableIds(): Boolean = true

    private fun rowAt(position: Int): WidgetRow? {
        val current = currentMetadata() ?: return null
        if (current.instance.kind != kind || position !in 0 until current.total) {
            return null
        }
        val start = (position / WidgetStore.COLLECTION_PAGE_SIZE) * WidgetStore.COLLECTION_PAGE_SIZE
        if (start != pageStart || pageSnapshot == null) loadPage(current, start)
        val page = pageSnapshot ?: return null
        return page.rows.getOrNull(position - pageStart)?.takeIf { row ->
            when (kind) {
                WidgetKind.HABITS -> row is WidgetRow.Habit
                WidgetKind.TASKS -> row is WidgetRow.Task
                null -> false
            }
        }
    }

    private fun loadPage(current: WidgetSnapshot, start: Int) {
        pageStart = start
        val limit = minOf(WidgetStore.COLLECTION_PAGE_SIZE, current.total - start).coerceAtLeast(1)
        val store = widgetStore() ?: run {
            pageSnapshot = null
            return
        }
        pageSnapshot = runBlocking(Dispatchers.IO) {
            store.collectionPage(widgetId, snapshotNow, deviceZone, start, limit)
                ?.takeIf { it.instance.kind == kind && it.date == current.date }
        }
    }

    private fun currentMetadata(): WidgetSnapshot? {
        val current = metadata ?: return null
        if (widgetId <= 0 || WidgetRefreshCoordinator.kindForWidgetId(context, widgetId) != kind) return null
        if (!privacyAllowsRead()) {
            metadata = null
            pageSnapshot = null
            return null
        }
        return current
    }

    private fun refreshMetadata() {
        snapshotNow = Instant.now()
        deviceZone = ZoneId.systemDefault()
        metadata = null
        if (widgetId <= 0 || kind == null || WidgetRefreshCoordinator.kindForWidgetId(context, widgetId) != kind) return
        if (!privacyAllowsRead()) return
        val store = widgetStore() ?: return
        metadata = runBlocking(Dispatchers.IO) {
            store.snapshot(widgetId, snapshotNow, deviceZone, rowLimit = 0)
                ?.takeIf { it.instance.kind == kind }
        }
    }

    private fun widgetStore() = (context.applicationContext as? LumaApplication)?.storage?.widgets

    private fun privacyAllowsRead(): Boolean {
        val app = context.applicationContext as? LumaApplication ?: return false
        return try {
            val policy = runBlocking(Dispatchers.IO) { app.localLockStore.read()?.policy }
            !ProtectedPreviewPolicy.redact(policy, ProtectedPreviewPolicy.widgetSection(requireNotNull(kind)))
        } catch (_: Exception) {
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun widthDp(): Int {
        val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId)
        val responsiveSizes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES).orEmpty()
        } else {
            emptyList()
        }
        if (responsiveSizes.isNotEmpty()) return responsiveSizes.minOf { it.width.roundToInt() }.coerceAtLeast(180)
        return WidgetLayoutPolicy.currentBounds(
            isLandscape = context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE,
            minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH),
            minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT),
            maxWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH),
            maxHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT),
            fallbackWidthDp = 250,
            fallbackHeightDp = 250,
        ).first
    }

    private fun stableItemId(recordId: UUID): Long = stableIds[recordId] ?: run {
        var salt = 0
        var candidate: Long
        do {
            candidate = hashId(recordId, salt++)
        } while (candidate == -1L || idOwners[candidate]?.let { it != recordId } == true)
        stableIds[recordId] = candidate
        idOwners[candidate] = recordId
        candidate
    }

    private fun hashId(recordId: UUID, salt: Int): Long {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest("$recordId:$salt".toByteArray(Charsets.UTF_8))
        var value = 0L
        repeat(Long.SIZE_BYTES) { index ->
            value = (value shl 8) or (bytes[index].toLong() and 0xffL)
        }
        return value
    }

}
