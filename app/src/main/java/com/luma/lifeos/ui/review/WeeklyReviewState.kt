package com.luma.lifeos.ui.review

import com.luma.lifeos.domain.repository.WeeklyReviewRepository
import com.luma.lifeos.domain.review.*
import com.luma.lifeos.domain.search.SearchPage
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*

sealed interface WeeklyReviewState {
    data object Loading : WeeklyReviewState
    data object Error : WeeklyReviewState
    data class Data(val report: WeeklyReview) : WeeklyReviewState
}
sealed interface ReviewSourcesState {
    data object Loading : ReviewSourcesState
    data object Error : ReviewSourcesState
    data class Data(val page: SearchPage) : ReviewSourcesState
}
fun restoredReviewDate(saved: String?, today: LocalDate): LocalDate =
    saved?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.takeIf { it <= today } ?: today

fun observeWeeklyReview(repository: WeeklyReviewRepository, period: ReviewPeriod): Flow<WeeklyReviewState> = flow {
    emit(WeeklyReviewState.Loading)
    repository.observe(period).collect { emit(WeeklyReviewState.Data(it)) }
}.catch { if (it is CancellationException) throw it; emit(WeeklyReviewState.Error) }

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
fun observeReviewSources(repository: WeeklyReviewRepository, dates: ReviewDates, zone: ZoneId,
    metric: ReviewMetric, offset: Int, limit: Int = 50): Flow<ReviewSourcesState> =
    repository.observeChanges().transformLatest {
        emit(ReviewSourcesState.Loading)
        var page = repository.sources(dates, zone, metric, offset, limit)
        if (offset > 0 && offset >= page.totalCount) {
            val last = maxOf(0, page.totalCount - 1) / limit * limit
            page = repository.sources(dates, zone, metric, last, limit)
        }
        emit(ReviewSourcesState.Data(page))
    }.catch { if (it is CancellationException) throw it; emit(ReviewSourcesState.Error) }
