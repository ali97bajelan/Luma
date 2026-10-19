package com.luma.lifeos.ui.habits

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.material3.SnackbarHostState
import com.luma.lifeos.domain.habit.HabitRepository
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@Composable
fun HabitsRoute(
    repository: HabitRepository,
    date: LocalDate?,
    onSelectDate: (LocalDate) -> Unit,
    onToday: () -> Unit,
    onCreate: () -> Unit,
    onHabit: (UUID, LocalDate) -> Unit,
    deviceDate: LocalDate,
    deviceZone: ZoneId,
    onBack: (() -> Unit)? = null,
    snackbarHostState: SnackbarHostState? = null,
) {
    val factory = androidx.compose.runtime.remember(repository) {
        viewModelFactory { initializer { HabitsViewModel(repository, createSavedStateHandle()) } }
    }
    val viewModel: HabitsViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(date, deviceDate, deviceZone) { viewModel.selectDate(date, deviceZone, deviceDate) }
    val renderedState = state
    val shownDate = date ?: renderedState.date ?: deviceDate
    HabitLogSnackbarFeedback(
        state = renderedState,
        snackbarHostState = snackbarHostState,
        onUndo = viewModel::undoLog,
        onAcknowledge = viewModel::acknowledgeUndoLog,
        onRetry = viewModel::undoLog,
    )
    HabitsScreen(renderedState, shownDate, deviceZone, onBack, onSelectDate, onToday, onCreate,
        onHabit = { id -> renderedState.rowsFor(shownDate).firstOrNull { it.day.habit.id == id }?.let { row ->
            viewModel.dateForRow(row)?.let { onHabit(id, it) }
        } },
        onLog = { id -> renderedState.rowsFor(shownDate).firstOrNull { it.day.habit.id == id }?.let(viewModel::log) },
        onRetryLog = viewModel::retryLog, onUndo = viewModel::undoLog,
        onRetry = viewModel::retry)
}
