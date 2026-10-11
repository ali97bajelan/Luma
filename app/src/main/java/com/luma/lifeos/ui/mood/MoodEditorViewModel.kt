package com.luma.lifeos.ui.mood

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luma.lifeos.domain.model.Emotion
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.LifeEventRecord
import com.luma.lifeos.domain.model.MoodInput
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

data class MoodDraft(
    val occurredAtMillis: Long,
    val score: Int? = null,
    val emotionIds: Set<UUID> = emptySet(),
    val energy: Int? = null,
    val stress: Int? = null,
    val note: String = "",
)

enum class MoodEditorError { LOAD, SAVE, DELETE, TIME }

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
)

class MoodEditorViewModel(
    private val repository: MoodRepository,
    val eventId: EventId?,
    private val saved: SavedStateHandle,
    clock: Clock = Clock.systemUTC(),
) : ViewModel() {
    private val initialTime = saved.get<Long>("initial_time") ?: clock.instant().toEpochMilli().also { saved["initial_time"] = it }
    private val captureId = EventId.parse(saved.get<String>("capture_id") ?: EventId.new().toString().also { saved["capture_id"] = it })
    private var baseline = MoodDraft(initialTime)
    private var editingId: EventId? = eventId
    private var loadingJob: Job? = null
    private val mutableState = MutableStateFlow(MoodEditorState(readDraft(), completed = saved["completed"] ?: false,
        deleted = saved["deleted"] ?: false, editing = eventId != null,
        hasChanges = saved.get<Boolean>("dirty") ?: (saved.get<Boolean>("has_draft") == true)))
    val state: StateFlow<MoodEditorState> = mutableState.asStateFlow()

    init { if (!state.value.completed) load() }

    fun load() {
        if (state.value.busy || state.value.ready || state.value.completed || loadingJob?.isActive == true) return
        mutableState.value = state.value.copy(loading = true, error = null)
        loadingJob = viewModelScope.launch {
            try {
                repository.prepareEmotions()
                val emotions = repository.observeEmotions().first()
                // A create may have committed before process death, without saving its completion flag.
                val record = repository.get(eventId ?: captureId)
                if (eventId != null) requireNotNull(record) { "Check-in unavailable" }
                baseline = if (record == null) MoodDraft(initialTime) else {
                    require(record.event.isEditable && record.mood != null)
                    record.toDraft()
                }
                editingId = record?.event?.id
                val draft = if (saved.get<Boolean>("has_draft") == true) readDraft() else baseline
                persist(draft)
                mutableState.value = state.value.copy(draft = draft, emotions = emotions, loading = false,
                    ready = true, error = null, hasChanges = draft != baseline, editing = editingId != null)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutableState.value = state.value.copy(loading = false, ready = false, error = MoodEditorError.LOAD)
            }
        }
    }

    fun setScore(score: Int) { require(score in 1..5); change { it.copy(score = score) } }
    fun setEnergy(energy: Int?) { require(energy == null || energy in 1..5); change { it.copy(energy = energy) } }
    fun setStress(stress: Int?) { require(stress == null || stress in 1..5); change { it.copy(stress = stress) } }
    fun setNote(note: String) = change { it.copy(note = note) }
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
        val draft = current.draft
        val input = MoodInput(requireNotNull(draft.score), Instant.ofEpochMilli(draft.occurredAtMillis),
            draft.emotionIds, draft.energy, draft.stress, draft.note)
        write(MoodEditorError.SAVE) {
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
        write(MoodEditorError.DELETE) { repository.delete(id) }
    }

    private fun write(error: MoodEditorError, action: suspend () -> Unit) {
        mutableState.value = state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                action()
                saved["completed"] = true
                saved["deleted"] = error == MoodEditorError.DELETE
                mutableState.value = state.value.copy(busy = false, completed = true, deleted = error == MoodEditorError.DELETE)
            } catch (failure: Exception) {
                if (failure is CancellationException) {
                    mutableState.value = state.value.copy(busy = false)
                    throw failure
                }
                mutableState.value = state.value.copy(busy = false, error = error)
            }
        }
    }

    private fun change(transform: (MoodDraft) -> MoodDraft) {
        if (!state.value.ready || state.value.busy || state.value.completed) return
        val draft = transform(state.value.draft)
        persist(draft)
        mutableState.value = state.value.copy(draft = draft, error = null, hasChanges = draft != baseline)
    }

    private fun persist(draft: MoodDraft) {
        saved["has_draft"] = true
        saved["dirty"] = draft != baseline
        saved["time"] = draft.occurredAtMillis
        saved["score"] = draft.score
        saved["energy"] = draft.energy
        saved["stress"] = draft.stress
        saved["note"] = draft.note
        saved["emotions"] = ArrayList(draft.emotionIds.map(UUID::toString))
    }

    private fun readDraft(): MoodDraft = MoodDraft(
        saved.get<Long>("time") ?: initialTime, saved["score"],
        saved.get<ArrayList<String>>("emotions")?.map(UUID::fromString)?.toSet() ?: emptySet(),
        saved["energy"], saved["stress"], saved.get<String>("note") ?: "")

    private fun LifeEventRecord.toDraft(): MoodDraft {
        val details = requireNotNull(mood)
        return MoodDraft(event.occurredAt.toEpochMilli(), details.score, details.emotions.map { it.id }.toSet(),
            details.energy, details.stress, details.note.orEmpty())
    }
}
