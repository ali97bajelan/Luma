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
import com.luma.lifeos.domain.time.TimeRange
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PlacePersistenceTest {
    private lateinit var database: LumaDatabase
    private lateinit var places: RoomPlaceRepository
    private val now = Instant.parse("2026-10-11T08:00:00Z")
    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, LumaDatabase::class.java).build()
        places = RoomPlaceRepository(database)
    }
    @After fun tearDown() { database.close() }

    @Test fun placeHistoryUsesStableChronologicalOrder() = runBlocking {
        val place = UUID.randomUUID()
        places.create(place, PlaceInput("Cafe"))
        val journals = RoomJournalRepository(database)
        val high = EventId.parse("00000000-0000-0000-0000-000000000003")
        val low = EventId.parse("00000000-0000-0000-0000-000000000002")
        val early = EventId.parse("00000000-0000-0000-0000-000000000001")
        for ((id, time) in listOf(high to now, low to now, early to now.minusSeconds(60))) {
            journals.create(id, JournalInput("Entry $id", time, placeSelection = PlaceSelection(place)))
        }
        val history = places.observeHistory(place).first()!!
        assertEquals(places.get(place), history.place)
        assertEquals(listOf(early, low, high), history.events.map { it.event.id })
        assertTrue(history.events.all { it.place == history.place && it.journal != null })
        assertNull(places.observeHistory(UUID.randomUUID()).first())
    }

    @Test fun placeHistoryTracksCommittedLinkAndEventChanges() = runBlocking {
        val place = UUID.randomUUID()
        places.create(place, PlaceInput("Cafe"))
        val journals = RoomJournalRepository(database)
        val id = EventId.new()
        coroutineScope {
            val snapshots = Channel<PlaceHistory?>(Channel.UNLIMITED)
            val observer = launch { places.observeHistory(place).collect { snapshots.send(it) } }
            suspend fun awaitHistory(matches: (PlaceHistory?) -> Boolean) = withTimeout(5_000) {
                var value = snapshots.receive()
                while (!matches(value)) value = snapshots.receive()
                value
            }
            try {
                awaitHistory { it?.events?.isEmpty() == true }
                journals.create(id, JournalInput("Before", now, placeSelection = PlaceSelection(place)))
                awaitHistory { it?.events?.singleOrNull()?.journal?.body == "Before" }
                places.update(place, PlaceInput("Renamed"))
                val renamed = awaitHistory { it?.place?.name == "Renamed" }!!
                assertEquals(renamed.place, renamed.events.single().place)
                journals.update(id, JournalInput("After", now.minusSeconds(120)))
                val edited = awaitHistory { it?.events?.singleOrNull()?.journal?.body == "After" }!!
                assertEquals(now.minusSeconds(120), edited.events.single().event.occurredAt)
                assertEquals(edited.place, edited.events.single().place)
                journals.update(id, JournalInput("After", now, placeSelection = PlaceSelection(null)))
                awaitHistory { it?.events?.isEmpty() == true }
                journals.update(id, JournalInput("After", now, placeSelection = PlaceSelection(place)))
                awaitHistory { it?.events?.singleOrNull()?.event?.id == id }
                places.delete(place)
                awaitHistory { it == null }
                assertEquals("After", journals.get(id)!!.journal!!.body)
                assertNull(journals.get(id)!!.place)
            } finally {
                observer.cancel(); snapshots.close()
            }
        }
    }

    @Test fun retriesAndEqualNamesKeepIdentityAndEditingPreservesCoordinates() = runBlocking {
        val id = UUID.randomUUID(); val other = UUID.randomUUID()
        places.create(id, PlaceInput("  Cafe  ", "  Near home  "))
        places.create(id, PlaceInput("Cafe", "Near home"))
        places.create(other, PlaceInput("Cafe"))
        assertEquals(2, places.observePlaces().first().size)
        assertTrue(runCatching { places.create(id, PlaceInput("Different")) }.isFailure)
        database.catalog().upsertPlace(PlaceEntity(id.toString(), "Cafe", "Near home", 35.7, 51.4))
        places.update(id, PlaceInput("  Cafe edited  "))
        assertEquals("Cafe edited", places.get(id)!!.name)
        assertNull(places.get(id)!!.note)
        assertEquals(35.7, places.get(id)!!.latitude!!, 0.0)
        assertEquals(51.4, places.get(id)!!.longitude!!, 0.0)
        places.delete(id); places.delete(id)
        assertNull(places.get(id))
        assertTrue(runCatching { places.update(id, PlaceInput("Unexpected")) }.isFailure)
        assertEquals(other, places.observePlaces().first().single().id)
    }

    @Test fun capturesReuseOneOptionalPlaceAndPromotionPreservesSource() = runBlocking {
        val first = UUID.randomUUID(); val second = UUID.randomUUID()
        places.create(first, PlaceInput("Home")); places.create(second, PlaceInput("Cafe"))
        val moods = RoomMoodRepository(database); val journals = RoomJournalRepository(database); val memories = RoomMemoryRepository(database)
        val mood = EventId.new(); val journal = EventId.new(); val memory = EventId.new()
        val selection = PlaceSelection(first)
        val moodInput = MoodInput(4, now, placeSelection = selection)
        moods.create(mood, moodInput); moods.create(mood, moodInput)
        journals.create(journal, JournalInput("Body", now, placeSelection = selection))
        memories.create(memory, MemoryInput("Moment", now, placeSelection = selection))
        assertEquals(first, moods.get(mood)!!.place!!.id)
        assertEquals(first, journals.get(journal)!!.place!!.id)
        assertEquals(first, memories.get(memory)!!.place!!.id)
        assertTrue(runCatching { moods.create(mood, moodInput.copy(placeSelection = PlaceSelection(null))) }.isFailure)
        moods.update(mood, MoodInput(2, now)) // Absent selection preserves.
        memories.update(memory, MemoryInput("Edited", now))
        assertEquals(first, moods.get(mood)!!.place!!.id)
        assertEquals(first, memories.get(memory)!!.place!!.id)
        val original = database.lifeEvents().getDetails(journal.toString())!!
        memories.saveMarker(journal, MemoryMarkerInput(5))
        assertEquals(first, memories.get(journal)!!.place!!.id)
        memories.saveMarker(journal, MemoryMarkerInput(4, placeSelection = PlaceSelection(second)))
        val replaced = database.lifeEvents().getDetails(journal.toString())!!
        assertEquals(original.event, replaced.event)
        assertEquals(original.journal, replaced.journal)
        assertEquals(listOf(second.toString()), replaced.places.map { it.id })
        memories.removeMarker(journal)
        assertEquals(second, journals.get(journal)!!.place!!.id)
        moods.update(mood, MoodInput(3, now, placeSelection = PlaceSelection(null)))
        memories.update(memory, MemoryInput("Edited", now, placeSelection = PlaceSelection(second)))
        assertNull(moods.get(mood)!!.place)
        assertEquals(second, memories.get(memory)!!.place!!.id)
    }

    @Test fun placeDeletionKeepsEntireEventGraphExceptItsPlaceLink() = runBlocking {
        val place = UUID.randomUUID(); val person = UUID.randomUUID()
        places.create(place, PlaceInput("Home"))
        RoomPersonRepository(database).create(person, PersonInput("Friend"))
        val id = EventId.new(); val journals = RoomJournalRepository(database)
        journals.create(id, JournalInput("Keep", now, tagNames = setOf("Work"), personIds = setOf(person), placeSelection = PlaceSelection(place)))
        RoomMemoryRepository(database).saveMarker(id, MemoryMarkerInput(5, "Marker"))
        val original = database.lifeEvents().getDetails(id.toString())!!
        assertEquals(id, places.observeHistory(place).first()!!.events.single().event.id)
        places.delete(place)
        assertNull(places.observeHistory(place).first())
        assertEquals(original.copy(places = emptyList()), database.lifeEvents().getDetails(id.toString()))
        assertNull(journals.get(id)!!.place)
        assertEquals("Keep", journals.get(id)!!.journal!!.body)
        assertNotNull(journals.get(id)!!.memory)
        assertEquals(person, journals.get(id)!!.people.single().id)
    }

    @Test fun activeDetailAndFeedReflectPlaceRenameAndDeletion() = runBlocking {
        val place = UUID.randomUUID()
        places.create(place, PlaceInput("Cafe"))
        val id = EventId.new()
        RoomJournalRepository(database).create(id, JournalInput("Keep", now, placeSelection = PlaceSelection(place)))
        coroutineScope {
            val details = Channel<Place?>(Channel.UNLIMITED)
            val records = Channel<List<LifeEventRecord>>(Channel.UNLIMITED)
            val detailObserver = launch { places.observePlace(place).collect { details.send(it) } }
            val feedObserver = launch {
                RoomLifeEventRepository(database).observeRecords(TimeRange(now.minusSeconds(1), now.plusSeconds(1))).collect { records.send(it) }
            }
            suspend fun awaitDetail(matches: (Place?) -> Boolean) = withTimeout(5_000) {
                var value = details.receive()
                while (!matches(value)) value = details.receive()
                value
            }
            suspend fun awaitFeed(matches: (List<LifeEventRecord>) -> Boolean) = withTimeout(5_000) {
                var value = records.receive()
                while (!matches(value)) value = records.receive()
                value
            }
            try {
                awaitDetail { it?.name == "Cafe" }
                awaitFeed { it.singleOrNull()?.place?.name == "Cafe" }
                places.update(place, PlaceInput("Renamed"))
                awaitDetail { it?.name == "Renamed" }
                awaitFeed { it.singleOrNull()?.place?.name == "Renamed" }
                places.delete(place)
                awaitDetail { it == null }
                val remaining = awaitFeed { it.singleOrNull()?.place == null }
                assertEquals(id, remaining.single().event.id)
                assertEquals("Keep", remaining.single().journal!!.body)
            } finally {
                detailObserver.cancel(); feedObserver.cancel(); details.close(); records.close()
            }
        }
    }

    @Test fun missingPlaceRollsBackContentTagsPeopleAndCacheAndCaptureCreation() = runBlocking {
        val place = UUID.randomUUID(); val person = UUID.randomUUID()
        places.create(place, PlaceInput("Home")); RoomPersonRepository(database).create(person, PersonInput("Friend"))
        val id = EventId.new(); val journals = RoomJournalRepository(database)
        val input = JournalInput("Before", now, tagNames = setOf("Work"), personIds = setOf(person), placeSelection = PlaceSelection(place))
        journals.create(id, input)
        val original = database.lifeEvents().getDetails(id.toString())!!
        val derived = RoomDerivedDataRepository(database); val date = LocalDate.parse("2026-10-11")
        derived.rebuildSummary(date, ZoneOffset.UTC) { DailySummaryEntity(date.toString(), ZoneOffset.UTC.id, "{}", 1, now.toEpochMilli()) }
        assertTrue(runCatching { journals.update(id, input.copy(body = "Must rollback", tagNames = setOf("New"),
            personIds = emptySet(), placeSelection = PlaceSelection(UUID.randomUUID()))) }.isFailure)
        assertEquals(original, database.lifeEvents().getDetails(id.toString()))
        assertEquals(listOf("Work"), journals.observeTags().first().map { it.name })
        assertEquals(SummaryFreshness.FRESH, derived.summary(date, ZoneOffset.UTC)!!.freshness)
        val capture = EventId.new()
        assertTrue(runCatching { RoomMemoryRepository(database).create(capture, MemoryInput("Failed", now,
            placeSelection = PlaceSelection(UUID.randomUUID()))) }.isFailure)
        assertNull(database.lifeEvents().get(capture.toString()))
        journals.update(id, input.copy(placeSelection = PlaceSelection(null)))
        assertNull(journals.get(id)!!.place)
        assertEquals(SummaryFreshness.STALE, derived.summary(date, ZoneOffset.UTC)!!.freshness)
    }
}
