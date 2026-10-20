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
import com.luma.lifeos.language.AppLanguages
import com.luma.lifeos.ui.theme.THEME_KEY
import java.time.LocalDate
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
    )

    private data class MeasuredRow(val heightDp: Int, val inlineHabit: Boolean = false)

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
        val header = headerPlan(appContext, kind, widthDp)
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
        val textColor = appContext.getColor(if (dark) R.color.widget_on_primary_dark else R.color.widget_on_primary)
        val buttonBackground = if (dark) R.drawable.widget_button_background_dark
        else R.drawable.widget_button_background
        val widgetBackground = if (dark) R.drawable.widget_background_dark else R.drawable.widget_background

        // AppWidgetHost may reapply this layout to an existing view. Dynamic rows must be
        // cleared first or a completed task remains visible after its snapshot removes it.
        views.removeAllViews(R.id.widget_data_rows)
        views.removeAllViews(R.id.widget_data_action_slot)
        views.removeAllViews(R.id.widget_feedback_slot)
        views.setViewVisibility(R.id.widget_data_date, View.GONE)
        views.setViewVisibility(R.id.widget_data_updated, if (snapshot == null || failure) View.GONE else View.VISIBLE)
        views.setInt(R.id.widget_data_root, "setLayoutDirection", appContext.resources.configuration.layoutDirection)
        views.setInt(R.id.widget_data_root, "setBackgroundResource", widgetBackground)
        listOf(R.id.widget_data_title, R.id.widget_data_more, R.id.widget_data_date, R.id.widget_data_summary,
            R.id.widget_data_updated, R.id.widget_data_refresh).forEach { views.setTextColor(it, textColor) }
        views.setInt(R.id.widget_data_refresh, "setBackgroundResource", buttonBackground)
        views.setInt(R.id.widget_data_open, "setBackgroundResource",
            if (dark) R.drawable.widget_utility_background_dark else R.drawable.widget_utility_background)
        views.setInt(R.id.widget_header_refresh, "setColorFilter", textColor)
        views.setContentDescription(R.id.widget_header_refresh, appContext.getString(
            if (kind == WidgetKind.TASKS) R.string.widget_tasks_refresh_description else R.string.widget_habits_refresh_description))
        views.setOnClickPendingIntent(R.id.widget_header_refresh, WidgetIntents.refresh(context, widgetId))
        val destination = destinationFor(kind)
        views.setTextViewText(R.id.widget_data_title, header.title)
        views.setTextViewText(R.id.widget_data_more, "")
        views.setViewVisibility(R.id.widget_data_more, View.GONE)
        views.setContentDescription(R.id.widget_data_open, viewAllDescription(appContext, kind, 0, showDetails = true))
        views.setOnClickPendingIntent(
            R.id.widget_data_open,
            if (snapshot == null && !failure) WidgetIntents.configuration(context, widgetId)
            else WidgetIntents.navigation(context, widgetId, destination),
        )
        views.setTextViewText(R.id.widget_data_refresh, appContext.getString(R.string.widget_capture))
        views.setContentDescription(R.id.widget_data_refresh,
            appContext.getString(R.string.widget_open_quick_capture_description))
        views.setOnClickPendingIntent(R.id.widget_data_refresh,
            WidgetIntents.navigation(context, widgetId, WidgetDestination.QuickCapture))
        views.setViewVisibility(R.id.widget_data_refresh, View.VISIBLE)
        if (failure) {
            views.setTextViewText(R.id.widget_data_date, "")
            views.setTextViewText(R.id.widget_data_summary, "")
            views.setTextViewText(R.id.widget_data_updated, "")
            views.setTextViewText(R.id.widget_data_summary, appContext.getString(R.string.widget_data_read_error))
            views.setContentDescription(R.id.widget_data_open, viewAllDescription(appContext, kind, 0, showDetails = true))
        } else if (snapshot == null) {
            views.setTextViewText(R.id.widget_data_date, "")
            views.setTextViewText(R.id.widget_data_summary, appContext.getString(R.string.widget_data_setup))
            views.setTextViewText(R.id.widget_data_updated, "")
            views.setContentDescription(R.id.widget_data_open, appContext.getString(R.string.widget_data_setup))
        } else {
            return renderSnapshot(context, appContext, views, widgetId, snapshot, widthDp, heightDp, textColor, buttonBackground)
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
        buttonBackground: Int,
    ): RemoteViews {
        val locale = AppLanguages.current(appContext).locale
        val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(snapshot.date)
        val deviceZone = ZoneId.systemDefault()
        val dateText = if (snapshot.zone != deviceZone) {
            val zoneName = BidiFormatter.getInstance(locale).unicodeWrap(snapshot.zone.id)
            appContext.getString(R.string.widget_data_date_with_zone, date, zoneName)
        } else {
            date
        }
        views.setViewVisibility(R.id.widget_data_date, View.GONE)

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
        val scopeText = "$dateText · $summary"
        views.setTextViewText(R.id.widget_data_summary, scopeText)
        val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
            .format(snapshot.updatedAt.atZone(snapshot.zone))
        val updatedDate = snapshot.updatedAt.atZone(snapshot.zone).toLocalDate()
        val updatedText = if (updatedDate == snapshot.date) {
            appContext.getString(R.string.widget_data_updated_time, time)
        } else {
            appContext.getString(R.string.widget_data_updated_at,
                DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(updatedDate), time)
        }
        views.setTextViewText(R.id.widget_data_updated, updatedText)
        val feedbackText = snapshot.feedback?.let { feedbackText(appContext, it.outcome, snapshot.instance.kind) }.orEmpty()
        val inlineFeedback = useInlineFeedback(appContext, widthDp, feedbackText, snapshot.feedback)

        val rows: List<WidgetRow> = if (snapshot.instance.showDetails) {
            when (snapshot.instance.kind) {
                WidgetKind.HABITS -> snapshot.rows.filterIsInstance<WidgetRow.Habit>()
                WidgetKind.TASKS -> snapshot.rows.filterIsInstance<WidgetRow.Task>()
            }.take(WidgetLayoutPolicy.MAX_ROWS)
        } else {
            emptyList()
        }
        val density = appContext.resources.displayMetrics.density
        val textWidthPx = ((widthDp - 24).coerceAtLeast(1) * density).toInt()
        val fontScale = appContext.resources.configuration.fontScale
        val header = headerPlan(appContext, snapshot.instance.kind, widthDp)
        val emptyActionHeight = if (!snapshot.hasAnyItems) 52 else 0
        val reserved = 24 + header.heightDp +
            measureTextHeight(appContext, scopeText, textWidthPx, 14f) + 4 +
            measureTextHeight(appContext, updatedText, textWidthPx, 12f) +
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
                setTextColor(R.id.widget_data_empty_action, textColor)
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
        renderFeedback(context, appContext, feedbackViews, widgetId, snapshot, textColor, buttonBackground, feedbackText, inlineFeedback)
        views.addView(R.id.widget_feedback_slot, feedbackViews)
        val measurements = rows.map { row ->
            when (row) {
                is WidgetRow.Habit -> measureHabitRow(appContext, widthDp, row)
                is WidgetRow.Task -> MeasuredRow(measureTaskRowHeight(appContext, widthDp, row, snapshot.date))
            }
        }
        val capacity = WidgetLayoutPolicy.rowCapacity(
            widthDp, heightDp, fontScale, reserved, measurements.map { it.heightDp },
        )
        val visibleRows = rows.take(capacity)
        val hidden = (snapshot.total - visibleRows.size).coerceAtLeast(0)
        views.setContentDescription(R.id.widget_data_open,
            viewAllDescription(appContext, snapshot.instance.kind, hidden, snapshot.instance.showDetails))
        val moreCue = if (snapshot.instance.showDetails && hidden > 0) {
            appContext.resources.getQuantityString(R.plurals.widget_more_count, hidden, hidden)
        } else ""
        val titleWidthDp = if (header.stacked) widthDp - 24 else widthDp - 24 - header.captureWidthDp - 48 - 16
        val cueWidthDp = if (moreCue.isEmpty()) 0f else measureTextWidth(appContext, moreCue, 12f) / density + 8
        val titleWidthNeededDp = measureTextWidth(appContext, header.title, 18f, bold = true) / density + cueWidthDp
        views.setTextViewText(R.id.widget_data_more, moreCue)
        views.setViewVisibility(R.id.widget_data_more,
            if (moreCue.isNotEmpty() && titleWidthNeededDp <= titleWidthDp) View.VISIBLE else View.GONE)
        visibleRows.forEachIndexed { index, row ->
            val rowViews = when (row) {
                is WidgetRow.Habit -> habitRow(context, appContext, snapshot, widgetId, row, textColor, buttonBackground,
                    measurements[index].inlineHabit)
                is WidgetRow.Task -> taskRow(context, appContext, snapshot, widgetId, row, textColor, buttonBackground)
            }
            views.addView(R.id.widget_data_rows, rowViews)
        }
        return views
    }

    private fun habitRow(
        context: Context,
        appContext: Context,
        snapshot: WidgetSnapshot,
        widgetId: Int,
        row: WidgetRow.Habit,
        textColor: Int,
        buttonBackground: Int,
        inline: Boolean,
    ) = RemoteViews(context.packageName,
        if (inline) R.layout.widget_habit_row_inline else R.layout.widget_habit_row).apply {
        setTextColor(R.id.widget_habit_row_title, textColor)
        setTextColor(R.id.widget_habit_row_count, textColor)
        setTextColor(R.id.widget_habit_row_action, textColor)
        setInt(R.id.widget_habit_row_action, "setBackgroundResource", buttonBackground)
        setTextViewText(R.id.widget_habit_row_title, row.title)
        setContentDescription(
            R.id.widget_habit_row_text,
            appContext.getString(R.string.widget_habit_open_description, row.title),
        )
        setOnClickPendingIntent(
            R.id.widget_habit_row_text,
            WidgetIntents.navigation(
                context,
                widgetId,
                WidgetDestination.HabitDetail(row.id, snapshot.date),
            ),
        )
        val count = habitProgressText(appContext, row)
        setTextViewText(R.id.widget_habit_row_count, count)
        setContentDescription(R.id.widget_habit_row_text,
            appContext.getString(R.string.widget_habit_open_description, row.title) + ", " + count)

        val canLog = !row.skipped && row.count < row.target && row.actionToken != null
        setViewVisibility(R.id.widget_habit_row_action, if (canLog) View.VISIBLE else View.GONE)
        if (canLog) {
            setTextViewText(R.id.widget_habit_row_action, appContext.getString(R.string.widget_habit_log_short))
            setContentDescription(
                R.id.widget_habit_row_action,
                appContext.getString(R.string.widget_habit_log_description, row.title),
            )
            setOnClickPendingIntent(
                R.id.widget_habit_row_action,
                WidgetIntents.mutation(context, widgetId, requireNotNull(row.actionToken)),
            )
        }
    }

    private fun taskRow(
        context: Context,
        appContext: Context,
        snapshot: WidgetSnapshot,
        widgetId: Int,
        row: WidgetRow.Task,
        textColor: Int,
        buttonBackground: Int,
    ) = RemoteViews(context.packageName, R.layout.widget_task_row).apply {
        setTextColor(R.id.widget_task_row_title, textColor)
        setTextColor(R.id.widget_task_row_due, textColor)
        setTextColor(R.id.widget_task_row_action, textColor)
        setInt(R.id.widget_task_row_action, "setBackgroundResource", buttonBackground)
        setTextViewText(R.id.widget_task_row_title, row.title)
        setContentDescription(
            R.id.widget_task_row_text,
            appContext.getString(R.string.widget_task_open_description, row.title),
        )
        setOnClickPendingIntent(
            R.id.widget_task_row_text,
            WidgetIntents.navigation(context, widgetId, WidgetDestination.Task(row.id)),
        )
        setTextViewText(R.id.widget_task_row_due, taskDueLabel(appContext, row, snapshot.date))
        setContentDescription(R.id.widget_task_row_text,
            appContext.getString(R.string.widget_task_open_description, row.title) + ", " + taskDueLabel(appContext, row, snapshot.date))

        val canComplete = row.actionToken != null
        setViewVisibility(R.id.widget_task_row_action, if (canComplete) View.VISIBLE else View.GONE)
        if (canComplete) {
            setTextViewText(R.id.widget_task_row_action, appContext.getString(R.string.widget_task_complete))
            setContentDescription(
                R.id.widget_task_row_action,
                appContext.getString(R.string.widget_task_complete_description, row.title),
            )
            setOnClickPendingIntent(
                R.id.widget_task_row_action,
                WidgetIntents.mutation(context, widgetId, requireNotNull(row.actionToken)),
            )
        }
    }

    private fun renderFeedback(
        context: Context,
        appContext: Context,
        views: RemoteViews,
        widgetId: Int,
        snapshot: WidgetSnapshot,
        textColor: Int,
        buttonBackground: Int,
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
        views.setTextColor(R.id.widget_data_feedback, textColor)
        views.setTextColor(R.id.widget_feedback_dismiss, textColor)
        views.setViewVisibility(R.id.widget_feedback_dismiss, View.VISIBLE)
        views.setContentDescription(
            R.id.widget_feedback_dismiss,
            appContext.getString(R.string.widget_feedback_dismiss_description),
        )
        views.setInt(R.id.widget_feedback_dismiss, "setBackgroundResource",
            if (isAppDark(context, appContext)) R.drawable.widget_utility_background_dark else R.drawable.widget_utility_background)
        views.setOnClickPendingIntent(
            R.id.widget_feedback_dismiss,
            WidgetIntents.dismissFeedback(context, widgetId),
        )

        val hasIssue = feedback.outcome !in setOf(WidgetOutcome.SAVED, WidgetOutcome.UNDONE)
        views.setViewVisibility(R.id.widget_feedback_icon, if (hasIssue) View.VISIBLE else View.GONE)
        if (hasIssue) views.setInt(R.id.widget_feedback_icon, "setColorFilter", textColor)

        fun action(viewId: Int, label: Int, description: Int, pendingIntent: android.app.PendingIntent) {
            views.setViewVisibility(viewId, View.VISIBLE)
            views.setTextViewText(viewId, appContext.getString(label))
            views.setContentDescription(viewId, appContext.getString(description))
            views.setTextColor(viewId, textColor)
            views.setInt(viewId, "setBackgroundResource", buttonBackground)
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

    private fun headerPlan(context: Context, kind: WidgetKind?, widthDp: Int): HeaderPlan {
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
        return HeaderPlan(stacked, actionsFit, if (stacked) 100 else 48, captureWidthDp, navigationTitle)
    }

    private fun viewAllDescription(
        context: Context,
        kind: WidgetKind?,
        hidden: Int,
        showDetails: Boolean,
    ): String {
        val label = when (kind) {
            WidgetKind.HABITS -> context.getString(R.string.widget_habits_view_all)
            WidgetKind.TASKS -> context.getString(R.string.widget_tasks_view_all)
            null -> context.getString(R.string.widget_data_open)
        }
        return when {
            !showDetails -> context.getString(R.string.widget_view_all_hidden, label)
            hidden > 0 -> context.getString(R.string.widget_view_all_more, label, hidden)
            else -> label
        }
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

    private fun measureHabitRow(context: Context, widthDp: Int, row: WidgetRow.Habit): MeasuredRow {
        val density = context.resources.displayMetrics.density
        val canLog = !row.skipped && row.count < row.target && row.actionToken != null
        val actionWidthDp = if (canLog) max(48f,
            measureTextWidth(context, context.getString(R.string.widget_habit_log_short), 14f) / density + 24f) else 0f
        val textWidthDp = (widthDp - 24 - actionWidthDp - 8).coerceAtLeast(1f)
        val textWidthPx = (textWidthDp * density).toInt().coerceAtLeast(1)
        val count = habitProgressText(context, row)
        val countWidthDp = measureTextWidth(context, count, 14f) / density
        val inlineTitleWidthDp = (textWidthDp - countWidthDp - 8).coerceAtLeast(1f)
        val inline = measureTextWidth(context, row.title, 16f, bold = true) <= inlineTitleWidthDp * density
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
        return MeasuredRow(height, inline)
    }

    private fun measureTaskRowHeight(context: Context, widthDp: Int, row: WidgetRow.Task, date: LocalDate): Int {
        val density = context.resources.displayMetrics.density
        val actionText = context.getString(R.string.widget_task_complete)
        val actionWidthDp = max(48f, measureTextWidth(context, actionText, 14f) / density + 16f)
        val textWidthPx = ((widthDp - 24f - actionWidthDp - 8f) * density).toInt().coerceAtLeast(1)
        val titleHeight = measureTextHeight(context, row.title, textWidthPx, 16f, maxLines = 2, bold = true)
        val dueHeight = measureTextHeight(context, taskDueLabel(context, row, date), textWidthPx, 14f)
        return max(48, titleHeight + dueHeight)
    }

    private fun habitProgressText(context: Context, row: WidgetRow.Habit): String = when {
        row.skipped -> context.getString(R.string.widget_habit_skipped, row.count, row.target)
        row.count >= row.target -> context.getString(R.string.widget_habit_target_reached, row.count, row.target)
        else -> context.getString(R.string.widget_habit_count, row.count, row.target)
    }

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
        val dueDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(row.dueDate)
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
