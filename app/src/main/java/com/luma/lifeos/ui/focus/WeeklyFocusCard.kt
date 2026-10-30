package com.luma.lifeos.ui.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.focus.FocusComparisonStatus
import com.luma.lifeos.language.localizedDate
import com.luma.lifeos.domain.review.ReviewPeriod
import java.time.format.FormatStyle
import java.util.Locale
import java.time.format.DateTimeFormatter

@Composable
internal fun WeeklyFocusCard(
    report: FocusReportState<FocusWeekReport>,
    period: ReviewPeriod,
    onRetry: () -> Unit,
    onCurrentHistory: () -> Unit,
    onPreviousHistory: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().testTag("review_focus_card")) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.focus_week_title), style = MaterialTheme.typography.titleLarge)
            when (report) {
                FocusReportState.Loading -> androidx.compose.material3.CircularProgressIndicator(
                    Modifier.testTag("review_focus_loading"),
                )
                FocusReportState.Error -> {
                    Text(stringResource(R.string.focus_report_unavailable), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry, modifier = Modifier.testTag("review_focus_retry")) {
                        Text(stringResource(R.string.retry))
                    }
                }
                is FocusReportState.Ready -> {
                    val value = report.value
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
                    if (value.days.isNotEmpty()) {
                        val dailyMaximum = value.days.mapNotNull { it.statistics?.totalMillis }.maxOrNull() ?: 0L
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            value.days.forEachIndexed { index, day ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    val dayLabel = day.date.format(
                                        DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
                                            .withLocale(Locale.getDefault()),
                                    )
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onCurrentHistory, modifier = Modifier.testTag("review_focus_current_history")) {
                            Text(stringResource(R.string.focus_view_current_sources))
                        }
                        TextButton(onClick = onPreviousHistory, modifier = Modifier.testTag("review_focus_previous_history")) {
                            Text(stringResource(R.string.focus_view_previous_sources))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun dateRange(start: java.time.LocalDate, end: java.time.LocalDate): String {
    val format = localizedDate(FormatStyle.MEDIUM)
    return stringResource(R.string.memory_review_range, start.format(format), end.format(format))
}

private fun barFraction(value: Long, maximum: Long): Float =
    if (maximum == 0L) 0f else (value.toDouble() / maximum.toDouble()).toFloat().coerceIn(0f, 1f)
