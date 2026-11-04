package com.luma.lifeos.ui.focus

import com.luma.lifeos.domain.focus.FocusCommand
import com.luma.lifeos.domain.focus.FocusPage
import com.luma.lifeos.domain.focus.FocusRecoveryChoice
import com.luma.lifeos.domain.focus.FocusRepository
import com.luma.lifeos.domain.focus.FocusSession
import com.luma.lifeos.domain.focus.FocusStartInput
import com.luma.lifeos.domain.focus.FocusStatistics
import com.luma.lifeos.domain.focus.FocusTag
import com.luma.lifeos.domain.focus.FocusTerminalReason
import com.luma.lifeos.domain.focus.FocusTimeSample
import com.luma.lifeos.domain.focus.FocusWriteContext
import com.luma.lifeos.domain.focus.FocusWriteResult
import com.luma.lifeos.domain.focus.FocusInsights
import com.luma.lifeos.domain.time.TimeRange
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusInsightsStateTest {
    @Test fun newRangeOrGenerationDropsAnOlderResult() {
        val firstRange = TimeRange(Instant.EPOCH, Instant.EPOCH.plusSeconds(60))
        val secondRange = TimeRange(Instant.EPOCH.plusSeconds(60), Instant.EPOCH.plusSeconds(120))
        val first = FocusInsightsQuery(firstRange, uuid(1))
        val second = FocusInsightsQuery(secondRange, uuid(1))
        val third = second.copy(generation = uuid(2))
        val loading = reduceFocusInsightsState(FocusInsightsState.Unselected, FocusInsightsEvent.Selected(first))
        val current = reduceFocusInsightsState(loading, FocusInsightsEvent.Selected(second))
        val stale = reduceFocusInsightsState(current, FocusInsightsEvent.Loaded(first, FocusInsights.EMPTY))
        val nextGeneration = reduceFocusInsightsState(stale, FocusInsightsEvent.Selected(third))
        val staleGeneration = reduceFocusInsightsState(
            nextGeneration,
            FocusInsightsEvent.Loaded(second, FocusInsights.EMPTY),
        )
        val currentResult = reduceFocusInsightsState(staleGeneration, FocusInsightsEvent.Loaded(third, FocusInsights.EMPTY))

        assertEquals(FocusInsightsState.Loading(second), current)
        assertEquals(current, stale)
        assertEquals(FocusInsightsState.Loading(third), nextGeneration)
        assertEquals(nextGeneration, staleGeneration)
        assertEquals(FocusInsightsState.Ready(third, FocusInsights.EMPTY), currentResult)
    }

    @Test fun successfulEmptySnapshotIsReadyWhileRepositoryFailureIsError() = runBlocking {
        val query = FocusInsightsQuery(null, uuid(3))
        val emptyRepository = InsightsRepository { flowOf(FocusInsights.EMPTY) }
        val failedRepository = InsightsRepository { flow { throw IllegalStateException("storage") } }

        val emptyStates = observeFocusInsights(emptyRepository, flowOf(query)).toListForTest()
        val failedStates = observeFocusInsights(failedRepository, flowOf(query)).toListForTest()

        assertEquals(
            listOf(FocusInsightsState.Loading(query), FocusInsightsState.Ready(query, FocusInsights.EMPTY)),
            emptyStates,
        )
        assertEquals(listOf(FocusInsightsState.Loading(query), FocusInsightsState.Error(query)), failedStates)
    }

    @Test fun reemittingTheSameQueryRetriesAfterAnError() = runBlocking {
        val query = FocusInsightsQuery(null, uuid(10))
        val requests = MutableSharedFlow<FocusInsightsQuery>(replay = 1).apply { tryEmit(query) }
        val attempts = AtomicInteger()
        val repository = InsightsRepository {
            if (attempts.incrementAndGet() == 1) {
                flow { throw IllegalStateException("first request failed") }
            } else {
                flowOf(FocusInsights.EMPTY)
            }
        }
        val states = mutableListOf<FocusInsightsState>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            observeFocusInsights(repository, requests).collect { states.add(it) }
        }
        try {
            withTimeout(2_000) {
                while (states.none { it == FocusInsightsState.Error(query) }) kotlinx.coroutines.yield()
            }
            requests.emit(query)
            withTimeout(2_000) {
                while (states.none { it == FocusInsightsState.Ready(query, FocusInsights.EMPTY) }) kotlinx.coroutines.yield()
            }

            assertEquals(2, attempts.get())
            assertEquals(2, states.count { it == FocusInsightsState.Loading(query) })
        } finally {
            job.cancel()
        }
    }

    @Test fun queryChangeCancelsPriorObservationAndRebindsResultToNewGeneration() = runBlocking {
        val range = TimeRange(Instant.EPOCH, Instant.EPOCH.plusSeconds(60))
        val first = FocusInsightsQuery(range, uuid(4))
        val second = FocusInsightsQuery(range, uuid(5))
        val requests = MutableStateFlow(first)
        val firstCancelled = CompletableDeferred<Unit>()
        val subscription = CompletableDeferred<Unit>()
        val repository = InsightsRepository {
            flow {
                subscription.complete(Unit)
                emit(FocusInsights.EMPTY)
                try {
                    awaitCancellation()
                } finally {
                    firstCancelled.complete(Unit)
                }
            }
        }
        val states = mutableListOf<FocusInsightsState>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            observeFocusInsights(repository, requests).collect(states::add)
        }
        try {
            withTimeout(2_000) { subscription.await() }
            requests.value = second
            withTimeout(2_000) { firstCancelled.await() }
            withTimeout(2_000) {
                while (states.none { it == FocusInsightsState.Ready(second, FocusInsights.EMPTY) }) kotlinx.coroutines.yield()
            }

            assertTrue(states.contains(FocusInsightsState.Loading(first)))
            assertTrue(states.contains(FocusInsightsState.Ready(first, FocusInsights.EMPTY)))
            assertTrue(states.contains(FocusInsightsState.Loading(second)))
            assertTrue(states.contains(FocusInsightsState.Ready(second, FocusInsights.EMPTY)))
            assertFalse(states.any { it.query == first && it !is FocusInsightsState.Loading && it !is FocusInsightsState.Ready })
        } finally {
            job.cancel()
        }
    }

    private class InsightsRepository(
        private val insights: (TimeRange?) -> Flow<FocusInsights>,
    ) : FocusRepository {
        override suspend fun currentGeneration(): UUID = error("Not used")
        override fun observeOpen(): Flow<FocusSession?> = emptyFlow()
        override suspend fun start(
            commandId: UUID,
            sessionId: UUID,
            input: FocusStartInput,
            sample: FocusTimeSample,
            context: FocusWriteContext,
        ): FocusWriteResult = error("Not used")
        override suspend fun apply(command: FocusCommand): FocusWriteResult = error("Not used")
        override suspend fun reconcile(sample: FocusTimeSample, context: FocusWriteContext): FocusWriteResult = error("Not used")
        override suspend fun resolveRecovery(
            sessionId: UUID,
            expectedRevision: Long,
            choice: FocusRecoveryChoice,
            sample: FocusTimeSample,
            context: FocusWriteContext,
            terminalReason: FocusTerminalReason,
        ): FocusWriteResult = error("Not used")
        override suspend fun checkpoint(
            sessionId: UUID,
            expectedRevision: Long,
            sample: FocusTimeSample,
            context: FocusWriteContext,
        ): FocusWriteResult = error("Not used")
        override fun observeStatistics(range: TimeRange): Flow<FocusStatistics> = error("Not used")
        override fun observeInsights(range: TimeRange?): Flow<FocusInsights> = insights(range)
        override fun observeTagSuggestions(): Flow<List<FocusTag>> = emptyFlow()
        override suspend fun history(range: TimeRange?, offset: Int, limit: Int): FocusPage = error("Not used")
        override suspend fun delete(
            sessionId: UUID,
            expectedRevision: Long,
            context: FocusWriteContext,
        ): FocusWriteResult = error("Not used")
    }

    private fun Flow<FocusInsightsState>.toListForTest(): List<FocusInsightsState> =
        kotlinx.coroutines.runBlocking { toList() }

    private fun uuid(value: Int) = UUID(0, value.toLong())
}
