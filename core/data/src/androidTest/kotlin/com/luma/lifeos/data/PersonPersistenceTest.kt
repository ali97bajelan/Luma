package com.luma.lifeos.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.entity.*
import com.luma.lifeos.data.repository.*
import com.luma.lifeos.domain.model.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.channels.Channel
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PersonPersistenceTest {
    private lateinit var database: LumaDatabase
    private lateinit var people: RoomPersonRepository
    private val now = Instant.parse("2026-10-11T08:00:00Z")
    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, LumaDatabase::class.java).build()
        people = RoomPersonRepository(database)
    }
    @After fun tearDown() { database.close() }

    @Test fun catalogCreateRetriesDistinctIdentitiesAndEditsPreservePhotoUri() = runBlocking {
        val id = UUID.randomUUID()
        val input = PersonInput("  Alex  ", "  Friend  ")
        people.create(id, input); people.create(id, input)
        assertEquals("Alex", people.get(id)!!.name)
        assertEquals("Friend", people.get(id)!!.note)
        val other = UUID.randomUUID()
        people.create(other, input)
        assertEquals(2, people.observePeople().first().size)
        assertTrue(runCatching { people.create(id, input.copy(name = "Other")) }.isFailure)
        database.catalog().upsertPerson(PersonEntity(id.toString(), "Alex", "Friend", "content://private/photo"))
        people.update(id, PersonInput("Alex edited"))
        assertNull(people.get(id)!!.note)
        assertEquals("content://private/photo", people.get(id)!!.photoUri)
        people.delete(id); people.delete(id)
        assertNull(people.get(id))
        assertTrue(runCatching { people.update(id, input) }.isFailure)
        assertEquals(other, people.observePeople().first().single().id)
    }

    @Test fun multiplePeopleLinkAllCaptureTypesAndHistoryKeepsChronologicalSourceGraphs() = runBlocking {
        val first = UUID.randomUUID(); val second = UUID.randomUUID()
        people.create(first, PersonInput("First")); people.create(second, PersonInput("Second"))
        val ids = setOf(first, second)
        val moods = RoomMoodRepository(database); val journals = RoomJournalRepository(database); val memories = RoomMemoryRepository(database)
        val mood = EventId.new(); val journal = EventId.new(); val memory = EventId.new()
        journals.create(journal, JournalInput("Body", now.plusSeconds(60), personIds = ids))
        moods.create(mood, MoodInput(4, now, personIds = ids))
        memories.create(memory, MemoryInput("Moment", now.plusSeconds(120), personIds = ids))
        val history = people.observeHistory(first).first()!!
        assertEquals(listOf(mood, journal, memory), history.events.map { it.event.id })
        assertEquals("Body", history.events[1].journal!!.body)
        assertTrue(history.events.all { it.people.map { person -> person.id }.toSet() == ids })
        memories.saveMarker(journal, MemoryMarkerInput(5))
        assertEquals(ids, memories.get(journal)!!.people.map { it.id }.toSet())
        memories.saveMarker(journal, MemoryMarkerInput(personIds = setOf(second)))
        assertEquals(setOf(second), memories.get(journal)!!.people.map { it.id }.toSet())
        assertEquals(listOf(mood, memory), people.observeHistory(first).first()!!.events.map { it.event.id })
        assertEquals("Body", journals.get(journal)!!.journal!!.body)
        moods.update(mood, MoodInput(2, now.plusSeconds(180))) // Null preserves previous people.
        assertEquals(ids, moods.get(mood)!!.people.map { it.id }.toSet())
        assertEquals(listOf(memory, mood), people.observeHistory(first).first()!!.events.map { it.event.id })
        memories.update(memory, MemoryInput("Edited moment", now.plusSeconds(120), personIds = emptySet()))
        assertTrue(memories.get(memory)!!.people.isEmpty())
    }

    @Test fun deletingPersonRemovesOnlyTheirLinksAndRetainsEventsDetailsAndOtherPeople() = runBlocking {
        val first = UUID.randomUUID(); val second = UUID.randomUUID()
        people.create(first, PersonInput("First")); people.create(second, PersonInput("Second"))
        val id = EventId.new()
        val journals = RoomJournalRepository(database)
        journals.create(id, JournalInput("Keep body", now, tagNames = setOf("Work"), personIds = setOf(first, second)))
        val memories = RoomMemoryRepository(database)
        memories.saveMarker(id, MemoryMarkerInput(5, "Keep marker"))
        val original = database.lifeEvents().getDetails(id.toString())!!
        people.delete(first)
        val remaining = database.lifeEvents().getDetails(id.toString())!!
        assertEquals(original.copy(people = original.people.filter { it.id != first.toString() }), remaining)
        assertNull(people.observeHistory(first).first())
        assertEquals(id, people.observeHistory(second).first()!!.events.single().event.id)
        assertEquals("Keep body", journals.get(id)!!.journal!!.body)
        assertNotNull(memories.get(id)!!.memory)
    }

    @Test fun openHistoryUpdatesForCatalogContentMarkersAndLinkChanges() = runBlocking {
        val person = UUID.randomUUID()
        people.create(person, PersonInput("Friend"))
        val journals = RoomJournalRepository(database)
        val id = EventId.new()
        journals.create(id, JournalInput("Before", now, personIds = setOf(person)))
        coroutineScope {
            val emissions = Channel<PersonHistory?>(Channel.UNLIMITED)
            val observing = launch { people.observeHistory(person).collect { emissions.send(it) } }
            suspend fun awaitHistory(matches: (PersonHistory?) -> Boolean): PersonHistory? = withTimeout(5_000) {
                var next = emissions.receive()
                while (!matches(next)) next = emissions.receive()
                next
            }
            try {
                awaitHistory { it?.events?.singleOrNull()?.journal?.body == "Before" }
                people.update(person, PersonInput("Renamed"))
                awaitHistory { it?.person?.name == "Renamed" }
                journals.update(id, JournalInput("After", now))
                awaitHistory { it?.events?.singleOrNull()?.journal?.body == "After" }
                RoomMemoryRepository(database).saveMarker(id, MemoryMarkerInput(5))
                awaitHistory { it?.events?.singleOrNull()?.memory?.importance == 5 }
                journals.update(id, JournalInput("After", now, personIds = emptySet()))
                awaitHistory { it?.events?.isEmpty() == true }
                people.delete(person)
                awaitHistory { it == null }
            } finally { observing.cancel(); emissions.close() }
        }
    }

    @Test fun missingPersonRollsBackEntryContentLinksCatalogAndSummaryChanges() = runBlocking {
        val person = UUID.randomUUID()
        people.create(person, PersonInput("Friend"))
        val journals = RoomJournalRepository(database)
        val id = EventId.new()
        val input = JournalInput("Original", now, tagNames = setOf("Work"), personIds = setOf(person))
        journals.create(id, input); journals.create(id, input)
        val original = database.lifeEvents().getDetails(id.toString())!!
        val derived = RoomDerivedDataRepository(database)
        val date = LocalDate.parse("2026-10-11")
        derived.rebuildSummary(date, ZoneOffset.UTC) { DailySummaryEntity(date.toString(), ZoneOffset.UTC.id, "{}", 1, now.toEpochMilli()) }
        assertTrue(runCatching {
            journals.update(id, input.copy(body = "Must rollback", tagNames = setOf("New"), personIds = setOf(UUID.randomUUID())))
        }.isFailure)
        assertEquals(original, database.lifeEvents().getDetails(id.toString()))
        assertEquals(listOf("Work"), journals.observeTags().first().map { it.name })
        assertEquals(SummaryFreshness.FRESH, derived.summary(date, ZoneOffset.UTC)!!.freshness)
        val missingId = EventId.new()
        assertTrue(runCatching { RoomMoodRepository(database).create(missingId, MoodInput(3, now, personIds = setOf(UUID.randomUUID()))) }.isFailure)
        assertNull(database.lifeEvents().get(missingId.toString()))
        journals.update(id, input.copy(personIds = emptySet()))
        assertTrue(journals.get(id)!!.people.isEmpty())
        assertEquals(SummaryFreshness.STALE, derived.summary(date, ZoneOffset.UTC)!!.freshness)
    }
}
