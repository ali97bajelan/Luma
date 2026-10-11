package com.luma.lifeos.domain.repository

import com.luma.lifeos.domain.model.Emotion
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.LifeEventRecord
import com.luma.lifeos.domain.model.MoodInput
import kotlinx.coroutines.flow.Flow

interface MoodRepository {
    suspend fun prepareEmotions()
    fun observeEmotions(): Flow<List<Emotion>>
    suspend fun get(id: EventId): LifeEventRecord?
    /** The caller retains this ID across retries; identical creates are idempotent. */
    suspend fun create(id: EventId, input: MoodInput): EventId
    /** Update/delete reject missing, incomplete, non-mood and imported records. */
    suspend fun update(id: EventId, input: MoodInput)
    suspend fun delete(id: EventId)
}
