package com.luma.lifeos.ui.review

import com.luma.lifeos.domain.repository.WeeklyReviewRepository
import com.luma.lifeos.domain.review.*
import com.luma.lifeos.domain.search.SearchPage
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Test
import org.junit.Assert.*

class WeeklyReviewStateTest {
    private val today = LocalDate.parse("2026-10-14")
    private val zone = ZoneOffset.UTC
    private val period = reviewPeriod(today, today, zone)
    @Test fun observationRetainsPeriodAndExposesUpdatesWithoutInventedSignals() = runBlocking {
        val empty = calculateWeeklyReview(period, emptyList(), emptyList())
        val repo = ReadRepository(flowOf(empty, empty.copy(current = empty.current.copy(records = 1))))
        val states = observeWeeklyReview(repo, period).toList()
        assertEquals(WeeklyReviewState.Loading, states.first())
        assertEquals(0, (states[1] as WeeklyReviewState.Data).report.current.records)
        assertNull((states[1] as WeeklyReviewState.Data).report.current.mood)
        assertEquals(1, (states.last() as WeeklyReviewState.Data).report.current.records)
        assertEquals(period, repo.request)
    }
    @Test fun loadErrorsAreRetryableAndCancellationPropagates() = runBlocking {
        val repo = ReadRepository(flow { error("failed") })
        assertEquals(WeeklyReviewState.Error, observeWeeklyReview(repo, period).toList().last())
        repo.reports = flowOf(calculateWeeklyReview(period, emptyList(), emptyList()))
        assertTrue(observeWeeklyReview(repo, period).toList().last() is WeeklyReviewState.Data)
        repo.reports = flow { throw CancellationException() }
        assertTrue(runCatching { observeWeeklyReview(repo, period).toList() }.exceptionOrNull() is CancellationException)
    }
    @Test fun sourcesRefreshAndClampAnOffsetAfterDeletion() = runBlocking {
        val repo = ReadRepository(flowOf())
        repo.total = 5
        val states = observeReviewSources(repo, period.current, zone, ReviewMetric(ReviewMetricKind.MOOD), 50).toList()
        val page = (states.last() as ReviewSourcesState.Data).page
        assertEquals(0, page.offset); assertEquals(5, page.totalCount)
        assertEquals(listOf(50, 0), repo.offsets)
    }
    @Test fun savedDateRetainsHistoricalWeekButFutureDateFallsBackAfterClockChange() {
        assertEquals(LocalDate.parse("2026-10-01"), restoredReviewDate("2026-10-01", today))
        assertEquals(today, restoredReviewDate(null, today))
        assertEquals(today, restoredReviewDate("2027-01-01", today))
        assertEquals(today, restoredReviewDate("broken", today))
    }
    @Test fun cancellingOldSourcesPreventsLatePageAndSourceFailuresRemainRetryable() = runBlocking {
        val repo = ReadRepository(flowOf())
        repo.delayPage = true
        val states = mutableListOf<ReviewSourcesState>()
        val job = launch { observeReviewSources(repo, period.current, zone, ReviewMetric(ReviewMetricKind.MOOD), 0).toList(states) }
        yield(); job.cancelAndJoin()
        assertFalse(states.any { it is ReviewSourcesState.Data })
        repo.delayPage = false; repo.failPage = true
        assertEquals(ReviewSourcesState.Error, observeReviewSources(repo, period.current, zone, ReviewMetric(ReviewMetricKind.MOOD), 0).toList().last())
    }
    private class ReadRepository(var reports: Flow<WeeklyReview>) : WeeklyReviewRepository {
        var request: ReviewPeriod? = null; var total = 0; var delayPage = false; var failPage = false
        val offsets = mutableListOf<Int>()
        override fun observe(period: ReviewPeriod): Flow<WeeklyReview> { request = period; return reports }
        override fun observeChanges() = flowOf(Unit)
        override suspend fun sources(dates: ReviewDates, zone: ZoneId, metric: ReviewMetric, offset: Int, limit: Int): SearchPage {
            require(limit == 50); offsets += offset
            if (delayPage) awaitCancellation()
            if (failPage) error("failed")
            return SearchPage(emptyList(), offset, total)
        }
    }
}
