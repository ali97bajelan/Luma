package com.luma.lifeos.domain.repository

import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.LifeEvent
import com.luma.lifeos.domain.model.SourceIdentity
import com.luma.lifeos.domain.time.TimeRange
import kotlinx.coroutines.flow.Flow

/**
 * Persistence boundary. Implementations own atomic writes and enforce unique source identities.
 * Observations emit oldest-first with LifeEvent.chronologicalOrder after committed changes.
 * IDs, creation timestamps, and upstream identities stay stable when updating a record.
 * Imported identities reconcile to an existing event ID on repeated import.
 */
interface LifeEventRepository {
    fun observe(range: TimeRange): Flow<List<LifeEvent>>
    suspend fun get(id: EventId): LifeEvent?
    suspend fun findBySource(identity: SourceIdentity): LifeEvent?
    suspend fun upsert(event: LifeEvent): EventId
    suspend fun delete(id: EventId)
}
