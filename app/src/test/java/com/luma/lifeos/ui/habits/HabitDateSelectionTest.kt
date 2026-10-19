package com.luma.lifeos.ui.habits

import androidx.lifecycle.SavedStateHandle
import com.luma.lifeos.domain.habit.HabitDay
import com.luma.lifeos.domain.habit.HabitRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HabitDateSelectionTest {
    private val dispatcher = StandardTestDispatcher()
    private val date = LocalDate.parse("2026-10-18")
    private val zone = ZoneId.of("UTC")
    private val clock = Clock.fixed(Instant.parse("2026-10-18T09:00:00Z"), zone)

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun selectingAnotherDateImmediatelyInvalidatesOldRows() = runTest(dispatcher) {
        val base = HabitRepositoryFixture(clock)
        val delayed = MutableSharedFlow<List<HabitDay>>()
        val repo = object : HabitRepository by base {
            override fun observeDay(date: LocalDate): Flow<List<HabitDay>> =
                if (date == this@HabitDateSelectionTest.date) base.observeDay(date) else delayed
        }
        val vm = HabitsViewModel(repo, SavedStateHandle(), clock)
        vm.selectDate(date, zone); advanceUntilIdle()
        assertEquals(1, vm.state.value.days.size)

        val oldRow = vm.state.value.days.single()
        vm.selectDate(date.plusDays(1), zone)
        // A tap may arrive before either the selection coroutine or the new read runs.
        vm.log(oldRow)
        assertEquals(date.plusDays(1), vm.state.value.date)
        assertTrue(vm.state.value.days.isEmpty())
        advanceUntilIdle()
        assertTrue(base.logInputs.isEmpty())
        assertTrue(vm.state.value.loading)
    }

    @Test fun failedNewDateStaysEmptyAndRetryReadsThatDate() = runTest(dispatcher) {
        val base = HabitRepositoryFixture(clock)
        val next = date.minusDays(1)
        val requests = mutableListOf<LocalDate>()
        var fail = true
        val repo = object : HabitRepository by base {
            override fun observeDay(date: LocalDate): Flow<List<HabitDay>> {
                requests += date
                return if (date == next && fail) flow { error("read failed") } else base.observeDay(date)
            }
        }
        val vm = HabitsViewModel(repo, SavedStateHandle(), clock)
        vm.selectDate(date, zone); advanceUntilIdle()
        vm.selectDate(next, zone); advanceUntilIdle()
        assertTrue(vm.state.value.days.isEmpty())
        assertEquals(next, vm.state.value.date)
        assertEquals(HabitUiError.LOAD, vm.state.value.error)
        fail = false
        vm.retry(); advanceUntilIdle()
        assertEquals(next, requests.last())
        assertEquals(next, vm.state.value.days.single().day.date)
        assertNull(vm.state.value.error)
    }

    @Test fun rapidSelectionsIgnoreLateRowsAndLateErrors() = runTest(dispatcher) {
        val base = HabitRepositoryFixture(clock)
        val releaseA = CompletableDeferred<Unit>()
        val releaseB = CompletableDeferred<Unit>()
        val next = date.minusDays(1)
        val future = date.plusDays(1)
        val repo = object : HabitRepository by base {
            override fun observeDay(date: LocalDate): Flow<List<HabitDay>> = when (date) {
                this@HabitDateSelectionTest.date -> lateFlow {
                    emit(base.habitDay(date))
                    withContext(NonCancellable) { releaseA.await() }
                    emit(base.habitDay(date))
                }
                next -> lateFlow {
                    withContext(NonCancellable) { releaseB.await() }
                    error("late read failure")
                }
                else -> base.observeDay(date)
            }
        }
        val vm = HabitsViewModel(repo, SavedStateHandle(), clock)
        vm.selectDate(date, zone); runCurrent()
        val rowA = vm.state.value.days.single()
        vm.selectDate(next, zone); runCurrent()
        vm.selectDate(future, zone); runCurrent()
        releaseA.complete(Unit); releaseB.complete(Unit); advanceUntilIdle()

        assertEquals(future, vm.state.value.date)
        assertEquals(future, vm.state.value.loadedDate)
        assertEquals(future, vm.state.value.days.single().day.date)
        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.days.single().canLog)
        assertNull(vm.dateForRow(rowA))
        vm.log(rowA)
        assertTrue(base.logInputs.isEmpty())
    }

    @Test fun oldRowCannotLogOrOpenAfterReturningToTheSameDate() = runTest(dispatcher) {
        val base = HabitRepositoryFixture(clock)
        val vm = HabitsViewModel(base, SavedStateHandle(), clock)
        vm.selectDate(date, zone); advanceUntilIdle()
        val old = vm.state.value.days.single()
        vm.selectDate(date.minusDays(1), zone); advanceUntilIdle()
        vm.selectDate(date, zone); advanceUntilIdle()

        vm.log(old)
        assertNull(vm.dateForRow(old))
        assertTrue(base.logInputs.isEmpty())
        val current = vm.state.value.days.single()
        assertEquals(date, vm.dateForRow(current))
        assertNull(vm.dateForRow(current.copy(day = current.day.copy(date = date.minusDays(1)))))
        vm.log(current); advanceUntilIdle()
        assertEquals(date, base.logs.values.single().date)
    }

    @Test fun zoneLookupDelayCancelsOldObservationImmediatelyAndIgnoresLateLookup() = runTest(dispatcher) {
        val base = HabitRepositoryFixture(clock)
        val zoneGate = CompletableDeferred<Unit>()
        var blockZone = false
        var oldCancelled = false
        val repo = object : HabitRepository by base {
            override suspend fun trackingZone(): ZoneId? {
                if (blockZone) withContext(NonCancellable) { zoneGate.await() }
                return zone
            }
            override fun observeDay(date: LocalDate): Flow<List<HabitDay>> = flow {
                try {
                    base.observeDay(date).collect { emit(it) }
                } finally { if (date == this@HabitDateSelectionTest.date) oldCancelled = true }
            }
        }
        val vm = HabitsViewModel(repo, SavedStateHandle(), clock)
        vm.selectDate(date, zone); runCurrent()
        val old = vm.state.value.days.single()
        blockZone = true
        vm.selectDate(date.minusDays(1), zone); runCurrent()
        assertTrue(oldCancelled)
        assertTrue(vm.state.value.days.isEmpty())
        assertNull(vm.dateForRow(old))
        blockZone = false
        vm.selectDate(date.minusDays(2), zone); runCurrent()
        zoneGate.complete(Unit); advanceUntilIdle()
        assertEquals(date.minusDays(2), vm.state.value.date)
        assertEquals(date.minusDays(2), vm.state.value.loadedDate)
    }

    @Test fun pendingRetryAfterRecreationKeepsItsOriginalDateZoneAndIdentity() = runTest(dispatcher) {
        val base = HabitRepositoryFixture(clock).apply { failNextLog = true }
        val saved = SavedStateHandle()
        val vm = HabitsViewModel(base, saved, clock)
        vm.selectDate(date.minusDays(1), zone); advanceUntilIdle()
        vm.log(vm.state.value.days.single()); advanceUntilIdle()
        val original = vm.state.value.pendingLogs.single()
        vm.selectDate(date.plusDays(1), zone); advanceUntilIdle()

        val restored = HabitsViewModel(base, saved, clock)
        // Restored rows are never inferred from a saved date or pending action.
        assertTrue(restored.state.value.days.isEmpty())
        assertNull(restored.state.value.loadedDate)
        restored.selectDate(date.plusDays(1), zone); advanceUntilIdle()
        restored.retryLog(original.id); advanceUntilIdle()

        val log = base.logs.values.single()
        assertEquals(original.id, log.id)
        assertEquals(date.minusDays(1), log.date)
        assertEquals(zone, log.zone)
        assertNull(log.occurredAt)
        assertEquals(listOf(original.id, original.id), base.logInputs.map { it.first })
        assertTrue(base.logInputs.all { it.second == original.input })
        assertFalse(restored.state.value.days.single().canLog)
    }

    // Deliberately simulates a source that delivers after cancellation; normal flow builders
    // enforce cancellation before emit and cannot exercise the generation guard.
    @OptIn(InternalCoroutinesApi::class)
    private fun lateFlow(block: suspend FlowCollector<List<HabitDay>>.() -> Unit) =
        object : Flow<List<HabitDay>> {
            override suspend fun collect(collector: FlowCollector<List<HabitDay>>) { collector.block() }
        }
}
