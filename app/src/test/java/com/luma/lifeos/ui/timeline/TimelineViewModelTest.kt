package com.luma.lifeos.ui.timeline

import androidx.lifecycle.SavedStateHandle
import com.luma.lifeos.domain.model.*
import com.luma.lifeos.domain.repository.TimelineRepository
import com.luma.lifeos.domain.time.TimeRange
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TimelineViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val day = LocalDate.parse("2026-10-11")
    private val zone = ZoneId.of("UTC")
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun appendAndPrependKeepOrderedRowsWithABoundedWindowAndNoGaps() = runTest(dispatcher) {
        val vm = TimelineViewModel(StoredRecords(1_000), SavedStateHandle())
        vm.selectDay(day, zone); advanceUntilIdle()
        assertEquals((999L downTo 850L).toList(), ids(vm))
        repeat(4) { vm.loadLater(); advanceUntilIdle() }
        assertEquals((949L downTo 650L).toList(), ids(vm)); assertEquals(50, vm.state.value.offset)
        vm.loadEarlier(); advanceUntilIdle()
        assertEquals((999L downTo 700L).toList(), ids(vm)); assertEquals(0, vm.state.value.offset)
        assertEquals(1_000, vm.state.value.totalCount)
    }
    @Test fun usageRowsDoNotIncreaseDisplayedEventCountOrStopPagination() = runTest(dispatcher) {
        val repo = StoredRecords(170)
        repo.records.replaceAll { it.copy(event = it.event.copy(type = EventType.APP_USAGE)) }
        val vm = TimelineViewModel(repo, SavedStateHandle(), initialShowAppUsage = true)
        vm.selectDay(day, zone); advanceUntilIdle()
        assertEquals(0, vm.state.value.eventCount); assertEquals(170, vm.state.value.totalCount)
        assertTrue(vm.state.value.canLoadLater)
        vm.loadLater(); advanceUntilIdle()
        assertEquals(170, vm.state.value.records.size); assertEquals(0, vm.state.value.eventCount)
        assertFalse(vm.state.value.canLoadLater)
        repo.records[0] = repo.records[0].copy(event = repo.records[0].event.copy(type = EventType.JOURNAL))
        repo.changes.emit(Unit); advanceUntilIdle()
        assertEquals(1, vm.state.value.eventCount); assertEquals(170, vm.state.value.totalCount)
    }
    @Test fun usageRowsAreHiddenByDefaultAndChangingVisibilityReloadsTheDay() = runTest(dispatcher) {
        val repo = StoredRecords(3)
        repo.records.replaceAll { it.copy(event = it.event.copy(type = EventType.APP_USAGE)) }
        val vm = TimelineViewModel(repo, SavedStateHandle())
        vm.selectDay(day, zone); advanceUntilIdle()

        assertFalse(vm.state.value.showAppUsage)
        assertTrue(vm.state.value.records.isEmpty())
        assertEquals(0, vm.state.value.totalCount)
        vm.setShowAppUsage(true); advanceUntilIdle()
        assertTrue(vm.state.value.showAppUsage)
        assertEquals(3, vm.state.value.totalCount)
        assertEquals(3, vm.state.value.records.size)
        assertEquals(0, vm.state.value.eventCount)
        vm.setShowAppUsage(false); advanceUntilIdle()
        assertFalse(vm.state.value.showAppUsage)
        assertTrue(vm.state.value.records.isEmpty())
        assertEquals(0, vm.state.value.totalCount)
    }
    @Test fun countChangingPaginationKeepsHiddenUsageRowsOutOfTheFallbackWindow() = runTest(dispatcher) {
        val repo = StoredRecords(170)
        val vm = TimelineViewModel(repo, SavedStateHandle())
        vm.selectDay(day, zone); advanceUntilIdle()

        repo.records.removeAt(0)
        val now = Instant.parse("2026-10-11T08:00:00Z")
        repo.records += LifeEventRecord(LifeEvent(EventId(UUID(0, 1_000)), EventType.APP_USAGE, now, now))
        vm.loadLater(); advanceUntilIdle()

        assertFalse(vm.state.value.showAppUsage)
        assertEquals(169, vm.state.value.totalCount)
        assertTrue(vm.state.value.records.none { it.event.type == EventType.APP_USAGE })
    }
    @Test fun emptyOffsetRecoveryKeepsHiddenUsageRowsOutOfTheRecenteredWindow() = runTest(dispatcher) {
        val repo = StoredRecords(1_000)
        repo.records.indices.filter { it % 3 == 0 }.forEach { index ->
            repo.records[index] = repo.records[index].copy(event = repo.records[index].event.copy(type = EventType.APP_USAGE))
        }
        val vm = TimelineViewModel(repo, SavedStateHandle())
        vm.selectDay(day, zone); advanceUntilIdle()
        repeat(4) { vm.loadLater(); advanceUntilIdle() }
        assertTrue(vm.state.value.offset > 0)

        repo.records.subList(20, repo.records.size).clear()
        repo.changes.emit(Unit); advanceUntilIdle()

        assertFalse(vm.state.value.showAppUsage)
        assertTrue(vm.state.value.records.none { it.event.type == EventType.APP_USAGE })
        assertEquals(13, vm.state.value.totalCount)
    }
    @Test fun exhaustedShortPageStopsLoadingAndErrorsRetryWithoutLosingRows() = runTest(dispatcher) {
        val repo = StoredRecords(170)
        val vm = TimelineViewModel(repo, SavedStateHandle()); vm.selectDay(day, zone); advanceUntilIdle()
        repo.failure = IllegalStateException("Unavailable")
        vm.loadLater(); advanceUntilIdle()
        assertEquals(150, vm.state.value.records.size); assertEquals(TimelineError.LATER, vm.state.value.error)
        repo.failure = null; vm.retry(); advanceUntilIdle()
        assertEquals((169L downTo 0L).toList(), ids(vm)); assertFalse(vm.state.value.canLoadLater)
        vm.loadLater(); advanceUntilIdle(); assertEquals(170, vm.state.value.records.size)
    }
    @Test fun failedInitialObservationAndLoadCanRetry() = runTest(dispatcher) {
        val repo = StoredRecords(1).apply { failure = IllegalStateException("Unavailable") }
        val vm = TimelineViewModel(repo, SavedStateHandle()); vm.selectDay(day, zone); advanceUntilIdle()
        assertEquals(TimelineError.LOAD, vm.state.value.error); assertFalse(vm.state.value.loaded)
        repo.failure = null; vm.retry(); advanceUntilIdle()
        assertTrue(vm.state.value.loaded); assertEquals(1, vm.state.value.records.size)
    }
    @Test fun registeredObservationPrecedesReadsAndTypedChangesRefreshTheWindow() = runTest(dispatcher) {
        val repo = StoredRecords(1, initial = false)
        val vm = TimelineViewModel(repo, SavedStateHandle()); vm.selectDay(day, zone); advanceUntilIdle()
        assertTrue(vm.state.value.loading); assertTrue(vm.state.value.records.isEmpty())
        repo.changes.emit(Unit); advanceUntilIdle()
        repo.records[0] = repo.records[0].copy(event = repo.records[0].event.copy(title = "Changed"))
        repo.changes.emit(Unit); advanceUntilIdle()
        assertEquals("Changed", vm.state.value.records.single().event.title)
    }
    @Test fun aLateOldDayResultCannotAppearUnderTheNewDate() = runTest(dispatcher) {
        val repo = StoredRecords(1)
        val old = CompletableDeferred<Unit>(); repo.block = old
        repo.records += LifeEventRecord(LifeEvent(EventId(UUID(0, 2)), EventType.ACTIVITY,
            Instant.parse("2026-10-12T08:00:00Z"), Instant.parse("2026-10-12T08:00:00Z"), title = "New day"))
        val vm = TimelineViewModel(repo, SavedStateHandle()); vm.selectDay(day, zone); runCurrent()
        vm.selectDay(day.plusDays(1), zone); runCurrent()
        old.complete(Unit); advanceUntilIdle()
        assertEquals(day.plusDays(1), vm.state.value.date)
        assertEquals("New day", vm.state.value.records.single().event.title)
    }
    @Test fun restorationKeepsWindowAndDeletingAllRowsBeyondAnchorRecenters() = runTest(dispatcher) {
        val repo = StoredRecords(1_000); val saved = SavedStateHandle()
        val vm = TimelineViewModel(repo, saved); vm.selectDay(day, zone); advanceUntilIdle()
        repeat(4) { vm.loadLater(); advanceUntilIdle() }
        val restored = TimelineViewModel(repo, saved)
        restored.startObserving(); restored.selectDay(day, zone); advanceUntilIdle()
        assertEquals(ids(vm), ids(restored))
        repo.records.subList(20, repo.records.size).clear(); repo.changes.emit(Unit); advanceUntilIdle()
        assertEquals((19L downTo 0L).toList(), ids(restored)); assertEquals(0, restored.state.value.offset)
    }
    @Test fun interruptedDateChangeRestoresTheNewDayAtItsBeginning() = runTest(dispatcher) {
        val repo = StoredRecords(1_000); val saved = SavedStateHandle()
        val vm = TimelineViewModel(repo, saved); vm.selectDay(day, zone); advanceUntilIdle()
        repeat(4) { vm.loadLater(); advanceUntilIdle() }
        vm.selectDay(day.plusDays(1), zone)
        vm.stopObserving()
        assertEquals(0, saved.get<Int>("offset"))
        assertEquals(TimelineViewModel.INITIAL, saved.get<Int>("window_size"))
        val restored = TimelineViewModel(repo, saved)
        restored.startObserving(); restored.selectDay(day.plusDays(1), zone); advanceUntilIdle()
        assertEquals(day.plusDays(1), restored.state.value.date)
        assertTrue(restored.state.value.records.isEmpty()); assertEquals(0, restored.state.value.offset)
    }
    @Test fun deletingEveryRecordResetsTheWindowAndEarlierControl() = runTest(dispatcher) {
        val repo = StoredRecords(1_000)
        val vm = TimelineViewModel(repo, SavedStateHandle()); vm.selectDay(day, zone); advanceUntilIdle()
        repeat(4) { vm.loadLater(); advanceUntilIdle() }
        assertTrue(vm.state.value.canLoadEarlier)
        repo.records.clear(); repo.changes.emit(Unit); advanceUntilIdle()
        assertEquals(0, vm.state.value.totalCount); assertEquals(0, vm.state.value.offset)
        assertTrue(vm.state.value.records.isEmpty())
        assertFalse(vm.state.value.canLoadEarlier); assertFalse(vm.state.value.canLoadLater)
    }
    @Test fun skippedCivilDayIsEmptyAndDstUsesActualMidnights() = runTest(dispatcher) {
        val vm = TimelineViewModel(StoredRecords(1), SavedStateHandle())
        vm.selectDay(LocalDate.parse("2011-12-30"), ZoneId.of("Pacific/Apia")); advanceUntilIdle()
        assertTrue(vm.state.value.loaded); assertTrue(vm.state.value.records.isEmpty()); assertNull(vm.state.value.error)
        val dst = timelineDayRange(LocalDate.parse("2026-03-08"), ZoneId.of("America/New_York"))!!
        assertEquals(Instant.parse("2026-03-08T05:00:00Z"), dst.start)
        assertEquals(Instant.parse("2026-03-09T04:00:00Z"), dst.endExclusive)
    }

    @Test fun stoppedScreensDoNotRefreshUntilForegroundObservationRestarts() = runTest(dispatcher) {
        val repo = StoredRecords(1)
        val vm = TimelineViewModel(repo, SavedStateHandle()); vm.selectDay(day, zone); advanceUntilIdle()
        vm.stopObserving()
        repo.records[0] = repo.records[0].copy(event = repo.records[0].event.copy(title = "Foreground change"))
        repo.changes.emit(Unit); advanceUntilIdle()
        assertNull(vm.state.value.records.single().event.title)
        vm.startObserving(); advanceUntilIdle()
        assertEquals("Foreground change", vm.state.value.records.single().event.title)
    }

    private fun ids(vm: TimelineViewModel) = vm.state.value.records.map { it.event.id.value.leastSignificantBits }
    private class StoredRecords(count: Int, initial: Boolean = true) : TimelineRepository {
        val changes = MutableSharedFlow<Unit>(replay = 1).apply { if (initial) tryEmit(Unit) }
        var failure: Exception? = null
        var block: CompletableDeferred<Unit>? = null
        private val time = Instant.parse("2026-10-11T08:00:00Z")
        val records = (0 until count).map { LifeEventRecord(LifeEvent(EventId(UUID(0, it.toLong())), EventType.ACTIVITY, time, time)) }.toMutableList()
        override fun observeChanges() = changes
        override suspend fun page(range: TimeRange, offset: Int, limit: Int, showAppUsage: Boolean): TimelinePage {
            if (range.start == Instant.parse("2026-10-11T00:00:00Z")) block?.let { withContext(NonCancellable) { it.await() } }
            failure?.let { throw it }
            val dayRecords = records.filter { it.event.occurredAt in range }
                .filter { showAppUsage || it.event.type != EventType.APP_USAGE }
                .sortedWith(compareBy<LifeEventRecord, LifeEvent>(LifeEvent.chronologicalOrder) { it.event }.reversed())
            return TimelinePage(dayRecords.drop(offset).take(limit), offset, dayRecords.size,
                eventCount = dayRecords.count { it.event.type != EventType.APP_USAGE })
        }
        override suspend fun get(id: EventId) = records.firstOrNull { it.event.id == id }
    }
}
