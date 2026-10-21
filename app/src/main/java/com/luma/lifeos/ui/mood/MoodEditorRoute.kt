package com.luma.lifeos.ui.mood

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.SavedStateHandle
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
import com.luma.lifeos.domain.model.PersonTextLimits
import com.luma.lifeos.ui.navigation.CatalogAttachmentFeedback
import com.luma.lifeos.ui.navigation.CatalogEditorResult
import com.luma.lifeos.ui.navigation.CatalogEntity
import com.luma.lifeos.ui.navigation.catalogEditorResultIdFlow
import com.luma.lifeos.ui.navigation.consumeCatalogEditorDelivery
import com.luma.lifeos.ui.navigation.readCatalogEditorDelivery
import com.luma.lifeos.R
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun MoodEditorRoute(repository: MoodRepository, eventId: EventId?, onCancel: () -> Unit,
    onFinished: (LocalDate?) -> Unit, people: PersonRepository, onCreatePerson: () -> Unit,
    places: PlaceRepository, onCreatePlace: () -> Unit, initialTimeMillis: Long? = null,
    draftStore: MoodDraftStore? = null, catalogResultHandle: SavedStateHandle? = null) {
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
    val resultIdFlow = remember(catalogResultHandle) {
        catalogResultHandle?.catalogEditorResultIdFlow() ?: MutableStateFlow<String?>(null)
    }
    val resultId by resultIdFlow.collectAsStateWithLifecycle()
    var personFeedback by remember { mutableStateOf<CatalogAttachmentFeedback?>(null) }
    var placeFeedback by remember { mutableStateOf<CatalogAttachmentFeedback?>(null) }
    val personFeedbackText = personFeedback?.let {
        if (it.argument == null) stringResource(it.messageResource) else stringResource(it.messageResource, it.argument)
    }
    val placeFeedbackText = placeFeedback?.let { stringResource(it.messageResource) }
    var cancelAfterDiscard by remember { mutableStateOf(false) }
    fun finish(deleted: Boolean, occurredAtMillis: Long) {
        onFinished(if (deleted) null else
            Instant.ofEpochMilli(occurredAtMillis).atZone(ZoneId.systemDefault()).toLocalDate())
    }
    LaunchedEffect(state.completionEventId) {
        if (state.completionEventId != null && editor.acknowledgeCompletion()) {
            withContext(Dispatchers.Main.immediate) {
                finish(state.deleted, state.draft.occurredAtMillis)
            }
        }
    }
    LaunchedEffect(
        resultId,
        state.ready,
        state.busy,
        state.completed,
        state.draft.personIds,
        state.draft.placeId,
        state.draftPersisting,
        state.draftDurable,
    ) {
        val handle = catalogResultHandle ?: return@LaunchedEffect
        val delivery = handle.readCatalogEditorDelivery(CatalogEntity.PERSON)
            ?: handle.readCatalogEditorDelivery(CatalogEntity.PLACE)
            ?: return@LaunchedEffect
        if (!state.ready || state.busy || state.completed || state.draftPersisting || !state.draftDurable) {
            return@LaunchedEffect
        }
        when (delivery.result) {
            is CatalogEditorResult.Created -> {
                val id = delivery.result.id
                val exists = try {
                    if (delivery.entity == CatalogEntity.PERSON) people.get(id) != null else places.get(id) != null
                } catch (failure: CancellationException) {
                    throw failure
                } catch (_: Exception) {
                    false
                }
                if (!exists) {
                    if (delivery.entity == CatalogEntity.PERSON) {
                        personFeedback = CatalogAttachmentFeedback(R.string.person_created_unavailable, isError = true)
                    } else {
                        placeFeedback = CatalogAttachmentFeedback(R.string.place_created_unavailable, isError = true)
                    }
                    handle.consumeCatalogEditorDelivery(delivery)
                    return@LaunchedEffect
                }
                when (delivery.entity) {
                    CatalogEntity.PERSON -> {
                        if (id !in state.draft.personIds && state.draft.personIds.size >= PersonTextLimits.SELECTION) {
                            personFeedback = CatalogAttachmentFeedback(
                                R.string.person_created_but_not_attached,
                                isError = true,
                                argument = PersonTextLimits.SELECTION,
                            )
                            handle.consumeCatalogEditorDelivery(delivery)
                            return@LaunchedEffect
                        }
                        if (!editor.addPerson(id) || !editor.state.value.draftDurable) return@LaunchedEffect
                        personFeedback = CatalogAttachmentFeedback(R.string.person_attached_to_capture, isError = false)
                    }
                    CatalogEntity.PLACE -> {
                        if (state.draft.placeId != id) editor.setPlace(id)
                        if (!editor.state.value.draftDurable) return@LaunchedEffect
                        placeFeedback = CatalogAttachmentFeedback(R.string.place_attached_to_capture, isError = false)
                    }
                }
            }
            is CatalogEditorResult.Saved, is CatalogEditorResult.Deleted -> Unit
        }
        handle.consumeCatalogEditorDelivery(delivery)
    }
    val discardAndCancel: () -> Unit = {
        cancelAfterDiscard = true
        scope.launch {
            if (editor.discardDraft()) {
                withContext(Dispatchers.Main.immediate) {
                    cancelAfterDiscard = false
                    onCancel()
                }
            }
        }
    }
    val retryDraft: () -> Unit = {
        scope.launch {
            val completionId = state.completionEventId
            if (completionId != null) {
                if (editor.acknowledgeCompletion()) withContext(Dispatchers.Main.immediate) {
                    finish(state.deleted, state.draft.occurredAtMillis)
                }
            } else if (editor.retryDraftStorage() && cancelAfterDiscard) {
                withContext(Dispatchers.Main.immediate) {
                    cancelAfterDiscard = false
                    onCancel()
                }
            }
        }
    }
    MoodEditorScreen(state, editing = state.editing, onCancel = discardAndCancel, onSave = editor::save,
        onDelete = editor::delete, onRetry = editor::load, onScore = editor::setScore,
        onEmotion = editor::toggleEmotion, onEnergy = editor::setEnergy, onStress = editor::setStress,
        onNote = editor::setNote, onTime = editor::setLocalTime, onRetryDraft = retryDraft, peopleContent = {
            PersonPicker(people, state.draft.personIds, state.ready && !state.busy && !state.completed,
                editor::togglePerson, { personFeedback = null; onCreatePerson() }, personFeedbackText, personFeedback?.isError == true)
        },
        placeContent = {
            PlacePicker(places, state.draft.placeId, state.ready && !state.busy && !state.completed,
                editor::setPlace, { placeFeedback = null; onCreatePlace() }, placeFeedbackText, placeFeedback?.isError == true)
        })
}
