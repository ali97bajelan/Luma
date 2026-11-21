package com.luma.lifeos.ui.memory

import androidx.lifecycle.SavedStateHandle
import com.luma.lifeos.ui.photos.*
import com.luma.lifeos.domain.lock.LockSection
import com.luma.lifeos.domain.lock.ProtectedRecordClassifier
import com.luma.lifeos.domain.model.EditorWriteResult
import com.luma.lifeos.domain.model.EventEditorBaseline
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
enum class MemoryEditorError { LOAD, SAVE, REMOVE, TIME, TEXT, PHOTO, CONFLICT, SOURCE_MISSING, ACCESS_BLOCKED }
enum class MemoryTextField { TITLE, NOTE, CONTEXT_NOTE }
private data class MemorySavedBaseline(
    val draft: MemoryDraft,
    val token: EventEditorBaseline?,
    val direct: Boolean,
    val editing: Boolean,
)
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
    val hasRestoredDraft: Boolean = false,
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
    private var baselineToken: EventEditorBaseline? = null
    private var editingId: EventId? = eventId
    val resultEventId: EventId get() = editingId ?: eventId ?: captureId
    private var loadingJob: Job? = null
    private val mutableState = MutableStateFlow(MemoryEditorState(readDraft(), direct = saved["direct"] ?: true,
        completed = saved["completed"] ?: false, removed = saved["removed"] ?: false,
        hasChanges = saved.get<Boolean>("dirty") ?: (saved.get<Boolean>("has_draft") == true),
        textErrors = readTextErrors(),
        hasRestoredDraft = saved.get<Boolean>("dirty") == true))
    val state = mutableState.asStateFlow()

    private val photoSession = PhotoDraftSession(photoRepository, saved, viewModelScope, captureId.value,
        current = { state.value.draft.photos },
        canEdit = { state.value.ready && !state.value.busy && !state.value.completed && hasPhotoRouteAccess() },
        busy = { mutableState.value = state.value.copy(busy = it) },
        update = { photos ->
            if (photos != state.value.draft.photos) saved["pending_remove"] = null
            val draft = state.value.draft.copy(photos = photos)
            persist(draft)
            mutableState.value = state.value.copy(draft = draft, hasChanges = draft != baseline,
                error = state.value.error.takeIf { it == MemoryEditorError.CONFLICT || it == MemoryEditorError.SOURCE_MISSING })
        }, error = {
            mutableState.value = state.value.copy(error = state.value.error.takeIf {
                it == MemoryEditorError.CONFLICT || it == MemoryEditorError.SOURCE_MISSING
            } ?: MemoryEditorError.PHOTO)
        })

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
        onError = {
            mutableState.value = state.value.copy(error = state.value.error.takeIf {
                it == MemoryEditorError.CONFLICT || it == MemoryEditorError.SOURCE_MISSING
            } ?: MemoryEditorError.PHOTO)
        },
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
            val fallbackSections = runCatching { readBaseline()?.token?.requiredSections }.getOrNull()
            val handle = if (eventId != null && fallbackSections != null) {
                registry.open(photoDraftId, eventId, fallbackSections)
            } else registry.open(photoDraftId, eventId)
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
    fun discardDraft(onDone: () -> Unit) {
        if (state.value.busy || state.value.completed ||
            photoEditSession.state.value !is MemoryPhotoEditState.Idle) return
        photoEditSession.cancel()
        photoSession.discard {
            clearDraftState()
            baseline = MemoryDraft(initialTime)
            baselineToken = null
            editingId = eventId
            onDone()
        }
    }

    private fun clearDraftState() {
        listOf(
            "has_draft", "dirty", "time", "title", "note", "importance", "people", "place", "photos",
            "tags", "context_note", "direct", "completed", "removed", "pending_remove", "text_error_fields",
            "baseline_present", "baseline_direct", "baseline_editing", "baseline_has_token", "baseline_updated_at",
            "baseline_sections", "baseline_time", "baseline_title", "baseline_note", "baseline_importance",
            "baseline_people", "baseline_place", "baseline_photos", "baseline_tags", "baseline_context_note",
        ).forEach { saved.remove<Any>(it) }
    }

    fun discardPhotos(onDone: () -> Unit) = discardDraft(onDone)

    init {
        viewModelScope.launch {
            repository.observeTags().collect { tags -> mutableState.value = state.value.copy(tags = tags) }
        }
        if (!state.value.completed) load()
    }

    fun load() {
        if (state.value.busy || state.value.ready || state.value.completed || loadingJob?.isActive == true) return
        val hadDraft = saved.get<Boolean>("has_draft") == true
        val restoredDraft = hadDraft && saved.get<Boolean>("dirty") == true
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
                if (eventId == null && record != null) {
                    require(record.event.type == EventType.MEMORY) { "Capture ID is occupied" }
                }
                val sourceRecord = record.takeIf {
                    eventId != null || it?.event?.type == EventType.MEMORY
                }
                val savedBaseline = readBaseline()
                if (eventId != null && sourceRecord == null && savedBaseline == null && !hadDraft) {
                    throw IllegalArgumentException("Original event unavailable")
                }
                val direct = if (eventId == null) true else sourceRecord?.let {
                    require(it.event.isEditable || it.canPromoteToMemory)
                    it.event.type == EventType.MEMORY
                } ?: savedBaseline?.direct ?: (saved["direct"] ?: true)
                sourceRecord?.let {
                    require(if (direct) it.event.isEditable && it.memory != null else it.canPromoteToMemory)
                }
                check(ensurePhotoDraftAccess()) { "Memory photo access is unavailable" }

                val loadedBaseline = sourceRecord?.toDraft(direct) ?: savedBaseline?.draft
                    ?: if (eventId != null && hadDraft) readDraft() else MemoryDraft(initialTime)
                // Larger legacy content stays in the database rather than entering saved state.
                require(loadedBaseline.title.length <= MemoryTextLimits.TITLE &&
                    loadedBaseline.note.length <= MemoryTextLimits.NOTE &&
                    loadedBaseline.contextNote.length <= MemoryTextLimits.CONTEXT_NOTE &&
                    loadedBaseline.tagNames.size <= MemoryTextLimits.TAG_COUNT &&
                    loadedBaseline.personIds.size <= PersonTextLimits.SELECTION)
                baseline = savedBaseline?.draft ?: loadedBaseline
                baselineToken = savedBaseline?.token ?: sourceRecord?.toEditorBaseline()
                editingId = eventId ?: sourceRecord?.event?.id
                    ?: captureId.takeIf { savedBaseline?.editing == true }
                saved["direct"] = direct
                val draft = if (hadDraft) readDraft() else baseline
                val sourceMissing = eventId != null && sourceRecord == null
                if (sourceMissing &&
                    !canReadBaseline(baselineToken ?: EventEditorBaseline(0L, emptySet()))
                ) {
                    mutableState.value = state.value.copy(
                        draft = draft,
                        original = null,
                        direct = direct,
                        editing = savedBaseline?.editing == true,
                        loading = false,
                        ready = false,
                        hasChanges = draft != baseline,
                        hasRestoredDraft = restoredDraft,
                        error = MemoryEditorError.ACCESS_BLOCKED,
                    )
                    return@launch
                }
                validatePhotos(draft.photos)
                photoSession.retain(draft.photos)
                if (savedBaseline == null || (sourceRecord != null && savedBaseline.token == null)) {
                    persistBaseline(baseline, baselineToken, direct)
                }
                persist(draft)
                val stale = sourceRecord != null && savedBaseline?.token != null &&
                    savedBaseline.token.updatedAtMillis != sourceRecord.event.updatedAt.toEpochMilli()
                mutableState.value = state.value.copy(
                    draft = draft,
                    original = sourceRecord.takeUnless { direct },
                    direct = direct,
                    editing = sourceRecord?.memory != null || (sourceRecord == null && savedBaseline?.editing == true),
                    loading = true,
                    ready = false,
                    hasChanges = draft != baseline,
                    error = when {
                        sourceMissing -> MemoryEditorError.SOURCE_MISSING
                        stale -> MemoryEditorError.CONFLICT
                        else -> null
                    },
                    hasRestoredDraft = restoredDraft,
                )
                photoEditSession.restore()
                mutableState.value = state.value.copy(loading = false, ready = true)
                photoSession.resumePending()
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                val savedToken = runCatching { readBaseline()?.token }.getOrNull()
                val accessBlocked = eventId != null &&
                    !canReadBaseline(savedToken ?: EventEditorBaseline(0L, emptySet()))
                mutableState.value = state.value.copy(
                    loading = false,
                    ready = false,
                    error = if (accessBlocked) MemoryEditorError.ACCESS_BLOCKED else MemoryEditorError.LOAD,
                )
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
            mutableState.value = state.value.copy(textErrors = errors,
                error = state.value.error.takeIf {
                    it == MemoryEditorError.CONFLICT || it == MemoryEditorError.SOURCE_MISSING
                } ?: MemoryEditorError.TEXT)
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
            mutableState.value = state.value.copy(error = state.value.error.takeIf {
                it == MemoryEditorError.CONFLICT || it == MemoryEditorError.SOURCE_MISSING
            } ?: MemoryEditorError.TIME)
            false
        }
    }
    fun reloadLatest() {
        val current = state.value
        val id = eventId ?: return
        if (!current.ready || current.busy ||
            current.error !in setOf(MemoryEditorError.CONFLICT, MemoryEditorError.SOURCE_MISSING)) return
        mutableState.value = current.copy(busy = true)
        viewModelScope.launch {
            try {
                val record = repository.get(id)
                if (record == null) {
                    mutableState.value = state.value.copy(busy = false, error = MemoryEditorError.SOURCE_MISSING)
                    return@launch
                }
                val direct = record.event.type == EventType.MEMORY
                require(if (direct) record.event.isEditable && record.memory != null else record.canPromoteToMemory)
                val latest = record.toDraft(direct)
                validatePhotos(latest.photos)
                require(latest.title.length <= MemoryTextLimits.TITLE &&
                    latest.note.length <= (if (direct) MemoryTextLimits.NOTE else MemoryTextLimits.CONTEXT_NOTE) &&
                    latest.contextNote.length <= MemoryTextLimits.CONTEXT_NOTE &&
                    latest.tagNames.size <= MemoryTextLimits.TAG_COUNT &&
                    latest.personIds.size <= PersonTextLimits.SELECTION)
                photoSession.retain(latest.photos)
                val token = record.toEditorBaseline()
                baseline = latest
                baselineToken = token
                editingId = id
                saved["direct"] = direct
                saved["pending_remove"] = null
                persistBaseline(latest, token, direct)
                persist(latest)
                mutableState.value = state.value.copy(
                    draft = latest,
                    original = record.takeUnless { direct },
                    direct = direct,
                    editing = record.memory != null,
                    busy = false,
                    hasChanges = false,
                    hasRestoredDraft = false,
                    error = null,
                )
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutableState.value = state.value.copy(busy = false,
                    error = state.value.error.takeIf {
                        it == MemoryEditorError.CONFLICT || it == MemoryEditorError.SOURCE_MISSING
                    } ?: MemoryEditorError.PHOTO)
            }
        }
    }

    fun save() {
        val current = state.value
        if (photoEditSession.state.value !is MemoryPhotoEditState.Idle || !current.ready || current.busy ||
            current.completed || current.error in setOf(MemoryEditorError.CONFLICT, MemoryEditorError.SOURCE_MISSING) ||
            (current.direct && current.draft.note.isBlank())) return
        val draft = current.draft
        val id = editingId
        val baseline = if (id == null) null else baselineToken ?: run {
            mutableState.value = current.copy(error = MemoryEditorError.SOURCE_MISSING)
            return
        }
        if (!current.direct && id == null) {
            mutableState.value = current.copy(error = MemoryEditorError.SOURCE_MISSING)
            return
        }
        saved["pending_remove"] = null
        mutableState.value = current.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                val result = if (current.direct) {
                    val input = MemoryInput(draft.note, Instant.ofEpochMilli(draft.occurredAtMillis), draft.title,
                        draft.importance, draft.personIds, PlaceSelection(draft.placeId), draft.photos, draft.tagNames,
                        draft.contextNote.trim().takeIf(String::isNotEmpty))
                    if (id == null) {
                        repository.create(captureId, input)
                        EditorWriteResult.SAVED
                    } else repository.updateIfUnchanged(id, requireNotNull(baseline), input)
                } else repository.saveMarkerIfUnchanged(requireNotNull(id), requireNotNull(baseline), MemoryMarkerInput(
                    draft.importance, draft.note, draft.personIds, PlaceSelection(draft.placeId), draft.photos, draft.tagNames,
                ))
                when (result) {
                    EditorWriteResult.SAVED -> {
                        photoSession.releaseAfterWrite()
                        finish(removed = false)
                    }
                    EditorWriteResult.STALE ->
                        mutableState.value = state.value.copy(busy = false, error = MemoryEditorError.CONFLICT)
                    EditorWriteResult.MISSING ->
                        mutableState.value = state.value.copy(busy = false, error = MemoryEditorError.SOURCE_MISSING)
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) {
                    mutableState.value = state.value.copy(busy = false)
                    throw failure
                }
                mutableState.value = state.value.copy(busy = false, error = MemoryEditorError.SAVE)
            }
        }
    }
    fun remove() {
        val current = state.value
        if (photoEditSession.state.value !is MemoryPhotoEditState.Idle) return
        val id = editingId ?: return
        if (!current.ready || !current.editing || current.busy || current.completed ||
            current.error in setOf(MemoryEditorError.CONFLICT, MemoryEditorError.SOURCE_MISSING)) return
        saved["pending_remove"] = if (current.direct) "delete" else "unmark"
        writeRemove {
            if (current.direct) repository.delete(id) else repository.removeMarker(id)
        }
    }
    private fun writeRemove(action: suspend () -> Unit) {
        mutableState.value = state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                action()
                photoSession.releaseAfterWrite()
                finish(removed = true)
            } catch (failure: Exception) {
                mutableState.value = state.value.copy(busy = false)
                if (failure is CancellationException) throw failure
                mutableState.value = state.value.copy(error = MemoryEditorError.REMOVE)
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
            error = current.error.takeIf { it == MemoryEditorError.CONFLICT || it == MemoryEditorError.SOURCE_MISSING }
                ?: if (errors.isNotEmpty()) MemoryEditorError.TEXT else null,
            hasChanges = draft != baseline)
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
        writeDraftFields(draft, "")
        saved["has_draft"] = true
        saved["dirty"] = draft != baseline
    }

    private fun persistBaseline(draft: MemoryDraft, token: EventEditorBaseline?, direct: Boolean) {
        writeDraftFields(draft, "baseline_")
        saved["baseline_present"] = true
        saved["baseline_direct"] = direct
        saved["baseline_editing"] = editingId != null
        saved["baseline_has_token"] = token != null
        saved["baseline_updated_at"] = token?.updatedAtMillis
        saved["baseline_sections"] = ArrayList(token?.requiredSections.orEmpty().map { it.name }.sorted())
    }

    private fun writeDraftFields(draft: MemoryDraft, prefix: String) {
        saved.savePhotos(draft.photos, prefix + "photos")
        saved[prefix + "place"] = draft.placeId?.toString()
        saved[prefix + "people"] = ArrayList(draft.personIds.map(UUID::toString))
        saved[prefix + "time"] = draft.occurredAtMillis
        saved[prefix + "title"] = draft.title
        saved[prefix + "note"] = draft.note
        saved[prefix + "importance"] = draft.importance
        saved[prefix + "tags"] = ArrayList(draft.tagNames)
        saved[prefix + "context_note"] = draft.contextNote
    }

    private fun readBaseline(): MemorySavedBaseline? {
        if (saved.get<Boolean>("baseline_present") != true) return null
        val token = if (saved.get<Boolean>("baseline_has_token") == true) {
            val updatedAt = saved.get<Long>("baseline_updated_at") ?: return null
            val sections = saved.get<ArrayList<String>>("baseline_sections").orEmpty()
                .mapNotNull { runCatching { LockSection.valueOf(it) }.getOrNull() }.toSet()
            EventEditorBaseline(updatedAt, sections)
        } else null
        return MemorySavedBaseline(
            draft = readDraft("baseline_"),
            token = token,
            direct = saved.get<Boolean>("baseline_direct") ?: true,
            editing = saved.get<Boolean>("baseline_editing") ?: (eventId != null),
        )
    }

    private fun readTextErrors(): Set<MemoryTextField> = saved.get<ArrayList<String>>("text_error_fields")
        .orEmpty().mapNotNull { runCatching { MemoryTextField.valueOf(it) }.getOrNull() }.toSet()

    private fun persistTextErrors(errors: Set<MemoryTextField>) {
        saved["text_error_fields"] = ArrayList(errors.map { it.name }.sorted())
    }

    private fun readDraft(prefix: String = "") = MemoryDraft(
        saved.get<Long>(prefix + "time") ?: initialTime,
        saved.get<String>(prefix + "title") ?: "",
        saved.get<String>(prefix + "note") ?: "",
        saved[prefix + "importance"],
        saved.get<ArrayList<String>>(prefix + "people")?.map(UUID::fromString)?.toSet() ?: emptySet(),
        saved.get<String>(prefix + "place")?.let(UUID::fromString),
        saved.readPhotos(prefix + "photos"),
        saved.get<ArrayList<String>>(prefix + "tags")?.toSet() ?: emptySet(),
        saved.get<String>(prefix + "context_note") ?: "",
    )

    private suspend fun canReadBaseline(baseline: EventEditorBaseline): Boolean = try {
        repository.canReadEditorBaseline(baseline)
    } catch (failure: Exception) {
        if (failure is CancellationException) throw failure
        false
    }

    private fun LifeEventRecord.toEditorBaseline() = EventEditorBaseline(
        updatedAtMillis = event.updatedAt.toEpochMilli(),
        requiredSections = ProtectedRecordClassifier.requiredSections(this),
    )

    private fun LifeEventRecord.toDraft(direct: Boolean) = MemoryDraft(event.occurredAt.toEpochMilli(),
        if (direct) event.title.orEmpty() else "",
        if (direct) (event.note ?: memory?.note).orEmpty() else memory?.note.orEmpty(), memory?.importance,
        people.map { it.id }.toSet(), place?.id, photos, tags.map(Tag::name).toSet(),
        if (direct) memory?.note?.takeUnless { it == event.note || event.note == null }.orEmpty() else "")
}
