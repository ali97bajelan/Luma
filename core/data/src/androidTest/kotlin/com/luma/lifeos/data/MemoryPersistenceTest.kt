package com.luma.lifeos.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.entity.*
import com.luma.lifeos.data.repository.*
import com.luma.lifeos.domain.model.*
import com.luma.lifeos.domain.time.TimeRange
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MemoryPersistenceTest {
    private lateinit var database: LumaDatabase
    private lateinit var memories: RoomMemoryRepository
    private val now = Instant.parse("2026-10-11T08:00:00Z")
    private val date = LocalDate.parse("2026-10-11")
    private val range = TimeRange.forDay(date, ZoneOffset.UTC)
    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            LumaDatabase::class.java).build()
        memories = RoomMemoryRepository(database)
    }
    @After fun tearDown() { database.close() }

    @Test fun directCaptureRetryEditMoveAndDeletePreserveIdentity() = runBlocking {
        val id = EventId.new()
        val input = MemoryInput("A moment", now, "  Title  ", 4)
        memories.create(id, input); memories.create(id, input)
        val original = memories.get(id)!!
        val childId = database.lifeEvents().getDetails(id.toString())!!.memory!!.id
        assertEquals(EventType.MEMORY, original.event.type)
        assertEquals("Title", original.event.title)
        assertEquals("A moment", original.memory!!.note)
        assertEquals(1, memories.observe(range).first().size)
        assertTrue(runCatching { memories.create(id, input.copy(note = "Different")) }.isFailure)
        memories.update(id, input.copy(note = "Edited", importance = null, title = null, occurredAt = now.plusSeconds(86400)))
        val updated = memories.get(id)!!
        assertNull(updated.event.title)
        assertNull(updated.memory!!.importance)
        assertEquals("Edited", updated.event.note)
        assertEquals(original.event.createdAt, updated.event.createdAt)
        assertEquals(childId, database.lifeEvents().getDetails(id.toString())!!.memory!!.id)
        assertTrue(memories.observe(range).first().isEmpty())
        memories.delete(id); memories.delete(id)
        assertNull(memories.get(id))
    }

    @Test fun journalPromotionEditingAndRemovalPreserveTheEntireOriginalGraph() = runBlocking {
        val id = EventId.new()
        val journals = RoomJournalRepository(database)
        journals.create(id, JournalInput("Original\nBody", now, "Title", setOf("Work")))
        val store = RoomEventStore(database)
        val graph = store.get(id)!!
        val person = PersonEntity(EventId.new().toString(), "Friend")
        val place = PlaceEntity(EventId.new().toString(), "Home")
        RoomCatalogRepository(database).savePerson(person)
        RoomCatalogRepository(database).savePlace(place)
        store.save(EventWrite(journals.get(id)!!.event, journal = graph.journal, tagIds = graph.tags.map { it.id }.toSet(),
            personIds = setOf(person.id), placeId = place.id, metadataJson = "{\"keep\":true}",
            attachments = listOf(MediaAttachmentEntity(EventId.new().toString(), id.toString(), "photos/moment.jpg"))))
        val before = store.get(id)!!
        memories.saveMarker(id, MemoryMarkerInput(importance = 5, note = "Why it matters"))
        val promoted = store.get(id)!!
        assertEquals(before, promoted.copy(memory = null))
        val memoryId = promoted.memory!!.id
        memories.saveMarker(id, MemoryMarkerInput(importance = 5, note = "Why it matters"))
        assertEquals(memoryId, store.get(id)!!.memory!!.id)
        assertEquals(1, memories.observe(range).first().size)
        assertEquals("Original\nBody", memories.observe(range).first().single().journal!!.body)
        journals.update(id, JournalInput("Edited source", now, "Title", setOf("Work")))
        assertEquals(memoryId, store.get(id)!!.memory!!.id)
        val afterSourceEdit = store.get(id)!!
        memories.saveMarker(id, MemoryMarkerInput())
        assertEquals(memoryId, store.get(id)!!.memory!!.id)
        memories.removeMarker(id); memories.removeMarker(id)
        assertEquals(afterSourceEdit.copy(memory = null), store.get(id))
        assertTrue(memories.observe(range).first().isEmpty())
        assertEquals("Edited source", journals.get(id)!!.journal!!.body)
    }

    @Test fun moodPromotionSurvivesMoodEditingAndCanBeReversed() = runBlocking {
        val id = EventId.new()
        val moods = RoomMoodRepository(database)
        moods.create(id, MoodInput(3, now))
        memories.saveMarker(id, MemoryMarkerInput())
        val childId = database.lifeEvents().getDetails(id.toString())!!.mood!!.entry.id
        moods.update(id, MoodInput(5, now, note = "Edited mood"))
        assertNotNull(memories.get(id)!!.memory)
        memories.removeMarker(id)
        assertEquals(5, moods.get(id)!!.mood!!.score)
        assertEquals(childId, database.lifeEvents().getDetails(id.toString())!!.mood!!.entry.id)
    }

    @Test fun protectedOrIncompleteEventsCannotBePromotedAndDirectMemoryCannotBeUnmarked() = runBlocking {
        val incomplete = EventId.new()
        RoomLifeEventRepository(database).upsert(LifeEvent(incomplete, EventType.JOURNAL, now, now))
        assertTrue(runCatching { memories.saveMarker(incomplete, MemoryMarkerInput()) }.isFailure)
        val imported = EventId.new()
        val event = LifeEvent(imported, EventType.JOURNAL, now, now, source = EventSource.HEALTH_CONNECT, sourceReference = "source")
        RoomEventStore(database).save(EventWrite(event, journal = JournalEntryEntity(EventId.new().toString(), imported.toString(), "Imported")))
        assertTrue(runCatching { memories.saveMarker(imported, MemoryMarkerInput()) }.isFailure)
        assertTrue(runCatching { memories.delete(imported) }.isFailure)
        assertTrue(runCatching { memories.removeMarker(imported) }.isFailure)
        val direct = EventId.new()
        memories.create(direct, MemoryInput("Direct", now))
        assertTrue(runCatching { memories.removeMarker(direct) }.isFailure)
        val graph = database.lifeEvents().getDetails(direct.toString())!!
        RoomEventStore(database).save(EventWrite(memories.get(direct)!!.event, memory = graph.memory,
            attachments = listOf(MediaAttachmentEntity(EventId.new().toString(), direct.toString(), "photos/direct.jpg"))))
        assertTrue(runCatching { memories.delete(direct) }.isFailure)
        assertNotNull(memories.get(direct)!!.memory)
    }

    @Test fun failedPromotionRollsBackMarkerAndSummaryAndSuccessfulChangesInvalidateCache() = runBlocking {
        val id = EventId.new()
        RoomJournalRepository(database).create(id, JournalInput("Body", now))
        val derived = RoomDerivedDataRepository(database)
        derived.rebuildSummary(date, ZoneOffset.UTC) { DailySummaryEntity(date.toString(), ZoneOffset.UTC.id, "{}", 1, now.toEpochMilli()) }
        val original = memories.get(id)!!
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_memory BEFORE INSERT ON memory BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        assertTrue(runCatching { memories.saveMarker(id, MemoryMarkerInput()) }.isFailure)
        assertEquals(original, memories.get(id))
        assertEquals(SummaryFreshness.FRESH, derived.summary(date, ZoneOffset.UTC)!!.freshness)
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_memory")
        memories.saveMarker(id, MemoryMarkerInput())
        assertEquals(SummaryFreshness.STALE, derived.summary(date, ZoneOffset.UTC)!!.freshness)
    }
}
