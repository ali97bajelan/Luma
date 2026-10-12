package com.luma.lifeos.domain.usage

import com.luma.lifeos.domain.time.TimeRange
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class UsageSyncerTest {
    private val now = Instant.parse("2026-10-11T12:00:00Z")
    private val row = UsageBucket("org.example.app", now.minusSeconds(43_200), now, 5_125)
    @Test fun disabledAndDeniedIntegrationsNeverImportAndRevocationRetainsCheckpoint() = runBlocking {
        val repo = MemoryRepo(); val source = Source(); val jobs = Jobs()
        val sync = UsageSyncer(source, repo, jobs, Clock.fixed(now, ZoneOffset.UTC))
        assertEquals(UsageSyncOutcome.DISABLED, sync.importNow()); assertNull(source.range)
        source.permission = UsageAccess.REQUIRED; sync.setEnabled(true)
        assertEquals(UsageSyncStatus.ACCESS_REQUIRED, repo.current.status)
        assertEquals(UsageSyncOutcome.ACCESS_REQUIRED, sync.importNow()); assertTrue(repo.rows.isEmpty())
        source.permission = UsageAccess.GRANTED; sync.refreshAccess(); source.rows = listOf(row)
        assertEquals(UsageSyncOutcome.SUCCESS, sync.importNow()); assertEquals(now, repo.current.checkpoint)
        source.permission = UsageAccess.REQUIRED; sync.refreshAccess()
        assertEquals(now, repo.current.checkpoint); assertEquals(listOf(row), repo.rows); assertFalse(jobs.active)
        sync.setEnabled(false); assertFalse(repo.current.enabled)
    }
    @Test fun importsUseBoundedOverlapAndCommitRealValuesOnly() = runBlocking {
        val repo = MemoryRepo(); val source = Source(); val sync = UsageSyncer(source, repo, Jobs(), Clock.fixed(now, ZoneOffset.UTC))
        sync.setEnabled(true); source.rows = listOf(row); sync.importNow()
        assertEquals(Instant.parse("2026-10-04T12:00:00Z"), source.range!!.start)
        assertEquals(5_125L, repo.rows.single().durationMillis)
        sync.importNow(); assertEquals(Instant.parse("2026-10-09T12:00:00Z"), source.range!!.start)
        assertEquals(1, repo.rows.size)
    }
    @Test fun emptyErrorsLockedDevicesAndRevocationDuringQueryDoNotAdvanceCheckpoint() = runBlocking {
        val repo = MemoryRepo(); val source = Source(); val sync = UsageSyncer(source, repo, Jobs(), Clock.fixed(now, ZoneOffset.UTC))
        sync.setEnabled(true)
        assertEquals(UsageSyncOutcome.NO_DATA, sync.importNow()); assertNull(repo.current.lastSync)
        source.failure = IllegalStateException("Offline platform")
        assertEquals(UsageSyncOutcome.ERROR, sync.importNow()); assertNull(repo.current.checkpoint)
        source.failure = null; source.permission = UsageAccess.LOCKED
        assertEquals(UsageSyncOutcome.LOCKED, sync.importNow()); assertNull(repo.current.checkpoint)
        source.permission = UsageAccess.GRANTED; source.rows = listOf(row)
        source.afterQuery = { source.permission = UsageAccess.REQUIRED }
        assertEquals(UsageSyncOutcome.ACCESS_REQUIRED, sync.importNow()); assertTrue(repo.rows.isEmpty())
    }
    @Test fun cancellationPropagatesWithoutMarkingTheIntegrationFailed() = runBlocking {
        val repo = MemoryRepo(); val source = Source(); val sync = UsageSyncer(source, repo, Jobs(), Clock.fixed(now, ZoneOffset.UTC))
        sync.setEnabled(true); source.failure = CancellationException("Stopped")
        try { sync.importNow(); fail("Cancellation swallowed") } catch (_: CancellationException) { }
        assertEquals(UsageSyncStatus.READY, repo.current.status); assertNull(repo.current.checkpoint)
    }
    @Test fun concurrentImportsSerializeAndConflictingNativeRowsNeverCommit() = runBlocking {
        val repo = MemoryRepo(); val source = Source(); val sync = UsageSyncer(source, repo, Jobs(), Clock.fixed(now, ZoneOffset.UTC))
        sync.setEnabled(true); source.rows = listOf(row, row.copy(durationMillis = 6000))
        assertEquals(UsageSyncOutcome.ERROR, sync.importNow()); assertTrue(repo.rows.isEmpty())
        source.rows = listOf(row); source.gate = CompletableDeferred()
        val first = async { sync.importNow() }; yield()
        val second = async { sync.importNow() }; yield()
        assertEquals(1, source.inFlight); source.gate!!.complete(Unit)
        assertEquals(UsageSyncOutcome.SUCCESS, first.await()); assertEquals(UsageSyncOutcome.SUCCESS, second.await())
        assertEquals(1, source.maximumInFlight)
    }
    @Test fun disablingDuringANativeQueryDoesNotWaitOrCommitThePendingImport() = runBlocking {
        val repo = MemoryRepo(); val source = Source(); val jobs = Jobs()
        val sync = UsageSyncer(source, repo, jobs, Clock.fixed(now, ZoneOffset.UTC))
        sync.setEnabled(true); source.rows = listOf(row); source.gate = CompletableDeferred()
        val importing = async { sync.importNow() }; yield()
        val disable = launch { sync.setEnabled(false) }; yield()
        val disabledImmediately = disable.isCompleted && !repo.current.enabled && !jobs.active
        source.gate!!.complete(Unit); disable.join(); val outcome = importing.await()
        assertTrue("Opt-out waited for the native query", disabledImmediately)
        assertEquals(UsageSyncOutcome.DISABLED, outcome); assertTrue(repo.rows.isEmpty()); assertNull(repo.current.checkpoint)
    }
    @Test fun shiftedNativeIntervalsKeepTheOriginalDataAndCheckpoint() = runBlocking {
        val repo = MemoryRepo(); val source = Source(); val sync = UsageSyncer(source, repo, Jobs(), Clock.fixed(now, ZoneOffset.UTC))
        sync.setEnabled(true); source.rows = listOf(row); sync.importNow()
        source.rows = listOf(row.copy(start = row.start.plusSeconds(5), end = row.end.plusSeconds(5)))
        assertEquals(UsageSyncOutcome.INTERVAL_CHANGED, sync.importNow())
        assertEquals(listOf(row), repo.rows); assertEquals(now, repo.current.checkpoint)
        assertEquals(UsageSyncStatus.INTERVAL_CHANGED, repo.current.status)
    }
    @Test fun optOutDuringAnAccessRefreshCannotRescheduleWork() = runBlocking {
        val repo = MemoryRepo(); val source = Source(); val jobs = Jobs()
        val sync = UsageSyncer(source, repo, jobs, Clock.fixed(now, ZoneOffset.UTC))
        sync.setEnabled(true); source.accessGate = CompletableDeferred()
        val refresh = async { sync.refreshAccess() }; yield()
        sync.setEnabled(false); source.accessGate!!.complete(Unit); refresh.await()
        assertFalse(repo.current.enabled); assertFalse("Late access result restarted work", jobs.active)
    }
    @Test fun transientAccessFailuresRemainRetryableAndNeverAdvanceCheckpoint() = runBlocking {
        val repo = MemoryRepo(); val source = Source(); val jobs = Jobs()
        val sync = UsageSyncer(source, repo, jobs, Clock.fixed(now, ZoneOffset.UTC))
        sync.setEnabled(true); source.accessFailure = IllegalStateException("AppOps temporarily unavailable")
        assertEquals(UsageSyncOutcome.ERROR, sync.importNow()); assertTrue(jobs.active)
        sync.refreshAccess()
        assertEquals(UsageSyncStatus.ERROR, repo.current.status); assertTrue(jobs.active); assertNull(repo.current.checkpoint)
        source.accessFailure = null; source.rows = listOf(row)
        assertEquals(UsageSyncOutcome.SUCCESS, sync.importNow())
    }
    @Test fun nonOverlappingClockCorrectionsPreserveHistoryAndOnlyAcceptFreshIntervals() = runBlocking {
        val repo = MemoryRepo(); val source = Source(); val sync = UsageSyncer(source, repo, Jobs(), Clock.fixed(now, ZoneOffset.UTC))
        sync.setEnabled(true)
        val short = row.copy(end = row.start.plusSeconds(300), durationMillis = 60_000)
        source.anchor = UsageClockAnchor(now, 100_000, 1); source.rows = listOf(short); sync.importNow()
        source.anchor = UsageClockAnchor(now.plusSeconds(600), 100_000, 1)
        source.rows = listOf(short.copy(start = short.start.plusSeconds(600), end = short.end.plusSeconds(600)))
        assertEquals(UsageSyncOutcome.INTERVAL_CHANGED, sync.importNow())
        assertEquals(listOf(short), repo.rows); assertEquals(now, repo.current.checkpoint)
        assertEquals(now.plusSeconds(600), repo.current.minimumBucketStart)
        assertEquals(UsageSyncOutcome.INTERVAL_CHANGED, sync.importNow())
        assertEquals(listOf(short), repo.rows)
        source.anchor = UsageClockAnchor(now.plusSeconds(86_400), 85_900_000, 1)
        val fresh = short.copy(start = row.start.plusSeconds(86_400), end = row.start.plusSeconds(86_700))
        source.rows = listOf(source.rows.single(), fresh)
        assertEquals(UsageSyncOutcome.SUCCESS, sync.importNow()); assertEquals(listOf(fresh), repo.rows)
    }
    @Test fun rebootQuarantinesOldNativeIntervalsWithoutInventingZeroUsage() = runBlocking {
        val repo = MemoryRepo(); val source = Source(); val sync = UsageSyncer(source, repo, Jobs(), Clock.fixed(now, ZoneOffset.UTC))
        sync.setEnabled(true); source.anchor = UsageClockAnchor(now, 100_000, 1); source.rows = listOf(row); sync.importNow()
        source.anchor = UsageClockAnchor(now.plusSeconds(60), 1_000, 2)
        assertEquals(UsageSyncOutcome.INTERVAL_CHANGED, sync.importNow())
        assertEquals(listOf(row), repo.rows); assertEquals(now, repo.current.checkpoint)
        sync.setEnabled(false); sync.setEnabled(true)
        assertEquals(now.plusSeconds(60), repo.current.minimumBucketStart)
        assertEquals(UsageSyncOutcome.INTERVAL_CHANGED, sync.importNow())
    }
    @Test fun clockCorrectionDuringQueryRejectsTheBatchBeforeCommit() = runBlocking {
        val repo = MemoryRepo(); val source = Source(); val sync = UsageSyncer(source, repo, Jobs(), Clock.fixed(now, ZoneOffset.UTC))
        sync.setEnabled(true); source.anchor = UsageClockAnchor(now, 100_000, 1); source.rows = listOf(row)
        source.afterQuery = { source.anchor = UsageClockAnchor(now.plusSeconds(86_400), 100_000, 1) }
        assertEquals(UsageSyncOutcome.INTERVAL_CHANGED, sync.importNow())
        assertTrue(repo.rows.isEmpty()); assertNull(repo.current.checkpoint)
    }
    @Test fun cumulativeClockDriftIsComparedToThePersistedEpoch() = runBlocking {
        val repo = MemoryRepo(); val source = Source(); val sync = UsageSyncer(source, repo, Jobs(), Clock.fixed(now, ZoneOffset.UTC))
        sync.setEnabled(true); val initial = UsageClockAnchor(now, 100_000, 1)
        source.anchor = initial; source.rows = listOf(row); sync.importNow()
        source.anchor = initial.copy(wallTime = now.plusMillis(500)); assertEquals(UsageSyncOutcome.SUCCESS, sync.importNow())
        source.anchor = initial.copy(wallTime = now.plusMillis(1_000)); assertEquals(UsageSyncOutcome.SUCCESS, sync.importNow())
        assertEquals(initial, repo.current.clockAnchor)
        source.anchor = initial.copy(wallTime = now.plusMillis(1_500))
        assertEquals(UsageSyncOutcome.INTERVAL_CHANGED, sync.importNow())
        assertEquals(listOf(row), repo.rows); assertEquals(now, repo.current.checkpoint)
    }
    @Test fun sessionsAreImportedWithoutDailyBucketsAndRevocationRejectsTheWholeBatch() = runBlocking {
        val repo = MemoryRepo(); val source = Source()
        val sync = UsageSyncer(source, repo, Jobs(), Clock.fixed(now, ZoneOffset.UTC))
        sync.setEnabled(true)
        source.sessions = UsageSessionBatch(TimeRange(now.minusSeconds(604_800), now),
            listOf(UsageSession("org.example.app", now.minusSeconds(60), now, ongoing = true)))
        assertEquals(UsageSyncOutcome.SUCCESS, sync.importNow())
        assertEquals(60_000L, repo.sessions!!.sessions.single().durationMillis)
        assertEquals(now, repo.current.checkpoint)
        val original = repo.sessions
        source.afterSessions = { source.permission = UsageAccess.REQUIRED }
        source.sessions = source.sessions!!.copy(sessions = emptyList())
        assertEquals(UsageSyncOutcome.ACCESS_REQUIRED, sync.importNow())
        assertEquals(original, repo.sessions)
    }
    @Test fun emptySessionWindowIsSavedAndDoesNotFabricateAppUsage() = runBlocking {
        val repo = MemoryRepo(); val source = Source()
        val sync = UsageSyncer(source, repo, Jobs(), Clock.fixed(now, ZoneOffset.UTC))
        sync.setEnabled(true)
        source.sessions = UsageSessionBatch(TimeRange(now.minusSeconds(604_800), now), emptyList())
        assertEquals(UsageSyncOutcome.NO_DATA, sync.importNow())
        assertTrue(repo.sessions!!.sessions.isEmpty()); assertTrue(repo.rows.isEmpty())
        assertEquals(now, repo.current.lastSync)
    }
    private class Source : UsageSource {
        var permission = UsageAccess.GRANTED; var rows = emptyList<UsageBucket>()
        var failure: Exception? = null; var range: TimeRange? = null
        var afterQuery: () -> Unit = {}; var gate: CompletableDeferred<Unit>? = null
        var accessGate: CompletableDeferred<Unit>? = null; var accessFailure: Exception? = null
        var anchor: UsageClockAnchor? = null
        var inFlight = 0; var maximumInFlight = 0
        var sessions: UsageSessionBatch? = null; var afterSessions: () -> Unit = {}
        override suspend fun access(): UsageAccess { accessGate?.await(); accessFailure?.let { throw it }; return permission }
        override suspend fun clockAnchor() = anchor
        override suspend fun querySessions(range: TimeRange): UsageSessionBatch? { afterSessions(); return sessions }
        override suspend fun query(range: TimeRange): List<UsageBucket> {
            this.range = range; failure?.let { throw it }
            inFlight++; maximumInFlight = maxOf(maximumInFlight, inFlight)
            try { gate?.await(); afterQuery(); return rows } finally { inFlight-- }
        }
    }
    private class Jobs : UsageScheduler { var active = false; override fun schedule() { active = true }; override fun cancel() { active = false } }
    private class MemoryRepo : UsageRepository {
        var current = UsageImportState(); var rows = emptyList<UsageBucket>()
        var sessions: UsageSessionBatch? = null
        override fun observeState() = flowOf(current)
        override suspend fun state() = current
        override suspend fun setEnabled(enabled: Boolean) { current = current.copy(enabled = enabled, status = if (enabled) UsageSyncStatus.READY else UsageSyncStatus.DISABLED) }
        override suspend fun recordStatus(status: UsageSyncStatus) { if (current.enabled) current = current.copy(status = status) }
        override suspend fun recordClockSafety(anchor: UsageClockAnchor, minimumBucketStart: Instant) {
            if (current.enabled) current = current.copy(clockAnchor = anchor, minimumBucketStart = minimumBucketStart, status = UsageSyncStatus.INTERVAL_CHANGED)
        }
        override suspend fun commit(buckets: List<UsageBucket>, syncedAt: Instant, checkpoint: Instant, anchor: UsageClockAnchor?) { check(current.enabled); assertUnambiguousUsage(buckets, rows); rows = buckets; current = current.copy(status = UsageSyncStatus.READY, lastSync = syncedAt, checkpoint = checkpoint, clockAnchor = current.clockAnchor ?: anchor) }
        override suspend fun commitImport(buckets: List<UsageBucket>, sessions: UsageSessionBatch?, syncedAt: Instant, checkpoint: Instant, anchor: UsageClockAnchor?) {
            commit(buckets, syncedAt, checkpoint, anchor); this.sessions = sessions
        }
        override fun observeDaily(date: LocalDate, zone: ZoneId) = flowOf(dailyUsageReport(rows, date, zone))
    }
}
