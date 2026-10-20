package com.luma.lifeos.ui.people

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luma.lifeos.domain.model.PersonInput
import com.luma.lifeos.domain.model.PersonTextLimits
import com.luma.lifeos.domain.repository.PersonRepository
import com.luma.lifeos.ui.navigation.CatalogEditorResult
import com.luma.lifeos.ui.navigation.persistCatalogEditorResult
import com.luma.lifeos.ui.navigation.readPersistedCatalogEditorResult
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PersonDraft(val name: String = "", val note: String = "")
enum class PersonEditorError { LOAD, SAVE, DELETE, TEXT }
enum class PersonTextField { NAME, NOTE }
data class PersonEditorState(
    val draft: PersonDraft, val loading: Boolean = true, val ready: Boolean = false,
    val busy: Boolean = false, val editing: Boolean = false, val completed: Boolean = false,
    val deleted: Boolean = false, val hasChanges: Boolean = false, val error: PersonEditorError? = null,
    val textErrors: Set<PersonTextField> = emptySet(),
    val result: CatalogEditorResult? = null,
)

class PersonEditorViewModel(private val repository: PersonRepository, private val personId: UUID?,
    private val saved: SavedStateHandle, requestId: UUID? = null) : ViewModel() {
    private val captureId = UUID.fromString(saved.get<String>("capture_id") ?: UUID.randomUUID().toString().also { saved["capture_id"] = it })
    private val catalogRequestId = saved.get<String>(KEY_CATALOG_REQUEST_ID)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        ?: requestId.also { if (it != null) saved[KEY_CATALOG_REQUEST_ID] = it.toString() }
    private var editingId = personId
    private var baseline = PersonDraft()
    private var loadingJob: Job? = null
    private val mutableState = MutableStateFlow(PersonEditorState(readDraft(), editing = personId != null,
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
                val person = repository.get(personId ?: captureId)
                if (person == null && saved.get<Boolean>("pending_delete") == true) {
                    val deletedId = saved.get<String>(KEY_PENDING_DELETE_ID)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        ?: personId ?: captureId
                    finish(CatalogEditorResult.Deleted(deletedId))
                    mutableState.value = state.value.copy(loading = false)
                    return@launch
                }
                if (personId != null) requireNotNull(person) { "Person unavailable" }
                if (personId == null && catalogRequestId != null && person != null) {
                    editingId = person.id
                    finish(CatalogEditorResult.Created(person.id))
                    mutableState.value = state.value.copy(loading = false)
                    return@launch
                }
                baseline = person?.let { PersonDraft(it.name, it.note.orEmpty()).also { draft ->
                    PersonInput(draft.name, draft.note)
                } } ?: PersonDraft()
                editingId = person?.id
                val draft = if (saved.get<Boolean>("has_draft") == true) readDraft() else baseline
                persist(draft)
                mutableState.value = state.value.copy(draft = draft, loading = false, ready = true,
                    editing = editingId != null, hasChanges = draft != baseline, error = null)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutableState.value = state.value.copy(loading = false, ready = false, error = PersonEditorError.LOAD)
            }
        }
    }
    fun setName(name: String) {
        if (name.length > PersonTextLimits.NAME) { rejectText(PersonTextField.NAME); return }
        change(clearTextField = PersonTextField.NAME) { it.copy(name = name) }
    }
    fun setNote(note: String) {
        if (note.length > PersonTextLimits.NOTE) { rejectText(PersonTextField.NOTE); return }
        change(clearTextField = PersonTextField.NOTE) { it.copy(note = note) }
    }
    private fun rejectText(field: PersonTextField) {
        if (state.value.ready && !state.value.busy && !state.value.completed) {
            val errors = state.value.textErrors + field
            persistTextErrors(errors)
            mutableState.value = state.value.copy(textErrors = errors, error = PersonEditorError.TEXT)
        }
    }
    fun save() {
        val current = state.value
        if (!current.ready || current.busy || current.completed || current.draft.name.isBlank()) return
        val input = PersonInput(current.draft.name, current.draft.note)
        saved["pending_delete"] = false
        write(PersonEditorError.SAVE) {
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
        write(PersonEditorError.DELETE) {
            repository.delete(id)
            CatalogEditorResult.Deleted(id)
        }
    }
    private fun write(error: PersonEditorError, action: suspend () -> CatalogEditorResult) {
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
    private fun change(clearTextField: PersonTextField? = null, transform: (PersonDraft) -> PersonDraft) {
        val current = state.value
        if (!current.ready || current.busy || current.completed) return
        val draft = transform(current.draft)
        if (draft == current.draft) return
        saved["pending_delete"] = false
        persist(draft)
        val errors = clearTextField?.let { current.textErrors - it } ?: current.textErrors
        persistTextErrors(errors)
        mutableState.value = current.copy(draft = draft, textErrors = errors,
            error = if (errors.isNotEmpty()) PersonEditorError.TEXT else null, hasChanges = draft != baseline)
    }
    private fun persist(draft: PersonDraft) {
        saved["has_draft"] = true; saved["dirty"] = draft != baseline
        saved["name"] = draft.name; saved["note"] = draft.note
    }
    private fun readTextErrors(): Set<PersonTextField> = saved.get<ArrayList<String>>("text_error_fields")
        .orEmpty().mapNotNull { runCatching { PersonTextField.valueOf(it) }.getOrNull() }.toSet()

    private fun persistTextErrors(errors: Set<PersonTextField>) {
        saved["text_error_fields"] = ArrayList(errors.map { it.name }.sorted())
    }
    private fun readDraft() = PersonDraft(saved.get<String>("name") ?: "", saved.get<String>("note") ?: "")

    private companion object {
        const val KEY_CATALOG_REQUEST_ID = "catalog_request_id"
        const val KEY_PENDING_DELETE_ID = "pending_delete_id"
    }
}
