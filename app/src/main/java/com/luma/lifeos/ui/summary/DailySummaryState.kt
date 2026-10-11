package com.luma.lifeos.ui.summary

import com.luma.lifeos.domain.repository.DailySummaryRepository
import com.luma.lifeos.domain.summary.DailySummary
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow

sealed interface DailySummaryState {
    data object Loading : DailySummaryState
    data object Error : DailySummaryState
    data class Data(val summary: DailySummary) : DailySummaryState
}
fun observeDailySummary(repository: DailySummaryRepository, date: LocalDate, zone: ZoneId): Flow<DailySummaryState> = flow<DailySummaryState> {
    emit(DailySummaryState.Loading)
    repository.observe(date, zone).collect {
        check(it.date == date && it.zone == zone) { "Summary does not match selected day" }
        emit(DailySummaryState.Data(it))
    }
}.catch { failure ->
    if (failure is CancellationException) throw failure
    emit(DailySummaryState.Error)
}
