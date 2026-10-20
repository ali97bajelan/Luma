package com.luma.lifeos.ui.mood

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.ui.places.PlacePicker
import com.luma.lifeos.domain.repository.PlaceRepository
import com.luma.lifeos.ui.people.PersonPicker
import com.luma.lifeos.domain.repository.PersonRepository
import com.luma.lifeos.domain.repository.MoodRepository
import java.time.Instant
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun MoodEditorRoute(repository: MoodRepository, eventId: EventId?, onCancel: () -> Unit,
    onFinished: (LocalDate?) -> Unit, people: PersonRepository, onCreatePerson: () -> Unit,
    places: PlaceRepository, onCreatePlace: () -> Unit, initialTimeMillis: Long? = null,
    draftStore: MoodDraftStore? = null) {
    val factory = remember(repository, eventId, initialTimeMillis, draftStore) {
        viewModelFactory { initializer {
            val savedState = createSavedStateHandle()
            MoodEditorViewModel(repository, eventId, savedState, initialTimeMillis = initialTimeMillis,
                draftStore = draftStore ?: SavedStateMoodDraftStore(savedState))
        } }
    }
    val editor: MoodEditorViewModel = viewModel(factory = factory)
    val state by editor.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var cancelAfterDiscard by remember { mutableStateOf(false) }
    fun finish(deleted: Boolean, occurredAtMillis: Long) {
        onFinished(if (deleted) null else
            Instant.ofEpochMilli(occurredAtMillis).atZone(ZoneId.systemDefault()).toLocalDate())
    }
    LaunchedEffect(state.completionEventId) {
        if (state.completionEventId != null && editor.acknowledgeCompletion()) {
            finish(state.deleted, state.draft.occurredAtMillis)
        }
    }
    val discardAndCancel: () -> Unit = {
        cancelAfterDiscard = true
        scope.launch {
            if (editor.discardDraft()) {
                cancelAfterDiscard = false
                onCancel()
            }
        }
    }
    val retryDraft: () -> Unit = {
        scope.launch {
            val completionId = state.completionEventId
            if (completionId != null) {
                if (editor.acknowledgeCompletion()) finish(state.deleted, state.draft.occurredAtMillis)
            } else if (editor.retryDraftStorage() && cancelAfterDiscard) {
                cancelAfterDiscard = false
                onCancel()
            }
        }
    }
    MoodEditorScreen(state, editing = state.editing, onCancel = discardAndCancel, onSave = editor::save,
        onDelete = editor::delete, onRetry = editor::load, onScore = editor::setScore,
        onEmotion = editor::toggleEmotion, onEnergy = editor::setEnergy, onStress = editor::setStress,
        onNote = editor::setNote, onTime = editor::setLocalTime, onRetryDraft = retryDraft, peopleContent = { PersonPicker(people, state.draft.personIds, state.ready && !state.busy && !state.completed, editor::togglePerson, onCreatePerson) },
        placeContent = { PlacePicker(places, state.draft.placeId, state.ready && !state.busy && !state.completed, editor::setPlace, onCreatePlace) })
}
