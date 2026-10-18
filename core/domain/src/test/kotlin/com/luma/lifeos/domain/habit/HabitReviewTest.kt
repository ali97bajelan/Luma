package com.luma.lifeos.domain.habit

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class HabitReviewTest {
    private val monday = LocalDate.parse("2026-10-05")
    private val zone = ZoneId.of("Asia/Tehran")

    @Test fun comparisonPairsTheSameElapsedCivilWeekdaysAndKeepsHabitsSeparate() {
        val water = habit(1, "Water", "glasses", monday.minusWeeks(1))
        val pages = habit(2, "Read", "pages", monday.minusWeeks(1))
        val previous = week(monday.minusWeeks(1), monday.plusDays(1).minusWeeks(1), listOf(
            row(water, monday.minusWeeks(1), monday.plusDays(1).minusWeeks(1), 2, 8),
            row(pages, monday.minusWeeks(1), monday.plusDays(1).minusWeeks(1), 30, 20),
        ))
        val current = week(monday, monday.plusDays(1), listOf(
            row(water, monday, monday.plusDays(1), 3, 8),
            row(pages, monday, monday.plusDays(1), 24, 20),
        ))

        val comparison = compareHabitWeeks(current, previous)

        assertEquals(listOf(water.id, pages.id), comparison.rows.map { it.habit.id })
        assertEquals(3, comparison.rows.single { it.habit.id == water.id }.currentRepetitions)
        assertEquals(2, comparison.rows.single { it.habit.id == water.id }.previousRepetitions)
        assertEquals(2, comparison.rows.single { it.habit.id == water.id }.currentScheduledDays)
        assertEquals(2, comparison.rows.single { it.habit.id == water.id }.previousScheduledDays)
        assertEquals(24, comparison.rows.single { it.habit.id == pages.id }.currentRepetitions)
    }

    @Test fun comparisonRejectsWeeksThatDoNotHaveMatchingElapsedDates() {
        val definition = habit(3, "Walk", null, monday.minusWeeks(1))
        val previous = week(monday.minusWeeks(1), monday.minusWeeks(1), listOf(row(definition, monday.minusWeeks(1), monday.minusWeeks(1), 1, 1)))
        val current = week(monday, monday.plusDays(1), listOf(row(definition, monday, monday.plusDays(1), 1, 1)))

        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            compareHabitWeeks(current, previous)
        }
    }

    private fun week(start: LocalDate, through: LocalDate, rows: List<HabitWeekRow>) =
        HabitWeek(start, through, zone, partial = through.dayOfWeek != DayOfWeek.SUNDAY, rows)

    private fun row(habit: HabitDefinition, start: LocalDate, through: LocalDate, count: Int, target: Int): HabitWeekRow {
        val days = generateSequence(start) { it.plusDays(1).takeIf { date -> !date.isAfter(through) } }
            .map { date ->
                val schedule = HabitSchedule(UUID.nameUUIDFromBytes("${habit.id}:$date".toByteArray()), habit.id,
                    habit.start, target, setOf(date.dayOfWeek), HabitLifecycle.ACTIVE)
                HabitDay(habit, date, zone, schedule, if (date == start) count else 0, skipRemaining = false)
            }.toList()
        return HabitWeekRow(habit, days)
    }

    private fun habit(id: Long, name: String, unit: String?, start: LocalDate) = HabitDefinition(
        UUID(0, id), name, unit, start, Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-01T00:00:00Z"),
    )
}
