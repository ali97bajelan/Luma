package com.luma.lifeos.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.entity.*
import com.luma.lifeos.data.repository.*
import com.luma.lifeos.domain.model.*
import com.luma.lifeos.domain.summary.*
import java.time.*
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*

class DailySummaryPersistenceTest {
    private lateinit var db: LumaDatabase
    private lateinit var summaries: RoomDailySummaryRepository
    private val date = LocalDate.parse("2026-10-11")
    private val now = Instant.parse("2026-10-11T12:00:00Z")
    private val zone = ZoneOffset.UTC
    private val clock = Clock.fixed(now, zone)
    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, LumaDatabase::class.java).build()
        summaries = RoomDailySummaryRepository(db, clock)
    }
    @After fun tearDown() { db.close() }
    @Test fun mixedMetricsAreCachedWithoutPrivateContentAndRebuildIdentically() = runBlocking {
        val mood = EventId.new(); RoomMoodRepository(db, clock).create(mood, MoodInput(4, now, energy = 2))
        val journal = EventId.new(); RoomJournalRepository(db, clock).create(journal, JournalInput("private-body-do-not-cache", now))
        RoomMemoryRepository(db, clock).saveMarker(journal, MemoryMarkerInput(note = "private-marker-do-not-cache"))
        val usage = RoomUsageRepository(db); usage.setEnabled(true)
        usage.commit(listOf(com.luma.lifeos.domain.usage.UsageBucket("org.example", date.atStartOfDay(zone).toInstant(), now, 5_125, "Example")), now, now)
        val activity = LifeEvent(EventId.new(), EventType.ACTIVITY, now, now)
        RoomEventStore(db).save(EventWrite(activity, activities = listOf(ActivityRecordEntity(UUID.randomUUID().toString(), activity.id.toString(), "steps", 1200.0, "count", EventSource.MANUAL))))
        val result = summaries.get(date, zone)
        assertEquals(4, result.records); assertEquals(1, result.journals); assertEquals(1, result.memories)
        assertEquals(4.0, result.mood!!.score.average, 0.0); assertEquals(5_125L, result.usage!!.totalMillis)
        assertEquals("1200", result.activities!!.single().total.toPlainString())
        val cache = db.derived().summary(date.toString(), zone.id)!!
        assertEquals(SummaryFreshness.FRESH, cache.freshness)
        assertFalse(cache.metricsJson.contains("private-body")); assertFalse(cache.metricsJson.contains("private-marker"))
        assertEquals(result, summaries.recalculate(date, zone))
        db.derived().deleteSummary(date.toString(), zone.id)
        assertEquals(result, RoomDailySummaryRepository(db, clock).get(date, zone))
    }
    @Test fun freshCacheIsReusableAndCorruptOrOldCacheRebuilds() = runBlocking {
        val result = summaries.get(date, zone); val original = db.derived().summary(date.toString(), zone.id)!!
        val later = RoomDailySummaryRepository(db, Clock.fixed(now.plusSeconds(60), zone))
        assertEquals(result, later.get(date, zone)); assertEquals(original.computedAt, db.derived().summary(date.toString(), zone.id)!!.computedAt)
        db.derived().upsertSummary(original.copy(metricsJson = "{broken"))
        assertEquals(result, later.get(date, zone)); assertEquals(now.plusSeconds(60).toEpochMilli(), db.derived().summary(date.toString(), zone.id)!!.computedAt)
        db.derived().upsertSummary(original.copy(algorithmVersion = 99))
        assertEquals(result, later.get(date, zone)); assertEquals(DAILY_SUMMARY_VERSION, db.derived().summary(date.toString(), zone.id)!!.algorithmVersion)
        assertNull(result.mood); assertNull(result.usage); assertNull(result.activities)
    }
    @Test fun capturePromotionEditsAndDeletionInvalidateAndReplaceMetrics() = runBlocking {
        val moods = RoomMoodRepository(db, clock); val id = EventId.new()
        summaries.get(date, zone); moods.create(id, MoodInput(2, now))
        assertEquals(SummaryFreshness.STALE, db.derived().summary(date.toString(), zone.id)!!.freshness)
        assertEquals(2.0, summaries.get(date, zone).mood!!.score.average, 0.0)
        RoomMemoryRepository(db, clock).saveMarker(id, MemoryMarkerInput())
        assertEquals(1, summaries.get(date, zone).memories)
        moods.update(id, MoodInput(5, now))
        assertEquals(5.0, summaries.get(date, zone).mood!!.score.average, 0.0)
        RoomLifeEventRepository(db).delete(id)
        val empty = summaries.get(date, zone); assertEquals(0, empty.records); assertNull(empty.mood); assertEquals(0, empty.memories)
    }
    @Test fun nativeUsageStartIsIncludedEvenWithAnOutsideParentOccurrence() = runBlocking {
        val start = date.atStartOfDay(zone).toInstant()
        val event = LifeEvent(EventId.new(), EventType.APP_USAGE, start.minusSeconds(86_400), now,
            source = EventSource.USAGE_STATS, sourceReference = "legacy-usage")
        RoomLifeEventRepository(db).upsert(event)
        db.content().insertAppUsage(AppUsageRecordEntity(UUID.randomUUID().toString(), event.id.toString(), start.toEpochMilli(), now.toEpochMilli(), "org.example", 5_125))
        val result = summaries.get(date, zone)
        assertEquals(0, result.records); assertEquals(5_125L, result.usage!!.totalMillis)
        assertNull(summaries.get(date.minusDays(1), zone).usage)
    }
    @Test fun failedRebuildRetainsTheOldCacheAndRawRecords() = runBlocking {
        val start = date.atStartOfDay(zone).toInstant()
        val source = RoomUsageRepository(db); source.setEnabled(true)
        source.commit(listOf(com.luma.lifeos.domain.usage.UsageBucket("org.example", start, now, 5_125)), now, now)
        summaries.get(date, zone); val previous = db.derived().summary(date.toString(), zone.id)!!
        val event = LifeEvent(EventId.new(), EventType.APP_USAGE, start, now, endedAt = now.plusSeconds(1),
            source = EventSource.USAGE_STATS, sourceReference = "conflicting-legacy-usage")
        RoomLifeEventRepository(db).upsert(event)
        db.content().insertAppUsage(AppUsageRecordEntity(UUID.randomUUID().toString(), event.id.toString(), start.toEpochMilli(), now.plusSeconds(1).toEpochMilli(), "org.example", 6_000))
        assertTrue(runCatching { summaries.recalculate(date, zone) }.isFailure)
        assertEquals(previous.metricsJson, db.derived().summary(date.toString(), zone.id)!!.metricsJson)
        assertNotNull(db.lifeEvents().get(event.id.toString()))
    }
    @Test fun observationRespondsToTypedWritesAndRestartedCollectionRebuilds() = runBlocking {
        withTimeout(10_000) {
            val id = EventId.new(); val initial = CompletableDeferred<Unit>()
            val changed = async { summaries.observe(date, zone).onEach { if (it.records == 0) initial.complete(Unit) }.first { it.mood?.score?.sum == 4L } }
            initial.await(); RoomMoodRepository(db, clock).create(id, MoodInput(4, now))
            assertEquals(1, changed.await().records)
            RoomMoodRepository(db, clock).update(id, MoodInput(5, now))
            assertEquals(5L, summaries.observe(date, zone).first().mood!!.score.sum)
        }
    }
    @Test fun zoneKeysAndSkippedCivilDaysDoNotInventSignals() = runBlocking {
        RoomMoodRepository(db, clock).create(EventId.new(), MoodInput(4, date.atStartOfDay(zone).toInstant()))
        assertNotNull(summaries.get(date, zone).mood)
        assertNull(summaries.get(date, ZoneId.of("America/New_York")).mood)
        assertEquals(4L, summaries.get(date.minusDays(1), ZoneId.of("America/New_York")).mood!!.score.sum)
        val skipped = summaries.get(LocalDate.parse("2011-12-30"), ZoneId.of("Pacific/Apia"))
        assertEquals(0, skipped.records); assertNull(skipped.activities)
    }
    @Test fun concurrentCaptureCannotLeaveAnOutdatedFreshCache() = runBlocking {
        val entered = CompletableDeferred<Unit>(); val release = CountDownLatch(1)
        val blockingClock = object : Clock() {
            override fun getZone(): ZoneId = this@DailySummaryPersistenceTest.zone
            override fun withZone(zone: ZoneId): Clock = this
            override fun instant(): Instant = now
            override fun millis(): Long { entered.complete(Unit); check(release.await(10, TimeUnit.SECONDS)); return now.toEpochMilli() }
        }
        val rebuilding = async(Dispatchers.Default) { RoomDailySummaryRepository(db, blockingClock).recalculate(date, zone) }
        try {
            withTimeout(10_000) { entered.await() }
            val started = CompletableDeferred<Unit>()
            val capture = async(Dispatchers.Default) { started.complete(Unit); RoomMoodRepository(db, clock).create(EventId.new(), MoodInput(4, now)) }
            started.await(); release.countDown(); rebuilding.await(); capture.await()
            assertEquals(SummaryFreshness.STALE, db.derived().summary(date.toString(), zone.id)!!.freshness)
            assertEquals(4L, summaries.get(date, zone).mood!!.score.sum)
        } finally { release.countDown() }
    }
}
