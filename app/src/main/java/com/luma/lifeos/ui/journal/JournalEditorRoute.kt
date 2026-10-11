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
import com.luma.lifeos.domain.repository.PhotoRepository
import com.luma.lifeos.ui.photos.PhotoEditor
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.ui.places.PlacePicker
import com.luma.lifeos.domain.repository.PlaceRepository
import com.luma.lifeos.ui.people.PersonPicker
import com.luma.lifeos.domain.repository.PersonRepository
import com.luma.lifeos.domain.repository.JournalRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun JournalEditorRoute(repository: JournalRepository, eventId: EventId?, onCancel: () -> Unit,
    onFinished: (LocalDate?) -> Unit, people: PersonRepository, onCreatePerson: () -> Unit, places: PlaceRepository, onCreatePlace: () -> Unit, photos: PhotoRepository) {
    val factory = remember(repository, eventId, photos) {
        viewModelFactory { initializer { JournalEditorViewModel(repository, eventId, createSavedStateHandle(), photoRepository = photos) } }
    }
    val editor: JournalEditorViewModel = viewModel(factory = factory)
    val state by editor.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.completed) {
        if (state.completed) onFinished(if (state.deleted) null else
            Instant.ofEpochMilli(state.draft.occurredAtMillis).atZone(ZoneId.systemDefault()).toLocalDate())
    }
    JournalEditorScreen(state, onCancel = { editor.discardPhotos(onCancel) }, onSave = editor::save,
        onDelete = editor::delete, onRetry = editor::load, onTitle = editor::setTitle,
        onBody = editor::setBody, onAddTag = editor::addTag, onRemoveTag = editor::removeTag,
        onTime = editor::setLocalTime, peopleContent = { PersonPicker(people, state.draft.personIds, state.ready && !state.busy && !state.completed, editor::togglePerson, onCreatePerson) },
        placeContent = { PlacePicker(places, state.draft.placeId, state.ready && !state.busy && !state.completed, editor::setPlace, onCreatePlace) },
        photosContent = { PhotoEditor(photos, state.draft.photos, state.ready && !state.busy && !state.completed,
            editor::importPhoto, editor::removePhoto, editor::retryPhoto, state.error == JournalEditorError.PHOTO && editor.hasPendingPhotoImport()) })
}
