package com.luma.lifeos.data.calendar

import androidx.room.ColumnInfo

data class CalendarTaskMarkerRow(
    @ColumnInfo(name = "due_date") val dueDate: String,
    @ColumnInfo(name = "task_count") val count: Int,
    @ColumnInfo(name = "overdue_count") val overdueCount: Int,
)

data class CalendarDateCountRow(
    val date: String,
    val count: Int,
)

data class CalendarReflectionsRow(
    @ColumnInfo(name = "total_count") val totalCount: Int,
    @ColumnInfo(name = "latest_event_id") val latestEventId: String?,
    @ColumnInfo(name = "latest_title") val latestTitle: String?,
    @ColumnInfo(name = "latest_body") val latestBody: String?,
)
