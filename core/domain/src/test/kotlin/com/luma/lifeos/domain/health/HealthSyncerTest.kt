package com.luma.lifeos.domain.health

import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import org.junit.Assert.*

class HealthSyncerTest {
    private val now = Instant.parse("2026-10-12T12:00:00Z")
    private val utc = ZoneId.of("UTC")
    private class Store : HealthRepository {
        val flow = MutableStateFlow(HealthState())
        var commits = 0
        var saved = emptyList<StepObservation>()
        override fun observeState() = flow
        override suspend fun state() = flow.value
        override suspend fun setEnabled(enabled: Boolean, zone: ZoneId) {
            if (enabled != flow.value.enabled) flow.value = flow.value.copy(enabled = enabled,
                importZone = flow.value.importZone ?: zone, revision = flow.value.revision + 1,
                status = if (enabled) HealthStatus.READY else HealthStatus.DISABLED)
        }
        override suspend fun recordStatus(status: HealthStatus, revision: Long) {
            if (flow.value.enabled && flow.value.revision == revision) flow.value = flow.value.copy(status = status)
        }
        override suspend fun commit(rows: List<StepObservation>, syncedAt: Instant, expected: HealthState) {
            if (!flow.value.enabled || flow.value.revision != expected.revision) throw HealthImportChangedException()
            commits++; saved = rows
            flow.value = flow.value.copy(lastSync = syncedAt, status = if (rows.any { it.count != null }) HealthStatus.READY else HealthStatus.NO_DATA)
        }
    }
    private class Source : HealthSource {
        var current = HealthAccess.GRANTED
        var queries = 0
        var windows = emptyList<StepWindow>()
        var effect: suspend () -> Unit = {}
        var count: Long? = 123
        var transform: (List<StepObservation>) -> List<StepObservation> = { it }
        var accessEffect: suspend () -> Unit = {}
        override suspend fun access(): HealthAccess { accessEffect(); return current }
        override suspend fun query(windows: List<StepWindow>): List<StepObservation> {
            queries++; this.windows = windows; effect()
            return transform(windows.map { StepObservation(it, count) })
        }
    }
    private fun sync(source: Source, store: Store, zone: ZoneId = utc) = HealthSyncer(source, store, Clock.fixed(now, ZoneOffset.UTC)) { zone }

