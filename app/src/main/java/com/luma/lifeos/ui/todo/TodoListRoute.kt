package com.luma.lifeos.ui.todo

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.luma.lifeos.domain.todo.TodoRepository
import java.time.LocalDate
import java.util.UUID

@Composable
fun TodoListRoute(
    repository: TodoRepository,
    today: LocalDate,
    restoreGeneration: Int,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onAddTask: () -> Unit,
    onOpenTask: (UUID) -> Unit,
) {
    val factory = remember(repository) {
        viewModelFactory { initializer { TodoListViewModel(repository, createSavedStateHandle()) } }
    }
    val vm: TodoListViewModel = viewModel(factory = factory)
    val state by vm.state.collectAsStateWithLifecycle()
    val handledRestore = rememberSaveable { mutableStateOf(restoreGeneration) }
    LaunchedEffect(today) { vm.refreshDay(today) }
    LaunchedEffect(restoreGeneration) {
        if (handledRestore.value != restoreGeneration) {
            handledRestore.value = restoreGeneration
            vm.invalidateAfterRestore()
        }
    }
    TodoListScreen(
        state = state,
        onBack = onBack,
        onAddTask = onAddTask,
        onOpenTask = onOpenTask,
        onSelectCompleted = vm::selectCompleted,
        onSetCompleted = vm::setCompleted,
        onRetry = vm::retry,
        onDismissRowError = vm::dismissRowError,
        onRetryLoad = vm::retryLoad,
        onLoadMore = vm::loadMore,
        undoFeedback = {
            state.undoableCompletion?.let { result ->
                TodoActionFeedbackCard(
                    result = result,
                    busy = state.undoing,
                    failed = state.undoFailed,
                    terminal = state.undoTerminal,
                    onUndo = vm::undoCompletion,
                    onDismiss = vm::dismissUndo,
                )
            }
        },
    )
}
