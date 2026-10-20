package com.luma.lifeos.ui.mood

import androidx.lifecycle.SavedStateHandle
import com.luma.lifeos.domain.model.EventId
import java.util.UUID

data class MoodDraftCompletion(val eventId: EventId, val deleted: Boolean)

data class MoodDraftSnapshot(
    val draft: MoodDraft,
    val revision: Long,
    val pendingDeleteId: EventId? = null,
    val completion: MoodDraftCompletion? = null,
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
        return MoodDraftSnapshot(draft, saved.get<Long>(REVISION) ?: 0L,
            saved.get<String>(PENDING_DELETE_ID)?.let(EventId::parse),
            completionId?.let { MoodDraftCompletion(it, saved.get<Boolean>(DELETED) ?: false) })
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
    }

    override suspend fun remove(id: UUID) {
        if (saved.get<String>(DRAFT_ID) != id.toString()) return
        listOf(DRAFT_ID, REVISION, HAS_DRAFT, "dirty", TIME, SCORE, ENERGY, STRESS, NOTE,
            EMOTIONS, PEOPLE, PLACE, PENDING_DELETE_ID, COMPLETION_ID, DELETED).forEach { key -> saved.remove<Any>(key) }
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
    }
}
