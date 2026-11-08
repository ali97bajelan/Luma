package com.luma.lifeos.ui.focus

import com.luma.lifeos.domain.focus.FocusComparison
import com.luma.lifeos.domain.focus.FocusRepository
import com.luma.lifeos.domain.focus.FocusSession
import com.luma.lifeos.domain.focus.FocusStatistics
import com.luma.lifeos.domain.focus.compareFocusStatistics
import com.luma.lifeos.domain.time.TimeRange
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/** A report carries its query range so a caller can associate data with its heading. */
sealed interface FocusReportState<out T> {
    data object Loading : FocusReportState<Nothing>
    data class Ready<T>(val value: T) : FocusReportState<T>
    data object Error : FocusReportState<Nothing>
    data object NeedsUnlock : FocusReportState<Nothing>
}

data class FocusDayReport(val range: TimeRange, val statistics: FocusStatistics)

data class FocusWeekDayRequest(
    val date: LocalDate,
    val range: TimeRange?,
    val available: Boolean = range != null,
) {
    init { require(range == null || available) }
}

data class FocusWeekDayReport(val date: LocalDate, val statistics: FocusStatistics?)

data class FocusWeekReport(
    val currentRange: TimeRange,
    val previousRange: TimeRange,
    val current: FocusStatistics,
    val previous: FocusStatistics,
    val comparison: FocusComparison,
    val days: List<FocusWeekDayReport> = emptyList(),
)

data class FocusReviewContextQuery(
    val range: TimeRange,
    val generation: UUID,
    val includeTaskOrigin: Boolean,
    val retry: Int,
)

sealed interface FocusReviewContextState {
    data object Hidden : FocusReviewContextState
    data class Loading(val query: FocusReviewContextQuery) : FocusReviewContextState
    data class Empty(val query: FocusReviewContextQuery) : FocusReviewContextState
    data class NeedsUnlock(val query: FocusReviewContextQuery) : FocusReviewContextState
    data class Ready(
        val query: FocusReviewContextQuery,
        val session: FocusSession,
    ) : FocusReviewContextState
    data class Error(val query: FocusReviewContextQuery) : FocusReviewContextState
}

/** Observes the single in-range candidate only while its report and authorization scope are current. */
@OptIn(ExperimentalCoroutinesApi::class)
fun observeFocusReviewContext(
    repository: FocusRepository,
    queries: Flow<FocusReviewContextQuery?>,
): Flow<FocusReviewContextState> = flow {
    queries.flatMapLatest { query ->
        if (query == null) {
            flowOf<FocusReviewContextState>(FocusReviewContextState.Hidden)
        } else {
            flow<FocusReviewContextState> {
                emit(FocusReviewContextState.Loading(query))
                if (repository.currentGeneration() != query.generation) {
                    emit(FocusReviewContextState.Hidden)
                    return@flow
                }
                repository.observeRecentTerminal(query.range, query.includeTaskOrigin).collect { session ->
                    val generation = repository.currentGeneration()
                    emit(
                        when {
                            generation != query.generation -> FocusReviewContextState.Hidden
                            session == null -> FocusReviewContextState.Empty(query)
                            !session.isTerminal || session.creditedMillis <= 0L ->
                                FocusReviewContextState.Empty(query)
                            session.taskOrigin && !query.includeTaskOrigin ->
                                FocusReviewContextState.NeedsUnlock(query)
                            else -> FocusReviewContextState.Ready(query, session)
                        },
                    )
                }
            }.catch { failure ->
                if (failure is CancellationException) throw failure
                emit(FocusReviewContextState.Error(query))
            }
        }
    }.collect { emit(it) }
}

/** Independent streams allow report failures to leave other Today and Review content intact. */
class FocusReportSource(private val repository: FocusRepository) {
    fun day(
        range: TimeRange,
        taskContentAccessAllowed: Boolean = true,
    ): Flow<FocusReportState<FocusDayReport>> =
        repository.observeStatistics(range)
            .map<FocusStatistics, FocusReportState<FocusDayReport>> {
                if (it.requiresTaskAccess && !taskContentAccessAllowed) FocusReportState.NeedsUnlock
                else FocusReportState.Ready(FocusDayReport(range, it))
            }
            .onStart { emit(FocusReportState.Loading) }
            .catch { if (it is CancellationException) throw it else emit(FocusReportState.Error) }

    fun week(
        current: TimeRange,
        previous: TimeRange,
        days: List<FocusWeekDayRequest> = emptyList(),
        taskContentAccessAllowed: Boolean = true,
    ): Flow<FocusReportState<FocusWeekReport>> {
        val observedDays = days.filter { it.range != null }
        val sources = buildList {
            add(repository.observeStatistics(current))
            add(repository.observeStatistics(previous))
            observedDays.forEach { add(repository.observeStatistics(requireNotNull(it.range))) }
        }
        return combine(sources) { statistics ->
            if (!taskContentAccessAllowed && statistics.any(FocusStatistics::requiresTaskAccess)) {
                return@combine FocusReportState.NeedsUnlock
            }
            val now = statistics[0]
            val before = statistics[1]
            var dayIndex = 2
            val dayReports = days.map { request ->
                val daily = when {
                    !request.available -> null
                    request.range == null -> FocusStatistics(0, 0, 0)
                    else -> statistics[dayIndex++]
                }
                FocusWeekDayReport(request.date, daily)
            }
            FocusReportState.Ready(
                FocusWeekReport(
                    current, previous, now, before, compareFocusStatistics(now, before), dayReports,
                ),
            ) as FocusReportState<FocusWeekReport>
        }.onStart { emit(FocusReportState.Loading) }
            .catch { if (it is CancellationException) throw it else emit(FocusReportState.Error) }
    }
}
