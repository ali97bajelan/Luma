package com.luma.lifeos.ui.calendar

import androidx.lifecycle.SavedStateHandle
import com.luma.lifeos.domain.calendar.CalendarFilter
import com.luma.lifeos.domain.calendar.CalendarMemories
import com.luma.lifeos.domain.calendar.CalendarMemory
import com.luma.lifeos.domain.calendar.CalendarRange
import com.luma.lifeos.domain.calendar.CalendarRepository
import com.luma.lifeos.domain.calendar.CalendarTaskMarker
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.todo.TodoPage
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val date = LocalDate.parse("2026-10-22")

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun allItemsCountsScheduledTasksAndMemoriesWhileRibbonUsesExactDateTasks() = runTest(dispatcher) {
        val vm = viewModel(Reads(memoryTotal = 1))
        advanceUntilIdle()
        assertEquals(4, vm.state.value.scheduledTaskCount)
        assertEquals(2, vm.state.value.exactTaskCount)
        assertEquals(1, vm.state.value.selectedMemoryCount)
        assertEquals(5, vm.state.value.allItemsCount)
        assertEquals(2, vm.state.value.carryInCount)
        vm.setFilter(CalendarFilter.OVERDUE)
        advanceUntilIdle()
        assertEquals(5, vm.state.value.allItemsCount)
        assertEquals(2, ready(vm.state.value.taskPage).total)
    }

    @Test fun memoryPagesKeepFullTotalsAndDoNotExpandTaskPages() = runTest(dispatcher) {
        val vm = viewModel(Reads(memoryTotal = 123))
        advanceUntilIdle()
        assertEquals(50, ready(vm.state.value.memoryPage).items.size)
        assertEquals(127, vm.state.value.allItemsCount)
        vm.loadMoreMemories()
        advanceUntilIdle()
        assertEquals(100, ready(vm.state.value.memoryPage).items.size)
        assertEquals(50, vm.state.value.pageLimit)
        vm.loadMoreMemories()
        advanceUntilIdle()
        val page = ready(vm.state.value.memoryPage)
        assertEquals(123, page.items.size)
        assertEquals(123, page.items.map { it.id }.distinct().size)
        assertEquals(127, vm.state.value.allItemsCount)
        vm.setFilter(CalendarFilter.TASKS)
        advanceUntilIdle()
        assertEquals(50, ready(vm.state.value.memoryPage).items.size)
    }

    @Test fun failedMemoryLoadMoreRetainsRowsAndRetryRecoversWithoutChangingSelection() = runTest(dispatcher) {
        val reads = Reads(memoryTotal = 123)
        val vm = viewModel(reads)
        advanceUntilIdle()
        reads.failMemoryPage = true
        vm.loadMoreMemories()
        advanceUntilIdle()
        assertEquals(50, ready(vm.state.value.memoryPage).items.size)
        assertTrue(vm.state.value.loadMoreMemoriesError)
        reads.failMemoryPage = false
        vm.retry(CalendarSource.MEMORIES)
        advanceUntilIdle()
        assertEquals(100, ready(vm.state.value.memoryPage).items.size)
        assertEquals(date, vm.state.value.selectedDate)
        assertFalse(vm.state.value.loadMoreMemoriesError)
        assertEquals(4, vm.state.value.scheduledTaskCount)
    }

    @Test fun unavailableMemoryMarkersNeverBecomeZeroOrAFalseAllItemsTotal() = runTest(dispatcher) {
        val reads = Reads(memoryTotal = 1).apply { failMemoryMarkers = true }
        val vm = viewModel(reads)
        advanceUntilIdle()
        assertNull(vm.state.value.selectedMemoryCount)
        assertNull(vm.state.value.allItemsCount)
        assertEquals(4, vm.state.value.scheduledTaskCount)
        reads.failMemoryMarkers = false
        vm.retry(CalendarSource.MEMORIES)
        advanceUntilIdle()
        assertEquals(5, vm.state.value.allItemsCount)
    }

    @Test fun dateSwitchClearsOldMemoryRowsImmediatelyAndResetsIndependentPaging() = runTest(dispatcher) {
        val vm = viewModel(Reads(memoryTotal = 123))
        advanceUntilIdle()
        vm.loadMoreMemories()
        advanceUntilIdle()
        vm.selectDate(date.plusDays(1))
        assertEquals(CalendarDataState.Loading, vm.state.value.memoryPage)
        assertEquals(50, vm.state.value.memoryPageLimit)
        advanceUntilIdle()
        assertEquals(0, ready(vm.state.value.memoryPage).total)
        assertEquals(0, vm.state.value.selectedMemoryCount)
        assertEquals(0, vm.state.value.allItemsCount)
    }

    private fun viewModel(reads: Reads) = CalendarViewModel(reads, SavedStateHandle()).also {
        it.setEnvironment(date, ZoneOffset.UTC, DayOfWeek.MONDAY, 0)
    }

    private fun <T> ready(state: CalendarDataState<T>): T = (state as CalendarDataState.Ready).value

    /** Controllable source boundary; assertions exercise real ViewModel counts and transitions. */
    private inner class Reads(private val memoryTotal: Int) : CalendarRepository {
        var failMemoryPage = false
        var failMemoryMarkers = false
        override fun observeTaskMarkers(range: CalendarRange, today: LocalDate) =
            flowOf(mapOf(date to CalendarTaskMarker(2, false)))
        override fun observeOverdueCount(today: LocalDate) = flowOf(2)
        override fun observeTasks(date: LocalDate, today: LocalDate, overdueOnly: Boolean, limit: Int) =
            flowOf(TodoPage(emptyList(), if (overdueOnly) 2 else if (date == today) 4 else 0))
        override fun observeMemoryMarkers(range: CalendarRange, zone: ZoneId): Flow<Map<LocalDate, Int>> = flow {
            if (failMemoryMarkers) error("Unavailable memory markers")
            emit(if (memoryTotal == 0) emptyMap() else mapOf(date to memoryTotal))
        }
        override fun observeMemories(date: LocalDate, zone: ZoneId, limit: Int): Flow<CalendarMemories> = flow {
            if (failMemoryPage) error("Unavailable memory page")
            val total = if (date == this@CalendarViewModelTest.date) memoryTotal else 0
            emit(CalendarMemories(total, (1..minOf(total, limit)).map { index ->
                CalendarMemory(EventId.parse("00000000-0000-0000-0000-${index.toString().padStart(12, '0')}"), "Memory $index", "Saved")
            }))
        }
    }
}
