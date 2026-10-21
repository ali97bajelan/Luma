package com.luma.lifeos.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.graphics.Paint
import android.text.BidiFormatter
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.luma.lifeos.R
import com.luma.lifeos.domain.widget.WidgetActionResult
import com.luma.lifeos.domain.widget.WidgetKind
import com.luma.lifeos.domain.widget.WidgetOutcome
import com.luma.lifeos.domain.widget.WidgetRow
import com.luma.lifeos.domain.widget.WidgetSnapshot
import com.luma.lifeos.domain.widget.WidgetStore
import com.luma.lifeos.language.AppLanguages
import com.luma.lifeos.ui.theme.THEME_KEY
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** Shared data-widget shell. Per-kind row content stays small and uses only persisted commands. */
object WidgetRenderer {
    private data class HeaderPlan(
        val stacked: Boolean,
        val actionsFit: Boolean,
        val heightDp: Int,
        val captureWidthDp: Int,
        val title: String,
        val totalCountCue: String?,
    )

    private data class MeasuredRow(
        val heightDp: Int,
        val inlineHabit: Boolean = false,
        val progressColumnWidthPx: Int = 0,
        val fitsWidth: Boolean = true,
    )

    /** Loads shell metadata plus only the small first page needed to size the collection viewport. */
    internal suspend fun snapshotForRendering(
        store: WidgetStore,
        widgetId: Int,
        now: Instant,
        deviceZone: ZoneId,
    ): WidgetSnapshot? {
        val metadata = store.snapshot(widgetId, now, deviceZone, rowLimit = 0) ?: return null
        if (!metadata.instance.showDetails || metadata.total <= 0) return metadata
        return store.collectionPage(
            widgetId,
            now,
            deviceZone,
            offset = 0,
            limit = minOf(WidgetLayoutPolicy.MAX_MEASUREMENT_ROWS, metadata.total),
        )?.takeIf { it.instance.kind == metadata.instance.kind && it.date == metadata.date } ?: metadata
    }

    fun render(
        context: Context,
        widgetId: Int,
        snapshot: WidgetSnapshot?,
        failure: Boolean,
        options: android.os.Bundle? = null,
        loadingKind: WidgetKind? = null,
    ): RemoteViews {
        val sizes = options?.let(::supportedSizes).orEmpty()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && sizes.isNotEmpty()) {
            val sizeByDimensions = sizes.associateBy { it.width to it.height }
            val responsiveViews = WidgetLayoutPolicy.distinctSizes(sizeByDimensions.keys.toList()).associate { dimensions ->
                val size = sizeByDimensions.getValue(dimensions)
                size to renderAtSize(
                    context,
                    widgetId,
                    snapshot,
                    failure,
                    dimensions.first.roundToInt(),
                    dimensions.second.roundToInt(),
                    loadingKind,
                )
            }
            return RemoteViews(responsiveViews)
        }

