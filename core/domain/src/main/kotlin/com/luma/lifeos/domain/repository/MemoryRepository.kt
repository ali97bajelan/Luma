package com.luma.lifeos.domain.repository

import com.luma.lifeos.domain.model.*
import com.luma.lifeos.domain.time.TimeRange
import kotlinx.coroutines.flow.Flow

interface MemoryRepository {
    /** Also returns unmarked events so eligible original records can be promoted. */
    suspend fun get(id: EventId): LifeEventRecord?
    fun observe(range: TimeRange): Flow<List<LifeEventRecord>>
    /** Direct capture; identical retries with the retained event ID are idempotent. */
    suspend fun create(id: EventId, input: MemoryInput): EventId
    suspend fun update(id: EventId, input: MemoryInput)
    /** Delete only a complete manual standalone memory; absent IDs are successful retries. */
    suspend fun delete(id: EventId)
    /** Add/edit only the marker of a complete manual mood or journal. */
    suspend fun saveMarker(id: EventId, input: MemoryMarkerInput)
    /** Preserve the original event. Standalone/imported/unsupported events cannot be unmarked. */
    suspend fun removeMarker(id: EventId)
}
