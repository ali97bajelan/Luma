package com.luma.lifeos.ui.today

import com.luma.lifeos.domain.model.LifeEventRecord
import com.luma.lifeos.domain.model.Place
import com.luma.lifeos.domain.model.Person
import com.luma.lifeos.domain.model.EventType
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.LifeEvent
import com.luma.lifeos.domain.model.MoodDetails
import com.luma.lifeos.domain.model.JournalDetails
import com.luma.lifeos.domain.model.MemoryDetails
import com.luma.lifeos.domain.repository.LifeEventRepository
import com.luma.lifeos.domain.time.TimeRange
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow

sealed interface TodayUiState {
    data object Loading : TodayUiState
    data object Empty : TodayUiState
    data object Error : TodayUiState
    data class Data(val events: List<LifeEvent>, val moods: Map<EventId, MoodDetails> = emptyMap(),
        val journals: Map<EventId, JournalDetails> = emptyMap(),
        val memories: Map<EventId, MemoryDetails> = emptyMap(),
        val people: Map<EventId, List<Person>> = emptyMap(),
        val places: Map<EventId, Place> = emptyMap(),
        val photoCounts: Map<EventId, Int> = emptyMap(),
        val records: Map<EventId, LifeEventRecord> = emptyMap()) : TodayUiState {
        val counts: Map<EventType, Int> = events.groupingBy { it.type }.eachCount()
    }
}

fun observeToday(repository: LifeEventRepository, date: LocalDate, zone: ZoneId): Flow<TodayUiState> =
    flow<TodayUiState> {
        emit(TodayUiState.Loading)
        repository.observeRecords(TimeRange.forDay(date, zone)).collect { stored ->
            val records = stored.filterNot { it.event.type == EventType.APP_USAGE }
            emit(if (records.isEmpty()) TodayUiState.Empty else TodayUiState.Data(
                records.map { it.event }.sortedWith(LifeEvent.chronologicalOrder),
                records.mapNotNull { record -> record.mood?.let { record.event.id to it } }.toMap(),
                records.mapNotNull { record -> record.journal?.let { record.event.id to it } }.toMap(),
                records.mapNotNull { record -> record.memory?.let { record.event.id to it } }.toMap(),
                records.associate { it.event.id to it.people },
                records.mapNotNull { record -> record.place?.let { record.event.id to it } }.toMap(),
                records.associate { it.event.id to it.photos.size }, records.associateBy { it.event.id }))
        }
    }.catch { failure ->
        if (failure is CancellationException) throw failure
        emit(TodayUiState.Error)
    }

// Material date picker timestamps represent UTC calendar dates, regardless of the device zone.
fun pickerDate(milliseconds: Long): LocalDate =
    Instant.ofEpochMilli(milliseconds).atZone(ZoneOffset.UTC).toLocalDate()

fun pickerMillis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
