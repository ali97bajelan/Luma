package com.luma.lifeos.data

import androidx.room.withTransaction
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.entity.*
import com.luma.lifeos.data.repository.*
import com.luma.lifeos.domain.model.*
import com.luma.lifeos.domain.review.*
import com.luma.lifeos.domain.usage.*
import java.time.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*

class WeeklyReviewPersistenceTest {
    private lateinit var db: LumaDatabase
    private lateinit var reviews: RoomWeeklyReviewRepository
    private val today = LocalDate.parse("2026-10-14")
    private val zone = ZoneOffset.UTC
    private val now = today.atTime(12, 0).toInstant(zone)
    private val clock = Clock.fixed(now, zone)
    private val period = reviewPeriod(today, today, zone)
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, LumaDatabase::class.java).build()
        reviews = RoomWeeklyReviewRepository(db)
    }
    @After fun close() { db.close() }
    private suspend fun report() = reviews.observe(period).first()
    private suspend fun sources(kind: ReviewMetricKind, id: UUID? = null) = reviews.sources(period.current, zone, ReviewMetric(kind, id), 0)
    @Test fun weightedMetricsAndEveryTypedSourceAgreeWithStoredRecords() = runBlocking {
        val a = EventId.new(); val b = EventId.new(); val c = EventId.new(); val journal = EventId.new()
        val moods = RoomMoodRepository(db, clock)
        moods.create(a, MoodInput(5, now.minusSeconds(86400), energy = 2))
        moods.create(b, MoodInput(5, now.minusSeconds(86400)))
        moods.create(c, MoodInput(3, now, stress = 4))
        RoomJournalRepository(db, clock).create(journal, JournalInput("private original", now))
        RoomMemoryRepository(db, clock).saveMarker(journal, MemoryMarkerInput())
        RoomLifeEventRepository(db).upsert(LifeEvent(EventId.new(), EventType.APP_USAGE, now, now,
            source = EventSource.USAGE_STATS, sourceReference = "incomplete"))
        val result = report()
        assertEquals(4, result.current.records); assertEquals(1, result.current.memories)
        assertEquals(13.0 / 3, result.current.mood!!.score.average, 0.00001)
        assertEquals(3, sources(ReviewMetricKind.MOOD).totalCount)
        assertEquals(a, sources(ReviewMetricKind.ENERGY).records.single().event.id)
        assertEquals(c, sources(ReviewMetricKind.STRESS).records.single().event.id)
        assertEquals("private original", sources(ReviewMetricKind.MEMORIES).records.single().journal!!.body)
        assertEquals(4, sources(ReviewMetricKind.ENTRIES).totalCount)
    }
    @Test fun catalogAndEmotionIdentityFiltersDoNotMultiplyRowsAndDeletionRefreshes() = runBlocking {
        val person = UUID.randomUUID(); val other = UUID.randomUUID(); val place = UUID.randomUUID(); val emotion = UUID.randomUUID()
        db.catalog().upsertPerson(PersonEntity(person.toString(), "Same")); db.catalog().upsertPerson(PersonEntity(other.toString(), "Same"))
        db.catalog().upsertPlace(PlaceEntity(place.toString(), "Park")); db.catalog().upsertEmotion(EmotionEntity(emotion.toString(), "Calm"))
        val id = EventId.new()
        RoomMoodRepository(db, clock).create(id, MoodInput(4, now, setOf(emotion), personIds = setOf(person, other), placeSelection = PlaceSelection(place)))
        assertEquals(2, report().current.people.size)
        assertEquals(id, sources(ReviewMetricKind.EMOTION, emotion).records.single().event.id)
        assertEquals(1, sources(ReviewMetricKind.PERSON, person).totalCount)
        db.catalog().upsertPerson(PersonEntity(person.toString(), "Renamed"))
        assertTrue(report().current.people.any { it.name.name == "Renamed" })
        db.catalog().deletePerson(person.toString())
        assertEquals(0, sources(ReviewMetricKind.PERSON, person).totalCount)
        assertEquals(1, report().current.records); assertEquals(1, report().current.people.size)
        assertEquals(id, sources(ReviewMetricKind.PLACE, place).records.single().event.id)
    }
    @Test fun sourcePagingIsBoundedAndUsesOccurrenceUuidOrdering() = runBlocking {
        repeat(105) { n -> RoomMoodRepository(db, clock).create(EventId(UUID(0, n.toLong() + 1)), MoodInput(4, now)) }
        val metric = ReviewMetric(ReviewMetricKind.MOOD)
        val first = reviews.sources(period.current, zone, metric, 0)
        val second = reviews.sources(period.current, zone, metric, 50)
        val last = reviews.sources(period.current, zone, metric, 100)
        assertEquals(105, first.totalCount); assertEquals(50, first.records.size); assertEquals(5, last.records.size)
        assertEquals(EventId(UUID(0, 1)), first.records.first().event.id)
        assertEquals(EventId(UUID(0, 51)), second.records.first().event.id)
        assertTrue(runCatching { reviews.sources(period.current, zone, metric, 0, 51) }.isFailure)
    }
    @Test fun sessionsWinOverAggregatesAndSplitAtMidnightWithNativeStartFallback() = runBlocking {
        val start = today.atStartOfDay(zone).toInstant()
        val usage = RoomUsageRepository(db); usage.setEnabled(true)
        val bucket = UsageBucket("reader", start, now, 900000, "Reader")
        usage.commitImport(listOf(bucket), UsageSessionBatch(com.luma.lifeos.domain.time.TimeRange(start.minusSeconds(3600), now),
            listOf(UsageSession("reader", start.minusSeconds(600), start.plusSeconds(600), "Reader"))), now, now)
        val result = report().current.usage!!
        assertEquals(1200000L, result.totalMillis)
        assertEquals(600000L, result.days.single { it.date == today }.totalMillis)
        assertEquals(600000L, result.days.single { it.date == today.minusDays(1) }.totalMillis)
        assertEquals(0, report().current.records)
        // Another date has only aggregate history and a displaced parent occurrence.
        val date = today.minusDays(2); val native = date.atStartOfDay(zone).toInstant()
        val legacy = UsageBucket("legacy", native, native.plusSeconds(3600), 5125)
        usage.commit(listOf(legacy), now, now)
        val parent = db.lifeEvents().findBySource(EventSource.USAGE_STATS, legacy.sourceReference)!!
        db.lifeEvents().update(parent.copy(occurredAt = period.previous.start.atStartOfDay(zone).toInstant().toEpochMilli()))
        assertEquals(5125L, report().current.usage!!.days.single { it.date == date }.totalMillis)
        assertNull(report().previous.usage)
        assertEquals(5125L, usage.observeDaily(date, zone).first()!!.totalMillis)
    }
    @Test fun measuredZeroAggregateSurvivesAnEmptyPartialWindow() = runBlocking {
        val start = today.atStartOfDay(zone).toInstant()
        val usage = RoomUsageRepository(db); usage.setEnabled(true)
        usage.commitImport(listOf(UsageBucket("unused", start, now, 0)),
            UsageSessionBatch(com.luma.lifeos.domain.time.TimeRange(start, now), emptyList()), now, now)
        assertEquals(0L, report().current.usage!!.totalMillis)
        assertTrue(report().current.usage!!.apps.isEmpty())
        assertEquals(UsageHistory.UNAVAILABLE, usage.observeDaily(today, zone).first()!!.history)
        assertEquals(0L, usage.observeDaily(today, zone).first()!!.totalMillis)
    }
    @Test fun activitySourceFiltersUseExactTypeUnitAndProviderAndKeepRecordedZero() = runBlocking {
        val event = LifeEvent(EventId.new(), EventType.ACTIVITY, now, now)
        RoomEventStore(db).save(EventWrite(event, activities = listOf(
            ActivityRecordEntity(UUID.randomUUID().toString(), event.id.toString(), "steps", 0.0, "count", EventSource.MANUAL),
            ActivityRecordEntity(UUID.randomUUID().toString(), event.id.toString(), "distance", 0.3, "km", EventSource.MANUAL))))
        val result = report().current
        assertEquals(2, result.activities.size)
        assertEquals("0", result.activities.single { it.total.type == "steps" }.total.total.toPlainString())
        val metric = ReviewMetric(ReviewMetricKind.ACTIVITY, activity = ReviewActivityKey("steps", "count", EventSource.MANUAL))
        assertEquals(event.id, reviews.sources(period.current, zone, metric, 0).records.single().event.id)
        assertEquals(0, reviews.sources(period.current, zone, metric.copy(activity = ReviewActivityKey("steps", "km", EventSource.MANUAL)), 0).totalCount)
    }
    @Test fun multiYearFixtureReportsSourcesAndMeasuresRepositoryLoad() = runBlocking {
        db.withTransaction {
            val sql = db.openHelper.writableDatabase
            repeat(50000) { n ->
                val stamp = if (n < 2500) now.minusSeconds((n % 3) * 86400L).toEpochMilli()
                    else now.minusSeconds((30 + n % 1800) * 86400L).toEpochMilli()
                val id = UUID(0, n.toLong() + 1).toString()
                sql.execSQL("INSERT INTO life_event(id,type,occurred_at,created_at,updated_at,source,metadata_json) VALUES (?,'MOOD',?,?,?,'MANUAL','{}')",
                    arrayOf(id, stamp, now.toEpochMilli(), now.toEpochMilli()))
                if (n < 2500) sql.execSQL("INSERT INTO mood_entry(id,life_event_id,mood_score) VALUES (?,?,4)",
                    arrayOf(UUID(1, n.toLong() + 1).toString(), id))
            }
        }
        report() // Warm-up excludes initialization.
        val timings = (1..20).map {
            val start = System.nanoTime(); val result = report()
            assertEquals(2500, result.current.records)
            assertEquals(10000L, result.current.mood!!.score.sum)
            (System.nanoTime() - start) / 1000000.0
        }.sorted()
        val page = sources(ReviewMetricKind.MOOD)
        assertEquals(2500, page.totalCount); assertEquals(50, page.records.size)
        println("Weekly Review 50,000 rows/five years/2,500 selected: median=${timings[10]} ms p95=${timings[18]} ms; device=${android.os.Build.MODEL}")
    }
    @Test fun activeObservationRefreshesAfterCorrectionAndDeletion() = runBlocking {
        val id = EventId.new(); val moods = RoomMoodRepository(db, clock)
        moods.create(id, MoodInput(2, now))
        withTimeout(10000) {
            val initial = CompletableDeferred<Unit>()
            val next = async { reviews.observe(period).onEach { if (it.current.mood?.score?.sum == 2L) initial.complete(Unit) }
                .first { it.current.mood?.score?.sum == 5L } }
            initial.await(); moods.update(id, MoodInput(5, now))
            assertEquals(5L, next.await().current.mood!!.score.sum)
        }
        moods.delete(id)
        assertEquals(0, report().current.records)
    }
}
