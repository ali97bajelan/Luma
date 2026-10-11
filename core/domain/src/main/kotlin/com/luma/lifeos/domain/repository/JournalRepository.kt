package com.luma.lifeos.domain.repository

import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.JournalInput
import com.luma.lifeos.domain.model.LifeEventRecord
import com.luma.lifeos.domain.model.Tag
import kotlinx.coroutines.flow.Flow

interface JournalRepository {
    fun observeTags(): Flow<List<Tag>>
    suspend fun get(id: EventId): LifeEventRecord?
    /** Retain the ID across retries. Identical creates are idempotent. */
    suspend fun create(id: EventId, input: JournalInput): EventId
    /** Changes require an existing complete manual journal. */
    suspend fun update(id: EventId, input: JournalInput)
    /** Repeating a committed delete is successful; existing protected records still reject deletion. */
    suspend fun delete(id: EventId)
}