        val bounds = if (options == null) {
            250 to 250
        } else {
            WidgetLayoutPolicy.currentBounds(
                isLandscape = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
                minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH),
                minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT),
                maxWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH),
                maxHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT),
                fallbackWidthDp = 250,
                fallbackHeightDp = 250,
            )
        }
        return renderAtSize(context, widgetId, snapshot, failure, bounds.first, bounds.second, loadingKind)
    }

    private fun renderAtSize(
        context: Context,
        widgetId: Int,
        snapshot: WidgetSnapshot?,
        failure: Boolean,
        widthDp: Int,
        heightDp: Int,
        loadingKind: WidgetKind?,
    ): RemoteViews {
        val appContext = AppLanguages.wrap(context)
        if (loadingKind != null) return WidgetCompactRenderer.render(context, appContext, widgetId,
            null, false, widthDp, heightDp, "", isAppDark(context, appContext), loadingKind)
        val kind = snapshot?.instance?.kind ?: WidgetRefreshCoordinator.kindForWidgetId(context, widgetId)
        val header = headerPlan(appContext, kind, widthDp, if (!failure) snapshot?.total else null)
        if (failure || snapshot == null) {
            val density = appContext.resources.displayMetrics.density
            val innerWidth = ((widthDp - 24) * density).toInt()
            val message = appContext.getString(if (failure) R.string.widget_data_read_error else R.string.widget_data_setup)
            val reserved = 24 + header.heightDp +
                measureTextHeight(appContext, message, innerWidth, 14f) + 4
            if (!header.actionsFit || WidgetLayoutPolicy.needsCompactShell(heightDp, reserved)) {
                return compactState(context, appContext, widgetId, snapshot, failure, widthDp, heightDp)
            }
        }
        if (!header.actionsFit) {
            return compactState(context, appContext, widgetId, snapshot, failure, widthDp, heightDp)
        }
        val views = RemoteViews(context.packageName,
            if (header.stacked) R.layout.widget_data_stacked_header else R.layout.widget_data)
        val dark = isAppDark(context, appContext)
        val textColor = appContext.getColor(if (dark) R.color.widget_text_primary_dark else R.color.widget_text_primary_light)
        val secondaryTextColor = appContext.getColor(if (dark) R.color.widget_text_secondary_dark else R.color.widget_text_secondary_light)
        val utilityTextColor = appContext.getColor(if (dark) R.color.widget_action_utility_dark else R.color.widget_action_utility_light)
        val buttonBackground = if (dark) R.drawable.widget_button_background_dark
        else R.drawable.widget_button_background
        val rowActionBackground = if (dark) R.drawable.widget_row_action_background_dark
        else R.drawable.widget_row_action_background
        val utilityBackground = if (dark) R.drawable.widget_utility_background_dark
        else R.drawable.widget_utility_background
        val widgetBackground = if (dark) R.drawable.widget_background_dark else R.drawable.widget_background

        views.removeAllViews(R.id.widget_data_action_slot)
        views.removeAllViews(R.id.widget_feedback_slot)
        views.setViewVisibility(R.id.widget_data_scroll_hint, View.GONE)
        views.setViewVisibility(R.id.widget_data_rows, View.GONE)
        if (kind != null) {
            views.setRemoteAdapter(
                R.id.widget_data_rows,
                WidgetCollectionService.adapterIntent(context, widgetId, kind),
            )
            views.setPendingIntentTemplate(
                R.id.widget_data_rows,
                WidgetIntents.collectionTemplate(context, widgetId),
            )
        }
        views.setViewVisibility(R.id.widget_data_updated, if (snapshot == null || failure) View.GONE else View.VISIBLE)
        views.setInt(R.id.widget_data_root, "setLayoutDirection", appContext.resources.configuration.layoutDirection)
        views.setInt(R.id.widget_data_root, "setBackgroundResource", widgetBackground)
        views.setTextColor(R.id.widget_data_title, textColor)
        views.setTextColor(R.id.widget_data_more, secondaryTextColor)
        views.setTextColor(R.id.widget_data_summary, textColor)
        views.setTextColor(R.id.widget_data_updated, secondaryTextColor)
        views.setTextColor(R.id.widget_data_refresh, utilityTextColor)
        views.setTextColor(R.id.widget_data_scroll_hint, secondaryTextColor)
        views.setInt(R.id.widget_data_refresh, "setBackgroundResource", utilityBackground)
        views.setInt(R.id.widget_data_open, "setBackgroundResource", utilityBackground)
        views.setInt(R.id.widget_header_refresh, "setColorFilter", utilityTextColor)
        views.setContentDescription(R.id.widget_header_refresh, appContext.getString(
            if (kind == WidgetKind.TASKS) R.string.widget_tasks_refresh_description else R.string.widget_habits_refresh_description))
        views.setOnClickPendingIntent(R.id.widget_header_refresh, WidgetIntents.refresh(context, widgetId))
        val destination = destinationFor(kind)
        views.setTextViewText(R.id.widget_data_title, header.title)
        views.setTextViewText(R.id.widget_data_more, header.totalCountCue.orEmpty())
        views.setViewVisibility(R.id.widget_data_more, if (header.totalCountCue == null) View.GONE else View.VISIBLE)
        views.setContentDescription(R.id.widget_data_open, viewAllDescription(appContext, kind, 0, showDetails = true))
        views.setOnClickPendingIntent(
            R.id.widget_data_open,
            if (snapshot == null && !failure) WidgetIntents.configuration(context, widgetId)
            else WidgetIntents.navigation(context, widgetId, destination),
        )
        views.setTextViewText(R.id.widget_data_refresh, appContext.getString(R.string.widget_capture))
        views.setTextColor(R.id.widget_data_refresh, utilityTextColor)
        views.setContentDescription(R.id.widget_data_refresh,
            appContext.getString(R.string.widget_open_quick_capture_description))
        views.setOnClickPendingIntent(R.id.widget_data_refresh,
            WidgetIntents.navigation(context, widgetId, WidgetDestination.QuickCapture))
        views.setViewVisibility(R.id.widget_data_refresh, View.VISIBLE)
        if (failure) {
            views.setTextViewText(R.id.widget_data_summary, "")
            views.setTextViewText(R.id.widget_data_updated, "")
            views.setTextViewText(R.id.widget_data_summary, appContext.getString(R.string.widget_data_read_error))
            views.setTextColor(R.id.widget_data_summary,
                appContext.getColor(if (dark) R.color.widget_error_dark else R.color.widget_error_light))
            views.setContentDescription(R.id.widget_data_open, viewAllDescription(appContext, kind, 0, showDetails = true))
        } else if (snapshot == null) {
            views.setTextViewText(R.id.widget_data_summary, appContext.getString(R.string.widget_data_setup))
            views.setTextViewText(R.id.widget_data_updated, "")
            views.setContentDescription(R.id.widget_data_open, appContext.getString(R.string.widget_data_setup))
        } else {
            return renderSnapshot(context, appContext, views, widgetId, snapshot, widthDp, heightDp,
                textColor, secondaryTextColor, utilityTextColor, buttonBackground, utilityBackground,
                rowActionBackground)
        }
        return views
    }

    @Suppress("DEPRECATION")
    private fun supportedSizes(options: android.os.Bundle): List<SizeF> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES).orEmpty()
        } else {
            emptyList()
        }

    private fun renderSnapshot(
        context: Context,
        appContext: Context,
        views: RemoteViews,
        widgetId: Int,
        snapshot: WidgetSnapshot,
        widthDp: Int,
        heightDp: Int,
        textColor: Int,
        secondaryTextColor: Int,
        utilityTextColor: Int,
        buttonBackground: Int,
        utilityBackground: Int,
        rowActionBackground: Int,
    ): RemoteViews {
        val locale = AppLanguages.current(appContext).locale
        val snapshotDate = bidiWrapped(appContext,
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(snapshot.date))
        val deviceZone = ZoneId.systemDefault()
        val zoneSuffix = if (snapshot.zone != deviceZone) {
            appContext.getString(R.string.widget_data_tracking_zone,
                BidiFormatter.getInstance(locale).unicodeWrap(snapshot.zone.id.takeUnless { it == "Z" } ?: "UTC"))
        } else {
            ""
        }

        val summary = when (snapshot.instance.kind) {
            WidgetKind.HABITS -> when {
                !snapshot.hasAnyItems -> appContext.getString(R.string.widget_habits_empty_library)
                snapshot.total == 0 -> appContext.getString(R.string.widget_habits_empty_scope)
                else -> appContext.getString(
                    R.string.widget_habits_summary,
                    snapshot.completed ?: 0,
                    snapshot.total,
                )
            }
            WidgetKind.TASKS -> when {
                !snapshot.hasAnyItems -> appContext.getString(R.string.widget_tasks_empty_library)
                snapshot.total == 0 -> appContext.getString(R.string.widget_tasks_empty_scope)
                else -> appContext.resources.getQuantityString(
                    R.plurals.widget_tasks_summary,
                    snapshot.total,
                    snapshot.total,
                )
            }
        }
        val scopeText = summary
        views.setTextViewText(R.id.widget_data_summary, bidiWrapped(appContext, scopeText))
        val time = bidiWrapped(appContext, DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
            .format(snapshot.updatedAt.atZone(snapshot.zone)))
        val updatedDate = snapshot.updatedAt.atZone(snapshot.zone).toLocalDate()
        val updatedText = if (updatedDate == snapshot.date) {
            appContext.getString(R.string.widget_data_fresh_same, snapshotDate, time, zoneSuffix)
        } else {
            appContext.getString(R.string.widget_data_fresh_different,
                snapshotDate,
                bidiWrapped(appContext,
                    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(updatedDate)),
                time,
                zoneSuffix)
        }
        views.setTextViewText(R.id.widget_data_updated, updatedText)
        val feedbackText = snapshot.feedback?.let { feedbackText(appContext, it.outcome, snapshot.instance.kind) }.orEmpty()
        val inlineFeedback = useInlineFeedback(appContext, widthDp, feedbackText, snapshot.feedback)

        // This small page is only a sizing sample. The ListView adapter owns the full collection.
        val measurementRows: List<WidgetRow> = if (snapshot.instance.showDetails) {
            when (snapshot.instance.kind) {
                WidgetKind.HABITS -> snapshot.rows.filterIsInstance<WidgetRow.Habit>()
                WidgetKind.TASKS -> snapshot.rows.filterIsInstance<WidgetRow.Task>()
            }.take(WidgetLayoutPolicy.MAX_MEASUREMENT_ROWS)
        } else {
            emptyList()
        }
        val density = appContext.resources.displayMetrics.density
        val textWidthPx = ((widthDp - 24).coerceAtLeast(1) * density).toInt()
        val fontScale = appContext.resources.configuration.fontScale
        val header = headerPlan(appContext, snapshot.instance.kind, widthDp, snapshot.total)
        val emptyActionHeight = if (!snapshot.hasAnyItems) 52 else 0
        val reserved = 24 + header.heightDp +
            measureTextHeight(appContext, scopeText, textWidthPx, 14f) + 4 +
            measureTextHeight(appContext, updatedText, textWidthPx, 12f) +
            8 +
            emptyActionHeight +
            (snapshot.feedback?.let {
                if (inlineFeedback) max(48, measureTextHeight(appContext, feedbackText,
                    (textWidthPx - 120 * density).toInt(), 14f)) + 4
                else measureFeedbackHeight(appContext, widthDp, feedbackText, it, snapshot.instance.kind)
            } ?: 0)
        if (!header.actionsFit || WidgetLayoutPolicy.needsCompactShell(heightDp, reserved)) {
            return compactState(context, appContext, widgetId, snapshot, false, widthDp, heightDp,
                "$scopeText\n$updatedText")
        }
        if (!snapshot.hasAnyItems) {
            val (label, description, destination) = when (snapshot.instance.kind) {
                WidgetKind.HABITS -> Triple(
                    R.string.widget_habits_add,
                    R.string.widget_habits_add_description,
                    WidgetDestination.NewHabit,
                )
                WidgetKind.TASKS -> Triple(
                    R.string.widget_tasks_add,
                    R.string.widget_tasks_add_description,
                    WidgetDestination.NewTask,
                )
            }
            val emptyAction = RemoteViews(context.packageName, R.layout.widget_data_empty_action).apply {
                setTextViewText(R.id.widget_data_empty_action, appContext.getString(label))
                setContentDescription(R.id.widget_data_empty_action, appContext.getString(description))
                setTextColor(R.id.widget_data_empty_action,
                    appContext.getColor(if (isAppDark(context, appContext)) R.color.widget_mutation_text_dark else R.color.widget_mutation_text_light))
                setInt(R.id.widget_data_empty_action, "setBackgroundResource", buttonBackground)
                setOnClickPendingIntent(
                    R.id.widget_data_empty_action,
                    WidgetIntents.navigation(context, widgetId, destination),
                )
            }
            views.addView(R.id.widget_data_action_slot, emptyAction)
        }
        val feedbackViews = RemoteViews(context.packageName,
            if (inlineFeedback) R.layout.widget_feedback_inline else R.layout.widget_feedback)
        renderFeedback(context, appContext, feedbackViews, widgetId, snapshot, textColor, utilityTextColor,
            buttonBackground, utilityBackground, feedbackText, inlineFeedback)
        views.addView(R.id.widget_feedback_slot, feedbackViews)
        val progressColumnWidthPx = if (snapshot.instance.kind == WidgetKind.HABITS) {
            collectionProgressColumnWidthPx(appContext)
        } else 0
        val measurements = measurementRows.map { row ->
            when (row) {
                is WidgetRow.Habit -> measureHabitRow(appContext, widthDp, row, progressColumnWidthPx)
                is WidgetRow.Task -> measureTaskRow(appContext, widthDp, row, snapshot.date)
            }
        }
        if (measurements.any { !it.fitsWidth }) {
            return compactState(context, appContext, widgetId, snapshot, false, widthDp, heightDp,
                "$scopeText\n$updatedText")
        }
        val sampledHeights = measurements.map { it.heightDp }.ifEmpty {
            if (snapshot.instance.showDetails && snapshot.total > 0) {
                listOf(if (snapshot.instance.kind == WidgetKind.TASKS) 56 else 64)
            } else emptyList()
        }
        val capacityWithoutHint = WidgetLayoutPolicy.rowCapacity(
            widthDp, heightDp, fontScale, reserved, sampledHeights,
        )
        val scrollHintHeight = measureTextHeight(
            appContext,
            appContext.getString(R.string.widget_scroll_for_more),
            textWidthPx,
            12f,
        ) + 4
        val capacityWithHint = WidgetLayoutPolicy.rowCapacity(
            widthDp,
            heightDp,
            fontScale,
            reserved + scrollHintHeight,
            sampledHeights,
        )
        val showScrollHint = snapshot.instance.showDetails && snapshot.total > capacityWithoutHint &&
            capacityWithHint > 0
        val capacity = if (showScrollHint) capacityWithHint else capacityWithoutHint
        if (snapshot.instance.showDetails && snapshot.total > 0 && capacity <= 0) {
            return compactState(context, appContext, widgetId, snapshot, false, widthDp, heightDp,
                "$scopeText\n$updatedText")
        }
        views.setContentDescription(
            R.id.widget_data_rows,
            collectionAccessibilityDescription(appContext, snapshot.instance.kind, snapshot.total),
        )
        views.setViewVisibility(R.id.widget_data_rows,
            if (snapshot.instance.showDetails && snapshot.total > 0) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_data_scroll_hint, if (showScrollHint) View.VISIBLE else View.GONE)
        views.setContentDescription(R.id.widget_data_open,
            viewAllDescription(appContext, snapshot.instance.kind, snapshot.total, snapshot.instance.showDetails))
        return views
    }

    private fun habitRow(
        context: Context,
        appContext: Context,
        snapshot: WidgetSnapshot,
        row: WidgetRow.Habit,
        textColor: Int,
        secondaryTextColor: Int,
        rowActionBackground: Int,
        inline: Boolean,
        progressColumnWidthPx: Int,
    ) = RemoteViews(context.packageName,
        if (inline) R.layout.widget_habit_row_inline else R.layout.widget_habit_row).apply {
        setTextColor(R.id.widget_habit_row_title, textColor)
        setTextColor(R.id.widget_habit_row_count, secondaryTextColor)
        setTextColor(R.id.widget_habit_row_action,
            appContext.getColor(if (isAppDark(context, appContext)) R.color.widget_mutation_text_dark else R.color.widget_mutation_text_light))
        setInt(R.id.widget_habit_row_action, "setBackgroundResource", rowActionBackground)
        setTextViewText(R.id.widget_habit_row_title, row.title)
        if (inline && progressColumnWidthPx > 0) {
            setInt(R.id.widget_habit_row_count, "setWidth", progressColumnWidthPx)
        }
        setContentDescription(
            R.id.widget_habit_row_text,
            appContext.getString(R.string.widget_habit_open_description, bidiWrapped(appContext, row.title)),
        )
        setOnClickFillInIntent(
            R.id.widget_habit_row_text,
            WidgetIntents.collectionNavigation(row.id, snapshot.date),
        )
        val count = habitProgressText(appContext, row)
        setTextViewText(R.id.widget_habit_row_count, count)
        setContentDescription(R.id.widget_habit_row_text,
            appContext.getString(R.string.widget_habit_open_description, bidiWrapped(appContext, row.title)) + ", " + count)

        val canLog = !row.skipped && row.count < row.target && row.actionToken != null
        setViewVisibility(R.id.widget_habit_row_action, if (canLog) View.VISIBLE else View.GONE)
        if (canLog) {
            setTextViewText(R.id.widget_habit_row_action, appContext.getString(R.string.widget_habit_log_short))
            setContentDescription(
                R.id.widget_habit_row_action,
                appContext.getString(R.string.widget_habit_log_description, bidiWrapped(appContext, row.title)) +
                    ", " + count,
            )
            setOnClickFillInIntent(
                R.id.widget_habit_row_action,
                WidgetIntents.collectionMutation(requireNotNull(row.actionToken), row.id, snapshot.date),
            )
        }
    }

    private fun taskRow(
        context: Context,
        appContext: Context,
        snapshot: WidgetSnapshot,
        row: WidgetRow.Task,
        textColor: Int,
        secondaryTextColor: Int,
        rowActionBackground: Int,
    ) = RemoteViews(context.packageName, R.layout.widget_task_row).apply {
        setTextColor(R.id.widget_task_row_title, textColor)
        val dueColor = if (row.dueDate.isBefore(snapshot.date)) {
            appContext.getColor(if (isAppDark(context, appContext)) R.color.widget_overdue_dark else R.color.widget_overdue_light)
        } else secondaryTextColor
        setTextColor(R.id.widget_task_row_due, dueColor)
        setTextColor(R.id.widget_task_row_action,
            appContext.getColor(if (isAppDark(context, appContext)) R.color.widget_mutation_text_dark else R.color.widget_mutation_text_light))
        setInt(R.id.widget_task_row_action, "setBackgroundResource", rowActionBackground)
        setTextViewText(R.id.widget_task_row_title, row.title)
        setContentDescription(
            R.id.widget_task_row_text,
            appContext.getString(R.string.widget_task_open_description, bidiWrapped(appContext, row.title)),
        )
        setOnClickFillInIntent(
            R.id.widget_task_row_text,
            WidgetIntents.collectionNavigation(row.id, snapshot.date),
        )
        val dueLabel = taskDueLabel(appContext, row, snapshot.date)
        setTextViewText(R.id.widget_task_row_due, dueLabel)
        setContentDescription(R.id.widget_task_row_text,
            appContext.getString(R.string.widget_task_open_description, bidiWrapped(appContext, row.title)) +
                ", " + dueLabel)

        val canComplete = row.actionToken != null
        setViewVisibility(R.id.widget_task_row_action, if (canComplete) View.VISIBLE else View.GONE)
        if (canComplete) {
            setTextViewText(R.id.widget_task_row_action, appContext.getString(R.string.widget_task_complete))
            setContentDescription(
                R.id.widget_task_row_action,
                appContext.getString(R.string.widget_task_complete_description, bidiWrapped(appContext, row.title)) +
                    ", " + dueLabel,
            )
            setOnClickFillInIntent(
                R.id.widget_task_row_action,
                WidgetIntents.collectionMutation(requireNotNull(row.actionToken), row.id, snapshot.date),
            )
        }
    }

    internal fun collectionRow(
        context: Context,
        snapshot: WidgetSnapshot,
        row: WidgetRow,
        widthDp: Int,
    ): RemoteViews {
        val appContext = AppLanguages.wrap(context)
        val dark = isAppDark(context, appContext)
        val textColor = appContext.getColor(if (dark) R.color.widget_text_primary_dark else R.color.widget_text_primary_light)
        val secondaryTextColor = appContext.getColor(
            if (dark) R.color.widget_text_secondary_dark else R.color.widget_text_secondary_light,
        )
        val rowActionBackground = if (dark) R.drawable.widget_row_action_background_dark
        else R.drawable.widget_row_action_background
        return when (row) {
            is WidgetRow.Habit -> {
                val progressWidth = collectionProgressColumnWidthPx(appContext)
                val measured = measureHabitRow(appContext, widthDp, row, progressWidth)
                habitRow(
                    context, appContext, snapshot, row, textColor, secondaryTextColor,
                    rowActionBackground, measured.inlineHabit, progressWidth,
                )
            }
            is WidgetRow.Task -> taskRow(
                context, appContext, snapshot, row, textColor, secondaryTextColor,
                rowActionBackground,
            )
        }
    }

    internal fun collectionProgressColumnWidthPx(context: Context): Int {
        val samples = listOf(
            context.getString(R.string.widget_habit_count, 999_999, 999),
            context.getString(R.string.widget_habit_target_reached, 999_999, 999),
            context.getString(R.string.widget_habit_skipped, 999_999, 999),
        ).map { bidiWrapped(context, it) }
        val density = context.resources.displayMetrics.density
        return ceil(samples.maxOf { measureTextWidth(context, it, 14f) } + 8f * density).toInt()
    }

    private fun renderFeedback(
        context: Context,
        appContext: Context,
        views: RemoteViews,
        widgetId: Int,
        snapshot: WidgetSnapshot,
        textColor: Int,
        utilityTextColor: Int,
        buttonBackground: Int,
        utilityBackground: Int,
        text: String,
        inline: Boolean,
    ) {
        val feedback = snapshot.feedback
        if (feedback == null) {
            views.setViewVisibility(R.id.widget_feedback_container, View.GONE)
            return
        }
        views.setViewVisibility(R.id.widget_feedback_container, View.VISIBLE)
        views.setTextViewText(R.id.widget_data_feedback, text)
        val dark = isAppDark(context, appContext)
        val errorTextColor = appContext.getColor(if (dark) R.color.widget_error_dark else R.color.widget_error_light)
        val mutationTextColor = appContext.getColor(if (dark) R.color.widget_mutation_text_dark else R.color.widget_mutation_text_light)
        val feedbackTextColor = if (feedback.outcome in setOf(WidgetOutcome.STALE, WidgetOutcome.CONFLICT,
                WidgetOutcome.MISSING, WidgetOutcome.INVALID, WidgetOutcome.FAILED)) errorTextColor else textColor
        views.setTextColor(R.id.widget_data_feedback, feedbackTextColor)
        views.setTextColor(R.id.widget_feedback_dismiss, utilityTextColor)
        views.setViewVisibility(R.id.widget_feedback_dismiss, View.VISIBLE)
        views.setContentDescription(
            R.id.widget_feedback_dismiss,
            appContext.getString(R.string.widget_feedback_dismiss_description),
        )
        views.setInt(R.id.widget_feedback_dismiss, "setBackgroundResource", utilityBackground)
        views.setOnClickPendingIntent(
            R.id.widget_feedback_dismiss,
            WidgetIntents.dismissFeedback(context, widgetId),
        )

        val hasIssue = feedback.outcome !in setOf(WidgetOutcome.SAVED, WidgetOutcome.UNDONE)
        views.setViewVisibility(R.id.widget_feedback_icon, if (hasIssue) View.VISIBLE else View.GONE)
        if (hasIssue) views.setInt(R.id.widget_feedback_icon, "setColorFilter", errorTextColor)

        fun action(viewId: Int, label: Int, description: Int, pendingIntent: android.app.PendingIntent) {
            views.setViewVisibility(viewId, View.VISIBLE)
            views.setTextViewText(viewId, appContext.getString(label))
            views.setContentDescription(viewId, appContext.getString(description))
            val isUtility = label == R.string.widget_data_refresh || label == openLabelResource(snapshot.instance.kind)
            views.setTextColor(viewId, if (isUtility) utilityTextColor else mutationTextColor)
            views.setInt(viewId, "setBackgroundResource", if (isUtility) utilityBackground else buttonBackground)
            views.setOnClickPendingIntent(viewId, pendingIntent)
        }
        fun hideAction(viewId: Int) = views.setViewVisibility(viewId, View.GONE)

        when (feedback.outcome) {
            WidgetOutcome.SAVED -> feedback.undoToken?.let { token ->
                val undoDescription = if (snapshot.instance.kind == WidgetKind.TASKS) {
                    R.string.widget_task_undo_description
                } else {
                    R.string.widget_feedback_undo_description
                }
                action(
                    R.id.widget_feedback_primary,
                    R.string.widget_feedback_undo,
                    undoDescription,
                    WidgetIntents.mutation(context, widgetId, token),
                )
            } ?: hideAction(R.id.widget_feedback_primary)
            WidgetOutcome.UNDONE -> hideAction(R.id.widget_feedback_primary)
            WidgetOutcome.STALE -> action(
                R.id.widget_feedback_primary,
                R.string.widget_data_refresh,
                R.string.widget_data_refresh_description,
                WidgetIntents.refresh(context, widgetId),
            )
            WidgetOutcome.FAILED -> feedback.retryToken?.let { token ->
                action(
                    R.id.widget_feedback_primary,
                    R.string.widget_feedback_retry,
                    if (snapshot.instance.kind == WidgetKind.TASKS) R.string.widget_task_retry_description
                    else R.string.widget_feedback_retry_description,
                    WidgetIntents.mutation(context, widgetId, token),
                )
            } ?: action(
                R.id.widget_feedback_primary,
                openLabelResource(snapshot.instance.kind),
                openDescriptionResource(snapshot.instance.kind),
                WidgetIntents.navigation(context, widgetId, destinationFor(snapshot.instance.kind)),
            )
            WidgetOutcome.CONFLICT, WidgetOutcome.MISSING, WidgetOutcome.INVALID -> action(
                R.id.widget_feedback_primary,
                openLabelResource(snapshot.instance.kind),
                openDescriptionResource(snapshot.instance.kind),
                WidgetIntents.navigation(context, widgetId, destinationFor(snapshot.instance.kind)),
            )
        }

        val needsSecondaryOpen = feedback.outcome == WidgetOutcome.STALE ||
            (feedback.outcome == WidgetOutcome.FAILED && feedback.retryToken != null)
        if (needsSecondaryOpen) {
            action(
                R.id.widget_feedback_secondary,
                openLabelResource(snapshot.instance.kind),
                openDescriptionResource(snapshot.instance.kind),
                WidgetIntents.navigation(context, widgetId, destinationFor(snapshot.instance.kind)),
            )
        } else {
            hideAction(R.id.widget_feedback_secondary)
        }
        val hasPrimary = feedback.outcome != WidgetOutcome.UNDONE &&
            (feedback.outcome != WidgetOutcome.SAVED || feedback.undoToken != null)
        views.setViewVisibility(R.id.widget_feedback_actions,
            if (needsSecondaryOpen || (hasPrimary && !inline)) View.VISIBLE else View.GONE)
    }

    private fun compactState(
        context: Context, appContext: Context, widgetId: Int, snapshot: WidgetSnapshot?,
        failure: Boolean, widthDp: Int, heightDp: Int, summary: String = "",
    ) = WidgetCompactRenderer.render(context, appContext, widgetId, snapshot, failure,
        widthDp, heightDp, summary, isAppDark(context, appContext))

    private fun headerPlan(context: Context, kind: WidgetKind?, widthDp: Int, total: Int?): HeaderPlan {
        val titleResource = when (kind) {
            WidgetKind.HABITS -> R.string.widget_habits_title
            WidgetKind.TASKS -> R.string.widget_tasks_title
            null -> R.string.app_name
        }
        val title = context.getString(titleResource)
        val navigationTitle = if (context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
            "‹ $title"
        } else {
            "$title ›"
        }
        val density = context.resources.displayMetrics.density
        val captureWidthDp = max(48, ceil(measureTextWidth(context,
            context.getString(R.string.widget_capture), 14f) / density).toInt() + 24)
        val titleWidthDp = ceil(measureTextWidth(context, navigationTitle, 18f, bold = true) / density).toInt() + 8
        val stacked = WidgetLayoutPolicy.needsStackedHeader(widthDp, titleWidthDp, captureWidthDp)
        val actionsFit = widthDp - 24 >= captureWidthDp + 48 + 8
        val titleBlockWidthDp = if (stacked) widthDp - 24 else widthDp - 24 - captureWidthDp - 48 - 16
        val titleTextWidthPx = ((titleBlockWidthDp - 8).coerceAtLeast(1) * density).toInt()
        val countCue = if (total != null && total > 0) {
            val countResource = when (kind) {
                WidgetKind.HABITS -> R.plurals.widget_habits_total
                else -> R.plurals.widget_tasks_total
            }
            bidiWrapped(context, context.resources.getQuantityString(countResource, total, total)).takeIf {
                measureTextWidth(context, it, 12f) <= titleTextWidthPx
            }
        } else null
        val titleHeight = measureTextHeight(context, navigationTitle, titleTextWidthPx, 18f, maxLines = 1, bold = true)
        val countHeight = countCue?.let { measureTextHeight(context, it, titleTextWidthPx, 12f, maxLines = 1) } ?: 0
        val titleBlockHeight = max(48, titleHeight + countHeight)
        val headerHeight = if (stacked) titleBlockHeight + 8 + 48 else titleBlockHeight
        return HeaderPlan(stacked, actionsFit, headerHeight, captureWidthDp, navigationTitle, countCue)
    }

    private fun viewAllDescription(
        context: Context,
        kind: WidgetKind?,
        total: Int,
        showDetails: Boolean,
    ): String {
        val label = when (kind) {
            WidgetKind.HABITS -> context.getString(R.string.widget_habits_view_all)
            WidgetKind.TASKS -> context.getString(R.string.widget_tasks_view_all)
            null -> context.getString(R.string.widget_data_open)
        }
        if (total <= 0) return label
        return if (showDetails) {
            bidiWrapped(context, context.getString(R.string.widget_view_all_count, label, total))
        } else {
            bidiWrapped(context, context.getString(R.string.widget_view_all_hidden_count, label, total))
        }
    }

    private fun collectionAccessibilityDescription(context: Context, kind: WidgetKind, total: Int): String =
        when (kind) {
            WidgetKind.HABITS -> context.resources.getQuantityString(
                R.plurals.widget_habits_collection_accessibility, total, total,
            )
            WidgetKind.TASKS -> context.resources.getQuantityString(
                R.plurals.widget_tasks_collection_accessibility, total, total,
            )
        }

    private fun useInlineFeedback(context: Context, widthDp: Int, text: String, result: WidgetActionResult?): Boolean {
        if (result == null || result.outcome !in setOf(WidgetOutcome.SAVED, WidgetOutcome.UNDONE)) return false
        val density = context.resources.displayMetrics.density
        val undoWidth = if (result.undoToken != null) max(48f,
            measureTextWidth(context, context.getString(R.string.widget_feedback_undo), 14f) / density + 12) else 0f
        return widthDp - 24 >= measureTextWidth(context, text, 14f) / density + undoWidth + 48 + 12
    }

    private fun destinationFor(kind: WidgetKind?) = when (kind) {
        WidgetKind.HABITS -> WidgetDestination.Habits
        WidgetKind.TASKS -> WidgetDestination.Tasks
        null -> WidgetDestination.Today
    }

    private fun measureHabitRow(
        context: Context,
        widthDp: Int,
        row: WidgetRow.Habit,
        progressColumnWidthPx: Int,
    ): MeasuredRow {
        val density = context.resources.displayMetrics.density
        val canLog = !row.skipped && row.count < row.target && row.actionToken != null
        val actionWidthDp = if (canLog) max(48f,
            measureTextWidth(context, context.getString(R.string.widget_habit_log_short), 14f) / density + 24f) else 0f
        val rawTextWidthDp = widthDp - 24 - actionWidthDp - if (canLog) 8 else 0
        val textWidthDp = rawTextWidthDp.coerceAtLeast(1f)
        val textWidthPx = (textWidthDp * density).toInt().coerceAtLeast(1)
        val count = habitProgressText(context, row)
        val countWidthDp = progressColumnWidthPx / density
        val inlineTitleWidthDp = (textWidthDp - countWidthDp - 8).coerceAtLeast(1f)
        val inline = measureTextWidth(context, count, 14f) <= progressColumnWidthPx &&
            measureTextWidth(context, row.title, 16f, bold = true) <= inlineTitleWidthDp * density
        val height = if (inline) {
            val titleHeight = measureTextHeight(context, row.title,
                (inlineTitleWidthDp * density).toInt(), 16f, maxLines = 1, bold = true)
            val countHeight = measureTextHeight(context, count,
                (countWidthDp * density).toInt(), 14f, maxLines = 1)
            max(48, max(titleHeight, countHeight))
        } else {
            val titleHeight = measureTextHeight(context, row.title, textWidthPx, 16f, maxLines = 2, bold = true)
            val countHeight = measureTextHeight(context, count, textWidthPx, 14f)
            max(48, titleHeight + countHeight)
        }
        return MeasuredRow(height, inline, progressColumnWidthPx, rawTextWidthDp >= 48f)
    }

    private fun measureTaskRow(
        context: Context,
        widthDp: Int,
        row: WidgetRow.Task,
        date: LocalDate,
    ): MeasuredRow {
        val density = context.resources.displayMetrics.density
        val canComplete = row.actionToken != null
        val actionText = context.getString(R.string.widget_task_complete)
        val actionWidthDp = if (canComplete) max(48f,
            measureTextWidth(context, actionText, 14f) / density + 16f) else 0f
        val rawTextWidthDp = widthDp - 24f - actionWidthDp - if (canComplete) 8f else 0f
        val textWidthPx = (rawTextWidthDp.coerceAtLeast(1f) * density).toInt()
        val titleHeight = measureTextHeight(context, row.title, textWidthPx, 16f, maxLines = 2, bold = true)
        val dueHeight = measureTextHeight(context, taskDueLabel(context, row, date), textWidthPx, 14f)
        return MeasuredRow(max(56, titleHeight + dueHeight), fitsWidth = rawTextWidthDp >= 48f)
    }

    private fun habitProgressText(context: Context, row: WidgetRow.Habit): String = when {
        row.skipped -> context.getString(R.string.widget_habit_skipped, row.count, row.target)
        row.count >= row.target -> context.getString(R.string.widget_habit_target_reached, row.count, row.target)
        else -> context.getString(R.string.widget_habit_count, row.count, row.target)
    }.let { bidiWrapped(context, it) }

    private fun bidiWrapped(context: Context, text: String): String =
        BidiFormatter.getInstance(AppLanguages.current(context).locale).unicodeWrap(text)

    internal fun measureTextWidth(context: Context, text: String, sizeSp: Float, bold: Boolean = false): Float {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textLocales = context.resources.configuration.locales
            if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
            textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                sizeSp,
                context.resources.displayMetrics,
            )
        }
        return paint.measureText(text)
    }

    private fun taskDueLabel(context: Context, row: WidgetRow.Task, date: LocalDate): String {
        if (row.dueDate == date) return context.getString(R.string.widget_task_due_today)
        val locale = AppLanguages.current(context).locale
        val dueDate = bidiWrapped(context,
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(row.dueDate))
        return if (row.dueDate.isBefore(date)) {
            context.getString(R.string.widget_task_overdue, dueDate)
        } else {
            context.getString(R.string.widget_task_due_date, dueDate)
        }
    }

    private fun openLabel(context: Context, kind: WidgetKind?) =
        context.getString(openLabelResource(kind))

    private fun openLabelResource(kind: WidgetKind?) =
        if (kind == WidgetKind.TASKS) R.string.widget_tasks_open else R.string.widget_data_open

    private fun openDescriptionResource(kind: WidgetKind?) =
        if (kind == WidgetKind.TASKS) R.string.widget_tasks_open_description else R.string.widget_data_open_description

    private fun measureFeedbackHeight(
        context: Context,
        widthDp: Int,
        text: String,
        result: WidgetActionResult,
        kind: WidgetKind,
    ): Int {
        val density = context.resources.displayMetrics.density
        val textWidthPx = ((widthDp - 24 - 20 - 48 - 4) * density).toInt().coerceAtLeast(1)
        val textHeight = max(48, measureTextHeight(context, text, textWidthPx, 14f))
        val actionWidthPx = ((widthDp - 32) * density / 2).toInt().coerceAtLeast(1)
        val actionLabels = listOfNotNull(
            result.undoToken?.let { context.getString(R.string.widget_feedback_undo) },
            result.retryToken?.let { context.getString(R.string.widget_feedback_retry) },
            if (result.outcome in setOf(
                    WidgetOutcome.STALE,
                    WidgetOutcome.CONFLICT,
                    WidgetOutcome.MISSING,
                    WidgetOutcome.INVALID,
                    WidgetOutcome.FAILED,
                )) openLabel(context, kind) else null,
        )
        val actionsHeight = if (actionLabels.isEmpty()) 0 else {
            max(48, actionLabels.maxOf { measureTextHeight(context, it, actionWidthPx, 14f) } + 12)
        }
        return 4 + textHeight + actionsHeight
    }

    internal fun measureTextHeight(
        context: Context,
        text: String,
        widthPx: Int,
        sizeSp: Float,
        maxLines: Int = Int.MAX_VALUE,
        bold: Boolean = false,
    ): Int {
        if (text.isEmpty()) return 0
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textLocales = context.resources.configuration.locales
            typeface = if (bold) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
            textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                sizeSp,
                context.resources.displayMetrics,
            )
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, widthPx.coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(android.text.TextDirectionHeuristics.FIRSTSTRONG_LTR)
            .setIncludePad(true)
            .setUseLineSpacingFromFallbacks(true)
            .setBreakStrategy(android.graphics.text.LineBreaker.BREAK_STRATEGY_HIGH_QUALITY)
            .setMaxLines(maxLines)
            .build()
        return ceil(layout.height / context.resources.displayMetrics.density).toInt()
    }

    private fun feedbackText(context: Context, outcome: WidgetOutcome, kind: WidgetKind): String = when (outcome) {
        WidgetOutcome.SAVED -> context.getString(if (kind == WidgetKind.TASKS) R.string.widget_task_saved else R.string.widget_habit_saved)
        WidgetOutcome.UNDONE -> context.getString(R.string.widget_feedback_undone)
        WidgetOutcome.STALE -> context.getString(R.string.widget_feedback_stale)
        WidgetOutcome.CONFLICT -> context.getString(R.string.widget_feedback_conflict)
        WidgetOutcome.MISSING -> context.getString(R.string.widget_feedback_missing)
        WidgetOutcome.INVALID -> context.getString(R.string.widget_feedback_unavailable)
        WidgetOutcome.FAILED -> context.getString(R.string.widget_feedback_failed)
    }

    private fun isAppDark(context: Context, appContext: Context): Boolean {
        val preference = context.getSharedPreferences("app_theme", Context.MODE_PRIVATE)
            .getString(THEME_KEY, "system")
        return when (preference) {
            "dark" -> true
            "light" -> false
            else -> (appContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        }
    }
}
