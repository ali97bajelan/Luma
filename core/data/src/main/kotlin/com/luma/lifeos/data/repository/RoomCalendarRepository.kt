package com.luma.lifeos.data.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import com.luma.lifeos.data.calendar.CalendarDao
import com.luma.lifeos.data.calendar.CalendarDateCountRow
import com.luma.lifeos.data.calendar.CalendarReflectionsRow
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.todo.toDomain
import com.luma.lifeos.domain.calendar.CalendarRange
import com.luma.lifeos.domain.calendar.CalendarReflection
import com.luma.lifeos.domain.calendar.CalendarReflections
import com.luma.lifeos.domain.calendar.CalendarRepository
import com.luma.lifeos.domain.calendar.CalendarTaskMarker
import com.luma.lifeos.domain.calendar.requireCalendarDate
import com.luma.lifeos.domain.habit.HabitDay
import com.luma.lifeos.domain.habit.HabitLifecycle
import com.luma.lifeos.domain.habit.HabitRepository
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.todo.TodoPage
import java.time.temporal.ChronoUnit
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class RoomCalendarRepository(
    database: LumaDatabase,
    private val habits: HabitRepository,
) : CalendarRepository {
    private val dao: CalendarDao = database.calendar()

    override fun observeTaskMarkers(
        range: CalendarRange,
        today: LocalDate,
    ): Flow<Map<LocalDate, CalendarTaskMarker>> {
        requireCalendarDate(today, "today")
        return dao.observeTaskMarkers(range.start.toString(), range.through.toString(), today.toString())
            .map { rows ->
                rows.associate { row ->
                    LocalDate.parse(row.dueDate) to CalendarTaskMarker(row.count, row.overdueCount > 0)
                }
            }.flowOn(Dispatchers.Default)
    }

    override fun observeHabitMarkers(range: CalendarRange): Flow<Map<LocalDate, Int>> =
        dao.observeHabitMarkers(range.start.toString(), range.through.toString()).map { rows ->
            rows.associate { row -> LocalDate.parse(row.date) to row.count }
        }.flowOn(Dispatchers.Default)

    override fun observeJournalMarkers(range: CalendarRange, zone: ZoneId): Flow<Map<LocalDate, Int>> = flow {
        emitAll(dao.observeJournalMarkers(journalMarkerQuery(range, zone)).map { rows ->
            rows.associate { row -> LocalDate.parse(row.date) to row.count }
        })
    }.flowOn(Dispatchers.Default)

    override fun observeTasks(
        date: LocalDate,
        today: LocalDate,
        overdueOnly: Boolean,
        limit: Int,
    ): Flow<TodoPage> {
        requireCalendarDate(date, "selected date")
        requireCalendarDate(today, "today")
        require(limit > 0) { "Calendar task page size must be positive" }
        return dao.observeTasks(date.toString(), today.toString(), overdueOnly, limit).map { rows ->
            TodoPage(
                items = rows.mapNotNull { it.toEntityOrNull()?.toDomain() },
                total = rows.firstOrNull()?.totalCount ?: 0,
            )
        }.flowOn(Dispatchers.Default)
    }

    override fun observeOverdueCount(today: LocalDate): Flow<Int> {
        requireCalendarDate(today, "today")
        return dao.observeOverdueCount(today.toString())
    }

    override fun observeHabits(date: LocalDate): Flow<List<HabitDay>> {
        requireCalendarDate(date, "selected date")
        return habits.observeDay(date).map { days -> days.filter(HabitDay::isScheduledActive) }
            .flowOn(Dispatchers.Default)
    }

    override fun observeReflections(date: LocalDate, zone: ZoneId): Flow<CalendarReflections> {
        requireCalendarDate(date, "selected date")
        val startMillis = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return dao.observeReflections(startMillis, endMillis).map(CalendarReflectionsRow::toDomain)
            .flowOn(Dispatchers.Default)
    }

    private fun journalMarkerQuery(range: CalendarRange, zone: ZoneId): SimpleSQLiteQuery {
        val values = List(rangeLength(range)) { index ->
            val date = range.start.plusDays(index.toLong())
            val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            Triple(date.toString(), start, end)
        }
        val placeholders = values.joinToString(", ") { "(?, ?, ?)" }
        val sql = """
            WITH day_bounds(day, start_millis, end_millis) AS (VALUES $placeholders)
            SELECT day_bounds.day AS date, COUNT(*) AS count
            FROM day_bounds
            INNER JOIN life_event AS event
                ON event.occurred_at >= day_bounds.start_millis
                AND event.occurred_at < day_bounds.end_millis
            INNER JOIN journal_entry AS journal ON journal.life_event_id = event.id
            WHERE event.source = 'MANUAL'
            GROUP BY day_bounds.day
        """.trimIndent()
        val arguments = values.flatMap { (date, start, end) -> listOf(date, start, end) }.toTypedArray()
        return SimpleSQLiteQuery(sql, arguments)
    }

    private fun rangeLength(range: CalendarRange): Int =
        Math.toIntExact(ChronoUnit.DAYS.between(range.start, range.through) + 1)
}

private fun CalendarReflectionsRow.toDomain(): CalendarReflections {
    val reflection = latestEventId?.let { id ->
        CalendarReflection(
            id = EventId.parse(id),
            title = latestTitle,
            preview = latestBody.orEmpty().calendarPreview(),
        )
    }
    check((totalCount == 0) == (reflection == null)) {
        "Calendar journal count and latest entry disagree"
    }
    return CalendarReflections(totalCount, reflection)
}

private fun HabitDay.isScheduledActive(): Boolean {
    val effectiveSchedule = schedule ?: return false
    return date >= habit.start && effectiveSchedule.lifecycle == HabitLifecycle.ACTIVE &&
        date.dayOfWeek in effectiveSchedule.weekdays
}

private fun String.calendarPreview(): String {
    val result = StringBuilder()
    var pendingSpace = false
    var outputCodePoints = 0
    var offset = 0
    while (offset < length) {
        val codePoint = codePointAt(offset)
        offset += Character.charCount(codePoint)
        if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
            pendingSpace = outputCodePoints > 0
            continue
        }
        val needed = 1 + if (pendingSpace) 1 else 0
        if (outputCodePoints + needed > 160) break
        if (pendingSpace) {
            result.append(' ')
            outputCodePoints++
        }
        result.appendCodePoint(codePoint)
        outputCodePoints++
        pendingSpace = false
    }
    return result.toString()
}
