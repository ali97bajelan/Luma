package com.luma.lifeos.ui.todo

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TodoFamilyActionsTest {
    @Test
    fun deleteNeedsAnExplicitPreviewConfirmationAndCancelPreservesTheTask() = runTest {
        val task = todoFixtureTask("Family delete")
        val repository = TodoRepositoryFixture(listOf(task))
        var deletedTaskId: java.util.UUID? = null
        val actions = TodoFamilyActions(
            repository = repository,
            saved = SavedStateHandle(),
            scope = this,
            keyPrefix = "todo_family_actions_test",
            onCompletionApplied = {},
            onDeleteApplied = { deletedTaskId = it },
        )

        actions.delete(task.id, task.title, task.revision)
        advanceUntilIdle()

        assertEquals(TodoFamilyActionPhase.AWAITING_CONFIRMATION, actions.state.value.phase)
        assertEquals(0, actions.state.value.childCount)
        assertFalse(repository.tasks.isEmpty())
        assertEquals(null, deletedTaskId)

        actions.cancel()
        advanceUntilIdle()
        assertEquals(TodoFamilyActionPhase.IDLE, actions.state.value.phase)
        assertEquals(task, repository.tasks.single())

        actions.delete(task.id, task.title, task.revision)
        advanceUntilIdle()
        actions.confirm()
        advanceUntilIdle()

        assertEquals(TodoFamilyActionPhase.APPLIED, actions.state.value.phase)
        assertTrue(repository.tasks.isEmpty())
        assertEquals(task.id, deletedTaskId)
    }
}
