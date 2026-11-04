package com.luma.lifeos.ui.focus

import com.luma.lifeos.domain.focus.FocusInsights
import com.luma.lifeos.domain.focus.FocusComparison
import com.luma.lifeos.domain.focus.FocusStatistics
import com.luma.lifeos.domain.focus.FocusRepository
import com.luma.lifeos.domain.focus.compareFocusStatistics
import com.luma.lifeos.domain.time.TimeRange
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/** The period and database generation identify which saved report a state belongs to. */
data class FocusInsightsQuery(
    val range: TimeRange?,
    val generation: UUID,
    val comparisonRange: TimeRange? = null,
)

sealed interface FocusInsightsState {
    val query: FocusInsightsQuery?

    data object Unselected : FocusInsightsState {
        override val query: FocusInsightsQuery? = null
    }

    data class Loading(val request: FocusInsightsQuery) : FocusInsightsState {
        override val query: FocusInsightsQuery get() = request
    }

    data class Ready(
        val request: FocusInsightsQuery,
        val insights: FocusInsights,
        val comparison: FocusComparison? = null,
    ) : FocusInsightsState {
        override val query: FocusInsightsQuery get() = request
    }

    data class Error(val request: FocusInsightsQuery) : FocusInsightsState {
        override val query: FocusInsightsQuery get() = request
    }
}

internal sealed interface FocusInsightsEvent {
    data class Selected(val query: FocusInsightsQuery) : FocusInsightsEvent
    data class Loaded(
        val query: FocusInsightsQuery,
        val insights: FocusInsights,
        val comparison: FocusComparison? = null,
    ) : FocusInsightsEvent
    data class Failed(val query: FocusInsightsQuery) : FocusInsightsEvent
    data object Cleared : FocusInsightsEvent
}

/** Results from a different period or restored database cannot replace the selected state. */
internal fun reduceFocusInsightsState(
    state: FocusInsightsState,
    event: FocusInsightsEvent,
): FocusInsightsState = when (event) {
    is FocusInsightsEvent.Selected -> FocusInsightsState.Loading(event.query)
    is FocusInsightsEvent.Loaded -> if (state.query == event.query) {
        FocusInsightsState.Ready(event.query, event.insights, event.comparison)
    } else {
        state
    }
    is FocusInsightsEvent.Failed -> if (state.query == event.query) {
        FocusInsightsState.Error(event.query)
    } else {
        state
    }
    FocusInsightsEvent.Cleared -> FocusInsightsState.Unselected
}

/**
 * Cancel and replace the previous report whenever a request is emitted. Re-emitting the same
 * range/generation is an explicit retry after Error.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun observeFocusInsights(
    repository: FocusRepository,
    queries: Flow<FocusInsightsQuery?>,
): Flow<FocusInsightsState> = flow {
    var state: FocusInsightsState = FocusInsightsState.Unselected
    queries.flatMapLatest { query ->
        if (query == null) {
            flowOf<FocusInsightsEvent>(FocusInsightsEvent.Cleared)
        } else {
            flow<FocusInsightsEvent> {
                emit(FocusInsightsEvent.Selected(query))
                try {
                    val report = query.comparisonRange?.let { previousRange ->
                        kotlinx.coroutines.flow.combine(
                            repository.observeInsights(query.range),
                            repository.observeInsights(previousRange),
                        ) { current, previous ->
                            FocusInsightsEvent.Loaded(
                                query,
                                current,
                                compareFocusStatistics(current.asStatistics(), previous.asStatistics()),
                            )
                        }
                    } ?: repository.observeInsights(query.range).let { source ->
                        kotlinx.coroutines.flow.flow {
                            source.collect { emit(FocusInsightsEvent.Loaded(query, it)) }
                        }
                    }
                    report.collect { event ->
                        emit(event)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    emit(FocusInsightsEvent.Failed(query))
                }
            }
        }
    }.collect { event ->
        state = reduceFocusInsightsState(state, event)
        emit(state)
    }
}

private fun FocusInsights.asStatistics() = FocusStatistics(
    totalMillis = totalMillis,
    contributingSessions = sessions,
    interruptedContributors = interruptedContributors,
)
