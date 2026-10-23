package com.luma.lifeos.ui.todo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.luma.lifeos.domain.todo.TodoRepository
import java.time.LocalDate
import java.util.UUID

@Composable
fun TodoEditorRoute(
    repository: TodoRepository,
    taskId: UUID?,
    initialDueDate: LocalDate?,
    onClose: (TodoEditorResult?) -> Unit,
    restoreGeneration: Int = 0,
) {
    val factory = remember(repository, taskId, initialDueDate) {
        viewModelFactory {
            initializer {
                TodoEditorViewModel(repository, taskId, initialDueDate, createSavedStateHandle())
            }
        }
    }
    val editor: TodoEditorViewModel = viewModel(factory = factory)
    val state by editor.state.collectAsStateWithLifecycle()
    val latestOnClose by rememberUpdatedState(onClose)
    val handledRestore = rememberSaveable { mutableStateOf(restoreGeneration) }

    LaunchedEffect(restoreGeneration) {
        if (handledRestore.value != restoreGeneration) {
            handledRestore.value = restoreGeneration
            editor.invalidateAfterRestore()
        }
    }

    LaunchedEffect(state.finished) {
        if (state.finished) latestOnClose(state.result)
    }

    TodoEditorScreen(
        state = state,
        onClose = { latestOnClose(null) },
        onSave = editor::save,
        onRetry = editor::retry,
        onReload = editor::reload,
        onTitle = editor::setTitle,
        onNote = editor::setNote,
        onDueDate = editor::setDueDate,
        onDueTime = editor::setDueTime,
        onSetCompleted = editor::setCompleted,
        onRequestDelete = editor::requestDelete,
        onConfirmDelete = editor::confirmDelete,
        onCancelDelete = editor::cancelDelete,
    )
}
