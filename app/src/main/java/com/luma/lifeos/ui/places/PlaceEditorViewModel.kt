package com.luma.lifeos.ui.places

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luma.lifeos.domain.model.PlaceInput
import com.luma.lifeos.domain.model.PlaceTextLimits
import com.luma.lifeos.domain.repository.PlaceRepository
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
)

class PlaceEditorViewModel(private val repository: PlaceRepository, private val placeId: UUID?,
    private val saved: SavedStateHandle) : ViewModel() {
    private val captureId = UUID.fromString(saved.get<String>("capture_id") ?: UUID.randomUUID().toString().also { saved["capture_id"] = it })
    private var editingId = placeId
    private var baseline = PlaceDraft()
    private var loadingJob: Job? = null
    private val mutableState = MutableStateFlow(PlaceEditorState(readDraft(), editing = placeId != null,
        completed = saved["completed"] ?: false, deleted = saved["deleted"] ?: false,
        hasChanges = saved.get<Boolean>("dirty") ?: (saved.get<Boolean>("has_draft") == true),
        textErrors = readTextErrors()))
    val state = mutableState.asStateFlow()
    init { if (!state.value.completed) load() }

    fun load() {
        if (state.value.ready || state.value.busy || state.value.completed || loadingJob?.isActive == true) return
        mutableState.value = state.value.copy(loading = true, error = null)
        loadingJob = viewModelScope.launch {
            try {
                val place = repository.get(placeId ?: captureId)
                if (place == null && saved.get<Boolean>("pending_delete") == true) {
                    finish(deleted = true)
                    mutableState.value = state.value.copy(loading = false)
                    return@launch
                }
                if (placeId != null) requireNotNull(place) { "Place unavailable" }
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
            if (recoveredId == null) repository.create(captureId, input) else {
                editingId = recoveredId
                repository.update(recoveredId, input)
            }
        }
    }
    fun delete() {
        val id = editingId ?: return
        if (!state.value.ready || state.value.busy || state.value.completed) return
        saved["pending_delete"] = true
        write(PlaceEditorError.DELETE) { repository.delete(id) }
    }
    private fun write(error: PlaceEditorError, action: suspend () -> Unit) {
        mutableState.value = state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try { action(); finish(deleted = error == PlaceEditorError.DELETE) }
            catch (failure: Exception) {
                mutableState.value = state.value.copy(busy = false)
                if (failure is CancellationException) throw failure
                mutableState.value = state.value.copy(error = error)
            }
        }
    }
    private fun finish(deleted: Boolean) {
        saved["completed"] = true; saved["deleted"] = deleted; saved["pending_delete"] = false
        mutableState.value = state.value.copy(busy = false, completed = true, deleted = deleted, error = null)
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
}
