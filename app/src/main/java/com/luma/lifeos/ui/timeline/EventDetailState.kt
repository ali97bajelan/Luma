package com.luma.lifeos.ui.timeline

import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.LifeEventRecord
import com.luma.lifeos.domain.repository.TimelineRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow

sealed interface EventDetailState {
    data object Loading : EventDetailState
    data object Missing : EventDetailState
    data object Error : EventDetailState
    data class Data(val record: LifeEventRecord) : EventDetailState
}
fun observeEventDetail(repository: TimelineRepository, id: EventId): Flow<EventDetailState> = flow<EventDetailState> {
    emit(EventDetailState.Loading)
    repository.observeChanges().collect {
        val record = repository.get(id)
        emit(if (record == null) EventDetailState.Missing else EventDetailState.Data(record))
    }
}.catch { failure ->
    if (failure is CancellationException) throw failure
    emit(EventDetailState.Error)
}
