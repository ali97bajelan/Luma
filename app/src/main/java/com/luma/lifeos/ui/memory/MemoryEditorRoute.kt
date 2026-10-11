package com.luma.lifeos.ui.memory

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.luma.lifeos.domain.repository.PhotoRepository
import com.luma.lifeos.ui.photos.PhotoEditor
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.ui.places.PlacePicker
import com.luma.lifeos.domain.repository.PlaceRepository
import com.luma.lifeos.ui.people.PersonPicker
import com.luma.lifeos.domain.repository.PersonRepository
import com.luma.lifeos.domain.repository.MemoryRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun MemoryEditorRoute(repository: MemoryRepository, eventId: EventId?, onCancel: () -> Unit, onFinished: (LocalDate?) -> Unit, people: PersonRepository, onCreatePerson: () -> Unit, places: PlaceRepository, onCreatePlace: () -> Unit, photos: PhotoRepository) {
    val factory = remember(repository, eventId, photos) {
        viewModelFactory { initializer { MemoryEditorViewModel(repository, eventId, createSavedStateHandle(), photoRepository = photos) } }
    }
    val editor: MemoryEditorViewModel = viewModel(factory = factory)
    val state by editor.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.completed) {
        if (state.completed) onFinished(if (state.removed && state.direct) null else
            Instant.ofEpochMilli(state.draft.occurredAtMillis).atZone(ZoneId.systemDefault()).toLocalDate())
    }
    MemoryEditorScreen(state, { editor.discardPhotos(onCancel) }, editor::save, editor::remove, editor::load,
        editor::setTitle, editor::setNote, editor::setImportance, editor::setLocalTime, peopleContent = { PersonPicker(people, state.draft.personIds, state.ready && !state.busy && !state.completed, editor::togglePerson, onCreatePerson) },
        placeContent = { PlacePicker(places, state.draft.placeId, state.ready && !state.busy && !state.completed, editor::setPlace, onCreatePlace) },
        photosContent = { PhotoEditor(photos, state.draft.photos, state.ready && !state.busy && !state.completed,
            editor::importPhoto, editor::removePhoto, editor::retryPhoto, state.error == MemoryEditorError.PHOTO && editor.hasPendingPhotoImport()) })
}
