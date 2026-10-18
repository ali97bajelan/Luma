package com.luma.lifeos.domain.repository

import com.luma.lifeos.domain.habit.TimelineFeedItem
import com.luma.lifeos.domain.habit.TimelineFeedPage
import com.luma.lifeos.domain.habit.legacyTimelineFeedRange
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.LifeEventRecord
import com.luma.lifeos.domain.model.TimelinePage
import com.luma.lifeos.domain.time.TimeRange
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

interface TimelineRepository {
    /** First emission confirms observation is registered; subsequent emissions invalidate loaded pages. */
    fun observeChanges(): Flow<Unit>
    /** Indexed newest-first page, ordered by occurrence and ID; 1..300 rows, non-negative offset. */
    suspend fun page(range: TimeRange, offset: Int, limit: Int, showAppUsage: Boolean = true): TimelinePage
    suspend fun get(id: EventId): LifeEventRecord?

    /** Mixed entry and habit history page. Legacy repositories can expose their existing rows by default. */
    suspend fun feed(date: LocalDate, zone: ZoneId, offset: Int, limit: Int,
        showAppUsage: Boolean = true): TimelineFeedPage {
        require(offset >= 0 && limit in 1..300)
        val range = legacyTimelineFeedRange(date, zone)
            ?: return TimelineFeedPage(emptyList(), offset, 0, 0, 0)
        val result = page(range, offset, limit, showAppUsage)
        return TimelineFeedPage(result.records.map { TimelineFeedItem.Entry(it) }, offset,
            result.totalCount, result.eventCount, habitLogCount = 0)
    }
}
