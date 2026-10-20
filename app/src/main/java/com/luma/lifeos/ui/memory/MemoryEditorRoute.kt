package com.luma.lifeos.ui.memory

import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.SavedStateHandle
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
import com.luma.lifeos.domain.model.PersonTextLimits
import com.luma.lifeos.ui.navigation.CatalogAttachmentFeedback
import com.luma.lifeos.ui.navigation.CatalogEditorResult
import com.luma.lifeos.ui.navigation.CatalogEntity
import com.luma.lifeos.ui.navigation.catalogEditorResultIdFlow
import com.luma.lifeos.ui.navigation.consumeCatalogEditorDelivery
import com.luma.lifeos.ui.navigation.readCatalogEditorDelivery
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import com.luma.lifeos.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun MemoryEditorRoute(repository: MemoryRepository, eventId: EventId?, onCancel: () -> Unit,
    onFinished: (LocalDate?) -> Unit, people: PersonRepository, onCreatePerson: () -> Unit,
    places: PlaceRepository, onCreatePlace: () -> Unit, photos: PhotoRepository, initialTimeMillis: Long? = null,
    catalogResultHandle: SavedStateHandle? = null) {
    val factory = remember(repository, eventId, photos, initialTimeMillis) {
        viewModelFactory { initializer {
            MemoryEditorViewModel(repository, eventId, createSavedStateHandle(), photoRepository = photos,
                initialTimeMillis = initialTimeMillis)
        } }
    }
    val editor: MemoryEditorViewModel = viewModel(factory = factory)
    val state by editor.state.collectAsStateWithLifecycle()
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

    LaunchedEffect(resultId, state.ready, state.busy, state.completed, state.draft.personIds, state.draft.placeId) {
        val handle = catalogResultHandle ?: return@LaunchedEffect
        val delivery = handle.readCatalogEditorDelivery(CatalogEntity.PERSON)
            ?: handle.readCatalogEditorDelivery(CatalogEntity.PLACE)
            ?: return@LaunchedEffect
        if (!state.ready || state.busy || state.completed) return@LaunchedEffect
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
                        if (!editor.addPerson(id)) return@LaunchedEffect
                        personFeedback = CatalogAttachmentFeedback(R.string.person_attached_to_capture, isError = false)
                    }
                    CatalogEntity.PLACE -> {
                        if (state.draft.placeId != id) editor.setPlace(id)
                        placeFeedback = CatalogAttachmentFeedback(R.string.place_attached_to_capture, isError = false)
                    }
                }
            }
            is CatalogEditorResult.Saved, is CatalogEditorResult.Deleted -> Unit
        }
        handle.consumeCatalogEditorDelivery(delivery)
    }
    LaunchedEffect(state.completed) {
        if (state.completed) onFinished(if (state.removed && state.direct) null else
            Instant.ofEpochMilli(state.draft.occurredAtMillis).atZone(ZoneId.systemDefault()).toLocalDate())
    }
    MemoryEditorScreen(state, { editor.discardPhotos(onCancel) }, editor::save, editor::remove, editor::load,
        editor::setTitle, editor::setNote, editor::setImportance, editor::setLocalTime, peopleContent = {
            PersonPicker(people, state.draft.personIds, state.ready && !state.busy && !state.completed,
                editor::togglePerson, { personFeedback = null; onCreatePerson() }, personFeedbackText, personFeedback?.isError == true)
        },
        placeContent = {
            PlacePicker(places, state.draft.placeId, state.ready && !state.busy && !state.completed,
                editor::setPlace, { placeFeedback = null; onCreatePlace() }, placeFeedbackText, placeFeedback?.isError == true)
        },
        photosContent = { PhotoEditor(photos, state.draft.photos, state.ready && !state.busy && !state.completed,
            editor::importPhoto, editor::removePhoto, editor::retryPhoto,
            importFailed = state.error == MemoryEditorError.PHOTO && editor.hasPendingPhotoImport(),
            operationFailed = state.error == MemoryEditorError.PHOTO && !editor.hasPendingPhotoImport()) })
}
