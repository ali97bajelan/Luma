package com.luma.lifeos.domain.repository

import com.luma.lifeos.domain.model.*
import com.luma.lifeos.domain.time.TimeRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface MemoryRepository {
    fun observeTags(): Flow<List<Tag>> = flowOf(emptyList())
    /** Also returns unmarked events so eligible original records can be promoted. */
    suspend fun get(id: EventId): LifeEventRecord?
    fun observe(range: TimeRange): Flow<List<LifeEventRecord>>
    /** Newest saved-memory records, bounded before the read and filtered to the requested sources. */
    fun observeLibrary(
        limit: Int,
        eventTypes: Set<EventType> = setOf(EventType.MEMORY, EventType.MOOD),
    ): Flow<List<LifeEventRecord>> {
        require(limit > 0) { "Library limit must be positive" }
        val supported = setOf(EventType.MEMORY, EventType.MOOD)
        require(eventTypes.all { it in supported }) { "Only memory and mood events can be listed as memories" }
        if (eventTypes.isEmpty()) return flowOf(emptyList())
        error("MemoryRepository implementations must provide a bounded library query")
    }
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
