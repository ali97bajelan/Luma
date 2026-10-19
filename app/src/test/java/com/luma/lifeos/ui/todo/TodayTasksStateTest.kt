package com.luma.lifeos.ui.todo

import androidx.lifecycle.SavedStateHandle
import com.luma.lifeos.domain.todo.TodoBucket
import com.luma.lifeos.domain.todo.todoBucket
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TodayTasksStateTest {
    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.parse("2026-10-19")

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun previewCapsRowsButReportsAllEligibleTasks() = runTest(dispatcher) {
        val eligible = listOf(
            todoFixtureTask("Older overdue", id = stableId("overdue-older"), dueDate = today.minusDays(4)),
            todoFixtureTask("Recent overdue", id = stableId("overdue-recent"), dueDate = today.minusDays(1)),
            todoFixtureTask("Due today", id = stableId("today"), dueDate = today),
            todoFixtureTask("Also due today", id = stableId("today-2"), dueDate = today),
        )
        val unrelated = listOf(
            todoFixtureTask("Future", id = stableId("future"), dueDate = today.plusDays(1)),
            todoFixtureTask("No date", id = stableId("no-date"), dueDate = null),
            todoFixtureTask("Completed", id = stableId("completed"), dueDate = today, completedAt = java.time.Instant.EPOCH),
        )
        val repository = TodoRepositoryFixture(eligible + unrelated)
        val states = observeTodayTasks(repository, today).take(2).toList()

        assertTrue(states.first() is TodayTasksState.Loading)
        val preview = states.last() as TodayTasksState.Data
        assertEquals(3, preview.tasks.size)
        assertEquals(4, preview.total)
        assertEquals(eligible.take(2).map { it.id }, preview.tasks.take(2).map { it.id })
        assertTrue(preview.tasks.last().id in eligible.takeLast(2).map { it.id })
        assertTrue(preview.tasks.none { it.id in unrelated.map { row -> row.id } })
        assertEquals(3, repository.todayRequests.single().limit)
        assertEquals(today, repository.todayRequests.single().date)
    }

    @Test fun historicalAndFutureDaysStayHiddenAcrossMidnightAndZoneChanges() {
        val task = todoFixtureTask("Civil date", dueDate = today)
        assertEquals(TodoBucket.TODAY, todoBucket(task, today))
        assertEquals(TodoBucket.OVERDUE, todoBucket(task, today.plusDays(1)))
        assertEquals(today, task.dueDate)

        assertTrue(shouldShowTodayTasks(today, today))
        assertFalse(shouldShowTodayTasks(today.minusDays(1), today))
        assertFalse(shouldShowTodayTasks(today.plusDays(1), today))
        // After midnight or travel, yesterday is historical; selecting the new device day restores it.
        assertFalse(shouldShowTodayTasks(today, today.plusDays(1)))
        assertTrue(shouldShowTodayTasks(today.plusDays(1), today.plusDays(1)))
    }

    @Test fun previewFailureIsScopedAndTheNextObservationCanRetry() = runTest(dispatcher) {
        val due = todoFixtureTask("Still due", dueDate = today)
        val repository = TodoRepositoryFixture(listOf(due)).apply { todayFailures = 1 }
        val failed = observeTodayTasks(repository, today).take(2).toList().last()
        assertEquals(TodayTasksState.Error, failed)

        val retried = observeTodayTasks(repository, today).take(2).toList().last()
        assertEquals(TodayTasksState.Data(listOf(due), 1), retried)
        assertEquals(today, repository.tasks.single().dueDate)
    }

    @Test fun committedCompletionBackfillsTheNextEligibleTask() = runTest(dispatcher) {
        val tasks = (1..4).map { index ->
            todoFixtureTask("Eligible $index", id = stableId("backfill-$index"), dueDate = today.minusDays(index.toLong()))
        }
        val repository = TodoRepositoryFixture(tasks)
        val updates = Channel<TodayTasksState>(Channel.UNLIMITED)
        backgroundScope.launch { observeTodayTasks(repository, today).collect(updates::send) }
        assertTrue(updates.receive() is TodayTasksState.Loading)
        val before = updates.receive() as TodayTasksState.Data
        assertEquals(3, before.tasks.size)
        assertEquals(4, before.total)
        assertFalse(before.tasks.any { it.id == tasks.first().id })

        val completed = before.tasks.first()
        repository.setCompleted(completed.id, completed.revision, UUID.randomUUID(), completed = true)
        advanceUntilIdle()
        val after = updates.receive() as TodayTasksState.Data

        assertEquals(3, after.tasks.size)
        assertEquals(3, after.total)
        assertFalse(after.tasks.any { it.id == completed.id })
        assertTrue(after.tasks.any { it.id == tasks.first().id })
    }

    @Test fun failedCompletionKeepsItsRevisionTokenAndPublishesOneResultOnRetry() = runTest(dispatcher) {
        val task = todoFixtureTask("Complete on Today", dueDate = today)
        val repository = TodoRepositoryFixture(listOf(task)).apply {
            failNextCompletionBeforeCommit = IllegalStateException("temporary write failure")
        }
        val saved = SavedStateHandle()
        val actions = TodayTasksViewModel(repository, saved)
        advanceUntilIdle()

        actions.setCompleted(task, true)
        advanceUntilIdle()
        assertEquals(TodoListRowError.FAILED, actions.state.value.rowErrors[task.id])
        val pending = actions.state.value.pendingIntents.getValue(task.id)
        assertFalse(task.id in actions.state.value.inFlight)

        actions.retry(task.id)
        advanceUntilIdle()

        val result = actions.state.value.actionResult
        assertNotNull(result)
        assertEquals(task.id, result?.taskId)
        assertTrue(result?.completed == true)
        assertEquals(pending.resultingRevision, result?.resultingRevision)
        assertEquals(2, repository.completionCalls.size)
        assertEquals(repository.completionCalls[0], repository.completionCalls[1])
        assertEquals(result?.resultingRevision, repository.tasks.single().revision)

        actions.consumeActionResult(requireNotNull(result))
        assertNull(actions.state.value.actionResult)
        val recreated = TodayTasksViewModel(repository, saved)
        advanceUntilIdle()
        assertNull(recreated.state.value.actionResult)
    }

    @Test fun committedResponseLossIsReconciledWithoutRepeatingCompletion() = runTest(dispatcher) {
        val task = todoFixtureTask("Ambiguous Today completion", dueDate = today)
        val repository = TodoRepositoryFixture(listOf(task)).apply {
            failNextCompletionAfterCommit = IllegalStateException("response lost")
        }
        val actions = TodayTasksViewModel(repository, SavedStateHandle())
        advanceUntilIdle()

        actions.setCompleted(task, true)
        advanceUntilIdle()

        assertEquals(1, repository.completionCalls.size)
        assertTrue(repository.tasks.single().isCompleted)
        assertEquals(repository.tasks.single().revision, actions.state.value.actionResult?.resultingRevision)
        assertTrue(actions.state.value.pendingIntents.isEmpty())
    }

    private fun stableId(value: String) = UUID.nameUUIDFromBytes(value.toByteArray())
}
