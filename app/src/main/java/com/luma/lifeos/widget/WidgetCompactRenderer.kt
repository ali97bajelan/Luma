package com.luma.lifeos.widget

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.luma.lifeos.R
import com.luma.lifeos.domain.widget.WidgetKind
import com.luma.lifeos.domain.widget.WidgetOutcome
import com.luma.lifeos.domain.widget.WidgetSnapshot

/** A complete recovery shell: optional navigation yields before the primary action does. */
internal object WidgetCompactRenderer {
    fun render(
        context: Context,
        appContext: Context,
        widgetId: Int,
        snapshot: WidgetSnapshot?,
        failure: Boolean,
        widthDp: Int,
        heightDp: Int,
        summary: String,
        dark: Boolean,
        loadingKind: WidgetKind? = null,
    ): RemoteViews {
        val kind = loadingKind ?: snapshot?.instance?.kind ?: WidgetRefreshCoordinator.kindForWidgetId(context, widgetId)
        val destination = if (kind == WidgetKind.TASKS) WidgetDestination.Tasks else WidgetDestination.Habits
        val open = if (kind == WidgetKind.TASKS) R.string.widget_tasks_open else R.string.widget_habits_view_all
        val feedback = snapshot?.feedback.takeUnless { failure }
        val fullMessage = when {
            loadingKind != null -> appContext.getString(if (loadingKind == WidgetKind.HABITS) R.string.widget_loading_habits else R.string.widget_loading_tasks)
            failure -> appContext.getString(R.string.widget_data_read_error)
            snapshot == null -> appContext.getString(R.string.widget_data_setup)
            feedback != null -> appContext.getString(when (feedback.outcome) {
                WidgetOutcome.SAVED -> if (kind == WidgetKind.TASKS) R.string.widget_task_saved else R.string.widget_habit_saved
                WidgetOutcome.UNDONE -> R.string.widget_feedback_undone
                WidgetOutcome.STALE -> R.string.widget_feedback_stale
                WidgetOutcome.FAILED -> R.string.widget_feedback_failed
                WidgetOutcome.CONFLICT -> R.string.widget_feedback_conflict
                WidgetOutcome.MISSING -> R.string.widget_feedback_missing
                WidgetOutcome.INVALID -> R.string.widget_feedback_unavailable
            })
            else -> summary
        }
        val emptyLibraryAction = if (snapshot?.hasAnyItems == false && !failure && feedback == null) {
            when (kind) {
                WidgetKind.HABITS -> R.string.widget_habits_add to
                    WidgetIntents.navigation(context, widgetId, WidgetDestination.NewHabit)
                WidgetKind.TASKS -> R.string.widget_tasks_add to
                    WidgetIntents.navigation(context, widgetId, WidgetDestination.NewTask)
                null -> null
            }
        } else null
        val primary = when {
            loadingKind != null -> R.string.widget_data_open to WidgetIntents.navigation(context, widgetId, destination)
            failure -> R.string.widget_data_refresh to WidgetIntents.refresh(context, widgetId)
            snapshot == null -> R.string.widget_set_up to WidgetIntents.configuration(context, widgetId)
            feedback?.outcome == WidgetOutcome.SAVED && feedback.undoToken != null ->
                R.string.widget_feedback_undo to WidgetIntents.mutation(context, widgetId, requireNotNull(feedback.undoToken))
            feedback?.outcome == WidgetOutcome.UNDONE ->
                R.string.widget_dismiss to WidgetIntents.dismissFeedback(context, widgetId)
            feedback?.outcome == WidgetOutcome.STALE ->
                R.string.widget_data_refresh to WidgetIntents.refresh(context, widgetId)
            feedback?.outcome == WidgetOutcome.FAILED && feedback.retryToken != null ->
                R.string.widget_feedback_retry to WidgetIntents.mutation(context, widgetId, requireNotNull(feedback.retryToken))
            emptyLibraryAction != null -> emptyLibraryAction
            else -> R.string.widget_data_open to WidgetIntents.navigation(context, widgetId, destination)
        }
        val secondary = when {
            snapshot == null && !failure -> R.string.widget_open_luma to WidgetIntents.navigation(context, widgetId, WidgetDestination.Today)
            emptyLibraryAction != null -> open to WidgetIntents.navigation(context, widgetId, destination)
            feedback?.outcome == WidgetOutcome.STALE || (feedback?.outcome == WidgetOutcome.FAILED && feedback.retryToken != null) ->
                open to WidgetIntents.navigation(context, widgetId, destination)
            feedback != null && feedback.outcome != WidgetOutcome.UNDONE ->
                R.string.widget_dismiss to WidgetIntents.dismissFeedback(context, widgetId)
            snapshot != null && !failure && feedback == null ->
                R.string.widget_data_refresh to WidgetIntents.refresh(context, widgetId)
            else -> open to WidgetIntents.navigation(context, widgetId, destination)
        }
        val capture = R.string.widget_capture to
            WidgetIntents.navigation(context, widgetId, WidgetDestination.QuickCapture)
        val density = appContext.resources.displayMetrics.density
        val primaryWidth = maxOf(48, (WidgetRenderer.measureTextWidth(appContext,
            appContext.getString(primary.first), 14f) / density).toInt() + 12)
        val secondaryWidth = maxOf(48, (WidgetRenderer.measureTextWidth(appContext,
            appContext.getString(secondary.first), 14f) / density).toInt() + 12)
        val captureWidth = maxOf(48, (WidgetRenderer.measureTextWidth(appContext,
            appContext.getString(capture.first), 14f) / density).toInt() + 12)
        val locale = com.luma.lifeos.language.AppLanguages.current(appContext).locale
        val bidi = android.text.BidiFormatter.getInstance(locale)
        val shortDate = snapshot?.let {
            bidi.unicodeWrap(java.time.format.DateTimeFormatter
                .ofLocalizedDate(java.time.format.FormatStyle.SHORT)
                .withLocale(locale).format(it.date)) +
                if (it.zone != java.time.ZoneId.systemDefault()) {
                    val offset = it.zone.rules.getOffset(it.updatedAt)
                    val zoneText = if (offset == java.time.ZoneOffset.UTC) "UTC" else "UTC$offset"
                    " · " + bidi.unicodeWrap(zoneText)
                } else ""
        }.orEmpty()
        val shortMessage = when {
            loadingKind != null -> appContext.getString(R.string.widget_loading_short)
            failure -> appContext.getString(R.string.widget_compact_read_error)
            snapshot == null -> appContext.getString(R.string.widget_compact_setup)
            feedback != null -> when (feedback.outcome) {
                WidgetOutcome.STALE -> appContext.getString(R.string.widget_compact_stale)
                WidgetOutcome.FAILED -> appContext.getString(R.string.widget_compact_failed)
                WidgetOutcome.CONFLICT -> appContext.getString(R.string.widget_compact_conflict)
                WidgetOutcome.MISSING, WidgetOutcome.INVALID -> appContext.getString(R.string.widget_compact_missing)
                else -> fullMessage
            }
            else -> shortDate + "\n" + bidi.unicodeWrap(if (kind == WidgetKind.HABITS)
                java.lang.String.format(locale, "%d / %d", snapshot.completed ?: 0, snapshot.total)
                else java.lang.String.format(locale, "%d", snapshot.total))
        }
        val innerWidth = widthDp - 24
        val primaryHeight = maxOf(48, WidgetRenderer.measureTextHeight(appContext,
            appContext.getString(primary.first), ((innerWidth - 12) * density).toInt(), 14f) + 12)
        fun fitsHorizontally(text: String): Boolean {
            val messageWidth = innerWidth - primaryWidth - 8
            return messageWidth >= 80 && primaryHeight <= heightDp - 24 &&
                WidgetRenderer.measureTextHeight(appContext, text, (messageWidth * density).toInt(), 14f) <= heightDp - 24
        }
        fun fitsVertically(text: String): Boolean = WidgetRenderer.measureTextHeight(appContext,
            text, (innerWidth * density).toInt(), 14f) + 8 + primaryHeight <= heightDp - 24
        // Retain date/zone before optional counts and freshness at the tightest bounds.
        val alternatives = listOf(fullMessage, shortMessage) +
            if (feedback == null && snapshot != null && !failure) listOf(shortDate) else emptyList()
        val message = alternatives.firstOrNull { fitsHorizontally(it) || fitsVertically(it) } ?: shortMessage
        val horizontal = fitsHorizontally(message)
        val views = RemoteViews(context.packageName,
            if (horizontal) R.layout.widget_data_compact_wide else R.layout.widget_data_compact)
        val color = appContext.getColor(if (dark) R.color.widget_on_primary_dark else R.color.widget_on_primary)
        views.setInt(R.id.widget_compact_root, "setBackgroundResource",
            if (dark) R.drawable.widget_background_dark else R.drawable.widget_background)
        views.setInt(R.id.widget_compact_root, "setLayoutDirection", appContext.resources.configuration.layoutDirection)
        views.setTextViewText(R.id.widget_compact_message, message)
        views.setTextColor(R.id.widget_compact_message, color)
        val roomForSecondary = if (horizontal) {
            val textWidth = widthDp - 24 - primaryWidth - secondaryWidth - 16
            textWidth >= 100 && WidgetRenderer.measureTextHeight(appContext, message,
                (textWidth * density).toInt(), 14f) <= heightDp - 24
        } else {
            // Both targets share the row only if their localized labels fit without squeezing.
            val targetWidth = (innerWidth - 8) / 2
            val height = maxOf(48, listOf(primary.first, secondary.first).maxOf {
                WidgetRenderer.measureTextHeight(appContext, appContext.getString(it),
                    ((targetWidth - 12) * density).toInt(), 14f) + 12
            })
            primaryWidth + secondaryWidth + 8 <= innerWidth &&
                WidgetRenderer.measureTextHeight(appContext, message, (innerWidth * density).toInt(), 14f) + 8 + height <= heightDp - 24
        }
        val roomForCapture = secondary != capture && if (horizontal) {
            val textWidth = innerWidth - primaryWidth - secondaryWidth - captureWidth - 24
            textWidth >= 80 &&
                WidgetRenderer.measureTextHeight(appContext, message, (textWidth * density).toInt(), 14f) <= heightDp - 24
        } else {
            val targetWidth = (innerWidth - 16) / 3
            val actionsFit = listOf(primaryWidth, secondaryWidth, captureWidth).all { it <= targetWidth }
            val actionsHeight = maxOf(48, listOf(primary, secondary, capture).maxOf { action ->
                WidgetRenderer.measureTextHeight(appContext, appContext.getString(action.first),
                    ((targetWidth - 12) * density).toInt(), 14f) + 12
            })
            actionsFit && WidgetRenderer.measureTextHeight(appContext, message,
                (innerWidth * density).toInt(), 14f) + 8 + actionsHeight <= heightDp - 24
        }
        listOf(R.id.widget_compact_primary to primary, R.id.widget_compact_secondary to secondary).forEach { (id, action) ->
            views.setTextViewText(id, appContext.getString(action.first))
            val description = when (action.first) {
                R.string.widget_feedback_retry -> if (kind == WidgetKind.TASKS)
                    R.string.widget_task_retry_description else R.string.widget_feedback_retry_description
                R.string.widget_feedback_undo -> if (kind == WidgetKind.TASKS)
                    R.string.widget_task_undo_description else R.string.widget_feedback_undo_description
                R.string.widget_dismiss -> R.string.widget_feedback_dismiss_description
                R.string.widget_data_open -> if (kind == WidgetKind.TASKS) R.string.widget_tasks_open_description else R.string.widget_data_open_description
                else -> action.first
            }
            views.setContentDescription(id, appContext.getString(description))
            val mutationAction = action.first == R.string.widget_feedback_undo ||
                action.first == R.string.widget_feedback_retry
            views.setTextColor(id, if (mutationAction) appContext.getColor(
                if (dark) R.color.widget_mutation_text_dark else R.color.widget_mutation_text_light) else color)
            views.setInt(id, "setBackgroundResource", when {
                mutationAction && dark -> R.drawable.widget_row_action_background_dark
                mutationAction -> R.drawable.widget_row_action_background
                dark -> R.drawable.widget_button_background_dark
                else -> R.drawable.widget_button_background
            })
            views.setOnClickPendingIntent(id, action.second)
        }
        views.setTextViewText(R.id.widget_compact_capture, appContext.getString(capture.first))
        views.setContentDescription(R.id.widget_compact_capture,
            appContext.getString(R.string.widget_open_quick_capture_description))
        views.setTextColor(R.id.widget_compact_capture, color)
        views.setInt(R.id.widget_compact_capture, "setBackgroundResource", if (dark)
            R.drawable.widget_button_background_dark else R.drawable.widget_button_background)
        views.setOnClickPendingIntent(R.id.widget_compact_capture, capture.second)
        views.setViewVisibility(R.id.widget_compact_capture, if (roomForCapture) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_compact_secondary,
            if (loadingKind == null && roomForSecondary && primary != secondary) View.VISIBLE else View.GONE)
        return views
    }
}
