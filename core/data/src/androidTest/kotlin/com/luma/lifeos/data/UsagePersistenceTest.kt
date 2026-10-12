package com.luma.lifeos.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.entity.*
import com.luma.lifeos.data.repository.*
import com.luma.lifeos.domain.model.*
import com.luma.lifeos.domain.usage.*
import com.luma.lifeos.domain.time.TimeRange
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
    @Test fun sessionRetriesKeepIdentityAndTotalsWithoutAddingLegacyBuckets() = runBlocking {
        repository.setEnabled(true)
        val visit = UsageSession("org.example.app", start.minusSeconds(60), start.plusSeconds(60), "Example")
        val later = UsageSession("org.example.app", start.plusSeconds(120), start.plusSeconds(180), "Example", true)
        val batch = UsageSessionBatch(TimeRange(start.minusSeconds(3600), now), listOf(visit, later))
        repository.commitImport(listOf(bucket), batch, now, now)
        repository.commitImport(listOf(bucket), batch, now, now)
        val report = RoomUsageRepository(db).observeDaily(LocalDate.parse("2026-10-11"), ZoneOffset.UTC).first()!!
        assertEquals(120_000L, report.totalMillis); assertEquals(2, report.sessions.size)
        assertEquals(UsageHistory.PARTIAL, report.history)
        assertEquals(5_125L, db.content().overlappingImportedUsage(start.toEpochMilli(), now.toEpochMilli()).single().durationMs)
        repository.commitImport(emptyList(), batch.copy(sessions = listOf(later.copy(end = start.plusSeconds(240), ongoing = false))), now, now)
        val updated = repository.observeDaily(LocalDate.parse("2026-10-11"), ZoneOffset.UTC).first()!!
        assertEquals(180_000L, updated.totalMillis); assertEquals(2, updated.sessions.size)
        assertFalse(updated.sessions.last().ongoing)
        val shorterRetention = UsageSessionBatch(TimeRange(start.plusSeconds(300), now), emptyList())
        repository.commitImport(emptyList(), shorterRetention, now, now)
        assertEquals(updated, repository.observeDaily(LocalDate.parse("2026-10-11"), ZoneOffset.UTC).first())
    }
    @Test fun sessionBatchRollbackOptOutAndClockFloorRetainPreviousSessions() = runBlocking {
        repository.setEnabled(true)
        val batch = UsageSessionBatch(TimeRange(start, now), listOf(UsageSession("org.example.app", start.plusSeconds(60), start.plusSeconds(120))))
        repository.commitImport(listOf(bucket), batch, now, now)
        val previous = repository.state()
        val shifted = bucket.copy(start = start.plusSeconds(5), end = now.plusSeconds(5))
        assertTrue(runCatching { repository.commitImport(listOf(shifted), batch.copy(sessions = emptyList()), now.plusSeconds(5), now.plusSeconds(5)) }.isFailure)
        assertEquals(previous, repository.state())
        repository.setEnabled(false)
        assertTrue(runCatching { repository.commitImport(emptyList(), batch, now, now) }.isFailure)
        repository.setEnabled(true)
        repository.recordClockSafety(UsageClockAnchor(now, 1000, 2), now)
        assertTrue(runCatching { repository.commitImport(emptyList(), batch, now, now) }.exceptionOrNull() is UsageIntervalChangedException)
        assertEquals(60_000L, repository.observeDaily(LocalDate.parse("2026-10-11"), ZoneOffset.UTC).first()!!.totalMillis)
    }
    @Test fun emptyPartialWindowsPreserveLegacyTotalsOutsideEventRetention() = runBlocking {
        repository.setEnabled(true); repository.commit(listOf(bucket), now, now)
        assertEquals(UsageHistory.UNAVAILABLE, repository.observeDaily(LocalDate.parse("2026-10-11"), ZoneOffset.UTC).first()!!.history)
        repository.commitImport(emptyList(), UsageSessionBatch(TimeRange(start, now), emptyList()), now, now)
        val legacy = repository.observeDaily(LocalDate.parse("2026-10-11"), ZoneOffset.UTC).first()!!
        assertEquals(5_125L, legacy.totalMillis); assertEquals(UsageHistory.UNAVAILABLE, legacy.history)
        val nextStart = start.plusSeconds(86_400)
        repository.commitImport(emptyList(), UsageSessionBatch(TimeRange(nextStart, nextStart.plusSeconds(3600)), emptyList()), now, now)
        val empty = repository.observeDaily(LocalDate.parse("2026-10-12"), ZoneOffset.UTC).first()!!
        assertTrue(empty.apps.isEmpty()); assertTrue(empty.sessions.isEmpty()); assertEquals(UsageHistory.PARTIAL, empty.history)
    }
    @Test fun completeWindowsCoverTheDayWithoutSummingLegacyUsageAgain() = runBlocking {
        repository.setEnabled(true); repository.commit(listOf(bucket), now, now)
        repository.commitImport(emptyList(), UsageSessionBatch(TimeRange(start, now),
            listOf(UsageSession("org.example.app", start.plusSeconds(60), start.plusSeconds(120))), UsageHistory.COMPLETE), now, now)
        val date = LocalDate.parse("2026-10-11")
        assertEquals(UsageHistory.PARTIAL, repository.observeDaily(date, ZoneOffset.UTC).first()!!.history)
        repository.commitImport(emptyList(), UsageSessionBatch(TimeRange(now, start.plusSeconds(86_400)), emptyList(), UsageHistory.COMPLETE), now, now)
        val report = repository.observeDaily(date, ZoneOffset.UTC).first()!!
        assertEquals(UsageHistory.COMPLETE, report.history); assertEquals(60_000L, report.totalMillis)
        val next = start.plusSeconds(86_400)
        repository.commitImport(listOf(bucket.copy(start = next, end = next.plusSeconds(43_200))),
            UsageSessionBatch(TimeRange(next, next.plusSeconds(86_400)), emptyList(), UsageHistory.COMPLETE), now, now)
        val empty = repository.observeDaily(date.plusDays(1), ZoneOffset.UTC).first()!!
        assertEquals(UsageHistory.COMPLETE, empty.history); assertTrue(empty.apps.isEmpty())
    }
}
