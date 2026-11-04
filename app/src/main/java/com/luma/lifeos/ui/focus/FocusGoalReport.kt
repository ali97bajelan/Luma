package com.luma.lifeos.ui.focus

import com.luma.lifeos.domain.focus.FocusGoalVersion
import com.luma.lifeos.domain.focus.FocusProductRepository
import com.luma.lifeos.domain.focus.FocusRepository
import com.luma.lifeos.domain.focus.FocusWeekDistribution
import com.luma.lifeos.domain.focus.focusWeekDistribution
import com.luma.lifeos.domain.time.TimeRange
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

sealed interface FocusGoalReportState {
    data object Loading : FocusGoalReportState
    data object Hidden : FocusGoalReportState
    data object Error : FocusGoalReportState
    data class Ready(val value: FocusGoalProgress) : FocusGoalReportState
}

data class FocusGoalProgress(
    val goal: FocusGoalVersion,
    val weekStart: LocalDate,
    val zone: ZoneId,
    val savedMillis: Long,
    val distribution: FocusWeekDistribution,
)

/** Saved-only goal progress always uses the goal version's captured local week. */
class FocusGoalReportSource(
    private val focusRepository: FocusRepository,
    private val productRepository: FocusProductRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun current(now: Instant): Flow<FocusGoalReportState> =
        productRepository.observeGoals().flatMapLatest { state ->
            val goal = state.activeGoal(now)
                ?: return@flatMapLatest flowOf(FocusGoalReportState.Hidden)
            observeGoalWeek(goal, now)
        }

    private fun observeGoalWeek(
        goal: FocusGoalVersion,
        now: Instant,
    ): Flow<FocusGoalReportState> {
        val zone = ZoneId.of(goal.zoneId)
        val monday = now.atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val dates = (0L..6L).map(monday::plusDays)
        val sources = dates.map { date ->
            val start = date.atStartOfDay(zone).toInstant()
            val end = date.plusDays(1).atStartOfDay(zone).toInstant()
            focusRepository.observeStatistics(TimeRange(start, end))
        }
        return combine(sources) { dailyStatistics ->
            val totals = dates.zip(dailyStatistics).associate { (date, statistics) -> date to statistics.totalMillis }
            val savedMillis = dailyStatistics.fold(0L) { total, statistics -> Math.addExact(total, statistics.totalMillis) }
            val ready: FocusGoalReportState = FocusGoalReportState.Ready(
                FocusGoalProgress(
                    goal = goal,
                    weekStart = monday,
                    zone = zone,
                    savedMillis = savedMillis,
                    distribution = focusWeekDistribution(totals),
                ),
            )
            ready
        }.onStart { emit(FocusGoalReportState.Loading) }
            .catch { if (it is CancellationException) throw it else emit(FocusGoalReportState.Error) }
    }
}
