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
import com.luma.lifeos.lock.PhotoDraftAccessRegistry
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class MemoryDraft(val occurredAtMillis: Long, val title: String = "", val note: String = "", val importance: Int? = null, val personIds: Set<UUID> = emptySet(), val placeId: UUID? = null, val photos: List<PhotoAttachment> = emptyList(), val tagNames: Set<String> = emptySet(), val contextNote: String = "")
enum class MemoryEditorError { LOAD, SAVE, REMOVE, TIME, TEXT, PHOTO }
enum class MemoryTextField { TITLE, NOTE, CONTEXT_NOTE }
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
    val textErrors: Set<MemoryTextField> = emptySet(),
    val tags: List<Tag> = emptyList(),
)

class MemoryEditorViewModel(private val repository: MemoryRepository, private val eventId: EventId?,
    private val saved: SavedStateHandle, clock: Clock = Clock.systemUTC(), private val photoRepository: PhotoRepository? = null,
    initialTimeMillis: Long? = null, private val photoDraftAccessRegistry: PhotoDraftAccessRegistry? = null) : ViewModel() {
    private val initialTime = saved.get<Long>("initial_time") ?: (initialTimeMillis ?: clock.millis())
        .also { saved["initial_time"] = it }
    private val captureId = EventId.parse(saved.get<String>("capture_id") ?: EventId.new().toString().also { saved["capture_id"] = it })
    val photoDraftId: UUID get() = captureId.value
    private var photoDraftAccess: AutoCloseable? = null
    private val activePhotoRoute = MutableStateFlow<Long?>(null)
    private var nextPhotoRouteGeneration = 0L
    private val photoViewerAccess = mutableMapOf<UUID, AutoCloseable>()
    private val photoViewerLeaseLocks = mutableMapOf<UUID, Mutex>()
    private var baseline = MemoryDraft(initialTime)
    private var editingId: EventId? = eventId
    val resultEventId: EventId get() = editingId ?: eventId ?: captureId
    private var loadingJob: Job? = null
    private val mutableState = MutableStateFlow(MemoryEditorState(readDraft(), direct = saved["direct"] ?: true,
        completed = saved["completed"] ?: false, removed = saved["removed"] ?: false,
        hasChanges = saved.get<Boolean>("dirty") ?: (saved.get<Boolean>("has_draft") == true),
        textErrors = readTextErrors()))
    val state = mutableState.asStateFlow()

    private val photoSession = PhotoDraftSession(photoRepository, saved, viewModelScope, captureId.value,
        current = { state.value.draft.photos },
        canEdit = { state.value.ready && !state.value.busy && !state.value.completed && hasPhotoRouteAccess() },
        busy = { mutableState.value = state.value.copy(busy = it) },
        update = { photos ->
            if (photos != state.value.draft.photos) saved["pending_remove"] = null
            val draft = state.value.draft.copy(photos = photos)
            persist(draft)
            mutableState.value = state.value.copy(draft = draft, hasChanges = draft != baseline, error = null)
        }, error = { mutableState.value = state.value.copy(error = MemoryEditorError.PHOTO) })

    private val photoEditSession = MemoryPhotoEditSession(
        repository = photoRepository,
        saved = saved,
        scope = viewModelScope,
        currentPhotos = { state.value.draft.photos },
        editorLeaseId = photoDraftId,
        canEdit = { state.value.ready && !state.value.busy && !state.value.completed && hasPhotoRouteAccess() },
        canAccess = ::hasPhotoRouteAccess,
        accessIsRegistered = { leaseId ->
            photoDraftAccessRegistry?.isRegistered(leaseId) ?: hasPhotoRouteAccess()
        },
        openAccess = ::openChildPhotoAccess,
        attach = photoSession::attachImported,
        onError = { mutableState.value = state.value.copy(error = MemoryEditorError.PHOTO) },
    )
    internal val photoEditState = photoEditSession.state

    fun selectPhoto(source: String) = photoEditSession.select(source)
    fun setPhotoCrop(crop: PhotoCrop) = photoEditSession.setCrop(crop)
    fun rotatePhotoClockwise() = photoEditSession.rotateClockwise()
    fun resetPhotoEdit() = photoEditSession.reset()
    fun confirmPhotoEdit(keepOriginal: Boolean) = photoEditSession.confirm(keepOriginal)
    fun retryPhotoEdit() = photoEditSession.retry()
    fun cancelPhotoEdit() = photoEditSession.cancel()

    fun importPhoto(source: String) {
        if (photoEditSession.state.value is MemoryPhotoEditState.Idle) photoSession.importPhoto(source)
    }
    fun removePhoto(id: UUID) {
        if (photoEditSession.state.value is MemoryPhotoEditState.Idle) photoSession.remove(id)
    }
    fun retryPhoto() = photoSession.retry()
    fun hasPendingPhotoImport(): Boolean = saved.get<String>("photo_pending") != null
    fun enterPhotoRoute(): Long {
        val generation = ++nextPhotoRouteGeneration
        activePhotoRoute.value = generation
        if (state.value.ready) {
            if (openPhotoDraftAccess(generation)) viewModelScope.launch { photoEditSession.restore() }
        } else load()
        return generation
    }
    fun leavePhotoRoute(generation: Long) {
        if (activePhotoRoute.value == generation) {
            photoEditSession.onRouteClosed()
            photoViewerAccess.keys.toList().forEach(::closePhotoViewer)
            activePhotoRoute.value = null
            closePhotoDraftAccess()
        }
    }
    private fun openPhotoDraftAccess(generation: Long): Boolean {
        closePhotoDraftAccess()
        val registry = photoDraftAccessRegistry ?: return true
        if (activePhotoRoute.value != generation) return false
        return try {
            val handle = registry.open(photoDraftId, eventId)
            if (activePhotoRoute.value != generation) {
                handle.close()
                false
            } else {
                photoDraftAccess = handle
                true
            }
        } catch (_: Exception) {
            false
        }
    }
    private fun hasPhotoRouteAccess(): Boolean = activePhotoRoute.value != null &&
        (photoDraftAccessRegistry == null || photoDraftAccessRegistry.isRegistered(photoDraftId))

    private fun openChildPhotoAccess(leaseId: UUID): AutoCloseable? {
        if (!hasPhotoRouteAccess()) return null
        val registry = photoDraftAccessRegistry ?: return AutoCloseable {}
        return registry.open(leaseId, eventId, photoDraftId)
    }

    fun registerPhotoViewer(leaseId: UUID): Boolean {
        if (!state.value.ready || !hasPhotoRouteAccess()) return false
        val registered = photoDraftAccessRegistry?.isRegistered(leaseId) ?: (photoViewerAccess[leaseId] != null)
        if (photoViewerAccess[leaseId] != null && registered) return true
        photoViewerAccess.remove(leaseId)?.close()
        val handle = openChildPhotoAccess(leaseId) ?: return false
        photoViewerAccess[leaseId] = handle
        return true
    }

    suspend fun preparePhotoViewer(leaseId: UUID, photos: List<PhotoAttachment>): Boolean =
        photoViewerLeaseLock(leaseId).withLock {
            if (!registerPhotoViewer(leaseId)) return@withLock false
            try {
                val repository = photoRepository ?: return@withLock false
                repository.retainDraft(leaseId, photos)
                hasPhotoRouteAccess() && photos.all { repository.isRetainedBy(leaseId, it) } &&
                    (photoDraftAccessRegistry?.permits(leaseId, photos.firstOrNull() ?: return@withLock false) ?: true)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                false
            }
        }

    fun closePhotoViewer(leaseId: UUID) {
        photoViewerAccess.remove(leaseId)?.close()
        viewModelScope.launch {
            photoViewerLeaseLock(leaseId).withLock {
                // A restored composition may already have registered this same durable lease.
                if (photoViewerAccess.containsKey(leaseId)) return@withLock
                try { photoRepository?.releaseDraft(leaseId) } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                }
                try { photoRepository?.cleanup() } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                }
            }
        }
    }

    private fun photoViewerLeaseLock(leaseId: UUID): Mutex =
        photoViewerLeaseLocks.getOrPut(leaseId) { Mutex() }

    private suspend fun ensurePhotoDraftAccess(): Boolean {
        if (photoDraftAccessRegistry == null) return true
        while (true) {
            val generation = activePhotoRoute.filterNotNull().first()
            if (openPhotoDraftAccess(generation)) return true
            if (activePhotoRoute.value == generation) return false
        }
    }
    fun closePhotoDraftAccess() {
        photoDraftAccess?.close()
        photoDraftAccess = null
    }
    fun discardPhotos(onDone: () -> Unit) {
        if (!state.value.busy && !state.value.completed && photoEditSession.state.value is MemoryPhotoEditState.Idle) {
            photoSession.discard(onDone)
        }
    }

    init {
        viewModelScope.launch {
            repository.observeTags().collect { tags -> mutableState.value = state.value.copy(tags = tags) }
        }
        if (!state.value.completed) load()
    }

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
                check(ensurePhotoDraftAccess()) { "Memory photo access is unavailable" }
                baseline = record?.toDraft(direct) ?: MemoryDraft(initialTime)
                // Larger legacy content stays in the database rather than entering saved state.
                require(baseline.title.length <= MemoryTextLimits.TITLE && baseline.note.length <= MemoryTextLimits.NOTE &&
                    baseline.contextNote.length <= MemoryTextLimits.CONTEXT_NOTE && baseline.tagNames.size <= MemoryTextLimits.TAG_COUNT &&
                    baseline.personIds.size <= PersonTextLimits.SELECTION)
                editingId = record?.event?.id
                saved["direct"] = direct
                val draft = if (saved.get<Boolean>("has_draft") == true) readDraft() else baseline
                validatePhotos(draft.photos)
                photoSession.retain(draft.photos)
                persist(draft)
                mutableState.value = state.value.copy(draft = draft, original = record.takeUnless { direct }, direct = direct,
                    editing = record?.memory != null, loading = true, ready = false, hasChanges = draft != baseline, error = null)
                photoEditSession.restore()
                mutableState.value = state.value.copy(loading = false, ready = true)
                photoSession.resumePending()
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutableState.value = state.value.copy(loading = false, ready = false, error = MemoryEditorError.LOAD)
            }
        }
    }

    fun setTitle(title: String) {
        if (!state.value.direct) return
        if (title.length > MemoryTextLimits.TITLE) { rejectText(MemoryTextField.TITLE); return }
        change(clearTextField = MemoryTextField.TITLE) { it.copy(title = title) }
    }
    fun setNote(note: String) {
        val limit = if (state.value.direct) MemoryTextLimits.NOTE else MemoryTextLimits.CONTEXT_NOTE
        if (note.length > limit) { rejectText(MemoryTextField.NOTE); return }
        change(clearTextField = MemoryTextField.NOTE) { it.copy(note = note) }
    }
    fun setContextNote(note: String) {
        if (note.length > MemoryTextLimits.CONTEXT_NOTE) { rejectText(MemoryTextField.CONTEXT_NOTE); return }
        change(clearTextField = MemoryTextField.CONTEXT_NOTE) { it.copy(contextNote = note) }
    }
    fun addTag(name: String): Boolean {
        val trimmed = name.trim()
        val current = state.value
        if (!current.ready || current.busy || current.completed || trimmed.isBlank() ||
            trimmed.length > MemoryTextLimits.TAG ||
            (current.draft.tagNames.size >= MemoryTextLimits.TAG_COUNT &&
                current.draft.tagNames.none { Tag.normalize(it) == Tag.normalize(trimmed) })) return false
        val catalogName = current.tags.firstOrNull { Tag.normalize(it.name) == Tag.normalize(trimmed) }?.name ?: trimmed
        if (current.draft.tagNames.any { Tag.normalize(it) == Tag.normalize(catalogName) }) return true
        change { it.copy(tagNames = it.tagNames + catalogName) }
        return catalogName in state.value.draft.tagNames
    }
    fun removeTag(name: String) = change { it.copy(tagNames = it.tagNames.filterNot { tag -> Tag.normalize(tag) == Tag.normalize(name) }.toSet()) }
    fun setImportance(value: Int?) {
        require(value == null || value in 1..5)
        change { it.copy(importance = value) }
    }
    private fun rejectText(field: MemoryTextField) {
        if (state.value.ready && !state.value.busy && !state.value.completed &&
            photoEditSession.state.value is MemoryPhotoEditState.Idle) {
            val errors = state.value.textErrors + field
            persistTextErrors(errors)
            mutableState.value = state.value.copy(textErrors = errors, error = MemoryEditorError.TEXT)
        }
    }
    fun setLocalTime(local: LocalDateTime, zone: ZoneId): Boolean {
        if (!state.value.direct || !state.value.ready || state.value.busy || state.value.completed ||
            photoEditSession.state.value !is MemoryPhotoEditState.Idle) return false
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
        if (photoEditSession.state.value !is MemoryPhotoEditState.Idle || !current.ready || current.busy || current.completed ||
            (current.direct && current.draft.note.isBlank())) return
        val draft = current.draft
        saved["pending_remove"] = null
        write(MemoryEditorError.SAVE) {
            if (current.direct) {
                val input = MemoryInput(draft.note, Instant.ofEpochMilli(draft.occurredAtMillis), draft.title,
                    draft.importance, draft.personIds, PlaceSelection(draft.placeId), draft.photos, draft.tagNames,
                    draft.contextNote.trim().takeIf(String::isNotEmpty))
                val recoveredId = editingId ?: repository.get(captureId)?.event?.id
                if (recoveredId == null) repository.create(captureId, input) else {
                    editingId = recoveredId
                    repository.update(recoveredId, input)
                }
            } else repository.saveMarker(requireNotNull(editingId), MemoryMarkerInput(
                draft.importance, draft.note, draft.personIds, PlaceSelection(draft.placeId), draft.photos, draft.tagNames,
            ))
        }
    }
    fun remove() {
        val current = state.value
        if (photoEditSession.state.value !is MemoryPhotoEditState.Idle) return
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

    override fun onCleared() {
        photoEditSession.onRouteClosed()
        photoViewerAccess.keys.toList().forEach(::closePhotoViewer)
        closePhotoDraftAccess()
        super.onCleared()
    }
    private fun change(clearTextField: MemoryTextField? = null, transform: (MemoryDraft) -> MemoryDraft) {
        val current = state.value
        if (!current.ready || current.busy || current.completed || photoEditSession.state.value !is MemoryPhotoEditState.Idle) return
        val draft = transform(current.draft)
        if (draft == current.draft) return
        saved["pending_remove"] = null
        persist(draft)
        val errors = clearTextField?.let { current.textErrors - it } ?: current.textErrors
        persistTextErrors(errors)
        mutableState.value = current.copy(draft = draft, textErrors = errors,
            error = if (errors.isNotEmpty()) MemoryEditorError.TEXT else null, hasChanges = draft != baseline)
    }
    fun setPlace(id: UUID?) = change { it.copy(placeId = id) }

    fun togglePerson(id: UUID) = change {
        it.copy(personIds = if (id in it.personIds) it.personIds - id
            else if (it.personIds.size < PersonTextLimits.SELECTION) it.personIds + id else it.personIds)
    }

    /** Adds a created picker result without letting a repeated delivery deselect it. */
    fun addPerson(id: UUID): Boolean {
        val current = state.value
        if (!current.ready || current.busy || current.completed) return false
        if (id in current.draft.personIds) return true
        if (current.draft.personIds.size >= PersonTextLimits.SELECTION) return false
        change { it.copy(personIds = it.personIds + id) }
        return id in state.value.draft.personIds
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
        saved["tags"] = ArrayList(draft.tagNames)
        saved["context_note"] = draft.contextNote
    }
    private fun readTextErrors(): Set<MemoryTextField> = saved.get<ArrayList<String>>("text_error_fields")
        .orEmpty().mapNotNull { runCatching { MemoryTextField.valueOf(it) }.getOrNull() }.toSet()

    private fun persistTextErrors(errors: Set<MemoryTextField>) {
        saved["text_error_fields"] = ArrayList(errors.map { it.name }.sorted())
    }
    private fun readDraft() = MemoryDraft(saved.get<Long>("time") ?: initialTime, saved.get<String>("title") ?: "",
        saved.get<String>("note") ?: "", saved["importance"],
        saved.get<ArrayList<String>>("people")?.map(UUID::fromString)?.toSet() ?: emptySet(),
        saved.get<String>("place")?.let(UUID::fromString), saved.readPhotos(),
        saved.get<ArrayList<String>>("tags")?.toSet() ?: emptySet(), saved.get<String>("context_note") ?: "")
    private fun LifeEventRecord.toDraft(direct: Boolean) = MemoryDraft(event.occurredAt.toEpochMilli(),
        if (direct) event.title.orEmpty() else "",
        if (direct) (event.note ?: memory?.note).orEmpty() else memory?.note.orEmpty(), memory?.importance,
        people.map { it.id }.toSet(), place?.id, photos, tags.map(Tag::name).toSet(),
        if (direct) memory?.note?.takeUnless { it == event.note || event.note == null }.orEmpty() else "")
}
