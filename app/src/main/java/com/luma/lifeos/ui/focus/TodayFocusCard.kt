package com.luma.lifeos.ui.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.focus.FocusState

@Composable
internal fun TodayFocusCard(
    report: FocusReportState<FocusDayReport>,
    inProgressMillis: Long?,
    inProgressState: FocusState?,
    onRetry: () -> Unit,
    onHistory: () -> Unit,
    onFocus: (() -> Unit)?,
) {
    Card(Modifier.fillMaxWidth().testTag("today_focus_card")) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.focus_today_title), style = MaterialTheme.typography.titleLarge)
            when (report) {
                FocusReportState.Loading -> CircularProgressIndicator(Modifier.testTag("today_focus_loading"))
                FocusReportState.Error -> {
                    Text(stringResource(R.string.focus_report_unavailable), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry, modifier = Modifier.testTag("today_focus_retry")) {
                        Text(stringResource(R.string.retry))
                    }
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
                    TextButton(onClick = onHistory, modifier = Modifier.testTag("today_focus_history")) {
                        Text(stringResource(R.string.focus_view_day_history))
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
            if (onFocus != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onFocus,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("today_focus_open"),
                ) {
                    Text(stringResource(R.string.today_focus_action))
                }
            }
        }
    }
}
