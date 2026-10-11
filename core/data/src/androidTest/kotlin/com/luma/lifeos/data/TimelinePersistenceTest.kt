package com.luma.lifeos.data

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.entity.*
import com.luma.lifeos.data.repository.*
import com.luma.lifeos.domain.model.*
import com.luma.lifeos.domain.time.TimeRange
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import org.junit.*
import org.junit.Assert.*

class TimelinePersistenceTest {
    private lateinit var db: LumaDatabase
    private lateinit var timeline: RoomTimelineRepository
    private val time = Instant.parse("2026-10-11T08:00:00Z")
    private val range = TimeRange.forDay(LocalDate.parse("2026-10-11"), ZoneId.of("UTC"))
    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, LumaDatabase::class.java).build()
        timeline = RoomTimelineRepository(db)
    }
    @After fun tearDown() { db.close() }
    @Test fun indexedMixedPagesAreBoundedDeterministicAndExcludeOtherDays() = runBlocking {
        db.withTransaction {
            // Reverse insertion and equal occurrence times catch reliance on insertion order.
            (2_000 downTo 0).forEach { n ->
                val type = if (n % 2 == 0) EventType.ACTIVITY else EventType.JOURNAL
                db.lifeEvents().insert(LifeEventEntity(UUID(0, n.toLong()).toString(), type, time.toEpochMilli(),
                    createdAt = time.toEpochMilli(), updatedAt = time.toEpochMilli()))
            }
            val outside = time.plusSeconds(86_400).toEpochMilli()
            db.lifeEvents().insert(LifeEventEntity(UUID.randomUUID().toString(), EventType.MEMORY, outside, createdAt = outside, updatedAt = outside))
        }
        val first = timeline.page(range, 0, 50); val next = timeline.page(range, 50, 50)
        assertEquals(2_001, first.totalCount)
        assertEquals((0L..49L).toList(), first.records.map { it.event.id.value.leastSignificantBits })
        assertEquals((50L..99L).toList(), next.records.map { it.event.id.value.leastSignificantBits })
        assertEquals(1, timeline.page(range, 2_000, 50).records.size)
        assertTrue(timeline.page(range, 2_100, 50).records.isEmpty())
        assertTrue(runCatching { timeline.page(range, -1, 50) }.isFailure)
        assertTrue(runCatching { timeline.page(range, 0, 301) }.isFailure)
        db.openHelper.readableDatabase.query("EXPLAIN QUERY PLAN SELECT * FROM life_event WHERE occurred_at >= ? AND occurred_at < ? ORDER BY occurred_at, id LIMIT 50 OFFSET 50",
            arrayOf(range.start.toEpochMilli(), range.endExclusive.toEpochMilli())).use { cursor ->
            val descriptions = buildList { while (cursor.moveToNext()) add(cursor.getString(3)) }
            assertTrue(descriptions.any { "index_life_event_occurred_at_id" in it })
            assertFalse(descriptions.any { "TEMP B-TREE" in it })
        }
    }
    @Test fun realSignalsAndFullManualGraphsAppearWithoutDuplicatingPromotion() = runBlocking {
        val store = RoomEventStore(db)
        val activity = LifeEvent(EventId.new(), EventType.ACTIVITY, time, time, source = EventSource.HEALTH_CONNECT, sourceReference = "steps:1")
        store.save(EventWrite(activity, activities = listOf(ActivityRecordEntity(UUID.randomUUID().toString(), activity.id.toString(), "steps", 120.0, "count", activity.source, "steps:1"))))
        val usage = LifeEvent(EventId.new(), EventType.APP_USAGE, time, time, endedAt = time.plusSeconds(60), source = EventSource.USAGE_STATS, sourceReference = "usage:1")
        store.save(EventWrite(usage, appUsage = AppUsageRecordEntity(UUID.randomUUID().toString(), usage.id.toString(), time.toEpochMilli(), time.plusSeconds(60).toEpochMilli(), "org.example.app", 5_125)))
        val journal = EventId.new()
        val person = UUID.randomUUID(); val place = UUID.randomUUID()
        RoomPersonRepository(db).create(person, PersonInput("Friend"))
        RoomPlaceRepository(db).create(place, PlaceInput("Park"))
        RoomJournalRepository(db).create(journal, JournalInput("Keep body", time, tagNames = setOf("Work"),
            personIds = setOf(person), placeSelection = PlaceSelection(place)))
        RoomMemoryRepository(db).saveMarker(journal, MemoryMarkerInput(5, "Marker"))
        val page = timeline.page(range, 0, 50)
        assertEquals(3, page.totalCount)
        assertEquals(120.0, page.records.single { it.event.id == activity.id }.activities.single().value, 0.0)
        assertEquals(5_125L, page.records.single { it.event.id == usage.id }.appUsage?.durationMillis)
        assertEquals("org.example.app", timeline.get(usage.id)?.appUsage?.packageName)
        val source = timeline.get(journal)!!
        assertEquals("Keep body", source.journal?.body); assertEquals("Work", source.journal?.tags?.single()?.name)
        assertEquals("Marker", source.memory?.note)
        assertEquals("Friend", source.people.single().name); assertEquals("Park", source.place?.name)
        assertNull(timeline.get(EventId.new()))
    }
    @Test fun typedOnlyChangesInvalidatePages() = runBlocking {
        val journal = EventId.new(); RoomJournalRepository(db).create(journal, JournalInput("Before", time))
        val initial = CompletableDeferred<Unit>()
        val changes = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5_000) { timeline.observeChanges().onEach { initial.complete(Unit) }.take(2).toList() }
        }
        initial.await()
        db.withTransaction { db.openHelper.writableDatabase.execSQL("UPDATE journal_entry SET body = 'After' WHERE life_event_id = ?", arrayOf(journal.toString())) }
        assertEquals(2, changes.await().size)
        assertEquals("After", timeline.page(range, 0, 50).records.single().journal?.body)
    }
    @Test fun catalogRenameInvalidatesTheLinkedGraphWithoutChangingEventEnvelope() = runBlocking {
        val person = UUID.randomUUID(); val people = RoomPersonRepository(db)
        people.create(person, PersonInput("Before"))
        val journal = EventId.new()
        RoomJournalRepository(db).create(journal, JournalInput("Body", time, personIds = setOf(person)))
        val envelope = timeline.get(journal)!!.event
        val initial = CompletableDeferred<Unit>()
        val changes = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeout(5_000) { timeline.observeChanges().onEach { initial.complete(Unit) }.take(2).toList() }
        }
        initial.await(); people.update(person, PersonInput("After"))
        assertEquals(2, changes.await().size)
        val row = timeline.page(range, 0, 50).records.single()
        assertEquals(envelope, row.event); assertEquals("After", row.people.single().name)
    }
    @Test fun dstHalfOpenDayHasNoMidnightLeak() = runBlocking {
        val day = TimeRange.forDay(LocalDate.parse("2026-03-08"), ZoneId.of("America/New_York"))
        val repo = RoomLifeEventRepository(db)
        val inside = LifeEvent(EventId.new(), EventType.ACTIVITY, day.endExclusive.minusMillis(1), time)
        repo.upsert(inside); repo.upsert(inside.copy(id = EventId.new(), occurredAt = day.endExclusive))
        assertEquals(listOf(inside.id), timeline.page(day, 0, 50).records.map { it.event.id })
    }
}
