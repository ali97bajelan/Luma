package com.luma.lifeos.ui.memory

import com.luma.lifeos.domain.model.LifeEvent
import com.luma.lifeos.domain.model.LifeEventRecord
import com.luma.lifeos.domain.repository.MemoryRepository
import com.luma.lifeos.domain.time.TimeRange
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow

sealed interface MemoryReviewState {
    data object Loading : MemoryReviewState
    data object Empty : MemoryReviewState
    data object Error : MemoryReviewState
    data class Data(val records: List<LifeEventRecord>) : MemoryReviewState
}

fun memoryWeekRange(date: LocalDate, zone: ZoneId): TimeRange {
    val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return TimeRange(monday.atStartOfDay(zone).toInstant(), monday.plusWeeks(1).atStartOfDay(zone).toInstant())
}

fun observeMemoryReview(repository: MemoryRepository, date: LocalDate, zone: ZoneId): Flow<MemoryReviewState> =
    flow<MemoryReviewState> {
        emit(MemoryReviewState.Loading)
        repository.observe(memoryWeekRange(date, zone)).collect { records ->
            emit(if (records.isEmpty()) MemoryReviewState.Empty else MemoryReviewState.Data(
                records.sortedWith { first, second -> LifeEvent.chronologicalOrder.compare(first.event, second.event) }))
        }
    }.catch { failure ->
        if (failure is CancellationException) throw failure
        emit(MemoryReviewState.Error)
    }
