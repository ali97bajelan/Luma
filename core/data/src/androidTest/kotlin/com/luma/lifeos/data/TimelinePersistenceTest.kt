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
        val usage = LifeEvent(EventId.new(), EventType.APP_USAGE, time, time, endedAt = time.plusSeconds(200), source = EventSource.USAGE_STATS, sourceReference = "usage:1")
        store.save(EventWrite(usage, appUsage = AppUsageRecordEntity(UUID.randomUUID().toString(), usage.id.toString(), time.toEpochMilli(), time.plusSeconds(200).toEpochMilli(), "org.example.app", 125_125)))
        val journal = EventId.new()
        val person = UUID.randomUUID(); val place = UUID.randomUUID()
        RoomPersonRepository(db).create(person, PersonInput("Friend"))
        RoomPlaceRepository(db).create(place, PlaceInput("Park"))
        RoomJournalRepository(db).create(journal, JournalInput("Keep body", time, tagNames = setOf("Work"),
            personIds = setOf(person), placeSelection = PlaceSelection(place)))
        RoomMemoryRepository(db).saveMarker(journal, MemoryMarkerInput(5, "Marker"))
        val page = timeline.page(range, 0, 50)
        assertEquals(3, page.totalCount)
        assertEquals(2, page.eventCount)
        assertEquals(120.0, page.records.single { it.event.id == activity.id }.activities.single().value, 0.0)
        assertEquals(125_125L, page.records.single { it.event.id == usage.id }.appUsage?.durationMillis)
        assertEquals("org.example.app", timeline.get(usage.id)?.appUsage?.packageName)
        val source = timeline.get(journal)!!
        assertEquals("Keep body", source.journal?.body); assertEquals("Work", source.journal?.tags?.single()?.name)
        assertEquals("Marker", source.memory?.note)
        assertEquals("Friend", source.people.single().name); assertEquals("Park", source.place?.name)
        assertNull(timeline.get(EventId.new()))
    }
    @Test fun timelineIncludesOnlyUsageBucketsLongerThanOneMinute() = runBlocking {
        val store = RoomEventStore(db)
        suspend fun usage(n: Int, durationMillis: Long?) {
            val start = time.plusSeconds(n.toLong())
            val end = start.plusMillis(maxOf(durationMillis ?: 5_000, 1))
            val event = LifeEvent(EventId(UUID(0, n.toLong())), EventType.APP_USAGE, start, start,
                endedAt = end, source = EventSource.USAGE_STATS, sourceReference = "threshold:$n")
            val child = durationMillis?.let {
                AppUsageRecordEntity(UUID(1, n.toLong()).toString(), event.id.toString(), start.toEpochMilli(), end.toEpochMilli(),
                    "org.example.app$n", it)
            }
            store.save(EventWrite(event, appUsage = child))
        }
        usage(1, 60_000)
        usage(2, 60_001)
        usage(3, 0)
        usage(4, null)
        val manual = LifeEvent(EventId(UUID(0, 5)), EventType.MOOD, time.plusSeconds(5), time.plusSeconds(5))
        RoomLifeEventRepository(db).upsert(manual)

        val page = timeline.page(range, 0, 50)

        assertEquals(2, page.totalCount)
        assertEquals(1, page.eventCount)
        assertEquals(setOf(EventId(UUID(0, 2)), manual.id), page.records.map { it.event.id }.toSet())
    }
    @Test fun eventCountExcludesUsageAcrossAllPagesAndIncludesOtherTypes() = runBlocking {
        val store = RoomEventStore(db)
        repeat(160) { index ->
            val start = time.plusMillis(index.toLong())
            val end = start.plusSeconds(61)
            val event = LifeEvent(EventId(UUID(0, index.toLong())), EventType.APP_USAGE, start, start,
                endedAt = end, source = EventSource.USAGE_STATS, sourceReference = "usage:$index")
            store.save(EventWrite(event, appUsage = AppUsageRecordEntity(UUID(1, index.toLong()).toString(), event.id.toString(),
                start.toEpochMilli(), end.toEpochMilli(), "org.example.app$index", 60_001)))
        }
        RoomLifeEventRepository(db).upsert(LifeEvent(EventId(UUID(0, 200)), EventType.MOOD, time, time))
        val first = timeline.page(range, 0, 50)
        assertEquals(161, first.totalCount); assertEquals(1, first.eventCount)
        val last = timeline.page(range, 150, 50)
        assertEquals(11, last.records.size); assertEquals(1, last.eventCount)
    }
    @Test fun shortAndMissingUsageAreExcludedBeforePagingWhileLongUsageRemains() = runBlocking {
        val store = RoomEventStore(db)
        suspend fun usage(n: Int, duration: Long) {
            val event = LifeEvent(EventId(UUID(0, n.toLong())), EventType.APP_USAGE, time, time,
                endedAt = time.plusSeconds(61), source = EventSource.USAGE_STATS, sourceReference = "fixture:$n")
            store.save(EventWrite(event, appUsage = AppUsageRecordEntity(UUID(1, n.toLong()).toString(),
                event.id.toString(), time.toEpochMilli(), time.plusSeconds(61).toEpochMilli(), "org.example.app$n", duration)))
        }
        repeat(160) { usage(it, 0) }
        usage(200, 60_000); usage(201, 60_001)
        RoomLifeEventRepository(db).upsert(LifeEvent(EventId(UUID(0, 300)), EventType.MOOD, time, time))
        RoomLifeEventRepository(db).upsert(LifeEvent(EventId(UUID(0, 400)), EventType.APP_USAGE, time, time))
        val first = timeline.page(range, 0, 2); val last = timeline.page(range, 2, 2)
        assertEquals(2, first.totalCount); assertEquals(1, first.eventCount)
        assertEquals(listOf(201L, 300L), first.records.map { it.event.id.value.leastSignificantBits })
        assertTrue(last.records.isEmpty())
        assertEquals(0L, timeline.get(EventId(UUID(0, 0)))!!.appUsage!!.durationMillis)
        db.openHelper.writableDatabase.execSQL("UPDATE app_usage_record SET duration_ms = 60_001 WHERE life_event_id = ?",
            arrayOf(EventId(UUID(0, 0)).toString()))
        assertEquals(3, timeline.page(range, 0, 2).totalCount)
        assertEquals(0L, timeline.page(range, 0, 2).records.first().event.id.value.leastSignificantBits)
    }
    @Test fun aDayOfOnlyUnusedAppsHasNoTimelineRowsOrPagingTotal() = runBlocking {
        val event = LifeEvent(EventId.new(), EventType.APP_USAGE, time, time,
            endedAt = time.plusSeconds(60), source = EventSource.USAGE_STATS, sourceReference = "unused-only")
        RoomEventStore(db).save(EventWrite(event, appUsage = AppUsageRecordEntity(UUID.randomUUID().toString(),
            event.id.toString(), time.toEpochMilli(), time.plusSeconds(60).toEpochMilli(), "org.example.unused", 0)))
        val page = timeline.page(range, 0, 50)
        assertTrue(page.records.isEmpty()); assertEquals(0, page.totalCount); assertEquals(0, page.eventCount)
        assertNotNull(timeline.get(event.id))
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
