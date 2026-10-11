package com.luma.lifeos.ui.memory

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.ui.people.PersonPicker
import com.luma.lifeos.domain.repository.PersonRepository
import com.luma.lifeos.domain.repository.MemoryRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun MemoryEditorRoute(repository: MemoryRepository, eventId: EventId?, onCancel: () -> Unit, onFinished: (LocalDate?) -> Unit, people: PersonRepository, onCreatePerson: () -> Unit) {
    val factory = remember(repository, eventId) {
        viewModelFactory { initializer { MemoryEditorViewModel(repository, eventId, createSavedStateHandle()) } }
    }
    val editor: MemoryEditorViewModel = viewModel(factory = factory)
    val state by editor.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.completed) {
        if (state.completed) onFinished(if (state.removed && state.direct) null else
            Instant.ofEpochMilli(state.draft.occurredAtMillis).atZone(ZoneId.systemDefault()).toLocalDate())
    }
    MemoryEditorScreen(state, onCancel, editor::save, editor::remove, editor::load,
        editor::setTitle, editor::setNote, editor::setImportance, editor::setLocalTime, peopleContent = { PersonPicker(people, state.draft.personIds, state.ready && !state.busy && !state.completed, editor::togglePerson, onCreatePerson) })
}
