package com.luma.lifeos.ui.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.luma.lifeos.ui.common.LumaTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.focus.FocusProductRepository
import com.luma.lifeos.domain.focus.FocusRepository
import com.luma.lifeos.domain.focus.FocusSession
import com.luma.lifeos.domain.focus.FocusState
import com.luma.lifeos.focus.FocusViewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.UUID

@Composable
internal fun FocusSessionDetailContent(
    session: FocusSession,
    repository: FocusRepository,
    productRepository: FocusProductRepository?,
    focusViewModel: FocusViewModel?,
    taskCompletionState: FocusTaskCompletionState,
    canAccessTaskActions: Boolean,
    generation: UUID?,
    isFocusReadable: Boolean,
    zone: ZoneId,
    deleting: Boolean = false,
    onRepeat: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onChanged: () -> Unit,
) {
    val formatter = remember(zone, Locale.getDefault()) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            .withLocale(Locale.getDefault())
            .withZone(zone)
    }
    var breakdownExpanded by rememberSaveable(session.id.toString(), generation?.toString()) {
        mutableStateOf(false)
    }
    val breakdownStateDescription = stringResource(
        if (breakdownExpanded) R.string.focus_disclosure_expanded else R.string.focus_disclosure_collapsed,
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FocusSessionMetadataEditor(
            session = session,
            productRepository = productRepository,
            generation = generation,
            isFocusReadable = isFocusReadable,
            onSaved = onChanged,
        )
        if (focusViewModel != null) {
            FocusTaskCompletionAction(
                session = session,
                generation = generation,
                state = taskCompletionState,
                canAccessTaskActions = canAccessTaskActions,
                onSessionChanged = focusViewModel::syncTaskCompletionSession,
                onMarkComplete = focusViewModel::markTaskComplete,
                onRetryCompletion = focusViewModel::retryTaskCompletion,
                onUnlinkTask = focusViewModel::unlinkTask,
                onRetryUnlink = focusViewModel::retryTaskUnlink,
                onChanged = onChanged,
            )
        }
        FocusReflectionEditor(
            session = session,
            repository = repository,
            productRepository = productRepository,
            generation = generation,
            isFocusReadable = isFocusReadable,
            onSaved = onChanged,
        )
        LumaTextButton(
            enabled = !deleting,
            onClick = onRepeat,
            modifier = Modifier.heightIn(min = 48.dp).testTag("focus_history_repeat"),
        ) { Text(stringResource(R.string.focus_repeat)) }
        onDelete?.let { delete ->
            LumaTextButton(
                enabled = !deleting,
                onClick = delete,
                modifier = Modifier.heightIn(min = 48.dp).testTag("focus_history_delete"),
            ) {
                Text(stringResource(if (deleting) R.string.focus_saving else R.string.focus_history_delete_action))
            }
        }
        LumaTextButton(
            onClick = { breakdownExpanded = !breakdownExpanded },
            modifier = Modifier.heightIn(min = 48.dp)
                .semantics { stateDescription = breakdownStateDescription }
                .testTag("focus_history_time_breakdown_toggle"),
        ) { Text(stringResource(R.string.focus_history_time_breakdown)) }
        if (breakdownExpanded) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.testTag("focus_history_time_breakdown"),
            ) {
                Text(
                    stringResource(R.string.focus_history_pause_explanation),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (session.state == FocusState.INTERRUPTED || session.recoveredOrUncertain) {
                    Text(
                        stringResource(R.string.focus_history_recovered),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("focus_history_recovered"),
                    )
                }
                session.runs.forEachIndexed { index, run ->
                    val end = requireNotNull(run.endedAt)
                    Text(
                        stringResource(
                            R.string.focus_history_segment,
                            index + 1,
                            formatter.format(run.startedAt),
                            formatter.format(end),
                            focusDuration(run.creditedMillis),
                        ),
                        modifier = Modifier.testTag("focus_history_segment_$index"),
                    )
                }
            }
        }
    }
}
