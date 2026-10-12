package com.luma.lifeos.ui.search

import androidx.lifecycle.SavedStateHandle
import com.luma.lifeos.domain.model.*
import com.luma.lifeos.domain.repository.SearchRepository
import com.luma.lifeos.domain.search.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val zone = ZoneId.of("UTC")
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }
    @Test fun typingDebouncesAndStructuredFiltersApplyImmediately() = runTest(dispatcher) {
        val repo = Fixture(); val vm = SearchViewModel(repo, SavedStateHandle())
        vm.start(zone); runCurrent(); assertTrue(vm.state.value.loaded)
        vm.setText("Older"); runCurrent(); assertTrue(vm.state.value.records.isEmpty())
        advanceTimeBy(299); runCurrent(); assertFalse(vm.state.value.loaded)
        advanceTimeBy(1); runCurrent(); assertEquals("Older", vm.state.value.records.single().event.title)
        vm.setFrom(LocalDate.parse("2026-10-13")); runCurrent()
        assertTrue(vm.state.value.loaded); assertTrue(vm.state.value.records.isEmpty()); vm.stop()
    }
    @Test fun lateUncooperativeResultCannotOverwriteNewInput() = runTest(dispatcher) {
        val repo = Fixture(); val vm = SearchViewModel(repo, SavedStateHandle())
        vm.start(zone); runCurrent()
        val blocked = CompletableDeferred<Unit>(); repo.block = blocked
        vm.setText("Older"); advanceTimeBy(300); runCurrent()
        repo.block = null; vm.setText("Newer"); advanceTimeBy(300); runCurrent()
        blocked.complete(Unit); runCurrent()
        assertEquals("Newer", vm.state.value.records.single().event.title); vm.stop()
    }
    @Test fun invalidDatesNeverQueryAndClearRestoresAllInputs() = runTest(dispatcher) {
        val vm = SearchViewModel(Fixture(), SavedStateHandle()); vm.start(zone); runCurrent()
        vm.setFrom(LocalDate.parse("2026-10-13")); vm.setThrough(LocalDate.parse("2026-10-12")); runCurrent()
        assertTrue(vm.state.value.invalidDates); assertTrue(vm.state.value.records.isEmpty())
        vm.clear(); runCurrent(); assertFalse(vm.state.value.invalidDates); assertEquals(2, vm.state.value.totalCount); vm.stop()
    }
    @Test fun errorRetryRetainsFiltersAndRestorationRetainsDeletedSelections() = runTest(dispatcher) {
        val repo = Fixture(); val saved = SavedStateHandle(); val id = UUID(1, 2)
        val vm = SearchViewModel(repo, saved); vm.start(zone); runCurrent()
        vm.togglePerson(id); vm.setPlace(id); vm.setText("Older"); runCurrent()
        repo.fail = true; advanceTimeBy(300); runCurrent(); assertTrue(vm.state.value.error)
        repo.fail = false; vm.retry(); runCurrent(); assertFalse(vm.state.value.error)
        assertEquals(setOf(id), vm.state.value.input.personIds)
        vm.stop(); val restored = SearchViewModel(repo, saved); restored.start(zone); runCurrent()
        assertEquals("Older", restored.state.value.input.text)
        assertEquals(id, restored.state.value.input.placeId); assertEquals(setOf(id), restored.state.value.input.personIds)
        assertTrue(restored.state.value.options.people.isEmpty()); restored.stop()
    }
    @Test fun pagesStayBoundedAndInvalidationResetsToAConsistentFirstPage() = runTest(dispatcher) {
        val repo = Fixture(1_000); val vm = SearchViewModel(repo, SavedStateHandle())
        vm.start(zone); runCurrent(); assertEquals(50, vm.state.value.records.size)
        repeat(7) { vm.loadLater(); runCurrent() }
        assertEquals(300, vm.state.value.records.size); assertEquals(100, vm.state.value.offset)
        vm.loadEarlier(); runCurrent(); assertEquals(50, vm.state.value.offset)
        repo.records.clear(); repo.changes.emit(Unit); runCurrent()
        assertEquals(0, vm.state.value.offset); assertEquals(0, vm.state.value.totalCount)
        assertTrue(vm.state.value.records.isEmpty()); vm.stop()
    }
    @Test fun restoredWindowAndForegroundZoneRequeryDateBounds() = runTest(dispatcher) {
        val repo = Fixture(1_000); val saved = SavedStateHandle()
        val vm = SearchViewModel(repo, saved); vm.start(zone); runCurrent()
        repeat(7) { vm.loadLater(); runCurrent() }; vm.stop()
        val restored = SearchViewModel(repo, saved); restored.start(zone); runCurrent()
        assertEquals(100, restored.state.value.offset)
        restored.setFrom(LocalDate.parse("2026-10-12")); restored.setThrough(LocalDate.parse("2026-10-12")); runCurrent()
        assertEquals(1_000, restored.state.value.totalCount)
        restored.start(ZoneId.of("Pacific/Kiritimati")); runCurrent()
        assertEquals(0, restored.state.value.totalCount); restored.stop()
    }
    @Test fun aShortFinalPageCanPrependWithoutOverlappingRows() = runTest(dispatcher) {
        val vm = SearchViewModel(Fixture(325), SavedStateHandle()); vm.start(zone); runCurrent()
        repeat(6) { vm.loadLater(); runCurrent() }
        assertEquals(25, vm.state.value.offset)
        vm.loadEarlier(); runCurrent()
        val ids = vm.state.value.records.map { it.event.id.value.leastSignificantBits }
        assertEquals((0L..299L).toList(), ids)
        assertEquals(300, ids.distinct().size); vm.stop()
    }
    @Test fun returningFromDetailKeepsTheOpenedResultNearWindowEndReachable() = runTest(dispatcher) {
        val repo = Fixture(1_000); val saved = SavedStateHandle()
        val vm = SearchViewModel(repo, saved); vm.start(zone); runCurrent()
        repeat(7) { vm.loadLater(); runCurrent() }
        val opened = vm.state.value.records[250].event.id
        vm.rememberResult(opened); vm.stop(); vm.start(zone); runCurrent()
        assertEquals(opened, vm.state.value.records.first().event.id)
        vm.stop()
        val restored = SearchViewModel(repo, saved); restored.start(zone); runCurrent()
        assertEquals(opened, restored.state.value.records.first().event.id); restored.stop()
    }
    @Test fun sameCountReorderingBeforeInvalidationCannotPublishDuplicateKeys() = runTest(dispatcher) {
        val repo = Fixture(150); val vm = SearchViewModel(repo, SavedStateHandle())
        vm.start(zone); runCurrent()
        repo.records.add(75, repo.records.removeAt(0))
        vm.loadLater(); runCurrent()
        val ids = vm.state.value.records.map { it.event.id.value.leastSignificantBits }
        assertEquals((1L..50L).toList(), ids)
        assertEquals(ids.size, ids.distinct().size); assertEquals(0, vm.state.value.offset); vm.stop()
    }
    private class Fixture(count: Int = 2) : SearchRepository {
        val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 10)
        var fail = false
        var block: CompletableDeferred<Unit>? = null
        val records = MutableList(count) { n -> LifeEventRecord(LifeEvent(EventId(UUID(0, n.toLong())),
            EventType.ACTIVITY, Instant.parse("2026-10-12T12:00:00Z"), Instant.parse("2026-10-12T12:00:00Z"), title = if (n == 0) "Older" else "Newer")) }
        override fun observeOptions() = flowOf(SearchOptions())
        override fun observeChanges() = changes.onStart { emit(Unit) }
        override suspend fun page(query: SearchQuery, zone: ZoneId, offset: Int, limit: Int): SearchPage {
            val captured = records.toList(); val wait = block
            if (wait != null) withContext(NonCancellable) { wait.await() }
            if (fail) error("Storage unavailable")
            val bounds = query.bounds(zone)
            val hits = captured.filter { (query.text.isBlank() || it.event.title!!.contains(query.text)) &&
                (bounds.start == null || !it.event.occurredAt.isBefore(bounds.start)) &&
                (bounds.endExclusive == null || it.event.occurredAt.isBefore(bounds.endExclusive)) }
            return SearchPage(hits.drop(offset).take(limit), offset, hits.size)
        }
    }
}
