package com.luma.lifeos.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
import com.luma.lifeos.R
import com.luma.lifeos.language.AppLanguages
import com.luma.lifeos.ui.theme.THEME_KEY
import kotlin.math.roundToInt

class QuickCaptureWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { render(context, manager, it, manager.getAppWidgetOptions(it)) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        render(context, appWidgetManager, appWidgetId, newOptions)
    }

    private fun render(context: Context, manager: AppWidgetManager, widgetId: Int, options: Bundle) {
        val appContext = AppLanguages.wrap(context)
        val sizes = supportedSizes(options)
        val views = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && sizes.isNotEmpty()) {
            val sizeByDimensions = sizes.associateBy { it.width to it.height }
            val responsiveViews = WidgetLayoutPolicy.distinctSizes(sizeByDimensions.keys.toList()).associate { dimensions ->
                val size = sizeByDimensions.getValue(dimensions)
                size to viewsForSize(
                    context,
                    appContext,
                    widgetId,
                    dimensions.first.roundToInt(),
                    dimensions.second.roundToInt(),
                )
            }
            RemoteViews(responsiveViews)
        } else {
            val (widthDp, heightDp) = WidgetLayoutPolicy.currentBounds(
                isLandscape = appContext.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
                minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH),
                minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT),
                maxWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH),
                maxHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT),
                fallbackWidthDp = 180,
                fallbackHeightDp = 160,
            )
            viewsForSize(context, appContext, widgetId, widthDp, heightDp)
        }
        manager.updateAppWidget(widgetId, views)
    }

    private fun viewsForSize(
        context: Context,
        appContext: Context,
        widgetId: Int,
        widthDp: Int,
        heightDp: Int,
    ): RemoteViews = if (shouldUseCompactLayout(appContext.resources.configuration.fontScale, widthDp, heightDp)) {
        compactViews(context, appContext, widgetId)
    } else {
        defaultViews(context, appContext, widgetId)
    }

    @Suppress("DEPRECATION")
    private fun supportedSizes(options: Bundle): List<SizeF> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES).orEmpty()
        } else {
            emptyList()
        }

    private fun compactViews(context: Context, appContext: Context, widgetId: Int) =
        RemoteViews(context.packageName, R.layout.widget_quick_capture_compact).apply {
            applyPalette(context, appContext, R.id.quick_capture_widget_root,
                listOf(R.id.widget_quick_capture_open))
            setTextViewText(R.id.widget_quick_capture_open, appContext.getString(R.string.widget_open_quick_capture))
            setContentDescription(R.id.widget_quick_capture_open,
                appContext.getString(R.string.widget_open_quick_capture_description))
            setOnClickPendingIntent(R.id.widget_quick_capture_open,
                WidgetIntents.navigation(context, widgetId, WidgetDestination.QuickCapture))
        }

    private fun defaultViews(context: Context, appContext: Context, widgetId: Int) =
        RemoteViews(context.packageName, R.layout.widget_quick_capture).apply {
            val buttons = listOf(
                R.id.widget_capture_mood,
                R.id.widget_capture_memory,
                R.id.widget_capture_journal,
                R.id.widget_open_luma,
            )
            applyPalette(context, appContext, R.id.quick_capture_widget_root, buttons)
            setTextViewText(R.id.widget_capture_mood, appContext.getString(R.string.widget_capture_mood))
            setContentDescription(R.id.widget_capture_mood,
                appContext.getString(R.string.widget_capture_mood_description))
            setOnClickPendingIntent(R.id.widget_capture_mood,
                WidgetIntents.navigation(context, widgetId, WidgetDestination.Capture(CaptureKind.MOOD)))

            setTextViewText(R.id.widget_capture_memory, appContext.getString(R.string.widget_capture_memory))
            setContentDescription(R.id.widget_capture_memory,
                appContext.getString(R.string.widget_capture_memory_description))
            setOnClickPendingIntent(R.id.widget_capture_memory,
                WidgetIntents.navigation(context, widgetId, WidgetDestination.Capture(CaptureKind.MEMORY)))

            setTextViewText(R.id.widget_capture_journal, appContext.getString(R.string.widget_capture_journal))
            setContentDescription(R.id.widget_capture_journal,
                appContext.getString(R.string.widget_capture_journal_description))
            setOnClickPendingIntent(R.id.widget_capture_journal,
                WidgetIntents.navigation(context, widgetId, WidgetDestination.Capture(CaptureKind.JOURNAL)))

            setTextViewText(R.id.widget_open_luma, appContext.getString(R.string.widget_open_luma))
            setContentDescription(R.id.widget_open_luma, appContext.getString(R.string.widget_open_luma_description))
            setOnClickPendingIntent(R.id.widget_open_luma,
                WidgetIntents.navigation(context, widgetId, WidgetDestination.Today))
        }

    private fun RemoteViews.applyPalette(
        context: Context,
        appContext: Context,
        rootId: Int,
        buttonIds: List<Int>,
    ) {
        val dark = isAppDark(context, appContext)
        setInt(rootId, "setLayoutDirection", appContext.resources.configuration.layoutDirection)
        setInt(rootId, "setBackgroundResource",
            if (dark) R.drawable.widget_background_dark else R.drawable.widget_background)
        val buttonBackground = if (dark) R.drawable.widget_button_background_dark
            else R.drawable.widget_button_background
        buttonIds.forEach { id ->
            setInt(id, "setBackgroundResource", buttonBackground)
            setTextColor(id, appContext.getColor(if (dark) R.color.widget_on_primary_dark else R.color.widget_on_primary))
        }
    }

    private fun isAppDark(context: Context, appContext: Context): Boolean {
        val choice = context.getSharedPreferences("app_theme", Context.MODE_PRIVATE)
            .getString(THEME_KEY, "system")
        return when (choice) {
            "dark" -> true
            "light" -> false
            else -> (appContext.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
    }

    companion object {
        internal fun shouldUseCompactLayout(fontScale: Float, minWidthDp: Int, minHeightDp: Int): Boolean =
            fontScale >= 1.6f || (minWidthDp > 0 && minWidthDp < 180) || (minHeightDp > 0 && minHeightDp < 160)
    }
}
