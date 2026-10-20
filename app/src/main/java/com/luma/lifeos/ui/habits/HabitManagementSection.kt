package com.luma.lifeos.ui.habits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.habit.HabitLifecycle

@Composable
internal fun HabitManagementSection(
    lifecycle: HabitLifecycle?,
    futureStart: Boolean,
    onEdit: () -> Unit,
    onLifecycle: (HabitLifecycle) -> Unit,
    onDelete: () -> Unit,
    deleteError: HabitDetailActionError.Delete?,
    onRetryDelete: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val expansionState = stringResource(if (expanded) R.string.habit_manage_expanded else R.string.habit_manage_collapsed)
    Column(Modifier.fillMaxWidth().testTag("habit_management_section"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .semantics { stateDescription = expansionState }
                .testTag("habit_manage"),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.habit_manage), modifier = Modifier.weight(1f))
                Text(stringResource(if (expanded) R.string.habit_manage_hide else R.string.habit_manage_show))
            }
        }
        if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onEdit, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .testTag("habit_manage_edit")) {
                    Text(stringResource(R.string.detail_edit))
                }
                if (!futureStart && lifecycle != HabitLifecycle.ARCHIVED) {
                    val resume = lifecycle == HabitLifecycle.PAUSED
                    TextButton(
                        onClick = { onLifecycle(if (resume) HabitLifecycle.ACTIVE else HabitLifecycle.PAUSED) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("habit_pause_resume"),
                    ) { Text(stringResource(if (resume) R.string.habit_resume else R.string.habit_pause)) }
                }
                if (lifecycle != HabitLifecycle.ARCHIVED) {
                    TextButton(onClick = { onLifecycle(HabitLifecycle.ARCHIVED) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("habit_archive")) {
                        Text(stringResource(R.string.habit_archive))
                    }
                }
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("habit_delete"),
                ) { Text(stringResource(R.string.habit_delete)) }
                deleteError?.let { error ->
                    Text(stringResource(if (error.outcomeUnknown) R.string.habit_delete_outcome_unknown
                        else R.string.habit_delete_error), color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("habit_delete_error"))
                    TextButton(onClick = onRetryDelete, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .testTag("habit_retry_delete")) { Text(stringResource(R.string.retry)) }
                }
            }
        }
    }
}
