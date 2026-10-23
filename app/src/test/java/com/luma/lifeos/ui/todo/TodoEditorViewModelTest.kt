package com.luma.lifeos.ui.todo

import androidx.lifecycle.SavedStateHandle
import com.luma.lifeos.domain.todo.TodoInput
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
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

    @Test fun timeOnlyEditIsDirtyAndCanRevert() = runTest(dispatcher) {
        val original = todoFixtureTask("Timed", dueTime = LocalTime.of(9, 5))
        val editor = TodoEditorViewModel(TodoRepositoryFixture(listOf(original)), original.id, null, SavedStateHandle())
        advanceUntilIdle()

        editor.setDueTime(LocalTime.of(10, 0))
        assertTrue(editor.state.value.dirty)
        editor.setDueTime(original.dueTime)

        assertFalse(editor.state.value.dirty)
        assertEquals(original.dueTime, editor.state.value.draft.dueTime)
    }

    @Test fun removeDateClearsTimeAndChangingDateRetainsTime() = runTest(dispatcher) {
        val original = todoFixtureTask("Timed", dueTime = LocalTime.of(9, 5))
        val editor = TodoEditorViewModel(TodoRepositoryFixture(listOf(original)), original.id, null, SavedStateHandle())
        advanceUntilIdle()
        val nextDate = original.dueDate!!.plusDays(1)

        editor.setDueDate(nextDate)
        assertEquals(LocalTime.of(9, 5), editor.state.value.draft.dueTime)
        editor.setDueDate(null)

        assertNull(editor.state.value.draft.dueDate)
        assertNull(editor.state.value.draft.dueTime)
    }

    @Test fun oldSavedStateHasNoTime() = runTest(dispatcher) {
        val saved = SavedStateHandle().apply {
            set("todo_has_draft", true)
            set("todo_draft_title", "Old draft")
            set("todo_draft_note", "")
            set("todo_draft_due_date", "2026-10-21")
        }
        val editor = TodoEditorViewModel(TodoRepositoryFixture(), null, null, saved)
        advanceUntilIdle()

        assertEquals(LocalDate.parse("2026-10-21"), editor.state.value.draft.dueDate)
        assertNull(editor.state.value.draft.dueTime)
    }

    @Test fun confirmedMinuteSurvivesRecreationAndMidnightIsNotNull() = runTest(dispatcher) {
        val original = todoFixtureTask("Timed", dueTime = null)
        val repository = TodoRepositoryFixture(listOf(original))
        val saved = SavedStateHandle()
        val editor = TodoEditorViewModel(repository, original.id, null, saved)
        advanceUntilIdle()
        editor.setDueTime(LocalTime.MIDNIGHT)

        assertEquals("00:00", saved.get<String>("todo_draft_due_time"))
        val recreated = TodoEditorViewModel(repository, original.id, null, saved)
        advanceUntilIdle()

        assertEquals(LocalTime.MIDNIGHT, recreated.state.value.draft.dueTime)
        assertTrue(recreated.state.value.dirty)
    }

    @Test fun pendingSaveReplaysExactMinute() = runTest(dispatcher) {
        val original = todoFixtureTask("Timed", dueTime = LocalTime.of(9, 5))
        val repository = TodoRepositoryFixture(listOf(original)).apply {
            failNextUpdateBeforeCommit = IllegalStateException("offline")
        }
        val saved = SavedStateHandle()
        val editor = TodoEditorViewModel(repository, original.id, null, saved)
        advanceUntilIdle()
        val selected = LocalTime.of(9, 6)
        editor.setDueTime(selected)
        editor.save()
        advanceUntilIdle()
        assertEquals(TodoEditorError.SAVE, editor.state.value.error)

        val recreated = TodoEditorViewModel(repository, original.id, null, saved)
        advanceUntilIdle()
        assertEquals(selected, recreated.state.value.draft.dueTime)
        recreated.retry()
        advanceUntilIdle()

        assertTrue(recreated.state.value.finished)
        assertEquals(selected, repository.tasks.single().dueTime)
    }

    @Test fun reloadUsesLatestTime() = runTest(dispatcher) {
        val original = todoFixtureTask("Timed", dueTime = LocalTime.of(9, 5))
        val repository = TodoRepositoryFixture(listOf(original))
        val editor = TodoEditorViewModel(repository, original.id, null, SavedStateHandle())
        advanceUntilIdle()
        editor.setDueTime(LocalTime.of(10, 0))
        repository.replace(original.copy(
            dueTime = LocalTime.of(11, 30),
            revision = UUID.randomUUID(),
            updatedAt = original.updatedAt.plusSeconds(2),
        ))
        editor.save()
        advanceUntilIdle()
        assertTrue(editor.state.value.conflict)

        editor.reload()
        advanceUntilIdle()

        assertEquals(LocalTime.of(11, 30), editor.state.value.draft.dueTime)
        assertFalse(editor.state.value.dirty)
    }

    @Test fun blockedEditorCannotChangeDueTime() = runTest(dispatcher) {
        val original = todoFixtureTask("Timed", dueTime = LocalTime.of(9, 5))
        val repository = TodoRepositoryFixture(listOf(original))
        val editor = TodoEditorViewModel(repository, original.id, null, SavedStateHandle())
        advanceUntilIdle()
        editor.setTitle("Unsaved local edit")
        repository.replace(original.copy(
            revision = UUID.randomUUID(),
            updatedAt = original.updatedAt.plusSeconds(2),
        ))
        editor.save()
        advanceUntilIdle()
        assertTrue(editor.state.value.conflict)

        editor.setDueTime(LocalTime.of(10, 0))

        assertEquals(LocalTime.of(9, 5), editor.state.value.draft.dueTime)
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

    @Test fun confirmedConflictReloadCanRetryFailedReadWithoutLosingDraft() = runTest(dispatcher) {
        val original = todoFixtureTask("Original")
        val repository = TodoRepositoryFixture(listOf(original))
        val editor = TodoEditorViewModel(repository, original.id, null, SavedStateHandle())
        advanceUntilIdle()
        editor.setTitle("My unsaved edit")
        val draftDueDate = LocalDate.parse("2026-10-24")
        editor.setDueDate(draftDueDate)
        val latest = original.copy(
            title = "Changed elsewhere",
            revision = UUID.randomUUID(),
            updatedAt = original.updatedAt.plusSeconds(2),
        )
        repository.replace(latest)
        editor.save()
        advanceUntilIdle()
        assertTrue(editor.state.value.conflict)

        repository.getFailure = IllegalStateException("transient read failure")
        val readsBeforeReload = repository.getCalls.size
        editor.reload()
        advanceUntilIdle()

        assertEquals(readsBeforeReload + 1, repository.getCalls.size)
        assertEquals(TodoEditorError.LOAD, editor.state.value.error)
        assertTrue(editor.state.value.conflict)
        assertTrue(editor.state.value.canRetryConfirmedReload)
        assertEquals("My unsaved edit", editor.state.value.draft.title)
        assertEquals(draftDueDate, editor.state.value.draft.dueDate)

        repository.getFailure = IllegalStateException("second transient read failure")
        editor.retry()
        advanceUntilIdle()

        assertEquals(readsBeforeReload + 2, repository.getCalls.size)
        assertEquals(TodoEditorError.LOAD, editor.state.value.error)
        assertTrue(editor.state.value.conflict)
        assertTrue(editor.state.value.canRetryConfirmedReload)
        assertEquals("My unsaved edit", editor.state.value.draft.title)
        assertEquals(draftDueDate, editor.state.value.draft.dueDate)

        editor.save()
        editor.setCompleted(true)
        editor.requestDelete()
        assertFalse(editor.state.value.deleteConfirmationRequested)
        assertTrue(repository.updateCalls.isEmpty())
        assertTrue(repository.completionCalls.isEmpty())
        assertTrue(repository.deleteCalls.isEmpty())

        editor.retry()
        advanceUntilIdle()

        assertEquals(readsBeforeReload + 3, repository.getCalls.size)
        assertNull(editor.state.value.error)
        assertFalse(editor.state.value.conflict)
        assertFalse(editor.state.value.canRetryConfirmedReload)
        assertFalse(editor.state.value.missing)
        assertEquals("Changed elsewhere", editor.state.value.draft.title)
        assertEquals(latest.dueDate, editor.state.value.draft.dueDate)
        assertFalse(editor.state.value.dirty)
        assertTrue(repository.updateCalls.isEmpty())
    }

    @Test fun confirmedReloadRetryReportsMissingTaskWithoutReplacingDraft() = runTest(dispatcher) {
        val original = todoFixtureTask("Original")
        val repository = TodoRepositoryFixture(listOf(original))
        val editor = TodoEditorViewModel(repository, original.id, null, SavedStateHandle())
        advanceUntilIdle()
        editor.setTitle("My unsaved edit")
        repository.replace(original.copy(title = "Changed elsewhere", revision = UUID.randomUUID()))
        editor.save()
        advanceUntilIdle()
        assertTrue(editor.state.value.conflict)

        repository.getFailure = IllegalStateException("transient read failure")
        editor.reload()
        advanceUntilIdle()
        assertEquals(TodoEditorError.LOAD, editor.state.value.error)
        assertEquals("My unsaved edit", editor.state.value.draft.title)

        repository.remove(original.id)
        editor.retry()
        advanceUntilIdle()

        assertTrue(editor.state.value.missing)
        assertFalse(editor.state.value.conflict)
        assertNull(editor.state.value.error)
        assertFalse(editor.state.value.canRetryConfirmedReload)
        assertEquals("My unsaved edit", editor.state.value.draft.title)
        assertTrue(repository.updateCalls.isEmpty())
        assertTrue(repository.completionCalls.isEmpty())
        assertTrue(repository.deleteCalls.isEmpty())
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
