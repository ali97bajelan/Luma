package com.luma.lifeos.domain.habit

import com.luma.lifeos.domain.model.LifeEventRecord
import com.luma.lifeos.domain.time.TimeRange
import java.time.LocalDate
import java.time.ZoneId

sealed interface TimelineFeedItem {
    val key: String

    data class Entry(val record: LifeEventRecord) : TimelineFeedItem {
        override val key: String = "event:${record.event.id}"
    }

    data class Habit(val log: HabitLog, val name: String, val unitLabel: String?) : TimelineFeedItem {
        override val key: String = "habit:${log.id}"
        init {
            require(name.isNotBlank()) { "Habit timeline name cannot be blank" }
        }
    }
}

data class TimelineFeedPage(
    val items: List<TimelineFeedItem>,
    val offset: Int,
    val totalCount: Int,
    val eventCount: Int,
    val habitLogCount: Int,
) {
    init {
        require(offset >= 0 && totalCount >= 0)
        require(eventCount >= 0 && habitLogCount >= 0)
        require(eventCount + habitLogCount <= totalCount)
        require(items.map { it.key }.distinct().size == items.size)
        require(items.size.toLong() <= maxOf(0L, totalCount.toLong() - offset))
    }
}

data class HabitWeekComparisonRow(
    val habit: HabitDefinition,
    val current: HabitWeekRow?,
    val previous: HabitWeekRow?,
) {
    val currentRepetitions: Int get() = current?.days?.sumOf(HabitDay::count) ?: 0
    val previousRepetitions: Int get() = previous?.days?.sumOf(HabitDay::count) ?: 0
    val currentScheduledDays: Int get() = current?.days?.count { it.reviewTarget() != null } ?: 0
    val previousScheduledDays: Int get() = previous?.days?.count { it.reviewTarget() != null } ?: 0
}

data class HabitWeekComparison(
    val current: HabitWeek,
    val previous: HabitWeek,
    val rows: List<HabitWeekComparisonRow>,
) {
    init {
        require(current.zone == previous.zone) { "Compared habit weeks must use the same tracking zone" }
        require(current.start == previous.start.plusWeeks(1) && current.through == previous.through.plusWeeks(1)) {
            "Compared habit weeks must cover matching civil weekdays"
        }
        require(rows.map { it.habit.id }.distinct().size == rows.size)
    }
}

fun HabitDay.reviewTarget(): Int? = schedule?.takeIf {
    it.lifecycle == HabitLifecycle.ACTIVE && date.dayOfWeek in it.weekdays
}?.target

fun compareHabitWeeks(current: HabitWeek, previous: HabitWeek): HabitWeekComparison {
    require(current.zone == previous.zone) { "Compared habit weeks must use the same tracking zone" }
    require(current.start == previous.start.plusWeeks(1) && current.through == previous.through.plusWeeks(1)) {
        "Compared habit weeks must cover matching civil weekdays"
    }
    val currentRows = current.rows.associateBy { it.habit.id }
    val previousRows = previous.rows.associateBy { it.habit.id }
    val rows = (currentRows.keys + previousRows.keys)
        .map { id ->
            val currentRow = currentRows[id]
            val previousRow = previousRows[id]
            HabitWeekComparisonRow(currentRow?.habit ?: requireNotNull(previousRow).habit, currentRow, previousRow)
        }
        .sortedWith(compareBy<HabitWeekComparisonRow> { it.habit.createdAt }.thenBy { it.habit.id.toString() })
    return HabitWeekComparison(current, previous, rows)
}

/** The legacy feed fallback keeps date-only habit logs unsupported only for non-Room test/fallback repositories. */
fun legacyTimelineFeedRange(date: LocalDate, zone: ZoneId): TimeRange? {
    val start = date.atStartOfDay(zone).toInstant()
    val end = date.plusDays(1).atStartOfDay(zone).toInstant()
    return if (start == end) null else TimeRange(start, end)
}

