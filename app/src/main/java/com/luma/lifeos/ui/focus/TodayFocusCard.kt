package com.luma.lifeos.ui.focus

import com.luma.lifeos.ui.common.LumaUi
import com.luma.lifeos.ui.common.LumaButton
import com.luma.lifeos.ui.common.LumaCard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.luma.lifeos.ui.common.LumaTextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.focus.FocusState

internal fun todayFocusActionLabel(state: FocusState?): Int = when (state) {
    FocusState.RUNNING -> R.string.today_focus_return_running
    FocusState.PAUSED -> R.string.today_focus_return_paused
    else -> R.string.today_focus_action
}

@Composable
internal fun TodayFocusCard(
    report: FocusReportState<FocusDayReport>,
    inProgressMillis: Long?,
    inProgressState: FocusState?,
    onRetry: () -> Unit,
    onHistory: () -> Unit,
    onFocus: (() -> Unit)?,
    onRequestUnlock: () -> Unit = {},
) {
    LumaCard(Modifier.fillMaxWidth().testTag("today_focus_card")) {
        Column(
            Modifier.fillMaxWidth().padding(LumaUi.SectionPadding),
            verticalArrangement = Arrangement.spacedBy(LumaUi.ContentGap),
        ) {
            Text(
                stringResource(R.string.focus_today_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            when (report) {
                FocusReportState.Loading -> CircularProgressIndicator(Modifier.testTag("today_focus_loading"))
                FocusReportState.Error -> {
                    Text(
                        stringResource(R.string.focus_report_unavailable),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    LumaTextButton(onClick = onRetry, modifier = Modifier.testTag("today_focus_retry")) {
                        Text(stringResource(R.string.retry))
                    }
                }
                FocusReportState.NeedsUnlock -> {
                    Text(stringResource(R.string.app_lock_gate_body))
                    LumaTextButton(
                        onClick = onRequestUnlock,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("today_focus_unlock"),
                    ) { Text(stringResource(R.string.app_lock_gate_unlock)) }
                }
                is FocusReportState.Ready -> {
                    Text(
                        stringResource(R.string.focus_today_saved, focusDuration(report.value.statistics.totalMillis)),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.testTag("today_focus_saved"),
                    )
                    Text(
                        focusContributorCount(report.value.statistics.contributingSessions),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("today_focus_contributors"),
                    )
                    if (report.value.statistics.interruptedContributors > 0) {
                        Text(
                            focusInterruptedCount(report.value.statistics.interruptedContributors),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("today_focus_interrupted"),
                        )
                    }
                }
            }
            if (inProgressMillis != null && inProgressState != null) {
                Text(
                    stringResource(
                        if (inProgressState == FocusState.PAUSED) R.string.focus_current_paused
                        else R.string.focus_current_in_progress,
                        focusDuration(inProgressMillis),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("today_focus_in_progress"),
                )
            }
            if (onFocus != null) {
                LumaButton(
                    onClick = onFocus,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("today_focus_open"),
                ) {
                    Text(stringResource(todayFocusActionLabel(inProgressState)))
                }
            }
            if (report is FocusReportState.Ready) {
                LumaTextButton(
                    onClick = onHistory,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("today_focus_history"),
                ) {
                    Text(stringResource(R.string.focus_view_day_history))
                }
            }
        }
    }
}
