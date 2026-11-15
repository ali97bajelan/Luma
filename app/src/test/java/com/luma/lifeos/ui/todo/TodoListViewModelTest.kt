package com.luma.lifeos.ui.todo

import androidx.lifecycle.SavedStateHandle
import com.luma.lifeos.domain.todo.TODO_DEFAULT_PAGE_SIZE
import com.luma.lifeos.domain.todo.TodoImportance
import com.luma.lifeos.domain.todo.TodoSort
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TodoListViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun listExpandsFromFiftyAndTracksLiveInsertDeleteAndReorder() = runTest(dispatcher) {
        val tasks = (0 until 51).map { index ->
            todoFixtureTask(
                title = "Task $index",
                id = stableId("list-$index"),
                dueDate = LocalDate.parse("2026-10-20").plusDays(index.toLong()),
            )
        }
        val repository = TodoRepositoryFixture(tasks)
        val list = TodoListViewModel(repository, SavedStateHandle())
        advanceUntilIdle()
        assertEquals(50, list.state.value.page.items.size)
        assertEquals(51, list.state.value.page.total)

        list.loadMore()
        advanceUntilIdle()
        assertEquals(100, list.state.value.pageLimit)
        assertEquals(51, list.state.value.page.items.size)
        assertEquals(50, repository.listRequests.first().limit)
        assertEquals(100, repository.listRequests.last().limit)

        val moved = tasks.last().copy(
            dueDate = LocalDate.parse("2026-10-18"),
            updatedAt = tasks.last().updatedAt.plusSeconds(1),
            revision = UUID.randomUUID(),
        )
        repository.replace(moved)
        repository.remove(tasks[10].id)
        val inserted = todoFixtureTask("Inserted", id = stableId("inserted"), dueDate = LocalDate.parse("2026-10-19"))
        repository.replace(inserted)
        advanceUntilIdle()

        val livePage = list.state.value.page
        assertEquals(51, livePage.total)
        assertEquals(51, livePage.items.size)
        assertEquals(livePage.items.size, livePage.items.map { it.id }.distinct().size)
        assertEquals(moved.id, livePage.items.first().id)
        assertTrue(livePage.items.any { it.id == inserted.id })
        assertFalse(livePage.items.any { it.id == tasks[10].id })
    }

    @Test fun failedInitialAndExpandedReadsHaveIndependentRetryStates() = runTest(dispatcher) {
        val tasks = (0 until 51).map { todoFixtureTask("Task $it", id = stableId("retry-$it")) }
        val repository = TodoRepositoryFixture(tasks).apply { listFailures = 1 }
        val list = TodoListViewModel(repository, SavedStateHandle())
        advanceUntilIdle()
        assertEquals(TodoListLoadError.INITIAL, list.state.value.loadError)

        list.retryLoad()
        advanceUntilIdle()
        assertTrue(list.state.value.dataFresh)
        assertEquals(50, list.state.value.page.items.size)

        repository.listFailures = 1
        list.loadMore()
        advanceUntilIdle()
        assertEquals(TodoListLoadError.PAGE, list.state.value.loadError)
        assertTrue(list.state.value.dataFresh)
        assertEquals(50, list.state.value.page.items.size)
        assertEquals(51, list.state.value.page.total)

        list.retryLoad()
        advanceUntilIdle()
        assertTrue(list.state.value.dataFresh)
        assertEquals(51, list.state.value.page.items.size)
        assertEquals(100, list.state.value.pageLimit)
    }

    @Test fun rowActionSubmitsOnceAndCompletionUndoUsesCommittedRevision() = runTest(dispatcher) {
        val open = todoFixtureTask("Finish this")
        val repository = TodoRepositoryFixture(listOf(open))
        val list = TodoListViewModel(repository, SavedStateHandle())
        advanceUntilIdle()

        list.setCompleted(open.id, true)
        list.setCompleted(open.id, true)
        advanceUntilIdle()

        assertEquals(TodoFamilyActionPhase.APPLIED, list.state.value.familyAction.phase)
        assertEquals(1, repository.completionCalls.size)
        val result = list.state.value.undoableCompletion
        assertNotNull(result)
        assertEquals(open.id, result?.taskId)
        assertTrue(result?.completed == true)
        assertNotNull(result?.completionCommandId)

        list.undoCompletion()
        advanceUntilIdle()
        assertTrue(repository.tasks.single().isCompleted.not())
        assertEquals(null, list.state.value.undoableCompletion)
        assertEquals(2, repository.completionCalls.size)
        assertFalse(list.state.value.undoing)
    }

    @Test fun staleCompletionUndoBecomesTerminalAndDoesNotChangeTheTask() = runTest(dispatcher) {
        val open = todoFixtureTask("Stale undo")
        val repository = TodoRepositoryFixture(listOf(open))
        val list = TodoListViewModel(repository, SavedStateHandle())
        advanceUntilIdle()
        list.setCompleted(open.id, true)
        advanceUntilIdle()

        val committed = repository.tasks.single()
        repository.replace(committed.copy(
            title = "Edited after completion",
            updatedAt = committed.updatedAt.plusSeconds(1),
            revision = UUID.randomUUID(),
        ))
        list.undoCompletion()
        advanceUntilIdle()

        assertTrue(list.state.value.undoTerminal)
        assertTrue(repository.tasks.single().isCompleted)
        assertEquals("Edited after completion", repository.tasks.single().title)
        assertNotNull(list.state.value.undoableCompletion?.completionCommandId)
    }

    @Test fun importanceFilterMatchesChildrenWithoutAddingTheirParentAndRetainsSelection() = runTest(dispatcher) {
        val root = todoFixtureTask("Undated None root", dueDate = null, importance = TodoImportance.NONE)
        val child = todoFixtureTask(
            "High child",
            id = stableId("high-child"),
            dueDate = null,
            importance = TodoImportance.HIGH,
            parentId = root.id,
            siblingPosition = 0,
            familyRevision = root.familyRevision,
        )
        val repository = TodoRepositoryFixture(listOf(root, child))
        val saved = SavedStateHandle()
        val list = TodoListViewModel(repository, saved)
        advanceUntilIdle()
        assertEquals(listOf(root.id), list.state.value.rowPage.items.map { it.task.id })

        list.setImportanceFilter(TodoImportance.HIGH)
        advanceUntilIdle()
        assertEquals(1, list.state.value.rowPage.total)
        assertEquals(listOf(child.id), list.state.value.rowPage.items.map { it.task.id })
        assertEquals(root.id, list.state.value.rowPage.items.single().parent?.id)

        val recreated = TodoListViewModel(repository, saved)
        advanceUntilIdle()
        assertEquals(TodoImportance.HIGH, recreated.state.value.importanceFilter)
        assertEquals(listOf(child.id), recreated.state.value.rowPage.items.map { it.task.id })

        recreated.setImportanceFilter(TodoImportance.NONE)
        advanceUntilIdle()
        assertEquals(1, recreated.state.value.rowPage.total)
        assertEquals(listOf(root.id), recreated.state.value.rowPage.items.map { it.task.id })
        recreated.setSort(TodoSort.IMPORTANCE)
        advanceUntilIdle()
        assertEquals(TodoSort.IMPORTANCE, recreated.state.value.sort)
        recreated.resetQuery()
        advanceUntilIdle()
        assertEquals(null, recreated.state.value.importanceFilter)
        assertEquals(TodoSort.DATE, recreated.state.value.sort)
    }

    @Test fun completedSelectionAndEachModePrefixSurviveRecreation() = runTest(dispatcher) {
        val open = (0 until 51).map { todoFixtureTask("Open $it", id = stableId("open-$it")) }
        val done = (0 until 51).map { index ->
            todoFixtureTask(
                "Done $index",
                id = stableId("done-$index"),
                completedAt = Instant.parse("2026-10-01T00:00:00Z").plusSeconds(index.toLong()),
            )
        }
        val repository = TodoRepositoryFixture(open + done)
        val saved = SavedStateHandle()
        val list = TodoListViewModel(repository, saved)
        advanceUntilIdle()
        list.loadMore()
        advanceUntilIdle()
        assertEquals(100, list.state.value.pageLimit)

        list.selectCompleted(true)
        advanceUntilIdle()
        assertEquals(TODO_DEFAULT_PAGE_SIZE, list.state.value.pageLimit)
        list.loadMore()
        advanceUntilIdle()
        list.selectCompleted(false)
        advanceUntilIdle()
        assertEquals(100, list.state.value.pageLimit)
        list.selectCompleted(true)
        advanceUntilIdle()
        assertEquals(100, list.state.value.pageLimit)

        val recreated = TodoListViewModel(repository, saved)
        advanceUntilIdle()
        assertTrue(recreated.state.value.completed)
        assertEquals(100, recreated.state.value.pageLimit)
        assertEquals(51, recreated.state.value.page.total)
        assertEquals(100, saved.get<Int>("todo_list_page_limit_open_-1_DATE"))
    }

    @Test fun refreshDayUpdatesTheDeviceDayUsedByListGroups() = runTest(dispatcher) {
        val list = TodoListViewModel(TodoRepositoryFixture(), SavedStateHandle())
        advanceUntilIdle()
        val tomorrow = LocalDate.parse("2026-10-20")
        list.refreshDay(tomorrow)
        assertEquals(tomorrow, list.state.value.today)
        assertFalse(list.state.value.today == LocalDate.parse("2026-10-19"))
    }

    private fun stableId(value: String) = UUID.nameUUIDFromBytes(value.toByteArray())
}
