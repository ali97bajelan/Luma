package com.luma.lifeos.ui.focus

import com.luma.lifeos.ui.common.LumaUi
import com.luma.lifeos.ui.common.LumaCard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.luma.lifeos.ui.common.LumaTextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.focus.FocusComparisonStatus
import com.luma.lifeos.domain.focus.focusWeekDistribution
import java.util.UUID
import com.luma.lifeos.language.localizedDate
import com.luma.lifeos.domain.review.ReviewPeriod
import java.time.LocalDate
import java.time.format.FormatStyle

@Composable
internal fun WeeklyFocusCard(
    report: FocusReportState<FocusWeekReport>,
    period: ReviewPeriod,
    onRetry: () -> Unit,
    onCurrentHistory: () -> Unit,
    onPreviousHistory: () -> Unit,
    onRequestUnlock: () -> Unit = {},
    onDayHistory: ((LocalDate) -> Unit)? = null,
    onPlanAnotherSession: (() -> Unit)? = null,
    goalReport: FocusGoalReportState = FocusGoalReportState.Hidden,
    inProgressMillis: Long? = null,
    inProgressPaused: Boolean = false,
    recentContext: FocusReviewContextState = FocusReviewContextState.Hidden,
    onRetryRecentContext: () -> Unit = {},
    onViewSession: (UUID) -> Unit = {},
) {
    LumaCard(Modifier.fillMaxWidth().testTag("review_focus_card")) {
        Column(
            Modifier.fillMaxWidth().padding(LumaUi.SectionPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.focus_week_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            val visibleReport = if (
                report is FocusReportState.Ready &&
                recentContext is FocusReviewContextState.NeedsUnlock
            ) FocusReportState.NeedsUnlock else report
            when (visibleReport) {
                FocusReportState.Loading -> androidx.compose.material3.CircularProgressIndicator(
                    Modifier.testTag("review_focus_loading"),
                )
                FocusReportState.Error -> {
                    Text(
                        stringResource(R.string.focus_report_unavailable),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    LumaTextButton(onClick = onRetry, modifier = Modifier.testTag("review_focus_retry")) {
                        Text(stringResource(R.string.retry))
                    }
                }
                FocusReportState.NeedsUnlock -> {
                    Text(stringResource(R.string.app_lock_gate_body))
                    LumaTextButton(
                        onClick = onRequestUnlock,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("review_focus_unlock"),
                    ) { Text(stringResource(R.string.app_lock_gate_unlock)) }
                }
                is FocusReportState.Ready -> {
                    val value = visibleReport.value
                    val dailyTotals = value.days.asSequence()
                        .filter { it.date in period.current.start..period.current.through }
                        .mapNotNull { day -> day.statistics?.let { day.date to it.totalMillis } }
                        .toMap()
                    val distribution = focusWeekDistribution(
                        dailyTotals,
                        firstDayOfWeek = period.current.start.dayOfWeek,
                    )
                    val ratio = maxOf(value.current.totalMillis, value.previous.totalMillis)
                    Text(
                        stringResource(
                            R.string.focus_week_this_period,
                            dateRange(period.current.start, period.current.through),
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (period.partial) {
                        Text(
                            stringResource(R.string.review_in_progress),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.testTag("review_focus_partial"),
                        )
                    }
                    Text(
                        stringResource(R.string.focus_week_duration, focusDuration(value.current.totalMillis)),
                        modifier = Modifier.testTag("review_focus_current"),
                    )
                    LinearProgressIndicator(
                        progress = { barFraction(value.current.totalMillis, ratio) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).testTag("review_focus_current_bar"),
                    )
                    when (goalReport) {
                        FocusGoalReportState.Hidden, FocusGoalReportState.Loading -> Unit
                        FocusGoalReportState.Error -> Text(
                            stringResource(R.string.focus_goal_unavailable),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                                .testTag("review_focus_goal_error"),
                        )
                        FocusGoalReportState.NeedsUnlock -> Row(
                            horizontalArrangement = Arrangement.spacedBy(LumaUi.ContentGap),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.app_lock_gate_body), modifier = Modifier.weight(1f))
                            LumaTextButton(
                                onClick = onRequestUnlock,
                                modifier = Modifier.heightIn(min = 48.dp).testTag("review_focus_goal_unlock"),
                            ) { Text(stringResource(R.string.app_lock_gate_unlock)) }
                        }
                        is FocusGoalReportState.Ready -> {
                            val goal = goalReport.value
                            val targetMillis = goal.goal.targetMinutes * 60_000L
                            val goalRatio = barFraction(goal.savedMillis, targetMillis)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Text(
                                stringResource(R.string.focus_goal_progress_heading),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.testTag("review_focus_goal_heading"),
                            )
                            Text(
                                stringResource(
                                    R.string.focus_goal_progress,
                                    focusDuration(goal.savedMillis),
                                    focusDuration(targetMillis),
                                ),
                                modifier = Modifier.testTag("review_focus_goal_progress"),
                            )
                            LinearProgressIndicator(
                                progress = { goalRatio },
                                modifier = Modifier.fillMaxWidth().height(6.dp).testTag("review_focus_goal_bar"),
                            )
                            Text(
                                pluralStringResource(
                                    R.plurals.focus_goal_active_days,
                                    goal.distribution.activeDays,
                                    goal.distribution.activeDays,
                                ),
                                modifier = Modifier.testTag("review_focus_goal_active_days"),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                stringResource(
                                    R.string.focus_goal_week_context,
                                    dateRange(goal.weekStart, goal.weekStart.plusDays(6)),
                                    goal.zone.id,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (inProgressMillis != null && inProgressMillis > 0) {
                                Text(
                                    stringResource(
                                        if (inProgressPaused) R.string.focus_current_paused else R.string.focus_current_in_progress,
                                        focusDuration(inProgressMillis),
                                    ),
                                    modifier = Modifier.testTag("review_focus_goal_in_progress"),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        pluralStringResource(
                            R.plurals.focus_review_active_days,
                            distribution.activeDays,
                            distribution.activeDays,
                        ),
                        modifier = Modifier.testTag("review_focus_active_days"),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    val highestDate = distribution.highestDate
                    if (highestDate == null) {
                        Text(
                            stringResource(R.string.focus_review_no_activity),
                            modifier = Modifier.testTag("review_focus_no_activity"),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        Text(
                            stringResource(
                                R.string.focus_review_highest_day,
                                localizedDate(highestDate, FormatStyle.MEDIUM),
                                focusDuration(dailyTotals.getValue(highestDate)),
                            ),
                            modifier = Modifier.testTag("review_focus_highest_day"),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (value.days.isNotEmpty()) {
                        val dailyMaximum = value.days.mapNotNull { it.statistics?.totalMillis }.maxOrNull() ?: 0L
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            value.days.forEachIndexed { index, day ->
                                val dayIsInRange = day.date in period.current.start..period.current.through
                                Row(
                                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).then(
                                        if (dayIsInRange && onDayHistory != null) {
                                            val actionDescription = stringResource(
                                                R.string.focus_review_day_history_action,
                                                localizedDate(day.date, FormatStyle.MEDIUM),
                                            )
                                            Modifier
                                                .clickable(role = Role.Button) { onDayHistory?.invoke(day.date) }
                                                .semantics(mergeDescendants = true) {
                                                    contentDescription = actionDescription
                                                }
                                        } else {
                                            Modifier
                                        },
                                    ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(LumaUi.ContentGap),
                                ) {
                                    val dayLabel = localizedDate(day.date, FormatStyle.SHORT)
                                    Text(dayLabel, modifier = Modifier.width(72.dp), style = MaterialTheme.typography.bodySmall)
                                    val stats = day.statistics
                                    if (stats == null) {
                                        Text(
                                            stringResource(R.string.focus_week_day_unavailable),
                                            modifier = Modifier.weight(1f).testTag("review_focus_day_unavailable_$index"),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    } else {
                                        LinearProgressIndicator(
                                            progress = { barFraction(stats.totalMillis, dailyMaximum) },
                                            modifier = Modifier.weight(1f).height(6.dp)
                                                .testTag("review_focus_day_bar_$index"),
                                        )
                                        Text(
                                            stringResource(R.string.focus_week_day_duration, dayLabel, focusDuration(stats.totalMillis)),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Text(
                        focusContributorCount(value.current.contributingSessions),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    FocusReviewContextItem(
                        state = recentContext,
                        onRetry = onRetryRecentContext,
                        onViewSession = onViewSession,
                        onRequestUnlock = onRequestUnlock,
                    )
                    Text(
                        stringResource(
                            R.string.focus_week_previous_period,
                            dateRange(period.previous.start, period.previous.through),
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.focus_week_duration, focusDuration(value.previous.totalMillis)),
                        modifier = Modifier.testTag("review_focus_previous"),
                    )
                    LinearProgressIndicator(
                        progress = { barFraction(value.previous.totalMillis, ratio) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).testTag("review_focus_previous_bar"),
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Text(
                        focusContributorCount(value.previous.contributingSessions),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    val comparisonText = when (value.comparison.status) {
                        FocusComparisonStatus.NO_CHANGE -> stringResource(R.string.focus_no_change)
                        FocusComparisonStatus.PREVIOUS_PERIOD_EMPTY -> stringResource(
                            R.string.focus_increase_from_empty,
                            focusDuration(value.comparison.currentMillis),
                        )
                        FocusComparisonStatus.CHANGE -> {
                            val amount = focusDuration(kotlin.math.abs(value.comparison.deltaMillis))
                            val signed = if (value.comparison.deltaMillis > 0) {
                                stringResource(R.string.focus_increase_by, amount)
                            } else {
                                stringResource(R.string.focus_decrease_by, amount)
                            }
                            val percent = focusPercentage(requireNotNull(value.comparison.percentageChange))
                            stringResource(R.string.focus_change_with_percent, signed, percent)
                        }
                    }
                    Text(comparisonText, modifier = Modifier.testTag("review_focus_comparison"))
                    if (value.comparison.status == FocusComparisonStatus.PREVIOUS_PERIOD_EMPTY) {
                        Text(stringResource(R.string.focus_previous_period_empty))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(LumaUi.ContentGap)) {
                        LumaTextButton(onClick = onCurrentHistory, modifier = Modifier.testTag("review_focus_current_history")) {
                            Text(stringResource(R.string.focus_view_current_sources))
                        }
                        LumaTextButton(onClick = onPreviousHistory, modifier = Modifier.testTag("review_focus_previous_history")) {
                            Text(stringResource(R.string.focus_view_previous_sources))
                        }
                    }
                    onPlanAnotherSession?.let { onPlan ->
                        LumaTextButton(
                            onClick = onPlan,
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                .testTag("review_focus_plan_another"),
                        ) {
                            Text(stringResource(R.string.focus_review_plan_another))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FocusReviewContextItem(
    state: FocusReviewContextState,
    onRetry: () -> Unit,
    onViewSession: (UUID) -> Unit,
    onRequestUnlock: () -> Unit,
) {
    when (state) {
        FocusReviewContextState.Hidden,
        is FocusReviewContextState.Empty,
        -> Unit

        is FocusReviewContextState.Loading -> {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(verticalArrangement = Arrangement.spacedBy(LumaUi.RelatedGap)) {
                Text(
                    stringResource(R.string.focus_review_latest_work),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.testTag("review_focus_recent_context"),
                )
                Text(
                    stringResource(R.string.focus_recent_work_loading),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        is FocusReviewContextState.Error -> {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(verticalArrangement = Arrangement.spacedBy(LumaUi.RelatedGap)) {
                Text(
                    stringResource(R.string.focus_review_latest_work),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(R.string.focus_review_context_error),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        .testTag("review_focus_recent_context_error"),
                )
                LumaTextButton(
                    onClick = onRetry,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("review_focus_recent_context_retry"),
                ) { Text(stringResource(R.string.retry)) }
            }
        }

        is FocusReviewContextState.NeedsUnlock -> {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(verticalArrangement = Arrangement.spacedBy(LumaUi.RelatedGap)) {
                Text(
                    stringResource(R.string.focus_review_latest_work),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(stringResource(R.string.app_lock_gate_body), style = MaterialTheme.typography.bodySmall)
                LumaTextButton(
                    onClick = onRequestUnlock,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("review_focus_recent_context_unlock"),
                ) { Text(stringResource(R.string.app_lock_gate_unlock)) }
            }
        }

        is FocusReviewContextState.Ready -> {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(verticalArrangement = Arrangement.spacedBy(LumaUi.RelatedGap)) {
                Text(
                    stringResource(R.string.focus_review_latest_work),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.testTag("review_focus_recent_context"),
                )
                Text(
                    focusSessionIntention(state.session),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("review_focus_recent_context_name"),
                )
                state.session.reflection.nextStep?.takeIf(String::isNotBlank)?.let { nextStep ->
                    Text(stringResource(R.string.focus_reflection_saved_next_step, nextStep))
                }
                LumaTextButton(
                    onClick = { onViewSession(state.session.id) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("review_focus_recent_context_view"),
                ) { Text(stringResource(R.string.focus_review_view_session)) }
            }
        }
    }
}

@Composable
private fun dateRange(start: java.time.LocalDate, end: java.time.LocalDate): String {
    return stringResource(
        R.string.memory_review_range,
        localizedDate(start, FormatStyle.MEDIUM),
        localizedDate(end, FormatStyle.MEDIUM),
    )
}

private fun barFraction(value: Long, maximum: Long): Float =
    if (maximum == 0L) 0f else (value.toDouble() / maximum.toDouble()).toFloat().coerceIn(0f, 1f)
