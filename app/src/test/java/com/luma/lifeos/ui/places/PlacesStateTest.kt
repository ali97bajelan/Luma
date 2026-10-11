package com.luma.lifeos.ui.places

import com.luma.lifeos.domain.model.Place
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
        for (observation in listOf(observePlaces(repo), observePlace(repo, UUID.randomUUID()))) {
            try { observation.toList(); fail("Cancellation should propagate") } catch (_: CancellationException) { }
        }
    }
    private class TestPlaces : PlaceRepository {
        var failed = false
        var cancelled = false
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
