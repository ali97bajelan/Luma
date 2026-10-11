package com.luma.lifeos.ui.journal

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
import com.luma.lifeos.domain.repository.JournalRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun JournalEditorRoute(repository: JournalRepository, eventId: EventId?, onCancel: () -> Unit,
    onFinished: (LocalDate?) -> Unit) {
    val factory = remember(repository, eventId) {
        viewModelFactory { initializer { JournalEditorViewModel(repository, eventId, createSavedStateHandle()) } }
    }
    val editor: JournalEditorViewModel = viewModel(factory = factory)
    val state by editor.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.completed) {
        if (state.completed) onFinished(if (state.deleted) null else
            Instant.ofEpochMilli(state.draft.occurredAtMillis).atZone(ZoneId.systemDefault()).toLocalDate())
    }
    JournalEditorScreen(state, onCancel = onCancel, onSave = editor::save,
        onDelete = editor::delete, onRetry = editor::load, onTitle = editor::setTitle,
        onBody = editor::setBody, onAddTag = editor::addTag, onRemoveTag = editor::removeTag,
        onTime = editor::setLocalTime)
}
