package com.luma.lifeos.ui.mood

import androidx.lifecycle.SavedStateHandle
import com.luma.lifeos.domain.lock.LockSection
import com.luma.lifeos.domain.model.EventEditorBaseline
import com.luma.lifeos.domain.model.EventId
import java.util.UUID

data class MoodDraftCompletion(val eventId: EventId, val deleted: Boolean)

data class MoodDraftSnapshot(
    val draft: MoodDraft,
    val revision: Long,
    val pendingDeleteId: EventId? = null,
    val completion: MoodDraftCompletion? = null,
    val baselineDraft: MoodDraft? = null,
    val baseline: EventEditorBaseline? = null,
)

interface MoodDraftStore {
    suspend fun read(id: UUID): MoodDraftSnapshot?
    suspend fun write(id: UUID, snapshot: MoodDraftSnapshot)
    suspend fun remove(id: UUID)
}

/** Keeps direct ViewModel callers source-compatible; app navigation injects FileMoodDraftStore. */
internal class SavedStateMoodDraftStore(private val saved: SavedStateHandle) : MoodDraftStore {
    override suspend fun read(id: UUID): MoodDraftSnapshot? {
        if (saved.get<String>(DRAFT_ID) != id.toString() || saved.get<Boolean>(HAS_DRAFT) != true) return null
        val draft = MoodDraft(
            saved.get<Long>(TIME) ?: saved.get<Long>(INITIAL_TIME) ?: 0L,
            saved[SCORE],
            saved.get<ArrayList<String>>(EMOTIONS)?.map(UUID::fromString)?.toSet() ?: emptySet(),
            saved[ENERGY], saved[STRESS], saved.get<String>(NOTE) ?: "",
            saved.get<ArrayList<String>>(PEOPLE)?.map(UUID::fromString)?.toSet() ?: emptySet(),
            saved.get<String>(PLACE)?.let(UUID::fromString),
        )
        val completionId = saved.get<String>(COMPLETION_ID)?.let(EventId::parse)
        val baselineDraft = if (saved.get<Boolean>(BASELINE_PRESENT) == true) MoodDraft(
            saved.get<Long>(BASELINE_TIME) ?: saved.get<Long>(INITIAL_TIME) ?: 0L,
            saved[BASELINE_SCORE],
            saved.get<ArrayList<String>>(BASELINE_EMOTIONS)?.map(UUID::fromString)?.toSet() ?: emptySet(),
            saved[BASELINE_ENERGY], saved[BASELINE_STRESS], saved.get<String>(BASELINE_NOTE) ?: "",
            saved.get<ArrayList<String>>(BASELINE_PEOPLE)?.map(UUID::fromString)?.toSet() ?: emptySet(),
            saved.get<String>(BASELINE_PLACE)?.let(UUID::fromString),
        ) else null
        val baseline = saved.get<Long>(BASELINE_UPDATED_AT)?.let { updatedAt ->
            EventEditorBaseline(updatedAt, saved.get<ArrayList<String>>(BASELINE_SECTIONS).orEmpty()
                .mapNotNull { runCatching { LockSection.valueOf(it) }.getOrNull() }.toSet())
        }
        return MoodDraftSnapshot(draft, saved.get<Long>(REVISION) ?: 0L,
            saved.get<String>(PENDING_DELETE_ID)?.let(EventId::parse),
            completionId?.let { MoodDraftCompletion(it, saved.get<Boolean>(DELETED) ?: false) },
            baselineDraft, baseline)
    }

    override suspend fun write(id: UUID, snapshot: MoodDraftSnapshot) {
        saved[DRAFT_ID] = id.toString()
        saved[REVISION] = snapshot.revision
        saved[HAS_DRAFT] = true
        saved["dirty"] = true
        saved[TIME] = snapshot.draft.occurredAtMillis
        saved[SCORE] = snapshot.draft.score
        saved[ENERGY] = snapshot.draft.energy
        saved[STRESS] = snapshot.draft.stress
        saved[NOTE] = snapshot.draft.note
        saved[EMOTIONS] = ArrayList(snapshot.draft.emotionIds.map(UUID::toString))
        saved[PEOPLE] = ArrayList(snapshot.draft.personIds.map(UUID::toString))
        saved[PLACE] = snapshot.draft.placeId?.toString()
        saved[PENDING_DELETE_ID] = snapshot.pendingDeleteId?.toString()
        saved[COMPLETION_ID] = snapshot.completion?.eventId?.toString()
        saved[DELETED] = snapshot.completion?.deleted ?: false
        saved[BASELINE_PRESENT] = snapshot.baselineDraft != null
        snapshot.baselineDraft?.let { baseline ->
            saved[BASELINE_TIME] = baseline.occurredAtMillis
            saved[BASELINE_SCORE] = baseline.score
            saved[BASELINE_ENERGY] = baseline.energy
            saved[BASELINE_STRESS] = baseline.stress
            saved[BASELINE_NOTE] = baseline.note
            saved[BASELINE_EMOTIONS] = ArrayList(baseline.emotionIds.map(UUID::toString))
            saved[BASELINE_PEOPLE] = ArrayList(baseline.personIds.map(UUID::toString))
            saved[BASELINE_PLACE] = baseline.placeId?.toString()
        }
        saved[BASELINE_UPDATED_AT] = snapshot.baseline?.updatedAtMillis
        saved[BASELINE_SECTIONS] = ArrayList(snapshot.baseline?.requiredSections.orEmpty().map { it.name }.sorted())
    }

    override suspend fun remove(id: UUID) {
        if (saved.get<String>(DRAFT_ID) != id.toString()) return
        listOf(DRAFT_ID, REVISION, HAS_DRAFT, "dirty", TIME, SCORE, ENERGY, STRESS, NOTE,
            EMOTIONS, PEOPLE, PLACE, PENDING_DELETE_ID, COMPLETION_ID, DELETED,
            BASELINE_PRESENT, BASELINE_TIME, BASELINE_SCORE, BASELINE_ENERGY, BASELINE_STRESS,
            BASELINE_NOTE, BASELINE_EMOTIONS, BASELINE_PEOPLE, BASELINE_PLACE, BASELINE_UPDATED_AT,
            BASELINE_SECTIONS).forEach { key -> saved.remove<Any>(key) }
    }

    private companion object {
        const val DRAFT_ID = "mood_draft_id"
        const val REVISION = "mood_draft_revision"
        const val HAS_DRAFT = "has_draft"
        const val INITIAL_TIME = "initial_time"
        const val TIME = "time"
        const val SCORE = "score"
        const val ENERGY = "energy"
        const val STRESS = "stress"
        const val NOTE = "note"
        const val EMOTIONS = "emotions"
        const val PEOPLE = "people"
        const val PLACE = "place"
        const val PENDING_DELETE_ID = "mood_pending_delete_id"
        const val COMPLETION_ID = "mood_completion_id"
        const val DELETED = "deleted"
        const val BASELINE_PRESENT = "mood_baseline_present"
        const val BASELINE_TIME = "mood_baseline_time"
        const val BASELINE_SCORE = "mood_baseline_score"
        const val BASELINE_ENERGY = "mood_baseline_energy"
        const val BASELINE_STRESS = "mood_baseline_stress"
        const val BASELINE_NOTE = "mood_baseline_note"
        const val BASELINE_EMOTIONS = "mood_baseline_emotions"
        const val BASELINE_PEOPLE = "mood_baseline_people"
        const val BASELINE_PLACE = "mood_baseline_place"
        const val BASELINE_UPDATED_AT = "mood_baseline_updated_at"
        const val BASELINE_SECTIONS = "mood_baseline_sections"
    }
}
