package com.luma.lifeos.ui.habits

import com.luma.lifeos.domain.habit.reviewTarget

data class HabitBrowseGroups(
    val scheduled: List<HabitDayRow>,
    val other: List<HabitDayRow>,
)

internal fun splitHabitRows(rows: List<HabitDayRow>): HabitBrowseGroups {
    val (scheduled, other) = rows.partition { it.day.reviewTarget() != null }
    return HabitBrowseGroups(scheduled = scheduled, other = other)
}
