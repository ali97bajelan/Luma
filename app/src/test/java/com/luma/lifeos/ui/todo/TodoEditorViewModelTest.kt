package com.luma.lifeos.ui.todo

import androidx.lifecycle.SavedStateHandle
import com.luma.lifeos.domain.todo.TodoInput
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
class TodoEditorViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun lostCreateResponseIsRecoveredAfterRecreationWithoutDuplicateTask() = runTest(dispatcher) {
        val repository = TodoRepositoryFixture().apply {
            failNextCreateAfterCommit = IllegalStateException("response lost")
        }
        val saved = SavedStateHandle()
        val editor = TodoEditorViewModel(repository, null, null, saved)
        advanceUntilIdle()
        editor.setTitle("Persian note \u06a9\u0627\u0631")
        editor.setNote("Optional note")
        editor.setDueDate(LocalDate.parse("2026-10-21"))
        editor.save()
        advanceUntilIdle()

        assertEquals(TodoEditorError.SAVE, editor.state.value.error)
        assertEquals("Persian note \u06a9\u0627\u0631", editor.state.value.draft.title)
        val firstCall = repository.createCalls.single()

        val recreated = TodoEditorViewModel(repository, null, null, saved)
        advanceUntilIdle()

        assertTrue(recreated.state.value.finished)
        assertEquals(firstCall.id, repository.tasks.single().id)
        assertEquals(firstCall.initialRevision, repository.tasks.single().revision)
        assertEquals(1, repository.createCalls.size)
        assertEquals(TodoInput("Persian note \u06a9\u0627\u0631", "Optional note", LocalDate.parse("2026-10-21")), repository.tasks.single().let {
            TodoInput(it.title, it.note, it.dueDate)
        })
    }

    @Test fun repeatedSaveWhileCreateIsBusySubmitsOnlyOnce() = runTest(dispatcher) {
        val repository = TodoRepositoryFixture().apply {
            createStarted = CompletableDeferred()
            releaseCreate = CompletableDeferred()
        }
        val editor = TodoEditorViewModel(repository, null, null, SavedStateHandle())
        advanceUntilIdle()
        editor.setTitle("One task")

        editor.save()
        editor.save()
        runCurrent()

        assertTrue(editor.state.value.busy)
        assertEquals(1, repository.createCalls.size)
        repository.releaseCreate?.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, repository.tasks.size)
        assertTrue(editor.state.value.finished)
    }

    @Test fun pendingCreateDoesNotReplayUntilTheUserRetries() = runTest(dispatcher) {
        val repository = TodoRepositoryFixture().apply {
            failNextCreateBeforeCommit = IllegalStateException("offline")
        }
        val saved = SavedStateHandle()
        val editor = TodoEditorViewModel(repository, null, LocalDate.parse("2026-10-19"), saved)
        advanceUntilIdle()
        editor.setTitle("Retry me")
        editor.save()
        advanceUntilIdle()
        assertEquals(TodoEditorError.SAVE, editor.state.value.error)
        assertEquals(1, repository.createCalls.size)

        val recreated = TodoEditorViewModel(repository, null, null, saved)
        advanceUntilIdle()
        assertEquals(1, repository.createCalls.size)
        assertEquals(TodoEditorError.SAVE, recreated.state.value.error)
        assertEquals(LocalDate.parse("2026-10-19"), recreated.state.value.draft.dueDate)

        recreated.retry()
        advanceUntilIdle()
        assertTrue(recreated.state.value.finished)
        assertEquals(2, repository.createCalls.size)
        assertEquals(1, repository.tasks.size)
    }

    @Test fun conflictingUpdateRetainsDraftUntilExplicitReload() = runTest(dispatcher) {
        val original = todoFixtureTask("Original")
        val repository = TodoRepositoryFixture(listOf(original))
        val editor = TodoEditorViewModel(repository, original.id, null, SavedStateHandle())
        advanceUntilIdle()
        editor.setTitle("My unsaved edit")
        val concurrent = original.copy(title = "Changed elsewhere", revision = UUID.randomUUID(), updatedAt = original.updatedAt.plusSeconds(2))
        repository.replace(concurrent)

        editor.save()
        advanceUntilIdle()

        assertTrue(editor.state.value.conflict)
        assertEquals("My unsaved edit", editor.state.value.draft.title)
        assertEquals(0, repository.updateCalls.size)

        editor.reload()
        advanceUntilIdle()
        assertFalse(editor.state.value.conflict)
        assertFalse(editor.state.value.dirty)
        assertEquals("Changed elsewhere", editor.state.value.draft.title)
    }

    @Test fun missingEditTargetIsReportedWithoutCreatingAReplacement() = runTest(dispatcher) {
        val missingId = UUID.randomUUID()
        val repository = TodoRepositoryFixture()
        val editor = TodoEditorViewModel(repository, missingId, null, SavedStateHandle())
        advanceUntilIdle()

        assertTrue(editor.state.value.ready)
        assertTrue(editor.state.value.missing)
        assertEquals(0, repository.createCalls.size)
        assertEquals(0, repository.updateCalls.size)
    }

    @Test fun dirtyFieldsBlockStatusChangeAndCompletedEditsKeepCompletion() = runTest(dispatcher) {
        val open = todoFixtureTask("Open task")
        val repository = TodoRepositoryFixture(listOf(open))
        val editor = TodoEditorViewModel(repository, open.id, null, SavedStateHandle())
        advanceUntilIdle()
        editor.setTitle("Edited before status")
        editor.setCompleted(true)
        assertTrue(editor.state.value.dirty)
        assertTrue(repository.completionCalls.isEmpty())

        val completedAt = Instant.parse("2026-03-01T00:00:00Z")
        val completed = todoFixtureTask("Done task", completedAt = completedAt)
        repository.replace(completed)
        val completedEditor = TodoEditorViewModel(repository, completed.id, null, SavedStateHandle())
        advanceUntilIdle()
        completedEditor.setTitle("Renamed done task")
        completedEditor.save()
        advanceUntilIdle()

        assertTrue(completedEditor.state.value.finished)
        assertEquals(completedAt, repository.tasks.single { it.id == completed.id }.completedAt)
        assertTrue(repository.tasks.single { it.id == completed.id }.isCompleted)
    }

    @Test fun lostCompletionResponseIsRestoredAsCommittedEditorResult() = runTest(dispatcher) {
        val original = todoFixtureTask("Complete me")
        val repository = TodoRepositoryFixture(listOf(original)).apply {
            failNextCompletionAfterCommit = IllegalStateException("response lost")
        }
        val saved = SavedStateHandle()
        val editor = TodoEditorViewModel(repository, original.id, null, saved)
        advanceUntilIdle()
        editor.setCompleted(true)
        advanceUntilIdle()
        assertEquals(TodoEditorError.STATUS, editor.state.value.error)
        assertEquals(1, repository.completionCalls.size)

        val recreated = TodoEditorViewModel(repository, original.id, null, saved)
        advanceUntilIdle()

        assertTrue(recreated.state.value.finished)
        assertNotNull(recreated.state.value.result)
        assertEquals(original.id, recreated.state.value.result?.taskId)
        assertTrue(recreated.state.value.result?.completed == true)
        assertEquals(repository.tasks.single().revision, recreated.state.value.result?.resultingRevision)
        assertEquals(1, repository.completionCalls.size)
        assertNull(recreated.state.value.error)
    }
}
