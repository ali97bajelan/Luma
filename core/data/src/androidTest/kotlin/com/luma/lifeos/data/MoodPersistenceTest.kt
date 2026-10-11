package com.luma.lifeos.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.entity.MemoryEntity
import com.luma.lifeos.data.entity.PersonEntity
import com.luma.lifeos.data.entity.TagEntity
import com.luma.lifeos.data.entity.EmotionEntity
import com.luma.lifeos.data.entity.MoodEntryEntity
import com.luma.lifeos.data.repository.EventWrite
import com.luma.lifeos.data.repository.RoomEventStore
import com.luma.lifeos.data.repository.RoomCatalogRepository
import com.luma.lifeos.data.repository.RoomLifeEventRepository
import com.luma.lifeos.data.repository.RoomMoodRepository
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.MoodInput
import com.luma.lifeos.domain.model.LifeEvent
import com.luma.lifeos.domain.model.EventSource
import com.luma.lifeos.domain.model.EventType
import com.luma.lifeos.domain.time.TimeRange
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MoodPersistenceTest {
    private lateinit var database: LumaDatabase
    private lateinit var moods: RoomMoodRepository
    private val now = Instant.parse("2026-10-11T08:00:00Z")
    private val range = TimeRange.forDay(LocalDate.parse("2026-10-11"), ZoneId.of("UTC"))

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, LumaDatabase::class.java,
        ).build()
        moods = RoomMoodRepository(database)
    }
    @After fun tearDown() { database.close() }

    @Test fun capturesMultipleEmotionsAndEditsWithoutLosingOtherLinks() = runBlocking {
        moods.prepareEmotions()
        val emotions = moods.observeEmotions().first().take(2)
        val id = EventId.new()
        val input = MoodInput(4, now, emotions.map { it.id }.toSet(), energy = 2, stress = 5, note = "  A moment  ")
        moods.create(id, input)
        val original = moods.get(id)!!
        assertEquals(4, original.mood!!.score)
        assertEquals(2, original.mood!!.emotions.size)
        assertEquals("A moment", original.mood!!.note)
        val catalog = RoomCatalogRepository(database)
        val person = PersonEntity(EventId.new().toString(), "A friend")
        val tag = TagEntity(EventId.new().toString(), "day")
        catalog.savePerson(person); catalog.saveTag(tag)
        val store = RoomEventStore(database)
        val details = store.get(id)!!
        store.save(EventWrite(original.event, mood = details.mood!!.entry,
            emotionIds = emotions.map { it.id.toString() }.toSet(), personIds = setOf(person.id),
            tagIds = setOf(tag.id), metadataJson = "{\"keep\":true}",
            memory = MemoryEntity(EventId.new().toString(), id.toString())))
        moods.update(id, input.copy(score = 2, emotionIds = setOf(emotions.last().id), note = "Edited", energy = null))
        val updated = moods.get(id)!!
        val graph = store.get(id)!!
        assertEquals(original.event.createdAt, updated.event.createdAt)
        assertEquals(details.mood!!.entry.id, graph.mood!!.entry.id)
        assertEquals(2, updated.mood!!.score)
        assertEquals(listOf(emotions.last()), updated.mood!!.emotions)
        assertNull(updated.mood!!.energy)
        assertEquals(listOf(person), graph.people)
        assertEquals(listOf(tag), graph.tags)
        assertNotNull(graph.memory)
        assertEquals("{\"keep\":true}", graph.event.metadataJson)
        val feed = RoomLifeEventRepository(database).observeRecords(range).first().single()
        assertEquals("Edited", feed.mood!!.note)
        moods.delete(id)
        assertNull(moods.get(id))
        assertTrue(RoomLifeEventRepository(database).observeRecords(range).first().isEmpty())
        assertEquals(2, emotions.size)
        assertNotNull(catalog.person(person.id))
    }

    @Test fun initializationAndCreateRetriesAreIdempotentAndInvalidLinksRollBack() = runBlocking {
        moods.prepareEmotions()
        val first = moods.observeEmotions().first()
        moods.prepareEmotions()
        assertEquals(first, moods.observeEmotions().first())
        val id = EventId.new()
        val input = MoodInput(3, now)
        assertEquals(id, moods.create(id, input))
        assertEquals(id, moods.create(id, input))
        assertEquals(1, RoomLifeEventRepository(database).observeRecords(range).first().size)
        val failure = runCatching { moods.update(id, input.copy(score = 5, emotionIds = setOf(UUID.randomUUID()))) }
        assertTrue(failure.isFailure)
        assertEquals(3, moods.get(id)!!.mood!!.score)
        moods.delete(id)
        assertTrue(runCatching { moods.update(id, input) }.isFailure)
        assertNull(moods.get(id))
    }

    @Test fun initializingTheCatalogPreservesRenamedSeedRowsAndExistingReferences() = runBlocking {
        moods.prepareEmotions()
        val happy = moods.observeEmotions().first().single { it.name == "Happy" }
        val id = EventId.new()
        moods.create(id, MoodInput(5, now, emotionIds = setOf(happy.id)))
        RoomCatalogRepository(database).saveEmotion(EmotionEntity(happy.id.toString(), "Joy"))
        moods.prepareEmotions()
        assertEquals("Joy", moods.get(id)!!.mood!!.emotions.single().name)
        assertEquals("Joy", moods.observeEmotions().first().single { it.id == happy.id }.name)
    }

    @Test fun movingATimestampMovesTheMoodToTheCorrectDayFeed() = runBlocking {
        val id = EventId.new()
        moods.create(id, MoodInput(3, now))
        moods.update(id, MoodInput(5, Instant.parse("2026-10-12T08:00:00Z")))
        val events = RoomLifeEventRepository(database)
        assertTrue(events.observeRecords(range).first().isEmpty())
        val next = events.observeRecords(TimeRange.forDay(LocalDate.parse("2026-10-12"), ZoneId.of("UTC"))).first()
        assertEquals(id, next.single().event.id)
        assertEquals(5, next.single().mood!!.score)
    }

    @Test fun importedMoodUpdatesAndDeletesAreRejectedWithoutChangingStoredContent() = runBlocking {
        val id = EventId.new()
        val event = LifeEvent(id, EventType.MOOD, now, now, source = EventSource.HEALTH_CONNECT, sourceReference = "mood-source")
        RoomEventStore(database).save(EventWrite(event,
            mood = MoodEntryEntity(EventId.new().toString(), id.toString(), 2)))
        assertTrue(runCatching { moods.update(id, MoodInput(5, now)) }.isFailure)
        assertTrue(runCatching { moods.delete(id) }.isFailure)
        assertEquals(2, moods.get(id)!!.mood!!.score)
        assertEquals(EventSource.HEALTH_CONNECT, moods.get(id)!!.event.source)
    }
}
