package com.luma.lifeos.ui.journal

import androidx.lifecycle.SavedStateHandle
import com.luma.lifeos.ui.photos.*
import com.luma.lifeos.domain.repository.PhotoRepository
import com.luma.lifeos.domain.model.PhotoAttachment
import com.luma.lifeos.domain.model.validatePhotos
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luma.lifeos.domain.model.Tag
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.LifeEventRecord
import com.luma.lifeos.domain.model.JournalInput
import com.luma.lifeos.domain.model.JournalTextLimits
import com.luma.lifeos.domain.model.PlaceSelection
import com.luma.lifeos.domain.model.PersonTextLimits
import com.luma.lifeos.domain.repository.JournalRepository
import com.luma.lifeos.domain.time.moodInstant
import java.util.UUID
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class JournalDraft(
    val occurredAtMillis: Long,
    val title: String = "",
    val body: String = "",
    val tagNames: Set<String> = emptySet(),
    val personIds: Set<UUID> = emptySet(),
    val placeId: UUID? = null,
    val photos: List<PhotoAttachment> = emptyList(),
)

enum class JournalEditorError { LOAD, SAVE, DELETE, TIME, TEXT, PHOTO }

data class JournalEditorState(
    val draft: JournalDraft,
    val tags: List<Tag> = emptyList(),
    val loading: Boolean = true,
    val ready: Boolean = false,
    val busy: Boolean = false,
    val completed: Boolean = false,
    val deleted: Boolean = false,
    val editing: Boolean = false,
    val hasChanges: Boolean = false,
    val error: JournalEditorError? = null,
)

