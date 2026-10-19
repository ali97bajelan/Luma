package com.luma.lifeos.todo

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.luma.lifeos.R
import com.luma.lifeos.ui.theme.LifeOsTheme
import com.luma.lifeos.ui.todo.TodoDraft
import com.luma.lifeos.ui.todo.TodoEditorScreen
import com.luma.lifeos.ui.todo.TodoUiState
import java.time.LocalDate
import androidx.test.platform.app.InstrumentationRegistry
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

    private val appContext get() = InstrumentationRegistry.getInstrumentation().targetContext
}
