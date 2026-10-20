package com.luma.lifeos.ui.mood

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luma.lifeos.domain.model.Emotion
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.LifeEventRecord
import com.luma.lifeos.domain.model.MoodInput
import com.luma.lifeos.domain.model.MoodNotePolicy
import com.luma.lifeos.domain.model.PlaceSelection
import com.luma.lifeos.domain.model.PersonTextLimits
import com.luma.lifeos.domain.repository.MoodRepository
import com.luma.lifeos.domain.time.moodInstant
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class MoodDraft(
    val occurredAtMillis: Long,
    val score: Int? = null,
    val emotionIds: Set<UUID> = emptySet(),
    val energy: Int? = null,
    val stress: Int? = null,
    val note: String = "",
    val personIds: Set<UUID> = emptySet(),
    val placeId: UUID? = null,
)

enum class MoodEditorError { LOAD, SAVE, DELETE, TIME, DRAFT_STORAGE }

data class MoodEditorState(
    val draft: MoodDraft,
    val emotions: List<Emotion> = emptyList(),
    val loading: Boolean = true,
    val ready: Boolean = false,
    val busy: Boolean = false,
    val completed: Boolean = false,
    val deleted: Boolean = false,
    val editing: Boolean = false,
    val hasChanges: Boolean = false,
    val error: MoodEditorError? = null,
    val noteLimitError: Boolean = false,
    val draftPersisting: Boolean = false,
    val draftDurable: Boolean = false,
    val draftStorageError: Boolean = false,
    val completionEventId: EventId? = null,
)

