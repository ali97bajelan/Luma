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
import com.luma.lifeos.domain.calendar.CalendarRange
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.JournalInput
import com.luma.lifeos.domain.model.MemoryInput
import com.luma.lifeos.domain.model.MemoryMarkerInput
import com.luma.lifeos.domain.model.MoodInput
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CalendarProjectionTest {
    private lateinit var database: LumaDatabase
    private lateinit var calendar: RoomCalendarRepository
    private lateinit var memories: RoomMemoryRepository
    private val date = LocalDate.parse("2026-10-22")
    private val now = Instant.parse("2026-10-22T10:00:00Z")

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, LumaDatabase::class.java,
        ).build()
        calendar = RoomCalendarRepository(database)
        memories = RoomMemoryRepository(database)
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
}
