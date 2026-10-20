package com.luma.lifeos.ui.places

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luma.lifeos.domain.model.PlaceInput
import com.luma.lifeos.domain.model.PlaceTextLimits
import com.luma.lifeos.domain.repository.PlaceRepository
import com.luma.lifeos.ui.navigation.CatalogEditorResult
import com.luma.lifeos.ui.navigation.persistCatalogEditorResult
import com.luma.lifeos.ui.navigation.readPersistedCatalogEditorResult
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlaceDraft(val name: String = "", val note: String = "")
enum class PlaceEditorError { LOAD, SAVE, DELETE, TEXT }
enum class PlaceTextField { NAME, NOTE }
data class PlaceEditorState(
    val draft: PlaceDraft, val loading: Boolean = true, val ready: Boolean = false,
    val busy: Boolean = false, val editing: Boolean = false, val completed: Boolean = false,
    val deleted: Boolean = false, val hasChanges: Boolean = false, val error: PlaceEditorError? = null,
    val textErrors: Set<PlaceTextField> = emptySet(),
    val result: CatalogEditorResult? = null,
)

class PlaceEditorViewModel(private val repository: PlaceRepository, private val placeId: UUID?,
    private val saved: SavedStateHandle, requestId: UUID? = null) : ViewModel() {
    private val captureId = UUID.fromString(saved.get<String>("capture_id") ?: UUID.randomUUID().toString().also { saved["capture_id"] = it })
    private val catalogRequestId = saved.get<String>(KEY_CATALOG_REQUEST_ID)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        ?: requestId.also { if (it != null) saved[KEY_CATALOG_REQUEST_ID] = it.toString() }
    private var editingId = placeId
    private var baseline = PlaceDraft()
    private var loadingJob: Job? = null
    private val mutableState = MutableStateFlow(PlaceEditorState(readDraft(), editing = placeId != null,
        completed = saved["completed"] ?: false, deleted = saved["deleted"] ?: false,
        hasChanges = saved.get<Boolean>("dirty") ?: (saved.get<Boolean>("has_draft") == true),
        textErrors = readTextErrors(), result = saved.readPersistedCatalogEditorResult()))
    val state = mutableState.asStateFlow()
    init { if (!state.value.completed) load() }

    fun load() {
        if (state.value.ready || state.value.busy || state.value.completed || loadingJob?.isActive == true) return
        mutableState.value = state.value.copy(loading = true, error = null)
        loadingJob = viewModelScope.launch {
            try {
                val place = repository.get(placeId ?: captureId)
                if (place == null && saved.get<Boolean>("pending_delete") == true) {
                    val deletedId = saved.get<String>(KEY_PENDING_DELETE_ID)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        ?: placeId ?: captureId
                    finish(CatalogEditorResult.Deleted(deletedId))
                    mutableState.value = state.value.copy(loading = false)
                    return@launch
                }
                if (placeId != null) requireNotNull(place) { "Place unavailable" }
                if (placeId == null && catalogRequestId != null && place != null) {
                    editingId = place.id
                    finish(CatalogEditorResult.Created(place.id))
                    mutableState.value = state.value.copy(loading = false)
                    return@launch
                }
                baseline = place?.let { PlaceDraft(it.name, it.note.orEmpty()).also { draft ->
                    PlaceInput(draft.name, draft.note)
                } } ?: PlaceDraft()
                editingId = place?.id
                val draft = if (saved.get<Boolean>("has_draft") == true) readDraft() else baseline
                persist(draft)
                mutableState.value = state.value.copy(draft = draft, loading = false, ready = true,
                    editing = editingId != null, hasChanges = draft != baseline, error = null)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutableState.value = state.value.copy(loading = false, ready = false, error = PlaceEditorError.LOAD)
            }
        }
    }
    fun setName(name: String) {
        if (name.length > PlaceTextLimits.NAME) { rejectText(PlaceTextField.NAME); return }
        change(clearTextField = PlaceTextField.NAME) { it.copy(name = name) }
    }
    fun setNote(note: String) {
        if (note.length > PlaceTextLimits.NOTE) { rejectText(PlaceTextField.NOTE); return }
        change(clearTextField = PlaceTextField.NOTE) { it.copy(note = note) }
    }
    private fun rejectText(field: PlaceTextField) {
        if (state.value.ready && !state.value.busy && !state.value.completed) {
            val errors = state.value.textErrors + field
            persistTextErrors(errors)
            mutableState.value = state.value.copy(textErrors = errors, error = PlaceEditorError.TEXT)
        }
    }
    fun save() {
        val current = state.value
        if (!current.ready || current.busy || current.completed || current.draft.name.isBlank()) return
        val input = PlaceInput(current.draft.name, current.draft.note)
        saved["pending_delete"] = false
        write(PlaceEditorError.SAVE) {
            val recoveredId = editingId ?: repository.get(captureId)?.id
            if (recoveredId == null) CatalogEditorResult.Created(repository.create(captureId, input)) else {
                editingId = recoveredId
                repository.update(recoveredId, input)
                CatalogEditorResult.Saved(recoveredId)
            }
        }
    }
    fun delete() {
        val id = editingId ?: return
        if (!state.value.ready || state.value.busy || state.value.completed) return
        saved["pending_delete"] = true
        saved[KEY_PENDING_DELETE_ID] = id.toString()
        write(PlaceEditorError.DELETE) {
            repository.delete(id)
            CatalogEditorResult.Deleted(id)
        }
    }
    private fun write(error: PlaceEditorError, action: suspend () -> CatalogEditorResult) {
        mutableState.value = state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try { finish(action()) }
            catch (failure: Exception) {
                mutableState.value = state.value.copy(busy = false)
                if (failure is CancellationException) throw failure
                mutableState.value = state.value.copy(error = error)
            }
        }
    }
    private fun finish(result: CatalogEditorResult) {
        val deleted = result is CatalogEditorResult.Deleted
        saved.persistCatalogEditorResult(result)
        saved["completed"] = true; saved["deleted"] = deleted; saved["pending_delete"] = false
        mutableState.value = state.value.copy(busy = false, completed = true, deleted = deleted, error = null, result = result)
    }
    private fun change(clearTextField: PlaceTextField? = null, transform: (PlaceDraft) -> PlaceDraft) {
        val current = state.value
        if (!current.ready || current.busy || current.completed) return
        val draft = transform(current.draft)
        if (draft == current.draft) return
        saved["pending_delete"] = false
        persist(draft)
        val errors = clearTextField?.let { current.textErrors - it } ?: current.textErrors
        persistTextErrors(errors)
        mutableState.value = current.copy(draft = draft, textErrors = errors,
            error = if (errors.isNotEmpty()) PlaceEditorError.TEXT else null, hasChanges = draft != baseline)
    }
    private fun persist(draft: PlaceDraft) {
        saved["has_draft"] = true; saved["dirty"] = draft != baseline
        saved["name"] = draft.name; saved["note"] = draft.note
    }
    private fun readTextErrors(): Set<PlaceTextField> = saved.get<ArrayList<String>>("text_error_fields")
        .orEmpty().mapNotNull { runCatching { PlaceTextField.valueOf(it) }.getOrNull() }.toSet()

    private fun persistTextErrors(errors: Set<PlaceTextField>) {
        saved["text_error_fields"] = ArrayList(errors.map { it.name }.sorted())
    }
    private fun readDraft() = PlaceDraft(saved.get<String>("name") ?: "", saved.get<String>("note") ?: "")

    private companion object {
        const val KEY_CATALOG_REQUEST_ID = "catalog_request_id"
        const val KEY_PENDING_DELETE_ID = "pending_delete_id"
    }
}
