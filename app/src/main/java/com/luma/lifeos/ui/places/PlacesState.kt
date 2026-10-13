package com.luma.lifeos.ui.places

import com.luma.lifeos.domain.model.Place
import com.luma.lifeos.domain.model.PlaceHistory
import com.luma.lifeos.domain.repository.PlaceRepository
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow

sealed interface PlacesState {
    data object Loading : PlacesState
    data object Error : PlacesState
    data class Data(val places: List<Place>) : PlacesState
}
sealed interface PlaceDetailState {
    data object Loading : PlaceDetailState
    data object Error : PlaceDetailState
    data object Missing : PlaceDetailState
    data class Data(val place: Place) : PlaceDetailState
}
fun observePlaces(repository: PlaceRepository): Flow<PlacesState> = flow<PlacesState> {
    emit(PlacesState.Loading)
    repository.observePlaces().collect { emit(PlacesState.Data(it)) }
}.catch { if (it is CancellationException) throw it; emit(PlacesState.Error) }

fun observePlace(repository: PlaceRepository, id: UUID): Flow<PlaceDetailState> = flow<PlaceDetailState> {
    emit(PlaceDetailState.Loading)
    repository.observePlace(id).collect { emit(it?.let(PlaceDetailState::Data) ?: PlaceDetailState.Missing) }
}.catch { if (it is CancellationException) throw it; emit(PlaceDetailState.Error) }

sealed interface PlaceHistoryState {
    data object Loading : PlaceHistoryState
    data object Error : PlaceHistoryState
    data object Missing : PlaceHistoryState
    data class Data(val history: PlaceHistory) : PlaceHistoryState
}

fun observePlaceHistory(repository: PlaceRepository, id: UUID): Flow<PlaceHistoryState> = flow<PlaceHistoryState> {
    emit(PlaceHistoryState.Loading)
    repository.observeHistory(id).collect { emit(it?.let(PlaceHistoryState::Data) ?: PlaceHistoryState.Missing) }
}.catch { if (it is CancellationException) throw it; emit(PlaceHistoryState.Error) }
