package com.luma.lifeos.ui.memory

import androidx.lifecycle.SavedStateHandle
import com.luma.lifeos.ui.photos.*
import com.luma.lifeos.domain.repository.PhotoRepository
import com.luma.lifeos.domain.model.PhotoAttachment
import com.luma.lifeos.domain.model.validatePhotos
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luma.lifeos.domain.model.*
import com.luma.lifeos.domain.repository.MemoryRepository
import com.luma.lifeos.domain.time.moodInstant
import java.util.UUID
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MemoryDraft(val occurredAtMillis: Long, val title: String = "", val note: String = "", val importance: Int? = null, val personIds: Set<UUID> = emptySet(), val placeId: UUID? = null, val photos: List<PhotoAttachment> = emptyList())
enum class MemoryEditorError { LOAD, SAVE, REMOVE, TIME, TEXT, PHOTO }
data class MemoryEditorState(
    val draft: MemoryDraft,
    val original: LifeEventRecord? = null,
    val direct: Boolean = true,
    val editing: Boolean = false,
    val loading: Boolean = true,
    val ready: Boolean = false,
    val busy: Boolean = false,
    val completed: Boolean = false,
    val removed: Boolean = false,
    val hasChanges: Boolean = false,
    val error: MemoryEditorError? = null,
)

