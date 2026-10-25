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
import com.luma.lifeos.domain.todo.TodoTask
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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

    @Test fun delayedTaskPageForPreviousDateCannotReplaceCurrentSelection() = runTest(dispatcher) {
        val reads = ControlledReads()
        val vm = CalendarViewModel(reads, SavedStateHandle())
        vm.setEnvironment(date, ZoneOffset.UTC, DayOfWeek.MONDAY, 0)
        advanceUntilIdle()

        val oldTask = task("00000000-0000-0000-0000-000000000001", "Old date", date)
        reads.emitTasks(date, TodoPage(listOf(oldTask), 1))
        advanceUntilIdle()
        assertEquals(listOf(oldTask.id), ready(vm.state.value.taskPage).items.map(TodoTask::id))

        val nextDate = date.plusDays(1)
        vm.selectDate(nextDate)
        assertEquals(CalendarDataState.Loading, vm.state.value.taskPage)
        advanceUntilIdle()
        val currentTask = task("00000000-0000-0000-0000-000000000002", "Current date", nextDate)
        reads.emitTasks(date, TodoPage(listOf(oldTask), 1))
        reads.emitTasks(nextDate, TodoPage(listOf(currentTask), 1))
        advanceUntilIdle()

        assertEquals(nextDate, vm.state.value.selectedDate)
        assertEquals(listOf(currentTask.id), ready(vm.state.value.taskPage).items.map(TodoTask::id))
    }

    @Test fun dayRolloverKeepsSelectionAndRefreshesMembershipAndPaging() = runTest(dispatcher) {
        val vm = viewModel(Reads(memoryTotal = 123))
        advanceUntilIdle()
        vm.loadMore()
        vm.loadMoreMemories()
        advanceUntilIdle()
        assertEquals(100, vm.state.value.pageLimit)
        assertEquals(100, vm.state.value.memoryPageLimit)

        vm.setEnvironment(date.plusDays(1), ZoneOffset.UTC, DayOfWeek.MONDAY, 0)
        assertEquals(date, vm.state.value.selectedDate)
        assertEquals(date.plusDays(1), vm.state.value.today)
        assertEquals(50, vm.state.value.pageLimit)
        assertEquals(50, vm.state.value.memoryPageLimit)
        assertEquals(CalendarDataState.Loading, vm.state.value.taskPage)
        advanceUntilIdle()
        assertEquals(2, vm.state.value.scheduledTaskCount)
        assertEquals(0, vm.state.value.carryInCount)
    }

    @Test fun restoreGenerationDropsOldRowsAndResetsBothPageLimits() = runTest(dispatcher) {
        val vm = viewModel(Reads(memoryTotal = 123))
        advanceUntilIdle()
        vm.loadMore()
        vm.loadMoreMemories()
        advanceUntilIdle()
        assertEquals(100, vm.state.value.pageLimit)
        assertEquals(100, vm.state.value.memoryPageLimit)

        vm.setEnvironment(date, ZoneOffset.UTC, DayOfWeek.MONDAY, 1)
        assertEquals(50, vm.state.value.pageLimit)
        assertEquals(50, vm.state.value.memoryPageLimit)
        assertEquals(CalendarDataState.Loading, vm.state.value.taskPage)
        assertEquals(CalendarDataState.Loading, vm.state.value.memoryPage)
        advanceUntilIdle()
        assertEquals(50, ready(vm.state.value.memoryPage).items.size)
        assertEquals(4, vm.state.value.scheduledTaskCount)
    }

    @Test fun taskRetryReloadsOnlyTaskSourceAndKeepsMemoryRows() = runTest(dispatcher) {
        val reads = Reads(memoryTotal = 1).apply { failTaskPage = true }
        val vm = viewModel(reads)
        advanceUntilIdle()
        assertEquals(CalendarDataState.Error, vm.state.value.taskPage)
        val memoriesBeforeRetry = vm.state.value.memoryPage
        assertEquals(1, reads.memoryPageReads)

        reads.failTaskPage = false
        vm.retry(CalendarSource.TASKS)
        advanceUntilIdle()

        assertTrue(vm.state.value.taskPage is CalendarDataState.Ready)
        assertEquals(memoriesBeforeRetry, vm.state.value.memoryPage)
        assertEquals(1, reads.memoryPageReads)
    }

    @Test fun malformedSavedStateFallsBackToEnvironmentAndValidValuesRestore() = runTest(dispatcher) {
        val malformed = SavedStateHandle(mapOf(
            "calendar_selected_date" to "10000-01-01",
            "calendar_mode" to "invalid",
            "calendar_filter" to "invalid",
        ))
        val fallbackVm = CalendarViewModel(Reads(0), malformed)
        fallbackVm.setEnvironment(date, ZoneOffset.UTC, DayOfWeek.MONDAY, 0)
        advanceUntilIdle()
        assertEquals(date, fallbackVm.state.value.selectedDate)
        assertEquals(com.luma.lifeos.domain.calendar.CalendarMode.MONTH, fallbackVm.state.value.mode)
        assertEquals(CalendarFilter.ALL, fallbackVm.state.value.filter)

        val restoredDate = date.minusDays(1)
        val restored = SavedStateHandle(mapOf(
            "calendar_selected_date" to restoredDate.toString(),
            "calendar_mode" to "WEEK",
            "calendar_filter" to "TASKS",
        ))
        val restoredVm = CalendarViewModel(Reads(0), restored)
        restoredVm.setEnvironment(date, ZoneOffset.UTC, DayOfWeek.MONDAY, 0)
        advanceUntilIdle()
        assertEquals(restoredDate, restoredVm.state.value.selectedDate)
        assertEquals(com.luma.lifeos.domain.calendar.CalendarMode.WEEK, restoredVm.state.value.mode)
        assertEquals(CalendarFilter.TASKS, restoredVm.state.value.filter)
    }

    private fun viewModel(reads: Reads) = CalendarViewModel(reads, SavedStateHandle()).also {
        it.setEnvironment(date, ZoneOffset.UTC, DayOfWeek.MONDAY, 0)
    }

    private fun <T> ready(state: CalendarDataState<T>): T = (state as CalendarDataState.Ready).value

    /** Controllable source boundary; assertions exercise real ViewModel counts and transitions. */
    private inner class Reads(private val memoryTotal: Int) : CalendarRepository {
        var failTaskPage = false
        var memoryPageReads = 0
        var failMemoryPage = false
        var failMemoryMarkers = false
        override fun observeTaskMarkers(range: CalendarRange, today: LocalDate) =
            flowOf(mapOf(date to CalendarTaskMarker(2, false)))
        override fun observeOverdueCount(today: LocalDate) = flowOf(2)
        override fun observeTasks(date: LocalDate, today: LocalDate, overdueOnly: Boolean, limit: Int) = flow {
            if (failTaskPage) error("Unavailable task page")
            val total = when {
                overdueOnly -> 2
                date == today -> 4
                date == this@CalendarViewModelTest.date -> 2
                else -> 0
            }
            emit(TodoPage(emptyList(), total))
        }
        override fun observeMemoryMarkers(range: CalendarRange, zone: ZoneId): Flow<Map<LocalDate, Int>> = flow {
            if (failMemoryMarkers) error("Unavailable memory markers")
            emit(if (memoryTotal == 0) emptyMap() else mapOf(date to memoryTotal))
        }
        override fun observeMemories(date: LocalDate, zone: ZoneId, limit: Int): Flow<CalendarMemories> = flow {
            memoryPageReads++
            if (failMemoryPage) error("Unavailable memory page")
            val total = if (date == this@CalendarViewModelTest.date) memoryTotal else 0
            emit(CalendarMemories(total, (1..minOf(total, limit)).map { index ->
                CalendarMemory(EventId.parse("00000000-0000-0000-0000-${index.toString().padStart(12, '0')}"), "Memory $index", "Saved")
            }))
        }
    }

    private inner class ControlledReads : CalendarRepository {
        private val taskPages = mutableMapOf<LocalDate, MutableSharedFlow<TodoPage>>()

        fun emitTasks(date: LocalDate, page: TodoPage) {
            check(taskPages.getOrPut(date) { MutableSharedFlow(replay = 1) }.tryEmit(page))
        }

        override fun observeTaskMarkers(range: CalendarRange, today: LocalDate) =
            flowOf(emptyMap<LocalDate, CalendarTaskMarker>())
        override fun observeOverdueCount(today: LocalDate) = flowOf(0)
        override fun observeTasks(date: LocalDate, today: LocalDate, overdueOnly: Boolean, limit: Int) =
            taskPages.getOrPut(date) { MutableSharedFlow(replay = 1) }
        override fun observeMemoryMarkers(range: CalendarRange, zone: ZoneId) = flowOf(emptyMap<LocalDate, Int>())
        override fun observeMemories(date: LocalDate, zone: ZoneId, limit: Int) = flowOf(CalendarMemories(0, emptyList()))
    }

    private fun task(id: String, title: String, dueDate: LocalDate) = TodoTask(
        id = UUID.fromString(id),
        title = title,
        note = null,
        dueDate = dueDate,
        completedAt = null,
        createdAt = Instant.parse("2026-10-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-10-01T00:00:00Z"),
        revision = UUID.fromString("10000000-0000-0000-0000-${id.takeLast(12)}"),
    )
}
