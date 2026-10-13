package com.luma.lifeos.domain.repository

import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.LifeEventRecord
import com.luma.lifeos.domain.model.TimelinePage
import com.luma.lifeos.domain.time.TimeRange
import kotlinx.coroutines.flow.Flow

interface TimelineRepository {
    /** First emission confirms observation is registered; subsequent emissions invalidate loaded pages. */
    fun observeChanges(): Flow<Unit>
    /** Indexed oldest-first page, ordered by occurrence and ID; 1..300 rows, non-negative offset. */
    suspend fun page(range: TimeRange, offset: Int, limit: Int, showAppUsage: Boolean = true): TimelinePage
    suspend fun get(id: EventId): LifeEventRecord?
}