class MemoryEditorViewModel(private val repository: MemoryRepository, private val eventId: EventId?,
    private val saved: SavedStateHandle, clock: Clock = Clock.systemUTC(), photoRepository: PhotoRepository? = null,
    initialTimeMillis: Long? = null) : ViewModel() {
    private val initialTime = saved.get<Long>("initial_time") ?: (initialTimeMillis ?: clock.millis())
        .also { saved["initial_time"] = it }
    private val captureId = EventId.parse(saved.get<String>("capture_id") ?: EventId.new().toString().also { saved["capture_id"] = it })
    private var baseline = MemoryDraft(initialTime)
    private var editingId: EventId? = eventId
    private var loadingJob: Job? = null
    private val mutableState = MutableStateFlow(MemoryEditorState(readDraft(), direct = saved["direct"] ?: true,
        completed = saved["completed"] ?: false, removed = saved["removed"] ?: false,
        hasChanges = saved.get<Boolean>("dirty") ?: (saved.get<Boolean>("has_draft") == true)))
    val state = mutableState.asStateFlow()

    private val photoSession = PhotoDraftSession(photoRepository, saved, viewModelScope, captureId.value,
        current = { state.value.draft.photos },
        canEdit = { state.value.ready && !state.value.busy && !state.value.completed },
        busy = { mutableState.value = state.value.copy(busy = it) },
        update = { photos ->
            if (photos != state.value.draft.photos) saved["pending_remove"] = null
            val draft = state.value.draft.copy(photos = photos)
            persist(draft)
            mutableState.value = state.value.copy(draft = draft, hasChanges = draft != baseline, error = null)
        }, error = { mutableState.value = state.value.copy(error = MemoryEditorError.PHOTO) })

    fun importPhoto(source: String) = photoSession.importPhoto(source)
    fun removePhoto(id: UUID) = photoSession.remove(id)
    fun retryPhoto() = photoSession.retry()
    fun hasPendingPhotoImport(): Boolean = saved.get<String>("photo_pending") != null
    fun discardPhotos(onDone: () -> Unit) {
        if (!state.value.busy && !state.value.completed) photoSession.discard(onDone)
    }

    init { if (!state.value.completed) load() }

    fun load() {
        if (state.value.busy || state.value.ready || state.value.completed || loadingJob?.isActive == true) return
        mutableState.value = state.value.copy(loading = true, error = null)
        loadingJob = viewModelScope.launch {
            try {
                val record = repository.get(eventId ?: captureId)
                val pending = saved.get<String>("pending_remove")
                if ((pending == "delete" && record == null) ||
                    (pending == "unmark" && record?.canPromoteToMemory == true && record.memory == null)) {
                    val direct = pending == "delete"
                    saved["direct"] = direct
                    photoSession.releaseAfterWrite()
                    finish(removed = true)
                    mutableState.value = state.value.copy(loading = false, direct = direct)
                    return@launch
                }
                if (eventId != null) requireNotNull(record) { "Original event unavailable" }
                if (eventId == null && record != null) require(record.event.type == EventType.MEMORY) { "Capture ID is occupied" }
                val direct = record == null || record.event.type == EventType.MEMORY
                if (record != null) require(if (direct) record.event.isEditable && record.memory != null else record.canPromoteToMemory)
                baseline = record?.toDraft(direct) ?: MemoryDraft(initialTime)
                // Larger legacy content stays in the database rather than entering saved state.
                require(baseline.title.length <= MemoryTextLimits.TITLE && baseline.note.length <= MemoryTextLimits.NOTE && baseline.personIds.size <= PersonTextLimits.SELECTION)
                editingId = record?.event?.id
                saved["direct"] = direct
                val draft = if (saved.get<Boolean>("has_draft") == true) readDraft() else baseline
                validatePhotos(draft.photos)
                photoSession.retain(draft.photos)
                persist(draft)
                mutableState.value = state.value.copy(draft = draft, original = record.takeUnless { direct }, direct = direct,
                    editing = record?.memory != null, loading = false, ready = true, hasChanges = draft != baseline, error = null)
                photoSession.resumePending()
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutableState.value = state.value.copy(loading = false, ready = false, error = MemoryEditorError.LOAD)
            }
        }
    }

    fun setTitle(title: String) {
        if (!state.value.direct) return
        if (title.length > MemoryTextLimits.TITLE) { rejectText(); return }
        change { it.copy(title = title) }
    }
    fun setNote(note: String) {
        if (note.length > MemoryTextLimits.NOTE) { rejectText(); return }
        change { it.copy(note = note) }
    }
    fun setImportance(value: Int?) {
        require(value == null || value in 1..5)
        change { it.copy(importance = value) }
    }
    private fun rejectText() {
        if (state.value.ready && !state.value.busy && !state.value.completed)
            mutableState.value = state.value.copy(error = MemoryEditorError.TEXT)
    }
    fun setLocalTime(local: LocalDateTime, zone: ZoneId): Boolean {
        if (!state.value.direct || !state.value.ready || state.value.busy || state.value.completed) return false
        return try {
            val instant = moodInstant(local, zone, Instant.ofEpochMilli(state.value.draft.occurredAtMillis))
            change { it.copy(occurredAtMillis = instant.toEpochMilli()) }
            true
        } catch (_: IllegalArgumentException) {
            mutableState.value = state.value.copy(error = MemoryEditorError.TIME)
            false
        }
    }
    fun save() {
        val current = state.value
        if (!current.ready || current.busy || current.completed || (current.direct && current.draft.note.isBlank())) return
        val draft = current.draft
        saved["pending_remove"] = null
        write(MemoryEditorError.SAVE) {
            if (current.direct) {
                val input = MemoryInput(draft.note, Instant.ofEpochMilli(draft.occurredAtMillis), draft.title, draft.importance, draft.personIds, PlaceSelection(draft.placeId), draft.photos)
                val recoveredId = editingId ?: repository.get(captureId)?.event?.id
                if (recoveredId == null) repository.create(captureId, input) else {
                    editingId = recoveredId
                    repository.update(recoveredId, input)
                }
            } else repository.saveMarker(requireNotNull(editingId), MemoryMarkerInput(draft.importance, draft.note, draft.personIds, PlaceSelection(draft.placeId), draft.photos))
        }
    }
    fun remove() {
        val current = state.value
        val id = editingId ?: return
        if (!current.ready || !current.editing || current.busy || current.completed) return
        saved["pending_remove"] = if (current.direct) "delete" else "unmark"
        write(MemoryEditorError.REMOVE) {
            if (current.direct) repository.delete(id) else repository.removeMarker(id)
        }
    }
    private fun write(error: MemoryEditorError, action: suspend () -> Unit) {
        mutableState.value = state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try { action(); photoSession.releaseAfterWrite(); finish(removed = error == MemoryEditorError.REMOVE) }
            catch (failure: Exception) {
                mutableState.value = state.value.copy(busy = false)
                if (failure is CancellationException) throw failure
                mutableState.value = state.value.copy(error = error)
            }
        }
    }
    private fun finish(removed: Boolean) {
        saved["completed"] = true
        saved["removed"] = removed
        saved["pending_remove"] = null
        mutableState.value = state.value.copy(busy = false, completed = true, removed = removed, error = null)
    }
    private fun change(transform: (MemoryDraft) -> MemoryDraft) {
        if (!state.value.ready || state.value.busy || state.value.completed) return
        val draft = transform(state.value.draft)
        if (draft != state.value.draft) saved["pending_remove"] = null
        persist(draft)
        mutableState.value = state.value.copy(draft = draft, error = null, hasChanges = draft != baseline)
    }
    fun setPlace(id: UUID?) = change { it.copy(placeId = id) }

    fun togglePerson(id: UUID) = change {
        it.copy(personIds = if (id in it.personIds) it.personIds - id
            else if (it.personIds.size < PersonTextLimits.SELECTION) it.personIds + id else it.personIds)
    }

    private fun persist(draft: MemoryDraft) {
        saved.savePhotos(draft.photos)
        saved["place"] = draft.placeId?.toString()
        saved["people"] = ArrayList(draft.personIds.map(UUID::toString))
        saved["has_draft"] = true
        saved["dirty"] = draft != baseline
        saved["time"] = draft.occurredAtMillis
        saved["title"] = draft.title
        saved["note"] = draft.note
        saved["importance"] = draft.importance
    }
    private fun readDraft() = MemoryDraft(saved.get<Long>("time") ?: initialTime, saved.get<String>("title") ?: "",
        saved.get<String>("note") ?: "", saved["importance"],
        saved.get<ArrayList<String>>("people")?.map(UUID::fromString)?.toSet() ?: emptySet(),
        saved.get<String>("place")?.let(UUID::fromString), saved.readPhotos())
    private fun LifeEventRecord.toDraft(direct: Boolean) = MemoryDraft(event.occurredAt.toEpochMilli(),
        if (direct) event.title.orEmpty() else "", if (direct) (memory?.note ?: event.note).orEmpty() else memory?.note.orEmpty(), memory?.importance, people.map { it.id }.toSet(), place?.id, photos)
}
