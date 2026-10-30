package com.luma.lifeos.ui.focus

import com.luma.lifeos.domain.focus.FocusComparison
import com.luma.lifeos.domain.focus.FocusRepository
import com.luma.lifeos.domain.focus.FocusStatistics
import com.luma.lifeos.domain.focus.compareFocusStatistics
import com.luma.lifeos.domain.time.TimeRange
import java.time.LocalDate
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

data class FocusWeekDayRequest(
    val date: LocalDate,
    val range: TimeRange?,
    val available: Boolean = range != null,
) {
    init { require(range == null || available) }
}

data class FocusWeekDayReport(val date: LocalDate, val statistics: FocusStatistics?)

data class FocusWeekReport(
    val currentRange: TimeRange,
    val previousRange: TimeRange,
    val current: FocusStatistics,
    val previous: FocusStatistics,
    val comparison: FocusComparison,
    val days: List<FocusWeekDayReport> = emptyList(),
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

    fun week(
        current: TimeRange,
        previous: TimeRange,
        days: List<FocusWeekDayRequest> = emptyList(),
    ): Flow<FocusReportState<FocusWeekReport>> {
        val observedDays = days.filter { it.range != null }
        val sources = buildList {
            add(repository.observeStatistics(current))
            add(repository.observeStatistics(previous))
            observedDays.forEach { add(repository.observeStatistics(requireNotNull(it.range))) }
        }
        return combine(sources) { statistics ->
            val now = statistics[0]
            val before = statistics[1]
            var dayIndex = 2
            val dayReports = days.map { request ->
                val daily = when {
                    !request.available -> null
                    request.range == null -> FocusStatistics(0, 0, 0)
                    else -> statistics[dayIndex++]
                }
                FocusWeekDayReport(request.date, daily)
            }
            FocusReportState.Ready(
                FocusWeekReport(
                    current, previous, now, before, compareFocusStatistics(now, before), dayReports,
                ),
            ) as FocusReportState<FocusWeekReport>
        }.onStart { emit(FocusReportState.Loading) }
            .catch { if (it is CancellationException) throw it else emit(FocusReportState.Error) }
    }
}
