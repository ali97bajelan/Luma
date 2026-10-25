package com.luma.lifeos.data.calendar

import androidx.room.ColumnInfo

data class CalendarTaskMarkerRow(
    @ColumnInfo(name = "due_date") val dueDate: String,
    @ColumnInfo(name = "task_count") val count: Int,
    @ColumnInfo(name = "overdue_count") val overdueCount: Int,
)

data class CalendarDateCountRow(val date: String, val count: Int)

/** A left-join sentinel carries a zero total when the selected day contains no memories. */
data class CalendarMemoryRow(
    @ColumnInfo(name = "total_count") val totalCount: Int,
    @ColumnInfo(name = "event_id") val eventId: String?,
    val title: String?,
    @ColumnInfo(name = "memory_note") val memoryNote: String?,
    @ColumnInfo(name = "journal_body") val journalBody: String?,
    @ColumnInfo(name = "mood_note") val moodNote: String?,
    @ColumnInfo(name = "event_note") val eventNote: String?,
)
