package com.luma.lifeos.widget

import android.content.Context
import android.content.res.Configuration
import android.widget.RemoteViews
import com.luma.lifeos.R
import com.luma.lifeos.domain.widget.WidgetKind
import com.luma.lifeos.domain.widget.WidgetOutcome
import com.luma.lifeos.domain.widget.WidgetSnapshot
import com.luma.lifeos.language.AppLanguages
import com.luma.lifeos.ui.theme.THEME_KEY
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Safe summary/setup/error rendering shared by data widgets until their row renderers are added. */
object WidgetRenderer {
    fun render(
        context: Context,
        widgetId: Int,
        snapshot: WidgetSnapshot?,
        failure: Boolean,
    ): RemoteViews {
        val appContext = AppLanguages.wrap(context)
        val kind = snapshot?.instance?.kind ?: WidgetRefreshCoordinator.kindForWidgetId(context, widgetId)
        val views = RemoteViews(context.packageName, R.layout.widget_data_skeleton)
        val dark = isAppDark(context, appContext)
        views.setInt(R.id.widget_data_root, "setLayoutDirection", appContext.resources.configuration.layoutDirection)
        views.setInt(
            R.id.widget_data_root,
            "setBackgroundResource",
            if (dark) R.drawable.widget_background_dark else R.drawable.widget_background,
        )
        val textColor = appContext.getColor(if (dark) R.color.widget_on_primary_dark else R.color.widget_on_primary)
        listOf(
            R.id.widget_data_title,
            R.id.widget_data_summary,
            R.id.widget_data_updated,
            R.id.widget_data_feedback,
            R.id.widget_data_open,
            R.id.widget_data_refresh,
        ).forEach { views.setTextColor(it, textColor) }
        listOf(R.id.widget_data_open, R.id.widget_data_refresh).forEach { id ->
            views.setInt(
                id,
                "setBackgroundResource",
                if (dark) R.drawable.widget_button_background_dark else R.drawable.widget_button_background,
            )
        }

        val title = when (kind) {
            WidgetKind.HABITS -> R.string.widget_habits_title
            WidgetKind.TASKS -> R.string.widget_tasks_title
            null -> R.string.app_name
        }
        views.setTextViewText(R.id.widget_data_title, appContext.getString(title))
        views.setTextViewText(R.id.widget_data_open, appContext.getString(R.string.widget_data_open))
        views.setTextViewText(R.id.widget_data_refresh, appContext.getString(R.string.widget_data_refresh))
        views.setContentDescription(R.id.widget_data_open, appContext.getString(R.string.widget_data_open_description))
        views.setContentDescription(R.id.widget_data_refresh, appContext.getString(R.string.widget_data_refresh_description))

        val destination = when (kind) {
            WidgetKind.HABITS -> WidgetDestination.Habits
            WidgetKind.TASKS -> WidgetDestination.Tasks
            null -> WidgetDestination.Today
        }
        views.setOnClickPendingIntent(R.id.widget_data_open, WidgetIntents.navigation(context, widgetId, destination))
        views.setOnClickPendingIntent(R.id.widget_data_refresh, WidgetIntents.refresh(context, widgetId))

        if (failure) {
            views.setTextViewText(R.id.widget_data_summary, appContext.getString(R.string.widget_data_read_error))
            views.setTextViewText(R.id.widget_data_updated, "")
            views.setTextViewText(R.id.widget_data_feedback, "")
        } else if (snapshot == null) {
            views.setTextViewText(R.id.widget_data_summary, appContext.getString(R.string.widget_data_setup))
            views.setTextViewText(R.id.widget_data_updated, "")
            views.setTextViewText(R.id.widget_data_feedback, "")
        } else {
            val summary = when (snapshot.instance.kind) {
                WidgetKind.HABITS -> appContext.getString(
                    R.string.widget_habits_summary,
                    snapshot.completed ?: 0,
                    snapshot.total,
                )
                WidgetKind.TASKS -> appContext.resources.getQuantityString(
                    R.plurals.widget_tasks_summary,
                    snapshot.total,
                    snapshot.total,
                )
            }
            val locale = AppLanguages.current(appContext).locale
            val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
                .format(snapshot.date)
            val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
                .format(snapshot.updatedAt.atZone(snapshot.zone))
            views.setTextViewText(R.id.widget_data_summary, summary)
            views.setTextViewText(
                R.id.widget_data_updated,
                appContext.getString(R.string.widget_data_updated_at, date, time),
            )
            views.setTextViewText(
                R.id.widget_data_feedback,
                snapshot.feedback?.let { feedbackText(appContext, it.outcome) }.orEmpty(),
            )
        }
        return views
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
