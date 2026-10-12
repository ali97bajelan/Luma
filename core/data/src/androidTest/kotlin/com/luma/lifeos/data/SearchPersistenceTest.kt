package com.luma.lifeos.data

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.entity.*
import com.luma.lifeos.data.repository.*
import com.luma.lifeos.domain.model.*
import com.luma.lifeos.domain.search.SearchQuery
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class SearchPersistenceTest {
    private lateinit var db: LumaDatabase
    private lateinit var search: RoomSearchRepository
    private val time = Instant.parse("2026-10-12T12:00:00Z")
    private val zone = ZoneId.of("UTC")
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, LumaDatabase::class.java).build()
        search = RoomSearchRepository(db)
    }
    @After fun cleanup() { db.close() }
    private suspend fun journal(n: Long, title: String?, body: String = "Body secret", tags: Set<String> = emptySet(), people: Set<UUID> = emptySet(), place: UUID? = null, occurred: Instant = time): EventId {
        val id = EventId(UUID(0, n))
        RoomJournalRepository(db).create(id, JournalInput(body, occurred, title, tags, people, PlaceSelection(place)))
        return id
    }
    private suspend fun ids(query: SearchQuery = SearchQuery(), offset: Int = 0, limit: Int = 50) =
        search.page(query, zone, offset, limit).records.map { it.event.id }

    @Test fun matchesOnlyTitlesAndLinkedNamesWithLiteralWildcardsAndPersian() = runBlocking {
        val person = UUID.randomUUID(); val place = UUID.randomUUID()
        RoomPersonRepository(db).create(person, PersonInput("آرش", note = "hidden person note"))
        RoomPlaceRepository(db).create(place, PlaceInput("Park", note = "hidden place note"))
        val title = journal(1, "100%_done 'quote' \\path")
        val linked = journal(2, null, tags = setOf("Weekend"), people = setOf(person), place = place)
        journal(3, "Unrelated", body = "100%_done آرش Weekend Park")
        for (text in listOf("%_", "'quote'", "\\path", "100%")) assertEquals(listOf(title), ids(SearchQuery(text)))
        for (text in listOf("آرش", "weekEND", "park")) assertEquals(listOf(linked), ids(SearchQuery(text)))
        assertTrue(ids(SearchQuery("Body secret")).isEmpty())
        assertTrue(ids(SearchQuery("hidden")).isEmpty())
    }
    @Test fun combinesCategoriesAndDeduplicatesAnySelectionWithinEachCategory() = runBlocking {
        val a = UUID.randomUUID(); val b = UUID.randomUUID(); val p = UUID.randomUUID()
        val people = RoomPersonRepository(db)
        people.create(a, PersonInput("Same")); people.create(b, PersonInput("Same"))
        RoomPlaceRepository(db).create(p, PlaceInput("Park"))
        val hit = journal(1, "Walk", tags = setOf("Work", "Weekend"), people = setOf(a, b), place = p)
        journal(2, "Walk", tags = setOf("Work"), people = setOf(a), occurred = time.minusSeconds(86_400))
        journal(3, "Other", tags = setOf("Work"), people = setOf(b), place = p)
        val options = search.observeOptions().first()
        assertEquals(2, options.people.size)
        val q = SearchQuery("walk", LocalDate.parse("2026-10-12"), LocalDate.parse("2026-10-12"),
            options.tags.map { it.id }.toSet(), setOf(a, b), p)
        val page = search.page(q, zone, 0, 1)
        assertEquals(1, page.totalCount); assertEquals(listOf(hit), page.records.map { it.event.id })
        RoomMemoryRepository(db).saveMarker(hit, MemoryMarkerInput(5, "Remember"))
        assertEquals(listOf(hit), ids(q)); assertEquals(3, search.page(SearchQuery(), zone, 0, 50).totalCount)
    }
    @Test fun orderingAndFiltersApplyBeforePagingAndKnownZeroUsageIsHidden() = runBlocking {
        val events = RoomLifeEventRepository(db)
        repeat(120) { n -> events.upsert(LifeEvent(EventId(UUID(0, n.toLong())), EventType.ACTIVITY, time, time, title = if (n % 2 == 0) "match" else "other")) }
        val page = search.page(SearchQuery("match"), zone, 50, 50)
        assertEquals(60, page.totalCount)
        assertEquals((18L downTo 0L step 2).map { EventId(UUID(0, it)) }, page.records.map { it.event.id })
        val zero = LifeEvent(EventId(UUID(0, 200)), EventType.APP_USAGE, time, time, endedAt = time.plusSeconds(60), source = EventSource.USAGE_STATS, sourceReference = "zero")
        RoomEventStore(db).save(EventWrite(zero, appUsage = AppUsageRecordEntity(UUID.randomUUID().toString(), zero.id.toString(), time.toEpochMilli(), time.plusSeconds(60).toEpochMilli(), "org.example", 0)))
        assertEquals(120, search.page(SearchQuery(), zone, 0, 50).totalCount)
        assertNotNull(RoomTimelineRepository(db).get(zero.id))
    }
    @Test fun renameDeleteAndTitleEditsInvalidateAndNeverBroadenMissingIdSelection() = runBlocking {
        val person = UUID.randomUUID(); val people = RoomPersonRepository(db)
        people.create(person, PersonInput("Before")); val event = journal(1, "Original", people = setOf(person))
        val initial = CompletableDeferred<Unit>()
        val observed = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5_000) { search.observeChanges().onEach { initial.complete(Unit) }.take(2).toList() }
        }
        initial.await(); people.update(person, PersonInput("After")); assertEquals(2, observed.await().size)
        assertTrue(ids(SearchQuery("Before")).isEmpty()); assertEquals(listOf(event), ids(SearchQuery("After")))
        RoomJournalRepository(db).update(event, JournalInput("Body secret", time, "Updated"))
        assertTrue(ids(SearchQuery("Original")).isEmpty()); assertEquals(listOf(event), ids(SearchQuery("Updated")))
        people.delete(person)
        assertTrue(ids(SearchQuery(personIds = setOf(person))).isEmpty()); assertEquals(listOf(event), ids())
        RoomLifeEventRepository(db).delete(event); assertTrue(ids().isEmpty())
    }
    @Test fun dstDateHasNoNextMidnightLeakAndSkippedDayHasNoMatches() = runBlocking {
        journal(1, "Inside", occurred = Instant.parse("2026-03-09T03:59:59.999Z"))
        journal(2, "Outside", occurred = Instant.parse("2026-03-09T04:00:00Z"))
        val query = SearchQuery(from = LocalDate.parse("2026-03-08"), through = LocalDate.parse("2026-03-08"))
        assertEquals(listOf(EventId(UUID(0, 1))), search.page(query, ZoneId.of("America/New_York"), 0, 50).records.map { it.event.id })
        assertTrue(search.page(SearchQuery(from = LocalDate.parse("2011-12-30"), through = LocalDate.parse("2011-12-30")), ZoneId.of("Pacific/Apia"), 0, 50).records.isEmpty())
    }
    @Test fun fiftyThousandRowsKeepSelectiveBroadAndLaterPagesBounded() = runBlocking {
        db.withTransaction {
            repeat(50_000) { n ->
                val occurred = time.minusSeconds((n % 1825).toLong() * 86_400).toEpochMilli()
                val id = UUID(10, n.toLong()).toString()
                db.lifeEvents().insert(LifeEventEntity(id, EventType.JOURNAL, occurred, occurred, occurred, title = "Fixture $n"))
                db.content().insertJournal(JournalEntryEntity(UUID(11, n.toLong()).toString(), id, "Fixture $n", "Private body ".repeat(100)))
            }
        }
        val samples = mutableListOf<Double>()
        for (query in listOf(SearchQuery(), SearchQuery("Fixture 49"), SearchQuery("No match"), SearchQuery(from = LocalDate.parse("2026-10-01")))) {
            repeat(8) { n ->
                val started = android.os.SystemClock.elapsedRealtimeNanos()
                val result = search.page(query, zone, if (n % 2 == 0) 0 else 200, 50)
                samples += (android.os.SystemClock.elapsedRealtimeNanos() - started) / 1_000_000.0
                assertTrue(result.records.size <= 50)
            }
        }
        android.util.Log.i("LumaValidation", "Search rows=50000 page_ms=$samples")
        assertEquals(50_000, search.page(SearchQuery(), zone, 0, 50).totalCount)
    }
}
