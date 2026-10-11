package com.luma.lifeos.ui.people

import com.luma.lifeos.domain.model.Person
import com.luma.lifeos.domain.model.PersonHistory
import com.luma.lifeos.domain.repository.PersonRepository
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow

sealed interface PeopleState {
    data object Loading : PeopleState
    data object Error : PeopleState
    data class Data(val people: List<Person>) : PeopleState
}
sealed interface PersonHistoryState {
    data object Loading : PersonHistoryState
    data object Error : PersonHistoryState
    data object Missing : PersonHistoryState
    data class Data(val history: PersonHistory) : PersonHistoryState
}
fun observePeople(repository: PersonRepository): Flow<PeopleState> = flow<PeopleState> {
    emit(PeopleState.Loading)
    repository.observePeople().collect { emit(PeopleState.Data(it)) }
}.catch { if (it is CancellationException) throw it; emit(PeopleState.Error) }

fun observePersonHistory(repository: PersonRepository, id: UUID): Flow<PersonHistoryState> = flow<PersonHistoryState> {
    emit(PersonHistoryState.Loading)
    repository.observeHistory(id).collect { emit(it?.let(PersonHistoryState::Data) ?: PersonHistoryState.Missing) }
}.catch { if (it is CancellationException) throw it; emit(PersonHistoryState.Error) }
