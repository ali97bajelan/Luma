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
    fun render(
        context: Context,
        widgetId: Int,
        snapshot: WidgetSnapshot?,
        failure: Boolean,
        options: android.os.Bundle? = null,
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
        return renderAtSize(context, widgetId, snapshot, failure, bounds.first, bounds.second)
    }

    private fun renderAtSize(
        context: Context,
        widgetId: Int,
        snapshot: WidgetSnapshot?,
        failure: Boolean,
        widthDp: Int,
        heightDp: Int,
    ): RemoteViews {
        val appContext = AppLanguages.wrap(context)
        val kind = snapshot?.instance?.kind ?: WidgetRefreshCoordinator.kindForWidgetId(context, widgetId)
        val views = RemoteViews(context.packageName, R.layout.widget_data)
        val dark = isAppDark(context, appContext)
        val textColor = appContext.getColor(if (dark) R.color.widget_on_primary_dark else R.color.widget_on_primary)
        val buttonBackground = if (dark) R.drawable.widget_button_background_dark
        else R.drawable.widget_button_background
        val widgetBackground = if (dark) R.drawable.widget_background_dark else R.drawable.widget_background

        // AppWidgetHost may reapply this layout to an existing view. Dynamic rows must be
        // cleared first or a completed task remains visible after its snapshot removes it.
        views.removeAllViews(R.id.widget_data_rows)
        views.setInt(R.id.widget_data_root, "setLayoutDirection", appContext.resources.configuration.layoutDirection)
        views.setInt(R.id.widget_data_root, "setBackgroundResource", widgetBackground)
        listOf(
            R.id.widget_data_title,
            R.id.widget_data_date,
            R.id.widget_data_summary,
            R.id.widget_data_feedback,
            R.id.widget_feedback_dismiss,
            R.id.widget_feedback_primary,
            R.id.widget_feedback_secondary,
            R.id.widget_data_updated,
            R.id.widget_data_open,
            R.id.widget_data_refresh,
        ).forEach { views.setTextColor(it, textColor) }
        listOf(
            R.id.widget_feedback_dismiss,
            R.id.widget_feedback_primary,
            R.id.widget_feedback_secondary,
            R.id.widget_data_open,
            R.id.widget_data_refresh,
        ).forEach { views.setInt(it, "setBackgroundResource", buttonBackground) }

        val (title, destination) = when (kind) {
            WidgetKind.HABITS -> R.string.widget_habits_title to WidgetDestination.Habits
            WidgetKind.TASKS -> R.string.widget_tasks_title to WidgetDestination.Tasks
            null -> R.string.app_name to WidgetDestination.Today
        }
        views.setTextViewText(R.id.widget_data_title, appContext.getString(title))
        views.setTextViewText(R.id.widget_data_open, openLabel(appContext, kind))
        views.setContentDescription(
            R.id.widget_data_open,
            openDescription(appContext, kind),
        )
        views.setOnClickPendingIntent(R.id.widget_data_open, WidgetIntents.navigation(context, widgetId, destination))
        val secondaryLabel = if (kind == WidgetKind.TASKS) {
            appContext.getString(R.string.widget_tasks_add)
        } else {
            appContext.getString(R.string.widget_data_refresh)
        }
        val secondaryDescription = if (kind == WidgetKind.TASKS) {
            appContext.getString(R.string.widget_tasks_add_description)
        } else {
            appContext.getString(R.string.widget_data_refresh_description)
        }
        views.setTextViewText(R.id.widget_data_refresh, secondaryLabel)
        views.setContentDescription(
            R.id.widget_data_refresh,
            secondaryDescription,
        )
        views.setOnClickPendingIntent(
            R.id.widget_data_refresh,
            if (kind == WidgetKind.TASKS) {
                WidgetIntents.navigation(context, widgetId, WidgetDestination.NewTask)
            } else {
                WidgetIntents.refresh(context, widgetId)
            },
        )

        if (failure) {
            views.setTextViewText(R.id.widget_data_date, "")
            views.setTextViewText(R.id.widget_data_summary, "")
            views.setTextViewText(R.id.widget_data_updated, "")
            views.setViewVisibility(R.id.widget_feedback_container, View.VISIBLE)
            views.setTextViewText(R.id.widget_data_feedback, appContext.getString(R.string.widget_data_read_error))
            views.setViewVisibility(R.id.widget_feedback_icon, View.VISIBLE)
            views.setInt(
                R.id.widget_feedback_icon,
                "setColorFilter",
                appContext.getColor(if (isAppDark(context, appContext)) R.color.widget_on_primary_dark else R.color.widget_on_primary),
            )
            views.setViewVisibility(R.id.widget_feedback_dismiss, View.GONE)
            views.setViewVisibility(R.id.widget_feedback_actions, View.GONE)
        } else if (snapshot == null) {
            views.setTextViewText(R.id.widget_data_date, "")
            views.setTextViewText(R.id.widget_data_summary, appContext.getString(R.string.widget_data_setup))
            views.setTextViewText(R.id.widget_data_updated, "")
            views.setViewVisibility(R.id.widget_feedback_container, View.GONE)
        } else {
            renderSnapshot(context, appContext, views, widgetId, snapshot, widthDp, heightDp, textColor, buttonBackground)
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
    ) {
        val locale = AppLanguages.current(appContext).locale
        val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(snapshot.date)
        val deviceZone = ZoneId.systemDefault()
        val dateText = if (snapshot.zone != deviceZone) {
            val zoneName = BidiFormatter.getInstance(locale).unicodeWrap(snapshot.zone.id)
            appContext.getString(R.string.widget_data_date_with_zone, date, zoneName)
        } else {
            date
        }
        views.setTextViewText(R.id.widget_data_date, dateText)

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
        views.setTextViewText(R.id.widget_data_summary, summary)
        val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
            .format(snapshot.updatedAt.atZone(snapshot.zone))
        views.setTextViewText(
            R.id.widget_data_updated,
            appContext.getString(R.string.widget_data_updated_at, date, time),
        )

        val feedbackText = snapshot.feedback?.let { feedbackText(appContext, it.outcome) }.orEmpty()
        renderFeedback(context, appContext, views, widgetId, snapshot, textColor, buttonBackground, feedbackText)

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
        val updatedText = appContext.getString(R.string.widget_data_updated_at, date, time)
        val titleText = appContext.getString(
            if (snapshot.instance.kind == WidgetKind.TASKS) R.string.widget_tasks_title else R.string.widget_habits_title,
        )
        val reserved = 24 + 8 +
            measureTextHeight(appContext, titleText, textWidthPx, 18f) +
            measureTextHeight(appContext, dateText, textWidthPx, 14f) +
            measureTextHeight(appContext, summary, textWidthPx, 16f) +
            measureTextHeight(appContext, updatedText, textWidthPx, 12f) +
            measureFooterHeight(appContext, widthDp, listOf(
                openLabel(appContext, snapshot.instance.kind),
                if (snapshot.instance.kind == WidgetKind.TASKS) {
                    appContext.getString(R.string.widget_tasks_add)
                } else {
                    appContext.getString(R.string.widget_data_refresh)
                },
            )) +
            (snapshot.feedback?.let {
                measureFeedbackHeight(appContext, widthDp, feedbackText, it, snapshot.instance.kind)
            } ?: 0)
        val rowHeight = rows.maxOfOrNull { row ->
            when (row) {
                is WidgetRow.Habit -> measureHabitRowHeight(appContext, widthDp, row)
                is WidgetRow.Task -> measureTaskRowHeight(appContext, widthDp, row, snapshot.date)
            }
        } ?: 52
        val capacity = WidgetLayoutPolicy.rowCapacity(widthDp, heightDp, fontScale, reserved, rowHeight)
        rows.take(capacity).forEach { row ->
            val rowViews = when (row) {
                is WidgetRow.Habit -> habitRow(context, appContext, snapshot, widgetId, row, textColor, buttonBackground)
                is WidgetRow.Task -> taskRow(context, appContext, snapshot, widgetId, row, textColor, buttonBackground)
            }
            views.addView(R.id.widget_data_rows, rowViews)
        }
    }

    private fun habitRow(
        context: Context,
        appContext: Context,
        snapshot: WidgetSnapshot,
        widgetId: Int,
        row: WidgetRow.Habit,
        textColor: Int,
        buttonBackground: Int,
    ) = RemoteViews(context.packageName, R.layout.widget_habit_row).apply {
        setTextColor(R.id.widget_habit_row_title, textColor)
        setTextColor(R.id.widget_habit_row_count, textColor)
        setTextColor(R.id.widget_habit_row_action, textColor)
        setInt(R.id.widget_habit_row_action, "setBackgroundResource", buttonBackground)
        setTextViewText(R.id.widget_habit_row_title, row.title)
        setContentDescription(
            R.id.widget_habit_row_title,
            appContext.getString(R.string.widget_habit_open_description, row.title),
        )
        setOnClickPendingIntent(
            R.id.widget_habit_row_title,
            WidgetIntents.navigation(
                context,
                widgetId,
                WidgetDestination.HabitDetail(row.id, snapshot.date),
            ),
        )
        val count = when {
            row.skipped -> appContext.getString(R.string.widget_habit_skipped, row.count, row.target)
            row.count >= row.target -> appContext.getString(R.string.widget_habit_target_reached, row.count, row.target)
            else -> appContext.getString(R.string.widget_habit_count, row.count, row.target)
        }
        setTextViewText(R.id.widget_habit_row_count, count)

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
            R.id.widget_task_row_title,
            appContext.getString(R.string.widget_task_open_description, row.title),
        )
        setOnClickPendingIntent(
            R.id.widget_task_row_title,
            WidgetIntents.navigation(context, widgetId, WidgetDestination.Task(row.id)),
        )
        setTextViewText(R.id.widget_task_row_due, taskDueLabel(appContext, row, snapshot.date))

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
    ) {
        val feedback = snapshot.feedback
        if (feedback == null) {
            views.setViewVisibility(R.id.widget_feedback_container, View.GONE)
            return
        }
        views.setViewVisibility(R.id.widget_feedback_container, View.VISIBLE)
        views.setTextViewText(R.id.widget_data_feedback, text)
        views.setViewVisibility(R.id.widget_feedback_dismiss, View.VISIBLE)
        views.setContentDescription(
            R.id.widget_feedback_dismiss,
            appContext.getString(R.string.widget_feedback_dismiss_description),
        )
        views.setInt(R.id.widget_feedback_dismiss, "setBackgroundResource", buttonBackground)
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
                    R.string.widget_feedback_retry_description,
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
    }

    private fun destinationFor(kind: WidgetKind) = when (kind) {
        WidgetKind.HABITS -> WidgetDestination.Habits
        WidgetKind.TASKS -> WidgetDestination.Tasks
    }

    private fun measureHabitRowHeight(context: Context, widthDp: Int, row: WidgetRow.Habit): Int {
        val density = context.resources.displayMetrics.density
        val textWidthPx = ((widthDp - 24) * density * 0.64f).toInt().coerceAtLeast(1)
        val titleHeight = measureTextHeight(context, row.title, textWidthPx, 16f, maxLines = 2)
        val count = when {
            row.skipped -> context.getString(R.string.widget_habit_skipped, row.count, row.target)
            row.count >= row.target -> context.getString(R.string.widget_habit_target_reached, row.count, row.target)
            else -> context.getString(R.string.widget_habit_count, row.count, row.target)
        }
        val countHeight = measureTextHeight(context, count, textWidthPx, 14f)
        return max(48, titleHeight + countHeight + 8)
    }

    private fun measureTaskRowHeight(context: Context, widthDp: Int, row: WidgetRow.Task, date: LocalDate): Int {
        val density = context.resources.displayMetrics.density
        val actionText = context.getString(R.string.widget_task_complete)
        val actionWidthDp = max(48f, measureTextWidth(context, actionText, 14f) / density + 16f)
        val textWidthPx = ((widthDp - 24f - actionWidthDp - 8f) * density).toInt().coerceAtLeast(1)
        val titleHeight = measureTextHeight(context, row.title, textWidthPx, 16f, maxLines = 2)
        val dueHeight = measureTextHeight(context, taskDueLabel(context, row, date), textWidthPx, 14f)
        return max(48, titleHeight + dueHeight + 8)
    }

    private fun measureTextWidth(context: Context, text: String, sizeSp: Float): Float {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
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

    private fun openDescription(context: Context, kind: WidgetKind?) =
        context.getString(openDescriptionResource(kind))

    private fun openLabelResource(kind: WidgetKind?) =
        if (kind == WidgetKind.TASKS) R.string.widget_tasks_open else R.string.widget_data_open

    private fun openDescriptionResource(kind: WidgetKind?) =
        if (kind == WidgetKind.TASKS) R.string.widget_tasks_open_description else R.string.widget_data_open_description

    private fun measureFooterHeight(context: Context, widthDp: Int, labels: List<String>): Int {
        val buttonWidthPx = ((widthDp - 32) * context.resources.displayMetrics.density / 2).toInt().coerceAtLeast(1)
        val labelsHeight = labels.maxOf { measureTextHeight(context, it, buttonWidthPx, 14f) }
        return max(48, labelsHeight + 12) + 8
    }

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

    private fun measureTextHeight(
        context: Context,
        text: String,
        widthPx: Int,
        sizeSp: Float,
        maxLines: Int = Int.MAX_VALUE,
    ): Int {
        if (text.isEmpty()) return 0
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                sizeSp,
                context.resources.displayMetrics,
            )
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, widthPx.coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(true)
            .setMaxLines(maxLines)
            .build()
        return ceil(layout.height / context.resources.displayMetrics.density).toInt()
    }

    private fun feedbackText(context: Context, outcome: WidgetOutcome): String = when (outcome) {
        WidgetOutcome.SAVED -> context.getString(R.string.widget_feedback_saved)
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
