package com.luma.lifeos.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViewsService
import com.luma.lifeos.domain.widget.WidgetKind

/** Provides a distinct, host-bound collection identity for each widget instance. */
class WidgetCollectionService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        WidgetCollectionFactory(applicationContext, intent)

    companion object {
        fun adapterIntent(context: Context, widgetId: Int, kind: WidgetKind): Intent =
            Intent(context, WidgetCollectionService::class.java).apply {
                data = Uri.Builder()
                    .scheme(SCHEME)
                    .authority(AUTHORITY)
                    .appendPath(kind.name)
                    .appendPath(widgetId.toString())
                    .build()
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }

        internal fun parseIdentity(intent: Intent): Pair<Int, WidgetKind>? {
            val uri = intent.data ?: return null
            if (uri.scheme != SCHEME || uri.authority != AUTHORITY || uri.query != null || uri.fragment != null ||
                uri.userInfo != null || uri.port != -1
            ) return null
            val parts = uri.pathSegments
            if (parts.size != 2) return null
            val kind = runCatching { WidgetKind.valueOf(parts[0]) }.getOrNull() ?: return null
            val widgetId = parts[1].toIntOrNull()?.takeIf { it > 0 } ?: return null
            if (widgetId.toString() != parts[1] ||
                intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0) != widgetId
            ) return null
            return widgetId to kind
        }

        private const val SCHEME = "luma-widget"
        private const val AUTHORITY = "collection"
    }
}