class JournalEditorViewModel(
    private val repository: JournalRepository,
    val eventId: EventId?,
    private val saved: SavedStateHandle,
    clock: Clock = Clock.systemUTC(),
    photoRepository: PhotoRepository? = null,
) : ViewModel() {
    private val initialTime = saved.get<Long>("initial_time") ?: clock.instant().toEpochMilli().also { saved["initial_time"] = it }
    private val captureId = EventId.parse(saved.get<String>("capture_id") ?: EventId.new().toString().also { saved["capture_id"] = it })
    private var baseline = JournalDraft(initialTime)
    private var editingId: EventId? = eventId
    private var loadingJob: Job? = null
    private val mutableState = MutableStateFlow(JournalEditorState(readDraft(), completed = saved["completed"] ?: false,
        deleted = saved["deleted"] ?: false, editing = eventId != null,
        hasChanges = saved.get<Boolean>("dirty") ?: (saved.get<Boolean>("has_draft") == true)))
    val state: StateFlow<JournalEditorState> = mutableState.asStateFlow()

    private val photoSession = PhotoDraftSession(photoRepository, saved, viewModelScope, captureId.value,
        current = { state.value.draft.photos },
        canEdit = { state.value.ready && !state.value.busy && !state.value.completed },
        busy = { mutableState.value = state.value.copy(busy = it) },
        update = { photos ->
            if (photos != state.value.draft.photos) saved["pending_delete"] = false
            val draft = state.value.draft.copy(photos = photos)
            persist(draft)
            mutableState.value = state.value.copy(draft = draft, hasChanges = draft != baseline, error = null)
        }, error = { mutableState.value = state.value.copy(error = JournalEditorError.PHOTO) })

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
                val tags = repository.observeTags().first()
                // A create may have committed before process death, without saving its completion flag.
                val record = repository.get(eventId ?: captureId)
                if (record == null && saved.get<Boolean>("pending_delete") == true) {
                    photoSession.releaseAfterWrite()
                    saved["completed"] = true
                    saved["deleted"] = true
                    saved["pending_delete"] = false
                    mutableState.value = state.value.copy(loading = false, completed = true, deleted = true, error = null)
                    return@launch
                }
                if (eventId != null) requireNotNull(record) { "Journal unavailable" }
                baseline = if (record == null) JournalDraft(initialTime) else {
                    require(record.event.isEditable && record.journal != null)
                    record.toDraft().also { draft ->
                        JournalInput(draft.body, Instant.ofEpochMilli(draft.occurredAtMillis), draft.title, draft.tagNames, draft.personIds, PlaceSelection(draft.placeId), draft.photos)
                    }
                }
                editingId = record?.event?.id
                val draft = if (saved.get<Boolean>("has_draft") == true) readDraft() else baseline
                validatePhotos(draft.photos)
                photoSession.retain(draft.photos)
                persist(draft)
                mutableState.value = state.value.copy(draft = draft, tags = tags, loading = false,
                    ready = true, error = null, hasChanges = draft != baseline, editing = editingId != null)
                photoSession.resumePending()
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutableState.value = state.value.copy(loading = false, ready = false, error = JournalEditorError.LOAD)
            }
        }
    }

    fun setTitle(title: String) {
        if (title.length > JournalTextLimits.TITLE) { rejectText(); return }
        change { it.copy(title = title) }
    }
    fun setBody(body: String) {
        if (body.length > JournalTextLimits.BODY) { rejectText(); return }
        change { it.copy(body = body) }
    }
    private fun rejectText() {
        if (state.value.ready && !state.value.busy && !state.value.completed)
            mutableState.value = state.value.copy(error = JournalEditorError.TEXT)
    }
    fun addTag(name: String): Boolean {
        if (!state.value.ready || state.value.busy || name.isBlank()) return false
        val trimmed = name.trim()
        if (trimmed.length > JournalTextLimits.TAG ||
            (state.value.draft.tagNames.size >= JournalTextLimits.TAG_COUNT &&
                state.value.draft.tagNames.none { Tag.normalize(it) == Tag.normalize(trimmed) })) {
            rejectText()
            return false
        }
        val catalogName = state.value.tags.firstOrNull { Tag.normalize(it.name) == Tag.normalize(trimmed) }?.name ?: trimmed
        change { draft ->
            if (draft.tagNames.any { Tag.normalize(it) == Tag.normalize(catalogName) }) draft
            else draft.copy(tagNames = draft.tagNames + catalogName)
        }
        return true
    }
    fun removeTag(name: String) = change { it.copy(tagNames = it.tagNames - name) }

    fun setLocalTime(local: LocalDateTime, zone: ZoneId): Boolean {
        if (!state.value.ready || state.value.busy) return false
        return try {
            val instant = moodInstant(local, zone, Instant.ofEpochMilli(state.value.draft.occurredAtMillis))
            change { it.copy(occurredAtMillis = instant.toEpochMilli()) }
            true
        } catch (_: IllegalArgumentException) {
            mutableState.value = state.value.copy(error = JournalEditorError.TIME)
            false
        }
    }

    fun save() {
        val current = state.value
        if (!current.ready || current.busy || current.completed || current.draft.body.isBlank()) return
        val draft = current.draft
        val input = JournalInput(draft.body, Instant.ofEpochMilli(draft.occurredAtMillis),
            draft.title, draft.tagNames, draft.personIds, PlaceSelection(draft.placeId), draft.photos)
        write(JournalEditorError.SAVE) {
            // Also reconcile a committed write whose result failed to reach this still-open form.
            val recoveredId = editingId ?: repository.get(captureId)?.event?.id
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
        write(JournalEditorError.DELETE) { repository.delete(id) }
    }

    private fun write(error: JournalEditorError, action: suspend () -> Unit) {
        mutableState.value = state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                action()
                photoSession.releaseAfterWrite()
                saved["completed"] = true
                saved["pending_delete"] = false
                saved["deleted"] = error == JournalEditorError.DELETE
                mutableState.value = state.value.copy(busy = false, completed = true, deleted = error == JournalEditorError.DELETE)
            } catch (failure: Exception) {
                if (failure is CancellationException) {
                    mutableState.value = state.value.copy(busy = false)
                    throw failure
                }
                mutableState.value = state.value.copy(busy = false, error = error)
            }
        }
    }

    private fun change(transform: (JournalDraft) -> JournalDraft) {
        if (!state.value.ready || state.value.busy || state.value.completed) return
        val draft = transform(state.value.draft)
        if (draft != state.value.draft) saved["pending_delete"] = false
        persist(draft)
        mutableState.value = state.value.copy(draft = draft, error = null, hasChanges = draft != baseline)
    }

    fun setPlace(id: UUID?) = change { it.copy(placeId = id) }

    fun togglePerson(id: UUID) = change {
        it.copy(personIds = if (id in it.personIds) it.personIds - id
            else if (it.personIds.size < PersonTextLimits.SELECTION) it.personIds + id else it.personIds)
    }

    private fun persist(draft: JournalDraft) {
        saved.savePhotos(draft.photos)
        saved["place"] = draft.placeId?.toString()
        saved["people"] = ArrayList(draft.personIds.map(UUID::toString))
        saved["has_draft"] = true
        saved["dirty"] = draft != baseline
        saved["time"] = draft.occurredAtMillis
        saved["title"] = draft.title
        saved["body"] = draft.body
        saved["tags"] = ArrayList(draft.tagNames)
    }

    private fun readDraft(): JournalDraft = JournalDraft(
        saved.get<Long>("time") ?: initialTime,
        saved.get<String>("title") ?: "", saved.get<String>("body") ?: "",
        saved.get<ArrayList<String>>("tags")?.toSet() ?: emptySet(),
        saved.get<ArrayList<String>>("people")?.map(UUID::fromString)?.toSet() ?: emptySet(),
        saved.get<String>("place")?.let(UUID::fromString), saved.readPhotos())

    private fun LifeEventRecord.toDraft(): JournalDraft {
        val details = requireNotNull(journal)
        return JournalDraft(event.occurredAt.toEpochMilli(), details.title.orEmpty(), details.body,
            details.tags.map { it.name }.toSet(), people.map { it.id }.toSet(), place?.id, photos)
    }
}
