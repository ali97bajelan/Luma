package com.luma.lifeos.todo

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.luma.lifeos.R
import com.luma.lifeos.domain.todo.TodoConflictException
import com.luma.lifeos.domain.todo.TodoInput
import com.luma.lifeos.domain.todo.TodoPage
import com.luma.lifeos.domain.todo.TodoRepository
import com.luma.lifeos.domain.todo.TodoTask
import com.luma.lifeos.ui.theme.LifeOsTheme
import com.luma.lifeos.ui.todo.TodoDraft
import com.luma.lifeos.ui.todo.TodoEditorRoute
import com.luma.lifeos.ui.todo.TodoEditorScreen
import com.luma.lifeos.ui.todo.TodoUiState
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class TodoEditorFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun titleOnlyTaskCanSaveWithoutOptionalFields() {
        val state = mutableStateOf(TodoUiState(loading = false, ready = true))
        var saves = 0
        compose.setContent {
            LifeOsTheme {
                TodoEditorScreen(
                    state = state.value,
                    onClose = {},
                    onSave = { saves++ },
                    onRetry = {},
                    onReload = {},
                    onTitle = { state.value = state.value.copy(draft = state.value.draft.copy(title = it), dirty = true) },
                    onNote = { state.value = state.value.copy(draft = state.value.draft.copy(note = it), dirty = true) },
                    onDueDate = { state.value = state.value.copy(draft = state.value.draft.copy(dueDate = it), dirty = true) },
                    onSetCompleted = {},
                    onRequestDelete = {},
                    onConfirmDelete = {},
                    onCancelDelete = {},
                )
            }
        }

        compose.onNodeWithTag("todo_editor_title").performTextInput("Title only")
        compose.onNodeWithTag("todo_editor_save").assertIsEnabled()
        compose.onNodeWithTag("todo_editor_save").performClick()

        compose.runOnIdle {
            assertEquals(1, saves)
            assertEquals("Title only", state.value.draft.title)
            assertEquals("", state.value.draft.note)
            assertEquals(null, state.value.draft.dueDate)
        }
        compose.onNodeWithTag("todo_editor_due_date_value").assertTextContains(
            appContext.getString(R.string.todo_no_date),
        )
    }

    @Test fun pickerCancelDoesNothingAndConfirmingItsCurrentDateKeepsDraftClean() {
        val date = LocalDate.parse("2026-10-20")
        val state = mutableStateOf(TodoUiState(
            loading = false,
            ready = true,
            draft = TodoDraft(dueDate = date),
            dirty = false,
        ))
        val dueDateChanges = mutableListOf<LocalDate?>()
        compose.setContent {
            LifeOsTheme {
                TodoEditorScreen(
                    state = state.value,
                    onClose = {}, onSave = {}, onRetry = {}, onReload = {}, onTitle = {}, onNote = {},
                    onDueDate = { changed ->
                        dueDateChanges += changed
                        if (changed != state.value.draft.dueDate) {
                            state.value = state.value.copy(draft = state.value.draft.copy(dueDate = changed), dirty = true)
                        }
                    },
                    onSetCompleted = {}, onRequestDelete = {}, onConfirmDelete = {}, onCancelDelete = {},
                )
            }
        }

        compose.onNodeWithTag("todo_editor_change_due_date").performClick()
        compose.onNodeWithText(appContext.getString(R.string.cancel)).performClick()
        assertEquals(emptyList<LocalDate?>(), dueDateChanges)
        assertFalse(state.value.dirty)

        compose.onNodeWithTag("todo_editor_change_due_date").performClick()
        compose.onNodeWithTag("todo_editor_select_date").performClick()
        compose.runOnIdle {
            assertEquals(listOf(date), dueDateChanges)
            assertEquals(date, state.value.draft.dueDate)
            assertFalse(state.value.dirty)
        }
    }

    @Test fun optionalDueDateCanBeCleared() {
        val date = LocalDate.parse("2026-10-20")
        val state = mutableStateOf(TodoUiState(
            loading = false,
            ready = true,
            draft = TodoDraft(title = "Optional date", note = "Optional note", dueDate = date),
        ))
        val dateChanges = mutableListOf<LocalDate?>()
        compose.setContent {
            LifeOsTheme {
                TodoEditorScreen(
                    state = state.value,
                    onClose = {}, onSave = {}, onRetry = {}, onReload = {}, onTitle = {}, onNote = {},
                    onDueDate = { changed ->
                        dateChanges += changed
                        state.value = state.value.copy(draft = state.value.draft.copy(dueDate = changed))
                    },
                    onSetCompleted = {}, onRequestDelete = {}, onConfirmDelete = {}, onCancelDelete = {},
                )
            }
        }

        compose.onNodeWithTag("todo_editor_clear_due_date").performClick()
        compose.runOnIdle {
            assertEquals(listOf(null), dateChanges)
            assertEquals(null, state.value.draft.dueDate)
            assertEquals("Optional note", state.value.draft.note)
        }
    }

    @Test fun dirtyStatusActionIsDisabledWithSaveFirstExplanation() {
        val state = TodoUiState(
            loading = false,
            ready = true,
            editing = true,
            dirty = true,
            draft = TodoDraft(title = "Unsaved title"),
        )
        var statusActions = 0
        compose.setContent {
            LifeOsTheme {
                TodoEditorScreen(
                    state = state,
                    onClose = {}, onSave = {}, onRetry = {}, onReload = {}, onTitle = {}, onNote = {}, onDueDate = {},
                    onSetCompleted = { statusActions++ }, onRequestDelete = {}, onConfirmDelete = {}, onCancelDelete = {},
                )
            }
        }

        compose.onNodeWithTag("todo_editor_status").assertIsNotEnabled()
        compose.onNodeWithTag("todo_editor_status_help").assertExists()
        compose.runOnIdle { assertEquals(0, statusActions) }
    }

    @Test fun deleteCancelRetainsEditorAndConfirmedDeleteCallsOnce() {
        val state = mutableStateOf(TodoUiState(
            loading = false,
            ready = true,
            editing = true,
            draft = TodoDraft(title = "Keep until confirmed"),
            baselineTitle = "Keep until confirmed",
            deleteConfirmationRequested = true,
        ))
        var cancelled = 0
        var confirmed = 0
        compose.setContent {
            LifeOsTheme {
                TodoEditorScreen(
                    state = state.value,
                    onClose = {}, onSave = {}, onRetry = {}, onReload = {}, onTitle = {}, onNote = {}, onDueDate = {},
                    onSetCompleted = {}, onRequestDelete = {}, onConfirmDelete = { confirmed++ },
                    onCancelDelete = { cancelled++; state.value = state.value.copy(deleteConfirmationRequested = false) },
                )
            }
        }

        compose.onNodeWithTag("todo_editor_delete_dialog").assertExists()
        compose.onNodeWithText(appContext.getString(R.string.cancel)).performClick()
        compose.runOnIdle {
            assertEquals(1, cancelled)
            assertEquals(0, confirmed)
            assertEquals("Keep until confirmed", state.value.draft.title)
        }

        state.value = state.value.copy(deleteConfirmationRequested = true)
        compose.onNodeWithTag("todo_editor_confirm_delete").performClick()
        compose.runOnIdle { assertEquals(1, confirmed) }
    }

    @Test fun dirtyCloseCanKeepEditingOrConfirmDiscard() {
        val state = mutableStateOf(TodoUiState(
            loading = false,
            ready = true,
            dirty = true,
            draft = TodoDraft(title = "Retained draft"),
        ))
        var closed = 0
        compose.setContent {
            LifeOsTheme {
                TodoEditorScreen(
                    state = state.value,
                    onClose = { closed++ }, onSave = {}, onRetry = {}, onReload = {}, onTitle = {}, onNote = {}, onDueDate = {},
                    onSetCompleted = {}, onRequestDelete = {}, onConfirmDelete = {}, onCancelDelete = {},
                )
            }
        }

        compose.onNodeWithTag("todo_editor_close").performClick()
        compose.onNodeWithTag("todo_editor_discard_dialog").assertExists()
        compose.onNodeWithText(appContext.getString(R.string.keep_editing)).performClick()
        compose.runOnIdle {
            assertEquals(0, closed)
            assertEquals("Retained draft", state.value.draft.title)
        }

        compose.onNodeWithTag("todo_editor_close").performClick()
        compose.onNodeWithTag("todo_editor_confirm_discard").performClick()
        compose.runOnIdle { assertEquals(1, closed) }
    }

    @Test fun routeRetriesFailedConfirmedReloadThroughRetryCallback() {
        val instant = Instant.parse("2026-10-19T12:00:00Z")
        val task = TodoTask(
            id = UUID.randomUUID(),
            title = "Original",
            note = null,
            dueDate = LocalDate.parse("2026-10-20"),
            completedAt = null,
            createdAt = instant,
            updatedAt = instant,
            revision = UUID.randomUUID(),
        )
        val repository = ConflictReloadRepository(task)
        var closed = false
        compose.setContent {
            LifeOsTheme {
                TodoEditorRoute(repository, task.id, null, onClose = { closed = true })
            }
        }

        compose.waitUntil(5_000) { repository.reads == 1 }
        compose.onNodeWithTag("todo_editor_title").assertIsEnabled()
        compose.onNodeWithTag("todo_editor_title").performTextReplacement("My unsaved edit")
        repository.changeTitle("Changed elsewhere")
        compose.onNodeWithTag("todo_editor_save").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("todo_editor_conflict").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("todo_editor_title").assertTextContains("My unsaved edit")

        val readsBeforeCancelledReload = repository.reads
        compose.onNodeWithTag("todo_editor_reload").performClick()
        compose.onNodeWithTag("todo_editor_reload_dialog").assertExists()
        compose.onNodeWithText(appContext.getString(R.string.keep_editing)).performClick()
        compose.runOnIdle {
            assertEquals(readsBeforeCancelledReload, repository.reads)
        }
        compose.onNodeWithTag("todo_editor_title").assertTextContains("My unsaved edit")

        repository.failingReads = 2
        compose.onNodeWithTag("todo_editor_reload").performClick()
        compose.onNodeWithTag("todo_editor_confirm_reload").performClick()
        compose.waitUntil(5_000) {
            repository.reads == 3 && compose.onAllNodesWithTag("todo_editor_action_error").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("todo_editor_action_error")
            .assertTextContains(appContext.getString(R.string.todo_load_error))
        compose.onNodeWithTag("todo_editor_title").assertTextContains("My unsaved edit")

        compose.onNodeWithTag("todo_editor_reload").performClick()
        compose.onNodeWithTag("todo_editor_reload_dialog").assertDoesNotExist()
        compose.waitUntil(5_000) {
            repository.reads == 4 && compose.onAllNodesWithTag("todo_editor_action_error").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("todo_editor_title").assertTextContains("My unsaved edit")

        compose.onNodeWithTag("todo_editor_retry").performClick()
        compose.waitUntil(5_000) {
            repository.reads == 5 && runCatching {
                compose.onNodeWithTag("todo_editor_title").assertTextContains("Changed elsewhere")
            }.isSuccess
        }
        compose.onNodeWithTag("todo_editor_conflict").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(0, repository.updateCalls)
            assertEquals(5, repository.reads)
            assertFalse(closed)
        }
    }

    private val appContext get() = InstrumentationRegistry.getInstrumentation().targetContext

    private class ConflictReloadRepository(initialTask: TodoTask) : TodoRepository {
        var task: TodoTask? = initialTask
        var failingReads = 0
        var reads = 0
        var updateCalls = 0

        fun changeTitle(title: String) {
            val current = requireNotNull(task)
            task = current.copy(
                title = title,
                updatedAt = current.updatedAt.plusSeconds(1),
                revision = UUID.randomUUID(),
            )
        }

        override fun observeList(completed: Boolean, limit: Int): Flow<TodoPage> = flowOf(TodoPage(emptyList(), 0))

        override fun observeToday(date: LocalDate, limit: Int): Flow<TodoPage> = flowOf(TodoPage(emptyList(), 0))

        override suspend fun get(id: UUID): TodoTask? {
            reads++
            if (failingReads > 0) {
                failingReads--
                throw IllegalStateException("temporary read failure")
            }
            return task?.takeIf { it.id == id }
        }

        override suspend fun create(id: UUID, initialRevision: UUID, input: TodoInput): TodoTask = error("Unused in this flow")

        override suspend fun update(id: UUID, expectedRevision: UUID, nextRevision: UUID, input: TodoInput): TodoTask {
            updateCalls++
            throw TodoConflictException(id)
        }

        override suspend fun setCompleted(
            id: UUID,
            expectedRevision: UUID,
            nextRevision: UUID,
            completed: Boolean,
        ): TodoTask = error("Unused in this flow")

        override suspend fun delete(id: UUID, expectedRevision: UUID) = error("Unused in this flow")
    }
}
