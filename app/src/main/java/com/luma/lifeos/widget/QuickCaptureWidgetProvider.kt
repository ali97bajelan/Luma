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

    internal fun viewsForSize(
        context: Context,
        appContext: Context,
        widgetId: Int,
        widthDp: Int,
        heightDp: Int,
    ): RemoteViews {
        val labels = listOf(R.string.widget_capture_mood, R.string.widget_capture_memory, R.string.widget_capture_journal)
        val density = appContext.resources.displayMetrics.density
        val paint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textLocales = appContext.resources.configuration.locales
            textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,
                16f, appContext.resources.displayMetrics)
        }
        val wordWidths = labels.map { label ->
            appContext.getString(label).split(Regex("\\s+")).maxOfOrNull { paint.measureText(it) } ?: 0f
        }
        // At large text sizes, stack actions first. Never select a layout that splits a word.
        val layout = WidgetLayoutPolicy.captureLayoutOrder(appContext.resources.configuration.fontScale)
            .firstOrNull { candidate ->
                val columns = when (candidate) {
                    WidgetLayoutPolicy.CaptureLayout.WIDE -> 3
                    WidgetLayoutPolicy.CaptureLayout.GRID -> 2
                    else -> 1
                }
                val cellWidth = (widthDp - 24 - (columns - 1) * 8) / columns
                val labelWidths = when (candidate) {
                    WidgetLayoutPolicy.CaptureLayout.WIDE -> List(3) { cellWidth }
                    WidgetLayoutPolicy.CaptureLayout.GRID -> listOf(cellWidth, cellWidth, widthDp - 24)
                    WidgetLayoutPolicy.CaptureLayout.TALL -> List(3) { widthDp - 24 }
                    WidgetLayoutPolicy.CaptureLayout.FALLBACK -> emptyList()
                }
                val sizes = labels.mapIndexed { index, label ->
                    val labelWidth = labelWidths[index]
                    val text = appContext.getString(label)
                    val textLayout = android.text.StaticLayout.Builder.obtain(text, 0, text.length, paint,
                        ((labelWidth - 16).coerceAtLeast(1) * density).toInt())
                        .setIncludePad(true)
                        .setUseLineSpacingFromFallbacks(true)
                        .setBreakStrategy(android.graphics.text.LineBreaker.BREAK_STRATEGY_HIGH_QUALITY)
                        .build()
                    labelWidth.coerceAtLeast(48) to maxOf(48, kotlin.math.ceil(textLayout.height / density).toInt() + 16)
                }
                val contentWidthsPx = labelWidths.map { ((it - 16).coerceAtLeast(1) * density) }
                cellWidth >= 48 &&
                    WidgetLayoutPolicy.captureLabelsFitWithoutSplitting(wordWidths, contentWidthsPx) &&
                    WidgetLayoutPolicy.captureLayout(widthDp, heightDp, sizes) == candidate
            } ?: WidgetLayoutPolicy.CaptureLayout.FALLBACK
        return when (layout) {
            WidgetLayoutPolicy.CaptureLayout.FALLBACK -> compactViews(context, appContext, widgetId)
            else -> defaultViews(context, appContext, widgetId, when (layout) {
                WidgetLayoutPolicy.CaptureLayout.WIDE -> R.layout.widget_quick_capture_wide
                WidgetLayoutPolicy.CaptureLayout.TALL -> R.layout.widget_quick_capture_tall
                else -> R.layout.widget_quick_capture
            })
        }
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

    private fun defaultViews(context: Context, appContext: Context, widgetId: Int, layout: Int) =
        RemoteViews(context.packageName, layout).apply {
            val buttons = listOf(
                R.id.widget_capture_mood,
                R.id.widget_capture_memory,
                R.id.widget_capture_journal,
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

}
