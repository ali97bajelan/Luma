package com.luma.lifeos.ui.places

import com.luma.lifeos.domain.model.Place
import com.luma.lifeos.domain.model.PlaceHistory
import com.luma.lifeos.domain.model.PlaceInput
import com.luma.lifeos.domain.repository.PlaceRepository
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PlacesStateTest {
    @Test fun historyDistinguishesMissingFromLoadingAndError() = runTest {
        val repo = TestPlaces()
        val id = UUID.randomUUID()
        val history = PlaceHistory(Place(id, "Cafe"), emptyList())
        val renamed = history.copy(place = history.place.copy(name = "Renamed"))
        repo.history = flowOf(null, history, renamed, null)
        assertEquals(listOf(PlaceHistoryState.Loading, PlaceHistoryState.Missing,
            PlaceHistoryState.Data(history), PlaceHistoryState.Data(renamed), PlaceHistoryState.Missing),
            observePlaceHistory(repo, id).toList())
        repo.failed = true
        assertEquals(listOf(PlaceHistoryState.Loading, PlaceHistoryState.Error), observePlaceHistory(repo, id).toList())
        repo.failed = false
        repo.history = flowOf(history)
        assertEquals(listOf(PlaceHistoryState.Loading, PlaceHistoryState.Data(history)), observePlaceHistory(repo, id).toList())
    }
    @Test fun catalogFailuresSurfaceErrorAndNewObservationCanRetry() = runTest {
        val repo = TestPlaces()
        repo.failed = true
        assertEquals(listOf(PlacesState.Loading, PlacesState.Error), observePlaces(repo).toList())
        repo.failed = false
        assertEquals(listOf(PlacesState.Loading, PlacesState.Data(emptyList())), observePlaces(repo).toList())
    }
    @Test fun missingDetailAndCommittedChangesAreDistinctFromLoadingAndErrors() = runTest {
        val repo = TestPlaces()
        val id = UUID.randomUUID(); val place = Place(id, "Cafe")
        repo.detail = flowOf(null, place, place.copy(name = "Renamed"), null)
        assertEquals(listOf(PlaceDetailState.Loading, PlaceDetailState.Missing, PlaceDetailState.Data(place),
            PlaceDetailState.Data(place.copy(name = "Renamed")), PlaceDetailState.Missing), observePlace(repo, id).toList())
        repo.failed = true
        assertEquals(listOf(PlaceDetailState.Loading, PlaceDetailState.Error), observePlace(repo, id).toList())
    }
    @Test fun navigationCancellationIsNotRenderedAsLoadFailure() = runTest {
        val repo = TestPlaces().apply { cancelled = true }
        for (observation in listOf(observePlaces(repo), observePlace(repo, UUID.randomUUID()), observePlaceHistory(repo, UUID.randomUUID()))) {
            try { observation.toList(); fail("Cancellation should propagate") } catch (_: CancellationException) { }
        }
    }
    private class TestPlaces : PlaceRepository {
        var failed = false
        var cancelled = false
        var history: Flow<PlaceHistory?> = flowOf(null)
        override fun observeHistory(id: UUID) = flow { check(); history.collect { emit(it) } }
        var detail: Flow<Place?> = flowOf(null)
        private fun check() { if (cancelled) throw CancellationException(); if (failed) error("Unavailable") }
        override fun observePlaces() = flow<List<Place>> { check(); emit(emptyList()) }
        override fun observePlace(id: UUID) = flow { check(); detail.collect { emit(it) } }
        override suspend fun get(id: UUID): Place? = error("Unused")
        override suspend fun create(id: UUID, input: PlaceInput): UUID = error("Unused")
        override suspend fun update(id: UUID, input: PlaceInput): Unit = error("Unused")
        override suspend fun delete(id: UUID): Unit = error("Unused")
    }
}
