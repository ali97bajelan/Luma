package com.luma.lifeos.ui.focus

import com.luma.lifeos.domain.focus.FocusComparison
import com.luma.lifeos.domain.focus.FocusRepository
import com.luma.lifeos.domain.focus.FocusStatistics
import com.luma.lifeos.domain.focus.compareFocusStatistics
import com.luma.lifeos.domain.time.TimeRange
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/** A report carries its query range so a caller can associate data with its heading. */
sealed interface FocusReportState<out T> {
    data object Loading : FocusReportState<Nothing>
    data class Ready<T>(val value: T) : FocusReportState<T>
    data object Error : FocusReportState<Nothing>
}

data class FocusDayReport(val range: TimeRange, val statistics: FocusStatistics)

data class FocusWeekReport(
    val currentRange: TimeRange,
    val previousRange: TimeRange,
    val current: FocusStatistics,
    val previous: FocusStatistics,
    val comparison: FocusComparison,
)

/** Independent streams allow report failures to leave other Today and Review content intact. */
class FocusReportSource(private val repository: FocusRepository) {
    fun day(range: TimeRange): Flow<FocusReportState<FocusDayReport>> =
        repository.observeStatistics(range)
            .map<FocusStatistics, FocusReportState<FocusDayReport>> {
                FocusReportState.Ready(FocusDayReport(range, it))
            }
            .onStart { emit(FocusReportState.Loading) }
            .catch { if (it is CancellationException) throw it else emit(FocusReportState.Error) }

    fun week(current: TimeRange, previous: TimeRange): Flow<FocusReportState<FocusWeekReport>> =
        combine(repository.observeStatistics(current), repository.observeStatistics(previous)) { now, before ->
            FocusReportState.Ready(FocusWeekReport(current, previous, now, before, compareFocusStatistics(now, before))) as FocusReportState<FocusWeekReport>
        }.onStart { emit(FocusReportState.Loading) }
            .catch { if (it is CancellationException) throw it else emit(FocusReportState.Error) }
}
