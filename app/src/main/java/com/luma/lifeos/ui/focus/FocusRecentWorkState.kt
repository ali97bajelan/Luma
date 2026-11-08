package com.luma.lifeos.ui.focus

import com.luma.lifeos.domain.focus.FocusRepository
import com.luma.lifeos.domain.focus.FocusSession
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/** Identifies one in-memory recent-work observation and the authorization scope used for it. */
data class FocusRecentWorkQuery(
    val generation: UUID,
    val includeTaskOrigin: Boolean,
    val retry: Int,
)

sealed interface FocusRecentWorkState {
    val query: FocusRecentWorkQuery?

    data object Hidden : FocusRecentWorkState {
        override val query: FocusRecentWorkQuery? = null
    }

    data class Loading(val request: FocusRecentWorkQuery) : FocusRecentWorkState {
        override val query: FocusRecentWorkQuery get() = request
    }

    data class Empty(val request: FocusRecentWorkQuery) : FocusRecentWorkState {
        override val query: FocusRecentWorkQuery get() = request
    }

    data class Missing(
        val request: FocusRecentWorkQuery,
        val sessionId: UUID,
    ) : FocusRecentWorkState {
        override val query: FocusRecentWorkQuery get() = request
    }

    data class Ready(
        val request: FocusRecentWorkQuery,
        val session: FocusSession,
    ) : FocusRecentWorkState {
        override val query: FocusRecentWorkQuery get() = request
    }

    data class Error(val request: FocusRecentWorkQuery) : FocusRecentWorkState {
        override val query: FocusRecentWorkQuery get() = request
    }
}

/** Switches to the current authorized query and never retains a stale session after its request changes. */
@OptIn(ExperimentalCoroutinesApi::class)
fun observeFocusRecentWork(
    repository: FocusRepository,
    queries: Flow<FocusRecentWorkQuery?>,
): Flow<FocusRecentWorkState> = flow {
    queries.flatMapLatest { query ->
        if (query == null) {
            flowOf<FocusRecentWorkState>(FocusRecentWorkState.Hidden)
        } else {
            flow<FocusRecentWorkState> {
                emit(FocusRecentWorkState.Loading(query))
                if (repository.currentGeneration() != query.generation) {
                    emit(FocusRecentWorkState.Hidden)
                    return@flow
                }
                repository.observeRecentTerminal(range = null, includeTaskOrigin = query.includeTaskOrigin)
                    .collect { session ->
                        val currentGeneration = repository.currentGeneration()
                        emit(
                            when {
                                currentGeneration != query.generation -> FocusRecentWorkState.Hidden
                                session == null -> FocusRecentWorkState.Empty(query)
                                !session.isTerminal || session.creditedMillis <= 0L ||
                                    (!query.includeTaskOrigin && session.taskOrigin) -> FocusRecentWorkState.Hidden
                                else -> FocusRecentWorkState.Ready(query, session)
                            },
                        )
                    }
            }.catch { failure ->
                if (failure is CancellationException) throw failure
                emit(FocusRecentWorkState.Error(query))
            }
        }
    }.collect { emit(it) }
}
