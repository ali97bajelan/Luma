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

class JournalPersistenceTest {
    private lateinit var database: LumaDatabase
    private lateinit var journals: RoomJournalRepository
    private val now = Instant.parse("2026-10-11T08:00:00Z")
    private val range = TimeRange.forDay(LocalDate.parse("2026-10-11"), ZoneOffset.UTC)
    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            LumaDatabase::class.java).build()
        journals = RoomJournalRepository(database)
    }
    @After fun tearDown() { database.close() }

    @Test fun captureRetriesReuseTagsAndPreserveBodyFormatting() = runBlocking {
        val id = EventId.new()
        val input = JournalInput("  First\n\nSecond  ", now, "  Title  ", setOf(" Ideas ", "IDEAS", "Work"))
        journals.create(id, input); journals.create(id, input)
        val saved = journals.get(id)!!
        assertEquals("Title", saved.journal!!.title)
        assertEquals(input.body, saved.journal!!.body)
        assertEquals(2, saved.journal!!.tags.size)
        val ideas = saved.journal!!.tags.single { Tag.normalize(it.name) == "ideas" }
        val other = EventId.new()
        journals.create(other, JournalInput("Another", now, tagNames = setOf("ideas")))
        assertEquals(ideas.id, journals.get(other)!!.journal!!.tags.single().id)
        assertEquals(2, journals.observeTags().first().size)
        assertEquals(2, RoomLifeEventRepository(database).observeRecords(range).first().size)
        assertTrue(runCatching { journals.create(id, input.copy(body = "Different")) }.isFailure)
        assertEquals(input.body, journals.get(id)!!.journal!!.body)
    }

    @Test fun editsPreserveUnrelatedContentAndMoveTheTypedFeedThenDeleteKeepsCatalog() = runBlocking {
        val id = EventId.new()
        journals.create(id, JournalInput("Original", now, "Title", setOf("Work")))
        val store = RoomEventStore(database)
        val graph = store.get(id)!!
        val person = PersonEntity(EventId.new().toString(), "Friend")
        RoomCatalogRepository(database).savePerson(person)
        store.save(EventWrite(journals.get(id)!!.event, journal = graph.journal,
            tagIds = graph.tags.map { it.id }.toSet(), personIds = setOf(person.id),
            memory = MemoryEntity(EventId.new().toString(), id.toString()), metadataJson = "{\"keep\":true}"))
        journals.update(id, JournalInput("Edited", now.plusSeconds(86400), tagNames = setOf("New")))
        val edited = store.get(id)!!
        assertEquals(graph.journal!!.id, edited.journal!!.id)
        assertEquals(graph.event.createdAt, edited.event.createdAt)
        assertEquals("{\"keep\":true}", edited.event.metadataJson)
        assertNotNull(edited.memory)
        assertEquals(listOf(person), edited.people)
        assertNull(edited.journal!!.title)
        assertTrue(RoomLifeEventRepository(database).observeRecords(range).first().isEmpty())
        val next = TimeRange.forDay(LocalDate.parse("2026-10-12"), ZoneOffset.UTC)
        assertEquals("Edited", RoomLifeEventRepository(database).observeRecords(next).first().single().journal!!.body)
        journals.delete(id)
        journals.delete(id) // A lost completion result can safely be retried.
        assertNull(journals.get(id))
        assertNotNull(database.catalog().person(person.id))
        assertEquals(2, journals.observeTags().first().size)
        assertTrue(runCatching { journals.update(id, JournalInput("Missing", now)) }.isFailure)
    }

    @Test fun failedTagLinkRollsBackNewCatalogRowsBodyTimestampAndSummary() = runBlocking {
        val id = EventId.new()
        journals.create(id, JournalInput("Original", now, "Original title", setOf("Work")))
        val original = journals.get(id)!!
        val derived = RoomDerivedDataRepository(database)
        val date = LocalDate.parse("2026-10-11")
        derived.rebuildSummary(date, ZoneOffset.UTC) {
            DailySummaryEntity(date.toString(), ZoneOffset.UTC.id, "{}", 1, now.toEpochMilli())
        }
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_tag BEFORE INSERT ON event_tag BEGIN SELECT RAISE(ABORT, 'test link failure'); END")
        assertTrue(runCatching {
            journals.update(id, JournalInput("Changed", now.plusSeconds(86400), "Changed title", setOf("New tag")))
        }.isFailure)
        assertEquals(original, journals.get(id))
        assertEquals(listOf("Work"), journals.observeTags().first().map { it.name })
        assertEquals(SummaryFreshness.FRESH, derived.summary(date, ZoneOffset.UTC)!!.freshness)
        val newId = EventId.new()
        assertTrue(runCatching { journals.create(newId, JournalInput("New", now, tagNames = setOf("Other"))) }.isFailure)
        assertNull(database.lifeEvents().get(newId.toString()))
        assertEquals(listOf("Work"), journals.observeTags().first().map { it.name })
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_tag")
        journals.update(id, JournalInput("Changed", now))
        assertEquals(SummaryFreshness.STALE, derived.summary(date, ZoneOffset.UTC)!!.freshness)
    }

    @Test fun importedAndIncompleteRecordsRejectWritesAndMediaDeletionIsProtected() = runBlocking {
        val store = RoomEventStore(database)
        val imported = EventId.new()
        val event = LifeEvent(imported, EventType.JOURNAL, now, now,
            source = EventSource.HEALTH_CONNECT, sourceReference = "journal")
        store.save(EventWrite(event, journal = JournalEntryEntity(EventId.new().toString(), imported.toString(), "Imported")))
        assertTrue(runCatching { journals.update(imported, JournalInput("Changed", now, tagNames = setOf("Unexpected"))) }.isFailure)
        assertTrue(runCatching { journals.delete(imported) }.isFailure)
        assertEquals("Imported", journals.get(imported)!!.journal!!.body)
        assertTrue(journals.observeTags().first().isEmpty())
        val id = EventId.new()
        journals.create(id, JournalInput("Keep", now))
        val graph = store.get(id)!!
        store.save(EventWrite(journals.get(id)!!.event, journal = graph.journal,
            attachments = listOf(MediaAttachmentEntity(EventId.new().toString(), id.toString(), "photos/test.jpg"))))
        assertTrue(runCatching { journals.delete(id) }.isFailure)
        assertEquals("Keep", journals.get(id)!!.journal!!.body)
    }
}
