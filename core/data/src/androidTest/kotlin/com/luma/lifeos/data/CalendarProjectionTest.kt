package com.luma.lifeos.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.habit.HabitEntity
import com.luma.lifeos.data.habit.HabitLogEntity
import com.luma.lifeos.data.habit.HabitScheduleEntity
import com.luma.lifeos.data.repository.RoomCalendarRepository
import com.luma.lifeos.data.repository.RoomJournalRepository
import com.luma.lifeos.data.repository.RoomMemoryRepository
import com.luma.lifeos.data.repository.RoomMoodRepository
import com.luma.lifeos.data.repository.RoomTodoRepository
import com.luma.lifeos.domain.calendar.CalendarRange
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.JournalInput
import com.luma.lifeos.domain.model.MemoryInput
import com.luma.lifeos.domain.model.MemoryMarkerInput
import com.luma.lifeos.domain.model.MoodInput
import com.luma.lifeos.domain.todo.TodoInput
import java.time.Instant
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CalendarProjectionTest {
    private lateinit var database: LumaDatabase
    private lateinit var calendar: RoomCalendarRepository
    private lateinit var memories: RoomMemoryRepository
    private lateinit var todos: RoomTodoRepository
    private val date = LocalDate.parse("2026-10-22")
    private val now = Instant.parse("2026-10-22T10:00:00Z")

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, LumaDatabase::class.java,
        ).build()
        calendar = RoomCalendarRepository(database)
        memories = RoomMemoryRepository(database)
        todos = RoomTodoRepository(database, Clock.fixed(now, ZoneOffset.UTC))
    }
    @After fun tearDown() = database.close()

    @Test fun markedSourceRecordsCountOnceAndUnmarkedRecordsNeverBecomeMemories() = runBlocking {
        val journals = RoomJournalRepository(database)
        val moods = RoomMoodRepository(database)
        val journal = EventId.new()
        val mood = EventId.new()
        val direct = EventId.new()
        journals.create(journal, JournalInput("Original journal body", now, "Journal title"))
        moods.create(mood, MoodInput(3, now.minusSeconds(60), note = "Original mood note"))
        assertEquals(0, calendar.observeMemories(date, ZoneOffset.UTC).first().total)
        memories.saveMarker(journal, MemoryMarkerInput())
        memories.saveMarker(journal, MemoryMarkerInput())
        memories.saveMarker(mood, MemoryMarkerInput(note = "Why it matters"))
        memories.create(direct, MemoryInput("Saved  memory\n note", now.plusSeconds(60), "Walk"))
        val page = calendar.observeMemories(date, ZoneOffset.UTC).first()
        assertEquals(listOf(direct, journal, mood), page.items.map { it.id })
        assertEquals(listOf("Saved memory note", "Original journal body", "Why it matters"), page.items.map { it.preview })
        assertEquals(3, page.total)
        assertEquals(mapOf(date to 3), calendar.observeMemoryMarkers(CalendarRange(date, date), ZoneOffset.UTC).first())
        memories.removeMarker(journal)
        assertEquals(2, calendar.observeMemories(date, ZoneOffset.UTC).first().total)
        assertEquals("Original journal body", journals.get(journal)!!.journal!!.body)
    }

    @Test fun habitsAndHabitLogsCannotAffectCalendarRowsOrMarkers() = runBlocking {
        val habit = UUID.randomUUID().toString()
        val millis = now.toEpochMilli()
        database.habits().insertHabit(HabitEntity(habit, "Water", null, date.toString(), millis, millis))
        database.habits().insertSchedule(HabitScheduleEntity(UUID.randomUUID().toString(), habit, date.toString(), 8, 127, "ACTIVE"))
        database.habits().insertLog(HabitLogEntity(UUID.randomUUID().toString(), habit, date.toString(), "UTC", millis, millis, millis))
        assertEquals(0, calendar.observeTasks(date, date, false, 50).first().total)
        assertEquals(0, calendar.observeMemories(date, ZoneOffset.UTC).first().total)
        assertTrue(calendar.observeTaskMarkers(CalendarRange(date, date), date).first().isEmpty())
        assertTrue(calendar.observeMemoryMarkers(CalendarRange(date, date), ZoneOffset.UTC).first().isEmpty())
        memories.create(EventId.new(), MemoryInput("One real memory", now))
        assertEquals(1, calendar.observeMemories(date, ZoneOffset.UTC).first().total)
        assertEquals(mapOf(date to 1), calendar.observeMemoryMarkers(CalendarRange(date, date), ZoneOffset.UTC).first())
    }

    @Test fun memoryPagesHaveFullTotalsStableTieOrderingAndBoundedPreviews() = runBlocking {
        for (index in 123 downTo 1) {
            memories.create(EventId.parse("00000000-0000-0000-0000-${index.toString().padStart(12, '0')}"),
                MemoryInput("😀".repeat(200), now))
        }
        val first = calendar.observeMemories(date, ZoneOffset.UTC, 50).first()
        val all = calendar.observeMemories(date, ZoneOffset.UTC, 150).first()
        assertEquals(123, first.total)
        assertEquals(50, first.items.size)
        assertEquals("00000000-0000-0000-0000-000000000001", first.items.first().id.toString())
        assertEquals(123, all.items.size)
        assertEquals(123, all.items.map { it.id }.distinct().size)
        assertEquals("😀".repeat(160), first.items.first().preview)
    }

    @Test fun memoryDayIntervalsRespectDstAndExcludeTheNextMidnight() = runBlocking {
        val zone = ZoneId.of("America/New_York")
        for (day in listOf(LocalDate.parse("2026-03-08"), LocalDate.parse("2026-11-01"))) {
            val start = day.atStartOfDay(zone).toInstant()
            val end = day.plusDays(1).atStartOfDay(zone).toInstant()
            memories.create(EventId.new(), MemoryInput("Before", start.minusMillis(1)))
            memories.create(EventId.new(), MemoryInput("Start", start))
            memories.create(EventId.new(), MemoryInput("Last", end.minusMillis(1)))
            memories.create(EventId.new(), MemoryInput("Next day", end))
            assertEquals(2, calendar.observeMemories(day, zone).first().total)
            assertEquals(mapOf(day to 2), calendar.observeMemoryMarkers(CalendarRange(day, day), zone).first())
        }
    }

    @Test fun taskProjectionUsesCarryInExactDateGlobalOverdueAndCanonicalOrdering() = runBlocking {
        val ids = (1..7).map { index -> UUID.fromString("20000000-0000-0000-0000-${index.toString().padStart(12, '0')}") }
        val a = todos.create(ids[0], UUID.randomUUID(), TodoInput("A overdue", dueDate = date.minusDays(3)))
        val b = todos.create(ids[1], UUID.randomUUID(), TodoInput(
            "B overdue", dueDate = date.minusDays(1), dueTime = java.time.LocalTime.of(9, 5),
        ))
        val c = todos.create(ids[2], UUID.randomUUID(), TodoInput(
            "C today timed", dueDate = date, dueTime = java.time.LocalTime.of(17, 0),
        ))
        val d = todos.create(ids[3], UUID.randomUUID(), TodoInput("D today anytime", dueDate = date))
        val e = todos.create(ids[4], UUID.randomUUID(), TodoInput(
            "E midnight", dueDate = date.plusDays(1), dueTime = java.time.LocalTime.MIDNIGHT,
        ))
        todos.create(ids[5], UUID.randomUUID(), TodoInput("F undated"))
        val completed = todos.create(ids[6], UUID.randomUUID(), TodoInput(
            "G completed", dueDate = date, dueTime = java.time.LocalTime.of(8, 0),
        ))
        todos.setCompleted(completed.id, completed.revision, UUID.randomUUID(), true)

        val todayPage = calendar.observeTasks(date, date, overdueOnly = false, limit = 50).first()
        assertEquals(4, todayPage.total)
        assertEquals(listOf(a.id, b.id, c.id, d.id), todayPage.items.map { it.id })
        assertNull(todayPage.items[0].dueTime)
        assertEquals(java.time.LocalTime.of(9, 5), todayPage.items[1].dueTime)
        assertEquals(java.time.LocalTime.of(17, 0), todayPage.items[2].dueTime)
        assertNull(todayPage.items[3].dueTime)

        val historical = calendar.observeTasks(date.minusDays(1), date, overdueOnly = false, limit = 50).first()
        assertEquals(1, historical.total)
        assertEquals(listOf(b.id), historical.items.map { it.id })
        val overdue = calendar.observeTasks(date.plusDays(1), date, overdueOnly = true, limit = 50).first()
        assertEquals(2, overdue.total)
        assertEquals(listOf(a.id, b.id), overdue.items.map { it.id })
        val future = calendar.observeTasks(date.plusDays(1), date, overdueOnly = false, limit = 50).first()
        assertEquals(1, future.total)
        assertEquals(listOf(e.id), future.items.map { it.id })

        assertEquals(2, calendar.observeOverdueCount(date).first())
        assertEquals(
            mapOf(
                date.minusDays(3) to com.luma.lifeos.domain.calendar.CalendarTaskMarker(1, true),
                date.minusDays(1) to com.luma.lifeos.domain.calendar.CalendarTaskMarker(1, true),
                date to com.luma.lifeos.domain.calendar.CalendarTaskMarker(2, false),
                date.plusDays(1) to com.luma.lifeos.domain.calendar.CalendarTaskMarker(1, false),
            ),
            calendar.observeTaskMarkers(CalendarRange(date.minusDays(3), date.plusDays(1)), date).first(),
        )
    }

    @Test fun taskMarkersReemitAfterCreateAndCompletion() = runBlocking {
        val emissions = Channel<Map<LocalDate, com.luma.lifeos.domain.calendar.CalendarTaskMarker>>(Channel.UNLIMITED)
        val collection = launch {
            calendar.observeTaskMarkers(CalendarRange(date, date), date).collect { emissions.send(it) }
        }
        try {
            assertTrue(withTimeout(5_000) { emissions.receive() }.isEmpty())
            val created = todos.create(
                UUID.randomUUID(), UUID.randomUUID(), TodoInput("Observed Calendar task", dueDate = date),
            )
            assertEquals(
                mapOf(date to com.luma.lifeos.domain.calendar.CalendarTaskMarker(1, false)),
                withTimeout(5_000) { emissions.receive() },
            )
            todos.setCompleted(created.id, created.revision, UUID.randomUUID(), true)
            assertTrue(withTimeout(5_000) { emissions.receive() }.isEmpty())
        } finally {
            collection.cancelAndJoin()
            emissions.close()
        }
    }
}
