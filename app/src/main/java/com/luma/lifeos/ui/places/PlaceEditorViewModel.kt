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
data class PlaceEditorState(
    val draft: PlaceDraft, val loading: Boolean = true, val ready: Boolean = false,
    val busy: Boolean = false, val editing: Boolean = false, val completed: Boolean = false,
    val deleted: Boolean = false, val hasChanges: Boolean = false, val error: PlaceEditorError? = null,
)

class PlaceEditorViewModel(private val repository: PlaceRepository, private val placeId: UUID?,
    private val saved: SavedStateHandle) : ViewModel() {
    private val captureId = UUID.fromString(saved.get<String>("capture_id") ?: UUID.randomUUID().toString().also { saved["capture_id"] = it })
    private var editingId = placeId
    private var baseline = PlaceDraft()
    private var loadingJob: Job? = null
    private val mutableState = MutableStateFlow(PlaceEditorState(readDraft(), editing = placeId != null,
        completed = saved["completed"] ?: false, deleted = saved["deleted"] ?: false,
        hasChanges = saved.get<Boolean>("dirty") ?: (saved.get<Boolean>("has_draft") == true)))
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
        if (name.length > PlaceTextLimits.NAME) { rejectText(); return }
        change { it.copy(name = name) }
    }
    fun setNote(note: String) {
        if (note.length > PlaceTextLimits.NOTE) { rejectText(); return }
        change { it.copy(note = note) }
    }
    private fun rejectText() {
        if (state.value.ready && !state.value.busy && !state.value.completed)
            mutableState.value = state.value.copy(error = PlaceEditorError.TEXT)
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
    private fun change(transform: (PlaceDraft) -> PlaceDraft) {
        if (!state.value.ready || state.value.busy || state.value.completed) return
        val draft = transform(state.value.draft)
        if (draft != state.value.draft) saved["pending_delete"] = false
        persist(draft)
        mutableState.value = state.value.copy(draft = draft, error = null, hasChanges = draft != baseline)
    }
    private fun persist(draft: PlaceDraft) {
        saved["has_draft"] = true; saved["dirty"] = draft != baseline
        saved["name"] = draft.name; saved["note"] = draft.note
    }
    private fun readDraft() = PlaceDraft(saved.get<String>("name") ?: "", saved.get<String>("note") ?: "")
}
