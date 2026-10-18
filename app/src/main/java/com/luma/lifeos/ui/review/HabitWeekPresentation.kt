package com.luma.lifeos.ui.review

import com.luma.lifeos.domain.habit.HabitDay
import com.luma.lifeos.domain.habit.HabitWeekComparisonRow
import java.time.LocalDate

internal data class HabitWeekSlot(
    val date: LocalDate,
    val day: HabitDay?,
    val upcoming: Boolean,
)

internal fun habitWeekSlots(start: LocalDate, through: LocalDate, row: HabitWeekComparisonRow?): List<HabitWeekSlot> =
    (0..6).map { offset ->
        val date = start.plusDays(offset.toLong())
        val upcoming = date.isAfter(through)
        HabitWeekSlot(
            date = date,
            day = if (upcoming) null else row?.current?.days?.firstOrNull { it.date == date },
            upcoming = upcoming,
        )
    }