    @Test fun sevenDatesRefreshOnEveryImportWithStablePartialIdentity() = runBlocking {
        val source = Source(); val store = Store(); val sync = sync(source, store)
        sync.setEnabled(true); assertEquals(HealthStatus.READY, sync.importNow())
        assertEquals(7, store.saved.size); assertEquals(LocalDate.parse("2026-10-06"), store.saved.first().window.date)
        assertEquals(now, store.saved.last().window.end)
        source.count = 321; sync.importNow()
        assertEquals(2, store.commits); assertEquals(7, source.windows.size)
        val window = source.windows.last()
        assertEquals(window.sourceReference, window.copy(end = window.end.plusSeconds(1)).sourceReference)
    }
    @Test fun zoneSurvivesDisableAndTravel() = runBlocking {
        val source = Source(); val store = Store()
        val first = sync(source, store, ZoneId.of("Asia/Tehran")); first.setEnabled(true); first.importNow()
        val identities = store.saved.map { it.window.sourceReference }
        first.setEnabled(false)
        val traveled = sync(source, store, ZoneId.of("America/New_York")); traveled.setEnabled(true); traveled.importNow()
        assertEquals(ZoneId.of("Asia/Tehran"), store.state().importZone)
        assertEquals(identities, store.saved.map { it.window.sourceReference })
    }
    @Test fun calendarWindowsRespectDstAndMidnight() {
        val zone = ZoneId.of("America/New_York")
        val windows = stepWindows(Instant.parse("2026-11-02T05:00:00Z"), zone)
        assertEquals(6, windows.size)
        assertEquals(25, Duration.between(windows.last().start, windows.last().end).toHours())
        val spring = stepWindows(Instant.parse("2026-03-09T04:00:00Z"), zone)
        assertEquals(23, Duration.between(spring.last().start, spring.last().end).toHours())
    }
    @Test fun nullAndZeroRemainDistinctAndSuccessfulEmptyReadCommits() = runBlocking {
        val source = Source(); val store = Store(); val sync = sync(source, store); sync.setEnabled(true)
        source.count = null; assertEquals(HealthStatus.NO_DATA, sync.importNow())
        assertTrue(store.saved.all { it.count == null }); assertEquals(now, store.state().lastSync)
        source.count = 0; assertEquals(HealthStatus.READY, sync.importNow()); assertEquals(0L, store.saved.first().count)
    }
    @Test fun disabledDoesNotTouchProvider() = runBlocking {
        val source = Source(); val store = Store(); assertEquals(HealthStatus.DISABLED, sync(source, store).importNow())
        assertEquals(0, source.queries)
    }
    @Test fun deniedUnsupportedAndUpdateRequiredNeverQueryOrCommit() = runBlocking {
        val source = Source(); val store = Store(); val sync = sync(source, store); sync.setEnabled(true)
        for ((access, status) in listOf(HealthAccess.REQUIRED to HealthStatus.ACCESS_REQUIRED,
            HealthAccess.UPDATE_REQUIRED to HealthStatus.UPDATE_REQUIRED, HealthAccess.UNAVAILABLE to HealthStatus.UNAVAILABLE)) {
            source.current = access; assertEquals(status, sync.importNow()); assertEquals(status, store.state().status)
        }
        assertEquals(0, source.queries); assertNull(store.state().lastSync)
    }
    @Test fun revocationDuringReadPreservesCheckpoint() = runBlocking {
        val source = Source(); val store = Store(); val sync = sync(source, store); sync.setEnabled(true); sync.importNow()
        source.effect = { source.current = HealthAccess.REQUIRED }
        assertEquals(HealthStatus.ACCESS_REQUIRED, sync.importNow()); assertEquals(1, store.commits); assertEquals(now, store.state().lastSync)
    }
    @Test fun disableDoesNotWaitForReadAndPreventsLateCommit() = runBlocking {
        val source = Source(); val store = Store(); val sync = sync(source, store); sync.setEnabled(true)
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        source.effect = { entered.complete(Unit); release.await() }
        val importing = async { sync.importNow() }; entered.await()
        withTimeout(1000) { sync.setEnabled(false) }; release.complete(Unit)
        assertEquals(HealthStatus.DISABLED, importing.await()); assertEquals(0, store.commits)
    }
    @Test fun permissionExceptionMapsToDeniedAndOtherErrorsRetainData() = runBlocking {
        val source = Source(); val store = Store(); val sync = sync(source, store); sync.setEnabled(true); sync.importNow()
        source.effect = { throw HealthAccessException(HealthAccess.REQUIRED) }
        assertEquals(HealthStatus.ACCESS_REQUIRED, sync.importNow())
        source.effect = { error("provider failure") }; assertEquals(HealthStatus.ERROR, sync.importNow())
        assertEquals(1, store.commits); assertEquals(now, store.state().lastSync)
    }
    @Test fun cancellationPropagatesWithoutRecordingFailure() = runBlocking {
        val source = Source(); val store = Store(); val sync = sync(source, store); sync.setEnabled(true)
        source.effect = { throw CancellationException() }
        try { sync.importNow(); fail() } catch (_: CancellationException) { }
        assertEquals(HealthStatus.READY, store.state().status); assertEquals(0, store.commits)
    }
    @Test fun invalidCountsRejected() {
        val window = stepWindows(now, utc).first()
        for (count in listOf(-1L, Long.MAX_VALUE)) assertThrows(IllegalArgumentException::class.java) { StepObservation(window, count) }
    }
    @Test fun partialAndDuplicateResponsesCannotDeleteSavedHistory() = runBlocking {
        val source = Source(); val store = Store(); val sync = sync(source, store); sync.setEnabled(true); sync.importNow()
        source.transform = { it.dropLast(1) }; assertEquals(HealthStatus.ERROR, sync.importNow())
        source.transform = { it.dropLast(1) + it.first() }; assertEquals(HealthStatus.ERROR, sync.importNow())
        assertEquals(1, store.commits); assertEquals(now, store.state().lastSync)
    }
    @Test fun queuedImportsSerializeProviderReads() = runBlocking {
        val source = Source(); val store = Store(); val sync = sync(source, store); sync.setEnabled(true)
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        source.effect = { entered.complete(Unit); release.await() }
        val first = async { sync.importNow() }; entered.await()
        val second = async { sync.importNow() }; yield(); assertEquals(1, source.queries)
        release.complete(Unit)
        withTimeout(2000) {
            assertEquals(HealthStatus.READY, first.await()); assertEquals(HealthStatus.READY, second.await())
        }
        assertEquals(2, store.commits)
    }
    @Test fun optOutCancelsPendingProviderQuery() = runBlocking {
        val source = Source(); val store = Store(); val sync = sync(source, store); sync.setEnabled(true)
        val entered = CompletableDeferred<Unit>(); val stopped = CompletableDeferred<Unit>()
        source.effect = { entered.complete(Unit); try { awaitCancellation() } finally { stopped.complete(Unit) } }
        val importing = async { sync.importNow() }; entered.await(); sync.setEnabled(false)
        withTimeout(1000) { stopped.await(); assertEquals(HealthStatus.DISABLED, importing.await()) }
    }
    @Test fun optOutDuringAccessCheckDoesNotStartQuery() = runBlocking {
        val source = Source(); val store = Store(); val sync = sync(source, store); sync.setEnabled(true)
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        source.accessEffect = { entered.complete(Unit); release.await() }
        val importing = async { sync.importNow() }; entered.await(); sync.setEnabled(false); release.complete(Unit)
        assertEquals(HealthStatus.DISABLED, importing.await()); assertEquals(0, source.queries)
    }
    @Test fun delayedAccessRefreshCannotRestoreOlderErrorAfterSuccessfulImport() = runBlocking {
        val source = Source(); val store = Store(); val sync = sync(source, store); sync.setEnabled(true)
        store.recordStatus(HealthStatus.ERROR, store.state().revision)
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        source.accessEffect = { if (!entered.isCompleted) { entered.complete(Unit); release.await() } }
        val refreshing = async { sync.refreshAccess() }; entered.await()
        val importing = async { sync.importNow() }; yield(); release.complete(Unit)
        refreshing.await(); importing.await(); assertEquals(HealthStatus.READY, store.state().status)
    }
}
