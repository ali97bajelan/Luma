package com.luma.lifeos.ui.todo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.luma.lifeos.domain.focus.FocusProductRepository
import com.luma.lifeos.domain.todo.TodoRepository
import com.luma.lifeos.reminders.enqueueReminderReconciliation
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@Composable
fun TodoEditorRoute(
    repository: TodoRepository,
    taskId: UUID?,
    initialDueDate: LocalDate?,
    onClose: (TodoEditorResult?) -> Unit,
    focusProductRepository: FocusProductRepository? = null,
    onFocusTask: ((UUID, TodoEditorResult?) -> Unit)? = null,
    restoreGeneration: Int = 0,
    requestDelete: Boolean = false,
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
    val focusTotalFlow = remember(taskId, focusProductRepository) {
        if (taskId != null && focusProductRepository != null) {
            focusProductRepository.observeTaskFocusMillis(taskId).map { value -> value as Long? }
        } else {
            flowOf<Long?>(null)
        }
    }
    val linkedFocusMillis by focusTotalFlow.collectAsStateWithLifecycle(initialValue = null)
    val latestOnClose by rememberUpdatedState(onClose)
    val latestOnFocusTask by rememberUpdatedState(onFocusTask)
    var continueToFocusAfterSave by rememberSaveable { mutableStateOf(false) }
    val handledRestore = rememberSaveable { mutableStateOf(restoreGeneration) }
    val context = LocalContext.current

    LaunchedEffect(restoreGeneration) {
        if (handledRestore.value != restoreGeneration) {
            handledRestore.value = restoreGeneration
            editor.invalidateAfterRestore()
        }
    }

    LaunchedEffect(state.ready, requestDelete) {
        if (requestDelete && state.ready) editor.requestDelete()
    }

    LaunchedEffect(state.finished) {
        if (state.finished) {
            enqueueReminderReconciliation(context)
            val focusAfterSave = continueToFocusAfterSave && taskId != null
            continueToFocusAfterSave = false
            val focusAction = latestOnFocusTask
            if (focusAfterSave && focusAction != null) focusAction(taskId, state.result)
            else latestOnClose(state.result)
        }
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
        linkedFocusMillis = linkedFocusMillis,
        onFocusTask = taskId?.takeIf { onFocusTask != null }?.let { selectedId ->
            { latestOnFocusTask?.invoke(selectedId, null) }
        },
        onSaveAndFocusTask = taskId?.takeIf { onFocusTask != null }?.let {
            {
                continueToFocusAfterSave = true
                editor.save()
            }
        },
    )
}
