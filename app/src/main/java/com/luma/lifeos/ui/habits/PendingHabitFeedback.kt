package com.luma.lifeos.ui.habits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.language.localizedDate
import java.time.format.FormatStyle

@Composable
internal fun PendingHabitFeedback(
    pending: PendingHabitLog,
    onRetry: () -> Unit,
    onOpenDate: (java.time.LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    retryTag: String = "habit_retry_${pending.id}",
) {
    Column(modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(pending.habitName.takeIf(String::isNotBlank) ?: stringResource(R.string.habit_unavailable),
            style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.habit_pending_context,
            pending.input.date.format(localizedDate(FormatStyle.FULL)), pending.input.zone.id),
            style = MaterialTheme.typography.bodySmall)
        Text(stringResource(if (pending.error == null) R.string.habit_log_saving else R.string.habit_log_failed),
            style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onOpenDate(pending.input.date) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("habit_pending_open_${pending.id}")) {
                Text(stringResource(R.string.habit_pending_open_date))
            }
            if (pending.error != null) TextButton(onClick = onRetry,
                modifier = Modifier.heightIn(min = 48.dp).testTag(retryTag)) {
                Text(stringResource(R.string.habit_retry_action))
            }
        }
    }
}
