package com.luma.lifeos.ui.todo

import androidx.lifecycle.SavedStateHandle
import com.luma.lifeos.domain.todo.TodoInput
import com.luma.lifeos.domain.todo.TodoPage
import com.luma.lifeos.domain.todo.TodoRepository
import com.luma.lifeos.domain.todo.TodoTask
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TodoRestoreInvalidationTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
    }

    @After fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test fun restoredTaskWithSameIdCannotReceiveRetainedEditorDraft() = runTest(dispatcher) {
        val original = task("Before restore")
        val repository = FakeTodoRepository(original)
        val saved = SavedStateHandle()
        val editor = TodoEditorViewModel(repository, original.id, null, saved)
        advanceUntilIdle()

        editor.setTitle("Unsaved draft")
        editor.setDueTime(LocalTime.of(9, 5))
        assertTrue(editor.state.value.dirty)
        editor.invalidateAfterRestore()
        assertTrue(editor.state.value.finished)
        assertEquals("Unsaved draft", editor.state.value.draft.title)
        assertEquals("09:05", saved.get<String>("todo_draft_due_time"))

        repository.current = task("Restored data", id = original.id)
        val loadsAfterRestore = repository.loads
        val recreated = TodoEditorViewModel(repository, original.id, null, saved)
        advanceUntilIdle()

        assertTrue(recreated.state.value.finished)
        assertEquals("Unsaved draft", recreated.state.value.draft.title)
        assertEquals(loadsAfterRestore, repository.loads)
        assertEquals(0, repository.updates)
        assertEquals(0, repository.creates)
    }

    @Test fun pendingTimeSaveCannotReplayAfterRestoreInvalidation() = runTest(dispatcher) {
        val original = todoFixtureTask("Before restore", dueTime = LocalTime.of(9, 5))
        val repository = TodoRepositoryFixture(listOf(original)).apply {
            failNextUpdateBeforeCommit = IllegalStateException("write interrupted")
        }
        val saved = SavedStateHandle()
        val editor = TodoEditorViewModel(repository, original.id, null, saved)
        advanceUntilIdle()
        editor.setDueTime(LocalTime.of(10, 15))
        editor.save()
        advanceUntilIdle()
        assertEquals(TodoEditorError.SAVE, editor.state.value.error)
        assertEquals("10:15", saved.get<String>("todo_pending_input.due_time"))

        editor.invalidateAfterRestore()
        repository.replace(original.copy(
            dueTime = LocalTime.of(23, 59),
            revision = UUID.randomUUID(),
            updatedAt = original.updatedAt.plusSeconds(2),
        ))
        val updateCallsBeforeRecreation = repository.updateCalls.size
        val recreated = TodoEditorViewModel(repository, original.id, null, saved)
        advanceUntilIdle()

        assertTrue(recreated.state.value.finished)
        assertEquals(updateCallsBeforeRecreation, repository.updateCalls.size)
        assertEquals(LocalTime.of(23, 59), repository.tasks.single().dueTime)
    }

    private fun task(title: String, id: UUID = UUID.randomUUID()) = TodoTask(
        id = id,
        title = title,
        note = null,
        dueDate = LocalDate.parse("2026-10-20"),
        completedAt = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        revision = UUID.randomUUID(),
    )

    private class FakeTodoRepository(var current: TodoTask) : TodoRepository {
        var loads = 0
        var updates = 0
        var creates = 0

        override fun observeList(completed: Boolean, limit: Int): Flow<TodoPage> = flowOf(
            TodoPage(listOf(current).filter { it.isCompleted == completed }, 1),
        )

        override fun observeToday(date: LocalDate, limit: Int): Flow<TodoPage> = flowOf(TodoPage(emptyList(), 0))

        override suspend fun get(id: UUID): TodoTask? {
            loads++
            return current.takeIf { it.id == id }
        }

        override suspend fun create(id: UUID, initialRevision: UUID, input: TodoInput): TodoTask {
            creates++
            error("Restore invalidation must not replay a create")
        }

        override suspend fun update(id: UUID, expectedRevision: UUID, nextRevision: UUID, input: TodoInput): TodoTask {
            updates++
            error("Restore invalidation must not replay a stale edit")
        }

        override suspend fun setCompleted(id: UUID, expectedRevision: UUID, nextRevision: UUID, completed: Boolean): TodoTask =
            error("No status mutation expected")

        override suspend fun delete(id: UUID, expectedRevision: UUID) = error("No deletion expected")
    }
}
