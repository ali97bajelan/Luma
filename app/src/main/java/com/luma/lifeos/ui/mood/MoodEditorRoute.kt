package com.luma.lifeos.ui.mood

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.repository.MoodRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun MoodEditorRoute(repository: MoodRepository, eventId: EventId?, onCancel: () -> Unit,
    onFinished: (LocalDate?) -> Unit) {
    val factory = remember(repository, eventId) {
        viewModelFactory { initializer { MoodEditorViewModel(repository, eventId, createSavedStateHandle()) } }
    }
    val editor: MoodEditorViewModel = viewModel(factory = factory)
    val state by editor.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.completed) {
        if (state.completed) onFinished(if (state.deleted) null else
            Instant.ofEpochMilli(state.draft.occurredAtMillis).atZone(ZoneId.systemDefault()).toLocalDate())
    }
    MoodEditorScreen(state, editing = state.editing, onCancel = onCancel, onSave = editor::save,
        onDelete = editor::delete, onRetry = editor::load, onScore = editor::setScore,
        onEmotion = editor::toggleEmotion, onEnergy = editor::setEnergy, onStress = editor::setStress,
        onNote = editor::setNote, onTime = editor::setLocalTime)
}
