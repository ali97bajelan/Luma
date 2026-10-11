package com.luma.lifeos.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.entity.*
import com.luma.lifeos.data.repository.*
import com.luma.lifeos.domain.model.*
import com.luma.lifeos.domain.usage.*
import java.time.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*

class UsagePersistenceTest {
    private lateinit var db: LumaDatabase
    private lateinit var repository: RoomUsageRepository
    private val start = Instant.parse("2026-10-11T00:00:00Z")
    private val now = start.plusSeconds(43_200)
    private val bucket = UsageBucket("org.example.app", start, now, 5_125, "Example")
    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, LumaDatabase::class.java).build()
        repository = RoomUsageRepository(db)
    }
    @After fun tearDown() { db.close() }
    @Test fun rollingBucketRetriesRetainEventChildIdentityAuditAndUnrelatedLinks() = runBlocking {
        repository.setEnabled(true); repository.commit(listOf(bucket), now, now)
        val original = db.lifeEvents().findBySource(EventSource.USAGE_STATS, bucket.sourceReference)!!
        val child = db.lifeEvents().getDetails(original.id)!!.appUsage!!
        val person = UUID.randomUUID(); RoomPersonRepository(db).create(person, PersonInput("Friend"))
        db.content().insertPeople(listOf(EventPersonEntity(original.id, person.toString())))
        db.lifeEvents().update(original.copy(metadataJson = "{\"custom\":true}"))
        val growing = bucket.copy(end = now.plusSeconds(3600), durationMillis = 9_125, appLabel = "Renamed app")
        repository.commit(listOf(growing), now.plusSeconds(3600), now.plusSeconds(3600))
        repository.commit(listOf(growing), now.plusSeconds(3600), now.plusSeconds(3600))
        val updated = db.lifeEvents().getDetails(original.id)!!
        assertEquals(original.createdAt, updated.event.createdAt); assertEquals(child.id, updated.appUsage!!.id)
        assertEquals(9_125L, updated.appUsage!!.durationMs); assertEquals(1, updated.people.size)
        assertTrue(updated.event.metadataJson.contains("custom"))
        val report = repository.observeDaily(LocalDate.parse("2026-10-11"), ZoneOffset.UTC).first()!!
        assertEquals(1, report.apps.size); assertEquals(9_125L, report.totalMillis)
        assertEquals("Renamed app", report.apps.single().appLabel)
        assertEquals("Renamed app", RoomTimelineRepository(db).get(EventId.parse(original.id))!!.appUsage!!.appLabel)
    }
    @Test fun invalidBatchRollsBackRecordsAndCheckpoint() = runBlocking {
        repository.setEnabled(true); repository.commit(listOf(bucket), now, now)
        val previous = repository.state()
        val conflict = LifeEvent(EventId.new(), EventType.JOURNAL, start, now,
            source = EventSource.USAGE_STATS, sourceReference = bucket.copy(packageName = "org.example.conflict").sourceReference)
        RoomLifeEventRepository(db).upsert(conflict)
        assertTrue(runCatching { repository.commit(listOf(bucket.copy(durationMillis = 9_125), bucket.copy(packageName = "org.example.conflict")), now.plusSeconds(1), now.plusSeconds(1)) }.isFailure)
        assertEquals(previous, repository.state())
        assertEquals(5_125L, repository.observeDaily(LocalDate.parse("2026-10-11"), ZoneOffset.UTC).first()!!.totalMillis)
    }
    @Test fun disableAndPermissionStatusesKeepStoredUsageAndSuccessfulCheckpoint() = runBlocking {
        repository.setEnabled(true); repository.commit(listOf(bucket), now, now)
        repository.recordStatus(UsageSyncStatus.ACCESS_REQUIRED)
        assertEquals(now, repository.state().checkpoint); assertTrue(repository.state().enabled)
        repository.setEnabled(false)
        assertTrue(runCatching { repository.commit(listOf(bucket.copy(durationMillis = 9_125)), now.plusSeconds(1), now.plusSeconds(1)) }.isFailure)
        assertEquals(now, repository.state().lastSync)
        assertEquals(5_125L, repository.observeDaily(LocalDate.parse("2026-10-11"), ZoneOffset.UTC).first()!!.totalMillis)
        assertNull(repository.observeDaily(LocalDate.parse("2026-10-10"), ZoneOffset.UTC).first())
    }
    @Test fun dailyTotalsFollowNativeBucketStartInDisplayZoneWithoutProration() = runBlocking {
        repository.setEnabled(true)
        repository.commit(listOf(bucket, bucket.copy(packageName = "org.example.other", durationMillis = 2_000)), now, now)
        val report = repository.observeDaily(LocalDate.parse("2026-10-10"), ZoneId.of("America/New_York")).first()!!
        assertEquals(7_125L, report.totalMillis); assertEquals(2, report.apps.size)
        assertNull(repository.observeDaily(LocalDate.parse("2026-10-11"), ZoneId.of("America/New_York")).first())
    }
    @Test fun shiftedNativeIntervalsCannotDuplicateSavedUsageOrAdvanceCheckpoint() = runBlocking {
        repository.setEnabled(true); repository.commit(listOf(bucket), now, now)
        val previous = repository.state()
        val shifted = bucket.copy(start = start.plusSeconds(5), end = now.plusSeconds(5))
        assertTrue(runCatching { repository.commit(listOf(shifted), now.plusSeconds(5), now.plusSeconds(5)) }
            .exceptionOrNull() is UsageIntervalChangedException)
        assertEquals(previous, repository.state())
        assertEquals(5_125L, repository.observeDaily(LocalDate.parse("2026-10-11"), ZoneOffset.UTC).first()!!.totalMillis)
        assertNull(db.lifeEvents().findBySource(EventSource.USAGE_STATS, shifted.sourceReference))
    }
    @Test fun clockAnchorAndQuarantineSurviveRepositoryRecreationAndOptOut() = runBlocking {
        repository.setEnabled(true)
        val initial = UsageClockAnchor(now, 100_000, 1)
        repository.commit(listOf(bucket), now, now, initial)
        repository.commit(listOf(bucket), now.plusMillis(500), now.plusMillis(500), initial.copy(wallTime = now.plusMillis(500)))
        assertEquals(initial, RoomUsageRepository(db).state().clockAnchor)
        val rebooted = UsageClockAnchor(now.plusSeconds(60), 1_000, 2)
        repository.recordClockSafety(rebooted, rebooted.wallTime)
        repository.setEnabled(false); repository.setEnabled(true)
        val recreated = RoomUsageRepository(db); val state = recreated.state()
        assertEquals(rebooted, state.clockAnchor); assertEquals(rebooted.wallTime, state.minimumBucketStart)
        assertEquals(now.plusMillis(500), state.checkpoint)
        assertTrue(runCatching { recreated.commit(listOf(bucket), now.plusSeconds(60), now.plusSeconds(60), rebooted) }
            .exceptionOrNull() is UsageIntervalChangedException)
        assertEquals(5_125L, recreated.observeDaily(LocalDate.parse("2026-10-11"), ZoneOffset.UTC).first()!!.totalMillis)
        val fresh = bucket.copy(start = start.plusSeconds(86_400), end = now.plusSeconds(86_400))
        recreated.commit(listOf(fresh), fresh.end, fresh.end, rebooted.copy(wallTime = fresh.end, elapsedMillis = 86_341_000))
        assertEquals(5_125L, recreated.observeDaily(LocalDate.parse("2026-10-11"), ZoneOffset.UTC).first()!!.totalMillis)
        assertEquals(5_125L, recreated.observeDaily(LocalDate.parse("2026-10-12"), ZoneOffset.UTC).first()!!.totalMillis)
        assertEquals(rebooted, recreated.state().clockAnchor)
    }
}
