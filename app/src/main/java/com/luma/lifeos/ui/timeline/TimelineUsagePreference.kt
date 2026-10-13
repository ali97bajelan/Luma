package com.luma.lifeos.ui.timeline

import android.content.Context

/** Stores the user's Timeline-only choice; a fresh install keeps app-usage rows hidden. */
class TimelineUsagePreference(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun showAppUsage(): Boolean = preferences.getBoolean(SHOW_APP_USAGE, false)

    fun setShowAppUsage(show: Boolean) {
        preferences.edit().putBoolean(SHOW_APP_USAGE, show).apply()
    }

    private companion object {
        const val PREFERENCES = "timeline_usage"
        const val SHOW_APP_USAGE = "show_app_usage"
    }
}
