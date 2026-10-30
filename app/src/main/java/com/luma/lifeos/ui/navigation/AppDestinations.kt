package com.luma.lifeos.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.luma.lifeos.R

internal object AppRoutes {
    const val TODAY = "today"
    const val TIMELINE = "timeline"
    const val CALENDAR = "calendar"
    const val HABITS = "habits"
    const val REVIEW = "review"
    const val MORE = "more"
    const val FOCUS = "focus"
    const val FOCUS_HISTORY = "focus_history?start={start}&through={through}&zone={zone}"
    const val TASKS = "tasks"
    const val TASK_EDITOR = "task_editor?taskId={taskId}&dueDate={dueDate}"

    // Retained as secondary routes so restored back stacks keep resolving.
    const val PEOPLE = "people"
    const val SETTINGS = "settings"
}

internal enum class PrimaryDestination(
    val route: String,
    @param:StringRes val label: Int,
    @param:StringRes val compactLabel: Int,
    @param:DrawableRes val icon: Int,
) {
    TODAY(AppRoutes.TODAY, R.string.today, R.string.nav_today_compact, R.drawable.ic_nav_today),
    TIMELINE(AppRoutes.TIMELINE, R.string.timeline, R.string.nav_timeline_compact, R.drawable.ic_nav_timeline),
    CALENDAR(AppRoutes.CALENDAR, R.string.calendar_tab, R.string.nav_calendar_compact, R.drawable.ic_nav_calendar),
    REVIEW(AppRoutes.REVIEW, R.string.review, R.string.nav_review_compact, R.drawable.ic_nav_review),
    MORE(AppRoutes.MORE, R.string.more, R.string.nav_more_compact, R.drawable.ic_nav_more),
}
