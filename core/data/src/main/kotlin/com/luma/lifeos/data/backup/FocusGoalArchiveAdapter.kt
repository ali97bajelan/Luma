package com.luma.lifeos.data.backup

/** Portable goals are introduced in archive 10; earlier archives receive the disabled database default. */
internal object FocusGoalArchiveAdapter {
    private val goalTables = setOf("focus_goal_settings", "focus_goal_version")

    fun importColumns(table: String, schemaVersion: Int, currentColumns: List<String>): List<String> =
        if (table in goalTables && schemaVersion >= 10) currentColumns else currentColumns
}