class MoodEditorViewModel(
    private val repository: MoodRepository,
    val eventId: EventId?,
    private val saved: SavedStateHandle,
    clock: Clock = Clock.systemUTC(),
    initialTimeMillis: Long? = null,
    private val draftStore: MoodDraftStore = SavedStateMoodDraftStore(saved),
) : ViewModel() {
    private val initialTime = saved.get<Long>(INITIAL_TIME) ?: (initialTimeMillis ?: clock.instant().toEpochMilli())
        .also { saved[INITIAL_TIME] = it }
    private val captureId = EventId.parse(saved.get<String>(CAPTURE_ID)
        ?: EventId.new().toString().also { saved[CAPTURE_ID] = it })
    private val draftId = saved.get<String>(DRAFT_ID)?.let { value ->
        runCatching { UUID.fromString(value) }.getOrNull()
    } ?: UUID.randomUUID().also { saved[DRAFT_ID] = it.toString() }
    private var baseline = MoodDraft(initialTime)
    private var editingId: EventId? = eventId
    private var loadingJob: Job? = null
    private var draftSnapshot: MoodDraftSnapshot? = null
    private var pendingCompletion: MoodDraftCompletion? = null
    private var draftRevision = 0L
    private val draftWriteMutex = Mutex()
    private var discardPending = false
    private val mutableState = MutableStateFlow(MoodEditorState(MoodDraft(initialTime), editing = eventId != null,
        hasChanges = saved.get<Boolean>(DIRTY) ?: (saved.get<Boolean>(HAS_DRAFT) == true)))
    val state: StateFlow<MoodEditorState> = mutableState.asStateFlow()

    init { load() }

    fun load() {
        if (state.value.busy || state.value.completed || loadingJob?.isActive == true) return
        mutableState.value = state.value.copy(loading = true, error = null)
        loadingJob = viewModelScope.launch {
            var storageFailure = true
            try {
                var snapshot = draftStore.read(draftId)
                draftSnapshot = snapshot
                draftRevision = snapshot?.revision ?: 0L
                if (snapshot?.completion != null) {
                    restoreCompletion(snapshot.completion, snapshot.draft)
                    return@launch
                }
                if (snapshot == null && saved.get<Boolean>(COMPLETED) == true) {
                    val completion = MoodDraftCompletion(eventId ?: captureId, saved.get<Boolean>(DELETED) ?: false)
                    val legacyDraft = readLegacyDraft()
                    mutableState.value = state.value.copy(draft = legacyDraft)
                    snapshot = MoodDraftSnapshot(legacyDraft, 1L, completion = completion)
                    writeSnapshot(snapshot)
                    draftSnapshot = snapshot
                    draftRevision = snapshot.revision
                    clearLegacyDraft()
                    restoreCompletion(completion, snapshot.draft)
                    return@launch
                }
                if (snapshot == null && saved.get<Boolean>(HAS_DRAFT) == true) {
                    val legacyDraft = readLegacyDraft()
                    mutableState.value = state.value.copy(draft = legacyDraft)
                    snapshot = MoodDraftSnapshot(legacyDraft, 1L)
                    writeSnapshot(snapshot)
                    draftSnapshot = snapshot
                    draftRevision = snapshot.revision
                    clearLegacyDraft()
                }
                storageFailure = false

                snapshot?.let { mutableState.value = state.value.copy(draft = it.draft) }
                val pendingDelete = snapshot?.pendingDeleteId
                val pendingDeleteRecord = pendingDelete?.let { repository.get(it) }
                if (pendingDelete != null && pendingDeleteRecord == null) {
                    persistCompletion(MoodDraftCompletion(pendingDelete, deleted = true))
                    return@launch
                }
                if (pendingDelete != null) editingId = pendingDelete

                repository.prepareEmotions()
                val emotions = repository.observeEmotions().first()
                val record = pendingDeleteRecord ?: repository.get(eventId ?: captureId)
                if (eventId != null && pendingDelete == null) requireNotNull(record) { "Check-in unavailable" }
                baseline = if (record == null) MoodDraft(initialTime) else {
                    require(record.event.isEditable && record.mood != null)
                    record.toDraft().also { require(it.personIds.size <= PersonTextLimits.SELECTION) }
                }
                editingId = record?.event?.id ?: editingId
                val draft = snapshot?.draft ?: baseline
                if (snapshot == null) {
                    val initialSnapshot = MoodDraftSnapshot(draft, 1L)
                    mutableState.value = state.value.copy(draft = draft)
                    storageFailure = true
                    writeSnapshot(initialSnapshot)
                    draftSnapshot = initialSnapshot
                    draftRevision = initialSnapshot.revision
                    storageFailure = false
                }
                mutableState.value = state.value.copy(draft = draft, emotions = emotions, loading = false,
                    ready = true, error = null, hasChanges = draft != baseline, draftPersisting = false,
                    draftDurable = true, draftStorageError = false, editing = editingId != null)
                saved[DIRTY] = draft != baseline
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutableState.value = state.value.copy(loading = false, ready = false,
                    error = if (storageFailure) MoodEditorError.DRAFT_STORAGE else MoodEditorError.LOAD,
                    draftStorageError = storageFailure)
            }
        }
    }

    fun setScore(score: Int) { require(score in 1..5); change { it.copy(score = score) } }
    fun setEnergy(energy: Int?) { require(energy == null || energy in 1..5); change { it.copy(energy = energy) } }
    fun setStress(stress: Int?) { require(stress == null || stress in 1..5); change { it.copy(stress = stress) } }

    fun setNote(note: String) {
        val current = state.value
        if (!current.ready || current.busy || current.completed || discardPending) return
        if (!MoodNotePolicy.canAcceptMoodNote(current.draft.note, note)) {
            mutableState.value = current.copy(noteLimitError = true)
            return
        }
        change { it.copy(note = note) }
    }

    fun toggleEmotion(id: UUID) {
        if (state.value.emotions.none { it.id == id }) return
        change { it.copy(emotionIds = if (id in it.emotionIds) it.emotionIds - id else it.emotionIds + id) }
    }

    fun setLocalTime(local: LocalDateTime, zone: ZoneId): Boolean {
        if (!state.value.ready || state.value.busy) return false
        return try {
            val instant = moodInstant(local, zone, Instant.ofEpochMilli(state.value.draft.occurredAtMillis))
            change { it.copy(occurredAtMillis = instant.toEpochMilli()) }
            true
        } catch (_: IllegalArgumentException) {
            mutableState.value = state.value.copy(error = MoodEditorError.TIME)
            false
        }
    }

    fun save() {
        val current = state.value
        if (!current.ready || current.busy || current.completed || current.draft.score == null) return
        if (!current.draftDurable) {
            mutableState.value = current.copy(error = MoodEditorError.DRAFT_STORAGE, draftStorageError = true)
            return
        }
        val draft = current.draft
        val input = MoodInput(requireNotNull(draft.score), Instant.ofEpochMilli(draft.occurredAtMillis),
            draft.emotionIds, draft.energy, draft.stress, draft.note, draft.personIds, PlaceSelection(draft.placeId))
        mutableState.value = current.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                val recoveredId = editingId ?: repository.get(captureId)?.event?.id
                val committedId = if (recoveredId == null) repository.create(captureId, input) else {
                    editingId = recoveredId
                    repository.update(recoveredId, input)
                    recoveredId
                }
                persistCompletion(MoodDraftCompletion(committedId, deleted = false))
            } catch (failure: Exception) {
                if (failure is CancellationException) {
                    mutableState.value = state.value.copy(busy = false)
                    throw failure
                }
                mutableState.value = state.value.copy(busy = false, error = MoodEditorError.SAVE)
            }
        }
    }

    fun delete() {
        val id = editingId ?: return
        val current = state.value
        if (!current.ready || current.busy || current.completed || !current.draftDurable) return
        mutableState.value = current.copy(busy = true, error = null)
        viewModelScope.launch {
            val marker = nextSnapshot(current.draft).copy(pendingDeleteId = id, completion = null)
            try {
                writeSnapshot(marker)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutableState.value = state.value.copy(busy = false, error = MoodEditorError.DRAFT_STORAGE,
                    draftStorageError = true)
                return@launch
            }
            try {
                repository.delete(id)
                persistCompletion(MoodDraftCompletion(id, deleted = true))
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                try {
                    if (repository.get(id) == null) {
                        persistCompletion(MoodDraftCompletion(id, deleted = true))
                    } else {
                        mutableState.value = state.value.copy(busy = false, error = MoodEditorError.DELETE)
                    }
                } catch (reconcileFailure: Exception) {
                    if (reconcileFailure is CancellationException) throw reconcileFailure
                    mutableState.value = state.value.copy(busy = false, error = MoodEditorError.DELETE)
                }
            }
        }
    }

    fun setPlace(id: UUID?) = change { it.copy(placeId = id) }

    fun togglePerson(id: UUID) = change {
        it.copy(personIds = if (id in it.personIds) it.personIds - id
            else if (it.personIds.size < PersonTextLimits.SELECTION) it.personIds + id else it.personIds)
    }

    private fun change(transform: (MoodDraft) -> MoodDraft) {
        val current = state.value
        if (!current.ready || current.busy || current.completed || discardPending) return
        val draft = transform(current.draft)
        if (draft == current.draft) {
            if (current.noteLimitError) mutableState.value = current.copy(noteLimitError = false)
            return
        }
        val revision = maxOf(draftRevision, draftSnapshot?.revision ?: 0L) + 1L
        draftRevision = revision
        val snapshot = nextSnapshot(draft, revision)
        mutableState.value = current.copy(draft = draft, error = null, noteLimitError = false,
            hasChanges = draft != baseline, draftPersisting = true, draftDurable = false,
            draftStorageError = false)
        viewModelScope.launch {
            try {
                writeSnapshot(snapshot)
                if (revision == draftRevision) mutableState.value = state.value.copy(
                    draftPersisting = false, draftDurable = true, draftStorageError = false, error = null)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                if (revision == draftRevision) mutableState.value = state.value.copy(
                    draftPersisting = false, draftDurable = false, draftStorageError = true,
                    error = MoodEditorError.DRAFT_STORAGE)
            }
        }
    }

    private fun nextSnapshot(draft: MoodDraft, revision: Long = maxOf(draftRevision, draftSnapshot?.revision ?: 0L) + 1L) =
        (draftSnapshot ?: MoodDraftSnapshot(draft, revision)).copy(
            draft = draft,
            revision = revision,
            completion = null,
        )

    private suspend fun writeSnapshot(snapshot: MoodDraftSnapshot) {
        draftWriteMutex.withLock {
            if (discardPending) throw IllegalStateException("Mood draft is being discarded")
            draftStore.write(draftId, snapshot)
            if (snapshot.revision >= (draftSnapshot?.revision ?: -1L)) {
                draftSnapshot = snapshot
                draftRevision = maxOf(draftRevision, snapshot.revision)
            }
        }
    }

    private suspend fun persistCompletion(completion: MoodDraftCompletion): Boolean {
        val revision = maxOf(draftRevision, draftSnapshot?.revision ?: 0L) + 1L
        draftRevision = revision
        pendingCompletion = completion
        val snapshot = nextSnapshot(state.value.draft, revision).copy(
            pendingDeleteId = null,
            completion = completion,
        )
        return try {
            writeSnapshot(snapshot)
            pendingCompletion = null
            saved[COMPLETED] = true
            saved[DELETED] = completion.deleted
            mutableState.value = state.value.copy(busy = false, completed = true,
                deleted = completion.deleted, completionEventId = completion.eventId,
                draftPersisting = false, draftDurable = true, draftStorageError = false, error = null)
            true
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            mutableState.value = state.value.copy(loading = false, busy = false, error = MoodEditorError.DRAFT_STORAGE,
                draftPersisting = false, draftStorageError = true)
            false
        }
    }

    suspend fun acknowledgeCompletion(): Boolean {
        if (!state.value.completed || state.value.completionEventId == null) return true
        return try {
            draftWriteMutex.withLock {
                draftStore.remove(draftId)
                draftSnapshot = null
                clearLegacyDraft()
                saved.remove<Boolean>(COMPLETED)
                saved.remove<Boolean>(DELETED)
            }
            true
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            mutableState.value = state.value.copy(error = MoodEditorError.DRAFT_STORAGE, draftStorageError = true)
            false
        }
    }

    suspend fun discardDraft(): Boolean {
        if (state.value.busy || state.value.completed) return false
        discardPending = true
        draftRevision += 1L
        loadingJob?.cancel()
        loadingJob = null
        mutableState.value = state.value.copy(draftPersisting = true, draftDurable = false,
            draftStorageError = false, error = null)
        return try {
            draftWriteMutex.withLock {
                draftStore.remove(draftId)
                draftSnapshot = null
                clearLegacyDraft()
            }
            discardPending = false
            true
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            mutableState.value = state.value.copy(draftPersisting = false, draftDurable = false,
                draftStorageError = true, error = MoodEditorError.DRAFT_STORAGE)
            false
        }
    }

    suspend fun retryDraftStorage(): Boolean {
        if (state.value.completed) return acknowledgeCompletion()
        if (discardPending) return discardDraft()
        pendingCompletion?.let { return persistCompletion(it) }
        val current = state.value
        if (!current.ready) {
            load()
            return false
        }
        val revision = maxOf(draftRevision, draftSnapshot?.revision ?: 0L) + 1L
        draftRevision = revision
        val snapshot = nextSnapshot(current.draft, revision)
        mutableState.value = current.copy(error = null, draftPersisting = true, draftDurable = false,
            draftStorageError = false)
        return try {
            writeSnapshot(snapshot)
            mutableState.value = state.value.copy(error = null, draftPersisting = false,
                draftDurable = true, draftStorageError = false)
            true
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            mutableState.value = state.value.copy(error = MoodEditorError.DRAFT_STORAGE,
                draftPersisting = false, draftDurable = false, draftStorageError = true)
            false
        }
    }

    private fun restoreCompletion(completion: MoodDraftCompletion, draft: MoodDraft = state.value.draft) {
        mutableState.value = state.value.copy(draft = draft, loading = false, ready = false, busy = false,
            completed = true, deleted = completion.deleted, completionEventId = completion.eventId,
            draftStorageError = false)
    }

    private fun readLegacyDraft(): MoodDraft = MoodDraft(
        saved.get<Long>(TIME) ?: initialTime, saved[SCORE],
        saved.get<ArrayList<String>>(EMOTIONS)?.map(UUID::fromString)?.toSet() ?: emptySet(),
        saved[ENERGY], saved[STRESS], saved.get<String>(NOTE) ?: "",
        saved.get<ArrayList<String>>(PEOPLE)?.map(UUID::fromString)?.toSet() ?: emptySet(),
        saved.get<String>(PLACE)?.let(UUID::fromString))

    private fun clearLegacyDraft() {
        listOf(HAS_DRAFT, DIRTY, TIME, SCORE, ENERGY, STRESS, NOTE, EMOTIONS, PEOPLE, PLACE)
            .forEach { saved.remove<Any>(it) }
    }

    private fun LifeEventRecord.toDraft(): MoodDraft {
        val details = requireNotNull(mood)
        return MoodDraft(event.occurredAt.toEpochMilli(), details.score, details.emotions.map { it.id }.toSet(),
            details.energy, details.stress, details.note.orEmpty(), people.map { it.id }.toSet(), place?.id)
    }

    private companion object {
        const val INITIAL_TIME = "initial_time"
        const val CAPTURE_ID = "capture_id"
        const val DRAFT_ID = "mood_draft_id"
        const val HAS_DRAFT = "has_draft"
        const val DIRTY = "dirty"
        const val TIME = "time"
        const val SCORE = "score"
        const val ENERGY = "energy"
        const val STRESS = "stress"
        const val NOTE = "note"
        const val EMOTIONS = "emotions"
        const val PEOPLE = "people"
        const val PLACE = "place"
        const val COMPLETED = "completed"
        const val DELETED = "deleted"
    }
}
