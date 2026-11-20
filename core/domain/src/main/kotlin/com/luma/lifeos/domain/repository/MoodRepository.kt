package com.luma.lifeos.domain.repository

import com.luma.lifeos.domain.model.Emotion
import com.luma.lifeos.domain.model.EditorWriteResult
import com.luma.lifeos.domain.model.EventEditorBaseline
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.LifeEventRecord
import com.luma.lifeos.domain.model.MoodInput
import kotlinx.coroutines.flow.Flow

interface MoodRepository {
    suspend fun prepareEmotions()
    fun observeEmotions(): Flow<List<Emotion>>
    suspend fun get(id: EventId): LifeEventRecord?
    /** Confirms a recovered edit remains within its original protected sections if its source vanished. */
    suspend fun canReadEditorBaseline(baseline: EventEditorBaseline): Boolean = true
    /** The caller retains this ID across retries; identical creates are idempotent. */
    suspend fun create(id: EventId, input: MoodInput): EventId
    /** Update/delete reject missing, incomplete, non-mood and imported records. */
    suspend fun update(id: EventId, input: MoodInput)
    /** Compare the original editor revision and apply the update atomically when it still matches. */
    suspend fun updateIfUnchanged(
        id: EventId,
        baseline: EventEditorBaseline,
        input: MoodInput,
    ): EditorWriteResult {
        val current = get(id) ?: return EditorWriteResult.MISSING
        if (current.event.updatedAt.toEpochMilli() != baseline.updatedAtMillis) return EditorWriteResult.STALE
        update(id, input)
        return EditorWriteResult.SAVED
    }
    suspend fun delete(id: EventId)
}
