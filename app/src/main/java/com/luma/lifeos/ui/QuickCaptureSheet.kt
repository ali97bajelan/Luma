package com.luma.lifeos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.ui.common.LumaChoiceRow
import java.time.LocalDate
import java.time.format.FormatStyle

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun QuickCaptureSheet(
    browsedDate: LocalDate? = null,
    onDismiss: () -> Unit,
    onMood: (LocalDate?) -> Unit,
    onJournal: (LocalDate?) -> Unit,
    onMemory: (LocalDate?) -> Unit,
    onTask: (() -> Unit)? = null,
    onHabit: ((LocalDate?) -> Unit)? = null,
    taskDueDate: LocalDate? = null,
    onScheduledTask: ((LocalDate) -> Unit)? = null,
) {
    var useBrowsedDate by rememberSaveable(browsedDate) { mutableStateOf(false) }
    val captureDate = browsedDate.takeIf { useBrowsedDate }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .testTag("capture_sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.quick_capture),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp).testTag("capture_close_icon")) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.close))
                    }
                }
                Text(
                    stringResource(R.string.capture_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.capture_record_section), style = MaterialTheme.typography.titleSmall)
                if (browsedDate != null) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = !useBrowsedDate,
                            onClick = { useBrowsedDate = false },
                            label = { Text(stringResource(R.string.capture_use_now)) },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("capture_now"),
                        )
                        FilterChip(
                            selected = useBrowsedDate,
                            onClick = { useBrowsedDate = true },
                            label = {
                                Text(stringResource(
                                    R.string.capture_add_to_date,
                                    browsedDate.format(com.luma.lifeos.language.localizedDate(FormatStyle.MEDIUM)),
                                ))
                            },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("capture_on_browsed_date"),
                        )
                    }
                }
                LumaChoiceRow(
                    title = stringResource(R.string.capture_mood),
                    icon = R.drawable.ic_review_mood,
                    onClick = { onMood(captureDate) },
                    modifier = Modifier.testTag("capture_mood"),
                    showChevron = false,
                )
                LumaChoiceRow(
                    title = stringResource(R.string.capture_journal),
                    icon = R.drawable.ic_nav_timeline,
                    onClick = { onJournal(captureDate) },
                    modifier = Modifier.testTag("capture_journal"),
                    showChevron = false,
                )
                LumaChoiceRow(
                    title = stringResource(R.string.capture_memory),
                    icon = R.drawable.ic_bookmark_outline,
                    onClick = { onMemory(captureDate) },
                    modifier = Modifier.testTag("capture_memory"),
                    showChevron = false,
                )
            }

            onHabit?.let { action ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.habit_title), style = MaterialTheme.typography.titleSmall)
                    LumaChoiceRow(
                        title = stringResource(R.string.capture_habit),
                        icon = R.drawable.ic_nav_habits,
                        onClick = { action(captureDate) },
                        modifier = Modifier.testTag("capture_habit"),
                        showChevron = false,
                    )
                }
            }

            val scheduledTaskAction: (() -> Unit)? = taskDueDate?.let { dueDate ->
                onScheduledTask?.let { callback -> { callback(dueDate) } }
            }
            (scheduledTaskAction ?: onTask)?.let { action ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.capture_task_section), style = MaterialTheme.typography.titleSmall)
                        if (scheduledTaskAction != null && taskDueDate != null) {
                            Text(
                                stringResource(
                                    R.string.capture_task_due_date,
                                    taskDueDate.format(com.luma.lifeos.language.localizedDate(FormatStyle.MEDIUM)),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        } else {
                            Text(stringResource(R.string.capture_task_description), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    LumaChoiceRow(
                        title = stringResource(R.string.capture_task),
                        icon = R.drawable.ic_nav_tasks,
                        onClick = action,
                        modifier = Modifier.testTag("capture_task"),
                        showChevron = false,
                    )
                }
            }

            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                modifier = Modifier.heightIn(min = 48.dp).testTag("capture_close"),
            ) {
                Text(stringResource(R.string.close))
            }
        }
    }
}
