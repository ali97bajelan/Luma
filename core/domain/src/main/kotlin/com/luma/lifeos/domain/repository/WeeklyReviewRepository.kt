package com.luma.lifeos.domain.repository

import com.luma.lifeos.domain.review.*
import com.luma.lifeos.domain.search.SearchPage
import kotlinx.coroutines.flow.Flow

interface WeeklyReviewRepository {
    fun observe(period: ReviewPeriod): Flow<WeeklyReview>
    fun observeChanges(): Flow<Unit>
    /** Each source page applies the metric predicate before loading content. Maximum 50 rows. */
    suspend fun sources(dates: ReviewDates, zone: java.time.ZoneId, metric: ReviewMetric, offset: Int, limit: Int = 50): SearchPage
}
