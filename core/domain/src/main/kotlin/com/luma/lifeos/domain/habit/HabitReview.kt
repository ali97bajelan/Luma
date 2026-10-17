package com.luma.lifeos.domain.habit

import java.time.LocalDate
import java.time.ZoneId

data class HabitWeekRow(val habit: HabitDefinition, val days: List<HabitDay>) {
    init {
        require(days.all { it.habit.id == habit.id }) { "Habit week contains a day for another habit" }
        require(days.map { it.date }.distinct().size == days.size) { "Habit week contains duplicate dates" }
        require(days.map { it.date } == days.map { it.date }.sorted()) { "Habit week dates must be ordered" }
    }
}

data class HabitWeek(
    val start: LocalDate,
    val through: LocalDate,
    val zone: ZoneId,
    val partial: Boolean,
    val rows: List<HabitWeekRow>,
) {
    init {
        require(!through.isBefore(start)) { "Habit week end must be on or after its start" }
        require(through.toEpochDay() - start.toEpochDay() < 7) { "Habit week cannot exceed seven dates" }
        require(rows.map { it.habit.id }.distinct().size == rows.size) { "Habit week contains duplicate habits" }
        val expectedDates = generateSequence(start) { previous ->
            previous.plusDays(1).takeIf { !it.isAfter(through) }
        }.toList()
        require(rows.all { row ->
            row.days.map { it.date } == expectedDates && row.days.all { it.zone == zone }
        }) { "Habit week rows must cover the requested dates in the tracking zone" }
    }
}
