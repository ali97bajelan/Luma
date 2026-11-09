package com.luma.lifeos.ui.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.focus.FocusComparison
import com.luma.lifeos.domain.focus.FocusComparisonStatus
import com.luma.lifeos.domain.focus.FocusInsights
import com.luma.lifeos.domain.focus.FocusTagSummary
import com.luma.lifeos.ui.common.LumaCard

@Composable
internal fun FocusInsightsCard(
    state: FocusInsightsState,
    period: FocusPeriod,
    onPeriodChange: (FocusPeriod) -> Unit,
    onRetry: () -> Unit,
    onRequestUnlock: () -> Unit = {},
) {
    var showAllTags by remember(state.query) { mutableStateOf(false) }
    var progressExpanded by rememberSaveable { mutableStateOf(false) }
    val expandedContentRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(progressExpanded) {
        if (progressExpanded) expandedContentRequester.bringIntoView()
    }

    LumaCard(Modifier.fillMaxWidth().testTag("focus_insights_card")) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.focus_progress_heading),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            when (state) {
                FocusInsightsState.Unselected -> Text(stringResource(R.string.focus_progress_loading))
                is FocusInsightsState.Loading -> Text(stringResource(R.string.focus_progress_loading))
                is FocusInsightsState.Error -> Text(
                    stringResource(R.string.focus_insights_unavailable),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                is FocusInsightsState.NeedsUnlock -> Text(stringResource(R.string.app_lock_gate_body))
                is FocusInsightsState.Ready -> {
                    val insights = state.insights
                    if (insights.sessions == 0) {
                        Text(stringResource(R.string.focus_insights_empty))
                    } else {
                        Text(
                            stringResource(
                                R.string.focus_progress_summary,
                                stringResource(period.labelResource()),
                                focusDuration(insights.totalMillis),
                                focusContributorCount(insights.sessions),
                            ),
                            modifier = Modifier.testTag("focus_progress_summary"),
                        )
                    }
                }
            }
            val progressStateDescription = stringResource(
                if (progressExpanded) R.string.focus_disclosure_expanded
                else R.string.focus_disclosure_collapsed,
            )
            TextButton(
                onClick = { progressExpanded = !progressExpanded },
                modifier = Modifier.heightIn(min = 48.dp)
                    .semantics { stateDescription = progressStateDescription }
                    .testTag("focus_progress_toggle"),
            ) {
                Text(
                    stringResource(
                        if (progressExpanded) R.string.focus_progress_collapse
                        else R.string.focus_progress_view,
                    ),
                )
            }
            if (progressExpanded) {
                Column(
                    Modifier.fillMaxWidth()
                        .bringIntoViewRequester(expandedContentRequester)
                        .testTag("focus_insights_expanded"),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        stringResource(R.string.focus_insights_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FocusPeriod.entries.forEach { option ->
                            FilterChip(
                                selected = period == option,
                                onClick = { onPeriodChange(option) },
                                label = { Text(stringResource(option.labelResource())) },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                    .testTag("focus_period_${option.name.lowercase()}"),
                            )
                        }
                    }
                    when (state) {
                        FocusInsightsState.Unselected,
                        is FocusInsightsState.Loading -> CircularProgressIndicator(
                            modifier = Modifier.testTag("focus_insights_loading"),
                        )
                        is FocusInsightsState.Error -> {
                            Text(
                                stringResource(R.string.focus_insights_unavailable),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                            TextButton(
                                onClick = onRetry,
                                modifier = Modifier.heightIn(min = 48.dp).testTag("focus_insights_retry"),
                            ) { Text(stringResource(R.string.retry)) }
                        }
                        is FocusInsightsState.NeedsUnlock -> {
                            Text(stringResource(R.string.app_lock_gate_body))
                            TextButton(
                                onClick = onRequestUnlock,
                                modifier = Modifier.heightIn(min = 48.dp).testTag("focus_insights_unlock"),
                            ) { Text(stringResource(R.string.app_lock_gate_unlock)) }
                        }
                        is FocusInsightsState.Ready -> FocusInsightsContent(
                            state.insights,
                            state.comparison,
                            showAllTags,
                            onShowAllTags = { showAllTags = it },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FocusInsightsContent(
    insights: FocusInsights,
    comparison: FocusComparison?,
    showAllTags: Boolean,
    onShowAllTags: (Boolean) -> Unit,
) {
    if (insights.sessions == 0) {
        Text(stringResource(R.string.focus_insights_empty), modifier = Modifier.testTag("focus_insights_empty"))
    } else {
        Text(
            stringResource(R.string.focus_insights_total, focusDuration(insights.totalMillis)),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.testTag("focus_insights_total"),
        )
        Text(focusContributorCount(insights.sessions))
        Text(stringResource(R.string.focus_insights_average, focusDuration(insights.averageMillis)))
        if (insights.interruptedContributors > 0) Text(
            focusInterruptedCount(insights.interruptedContributors),
            style = MaterialTheme.typography.bodySmall,
        )
    }

    comparison?.let {
        Text(
            focusComparisonLabel(it),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("focus_insights_comparison"),
        )
        if (it.status == FocusComparisonStatus.PREVIOUS_PERIOD_EMPTY) {
            Text(stringResource(R.string.focus_previous_period_empty), style = MaterialTheme.typography.bodySmall)
        }
    }

    if (insights.sessions > 0) {
        val longest = insights.longest
        if (longest != null) {
            Text(stringResource(R.string.focus_longest_title), style = MaterialTheme.typography.titleMedium)
            Text(
                longest.session.metadata.name ?: stringResource(R.string.focus_session_fallback),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag("focus_insights_longest_name"),
            )
            Text(longest.session.metadata.tag?.name ?: stringResource(R.string.focus_tag_untagged))
            Text(stringResource(R.string.focus_longest_period_duration, focusDuration(longest.contributionMillis)))
            if (longest.contributionMillis < longest.session.creditedMillis) {
                Text(stringResource(R.string.focus_longest_full_duration, focusDuration(longest.session.creditedMillis)))
            }
            if (longest.session.state == com.luma.lifeos.domain.focus.FocusState.INTERRUPTED) {
                Text(stringResource(R.string.focus_longest_interrupted), style = MaterialTheme.typography.bodySmall)
            }
        }

        Text(stringResource(R.string.focus_insights_tags), style = MaterialTheme.typography.titleMedium)
        val visibleTags = if (showAllTags) insights.tags else insights.tags.take(5)
        visibleTags.forEachIndexed { index, tag ->
            FocusTagRow(tag, index)
        }
        if (insights.tags.size > 5) {
            TextButton(
                onClick = { onShowAllTags(!showAllTags) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("focus_insights_toggle_tags"),
            ) {
                Text(stringResource(if (showAllTags) R.string.focus_insights_show_less else R.string.focus_insights_show_all))
            }
        }

    }
}

@Composable
private fun FocusTagRow(summary: FocusTagSummary, index: Int) {
    val name = summary.tag?.name ?: stringResource(R.string.focus_tag_untagged)
    Text(
        stringResource(
            R.string.focus_insights_tag_row,
            name,
            focusDuration(summary.totalMillis),
            focusContributorCount(summary.sessions),
            focusPercentage(summary.percentage),
        ),
        modifier = Modifier.fillMaxWidth().testTag("focus_insights_tag_$index"),
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun focusComparisonLabel(comparison: FocusComparison): String = when (comparison.status) {
    FocusComparisonStatus.NO_CHANGE -> stringResource(R.string.focus_no_change)
    FocusComparisonStatus.PREVIOUS_PERIOD_EMPTY -> stringResource(
        R.string.focus_increase_from_empty,
        focusDuration(comparison.currentMillis),
    )
    FocusComparisonStatus.CHANGE -> {
        val amount = focusDuration(kotlin.math.abs(comparison.deltaMillis))
        val signed = if (comparison.deltaMillis > 0) {
            stringResource(R.string.focus_increase_by, amount)
        } else {
            stringResource(R.string.focus_decrease_by, amount)
        }
        stringResource(
            R.string.focus_change_with_percent,
            signed,
            focusPercentage(requireNotNull(comparison.percentageChange)),
        )
    }
}

private fun FocusPeriod.labelResource(): Int = when (this) {
    FocusPeriod.TODAY -> R.string.focus_period_today
    FocusPeriod.THIS_WEEK -> R.string.focus_period_week
    FocusPeriod.ALL_TIME -> R.string.focus_period_all
}
