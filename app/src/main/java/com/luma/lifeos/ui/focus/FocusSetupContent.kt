package com.luma.lifeos.ui.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.focus.FocusStartInput
import com.luma.lifeos.domain.focus.FocusTimerMode
import com.luma.lifeos.domain.focus.normalizeFocusStartInput
import com.luma.lifeos.focus.FocusScreenState
import com.luma.lifeos.ui.common.LumaButton

@Composable
internal fun FocusSetupContent(
    state: FocusScreenState,
    onNameChange: (String) -> Unit,
    onNameBlur: () -> Unit,
    onTagChange: (String) -> Unit,
    onTagBlur: () -> Unit,
    onTagAreaExpandedChange: (Boolean) -> Unit,
    onTimerModeChange: (FocusTimerMode) -> Unit,
    onTimedMinutesChange: (String) -> Unit,
    onTimedMinutesBlur: () -> Unit,
    onSubmitStart: () -> Unit,
    onRepeatSession: (java.util.UUID) -> Unit,
    onOpenTaskPicker: (() -> Unit)?,
    onClearTaskSelection: () -> Unit,
    onRetryRecentWork: () -> Unit,
) {
    val setup = state.setup
    var retryReplacementSessionId by remember { mutableStateOf<java.util.UUID?>(null) }
    val nameValid = runCatching {
        normalizeFocusStartInput(FocusStartInput(name = setup.name, tagName = null))
    }.isSuccess
    val tagValid = setup.tag.isBlank() || runCatching {
        normalizeFocusStartInput(FocusStartInput(name = "Focus", tagName = setup.tag))
    }.isSuccess
    val timedMinutesValid = setup.timedMinutesText.toIntOrNull()?.let { it in 1..180 } == true
    val fieldsEnabled = !state.busy && !setup.repeatLoading
    var showTagPicker by remember { mutableStateOf(false) }

    Text(stringResource(R.string.focus_intro), style = MaterialTheme.typography.bodyLarge)
    if (setup.repeatLoading) {
        Text(stringResource(R.string.focus_repeat_loading), modifier = Modifier.testTag("focus_repeat_loading"))
    }
    val missingRecentSource = state.recentWork as? FocusRecentWorkState.Missing
    if (setup.repeatLoadFailed && missingRecentSource?.sessionId != setup.repeatSourceSessionId) {
        Text(stringResource(R.string.focus_repeat_error), color = MaterialTheme.colorScheme.error)
        setup.repeatSourceSessionId?.let { sourceId ->
            TextButton(
                onClick = {
                    if (setup.hasUserAuthoredDraft) retryReplacementSessionId = sourceId
                    else onRepeatSession(sourceId)
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("focus_repeat_retry"),
            ) { Text(stringResource(R.string.retry)) }
        }
    }
    if (setup.repeatNeedsName) {
        Text(stringResource(R.string.focus_repeat_legacy_name), modifier = Modifier.testTag("focus_repeat_legacy_name"))
    }
    setup.repeatNextStep?.let { nextStep ->
        Text(stringResource(R.string.focus_repeat_next_step, nextStep), modifier = Modifier.testTag("focus_repeat_next_step"))
    }
    if (onOpenTaskPicker != null) {
        OutlinedButton(
            onClick = onOpenTaskPicker,
            enabled = fieldsEnabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("focus_choose_task"),
        ) { Text(stringResource(R.string.focus_choose_task)) }
    }
    if (setup.selectedTaskId != null) {
        Text(
            stringResource(R.string.focus_task_selected, setup.selectedTaskTitle ?: setup.name),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("focus_task_selected"),
        )
        TextButton(
            onClick = onClearTaskSelection,
            enabled = fieldsEnabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("focus_task_remove"),
        ) { Text(stringResource(R.string.focus_task_remove)) }
    } else if (setup.taskOrigin) {
        Text(stringResource(R.string.focus_task_link_removed), modifier = Modifier.testTag("focus_task_link_removed"))
    }
    if (setup.taskSelectionLoading) {
        Text(stringResource(R.string.focus_task_loading), modifier = Modifier.testTag("focus_task_loading"))
    }
    if (setup.taskSelectionConflict) {
        Text(
            stringResource(R.string.focus_task_selection_conflict),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("focus_task_selection_conflict"),
        )
    }
    OutlinedTextField(
        value = setup.name,
        onValueChange = onNameChange,
        label = { Text(stringResource(R.string.focus_name_label)) },
        placeholder = { Text(stringResource(R.string.focus_name_example)) },
        singleLine = true,
        enabled = fieldsEnabled,
        isError = setup.showNameError && !nameValid,
        supportingText = {
            if (setup.showNameError && !nameValid) Text(stringResource(R.string.focus_name_invalid))
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmitStart() }),
        modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused) onNameBlur() }
            .testTag("focus_name"),
    )
    Text(stringResource(R.string.focus_timer_mode_label), style = MaterialTheme.typography.titleSmall)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(
                selected = setup.timerMode == FocusTimerMode.COUNT_UP,
                onClick = { onTimerModeChange(FocusTimerMode.COUNT_UP) },
                enabled = fieldsEnabled,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("focus_mode_count_up"),
                label = { Text(stringResource(R.string.focus_timer_mode_count_up)) },
            )
            FilterChip(
                selected = setup.timerMode == FocusTimerMode.TIMED,
                onClick = { onTimerModeChange(FocusTimerMode.TIMED) },
                enabled = fieldsEnabled,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("focus_mode_timed"),
                label = { Text(stringResource(R.string.focus_timer_mode_timed)) },
            )
        }
        TextButton(
            onClick = {
                onTimerModeChange(FocusTimerMode.TIMED)
                onTimedMinutesChange("25")
            },
            enabled = fieldsEnabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("focus_25_minute_shortcut"),
        ) { Text(stringResource(R.string.focus_25_minute_shortcut)) }
    }
    if (setup.timerMode == FocusTimerMode.TIMED) {
        OutlinedTextField(
            value = setup.timedMinutesText,
            onValueChange = onTimedMinutesChange,
            label = { Text(stringResource(R.string.focus_timed_minutes_label)) },
            singleLine = true,
            enabled = fieldsEnabled,
            isError = setup.showTimedMinutesError && !timedMinutesValid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmitStart() }),
            supportingText = {
                if (setup.showTimedMinutesError && !timedMinutesValid) {
                    Text(stringResource(R.string.focus_timed_minutes_invalid))
                }
            },
            modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused) onTimedMinutesBlur() }
                .testTag("focus_timed_minutes"),
        )
        Text(stringResource(R.string.focus_alert_limitations), style = MaterialTheme.typography.bodyMedium)
    }

    val tagDisclosureLabel = if (setup.tag.isBlank()) {
        stringResource(if (setup.tagAreaExpanded) R.string.focus_tag_hide else R.string.focus_tag_show)
    } else if (setup.tagAreaExpanded) {
        stringResource(R.string.focus_tag_hide)
    } else {
        stringResource(R.string.focus_tag_selected, setup.tag)
    }
    OutlinedButton(
        onClick = { onTagAreaExpandedChange(!setup.tagAreaExpanded) },
        enabled = fieldsEnabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("focus_tag_disclosure"),
    ) { Text(tagDisclosureLabel) }
    if (setup.tagAreaExpanded) {
        OutlinedTextField(
            value = setup.tag,
            onValueChange = onTagChange,
            label = { Text(stringResource(R.string.focus_tag_label)) },
            singleLine = true,
            enabled = fieldsEnabled,
            isError = setup.showTagError && !tagValid,
            supportingText = {
                if (setup.showTagError && !tagValid) Text(stringResource(R.string.focus_tag_invalid))
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmitStart() }),
            modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused) onTagBlur() }
                .testTag("focus_tag"),
        )
        if (state.tagSuggestions.isNotEmpty()) {
            Text(stringResource(R.string.focus_tag_suggestions), style = MaterialTheme.typography.titleSmall)
            state.tagSuggestions.take(3).forEach { suggestion ->
                TextButton(
                    onClick = { onTagChange(suggestion.name) },
                    enabled = fieldsEnabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .testTag("focus_tag_suggestion_${suggestion.id}"),
                ) { Text(suggestion.name) }
            }
            TextButton(
                onClick = { showTagPicker = true },
                enabled = fieldsEnabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("focus_view_tags"),
            ) { Text(stringResource(R.string.focus_view_tags)) }
        }
    }
    if (setup.startFailure == FocusSetupFailure.VALIDATION) {
        Text(stringResource(R.string.focus_setup_invalid), color = MaterialTheme.colorScheme.error)
    }

    FocusRecentWorkCard(
        state = state.recentWork,
        hasUserAuthoredDraft = setup.hasUserAuthoredDraft,
        canRepeat = !state.busy && !state.operationFailed && !setup.repeatLoading &&
            setup.capturedStart == null && !setup.unresolvedStartPending,
        onRepeatSession = onRepeatSession,
        onRetry = onRetryRecentWork,
    )

    retryReplacementSessionId?.let { sourceId ->
        AlertDialog(
            onDismissRequest = { retryReplacementSessionId = null },
            title = { Text(stringResource(R.string.focus_recent_replace_title)) },
            text = { Text(stringResource(R.string.focus_recent_replace_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        retryReplacementSessionId = null
                        onRepeatSession(sourceId)
                    },
                    modifier = Modifier.testTag("focus_repeat_retry_replace_confirm"),
                ) { Text(stringResource(R.string.focus_recent_replace_setup)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { retryReplacementSessionId = null },
                    modifier = Modifier.testTag("focus_repeat_retry_replace_cancel"),
                ) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (showTagPicker) {
        val tags = state.tagSuggestions.take(10)
        AlertDialog(
            onDismissRequest = { showTagPicker = false },
            title = { Text(stringResource(R.string.focus_tag_picker_title)) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    tags.forEach { suggestion ->
                        TextButton(
                            onClick = {
                                onTagChange(suggestion.name)
                                showTagPicker = false
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .testTag("focus_tag_picker_${suggestion.id}"),
                        ) { Text(suggestion.name) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTagPicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
internal fun FocusSetupStartAction(
    state: FocusScreenState,
    onStartAttempted: () -> Unit,
    onStart: (FocusStartInput) -> Unit,
) {
    val setup = state.setup
    val input = setup.normalizedInput
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .testTag("focus_start_action"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.focus_scope_near_start), style = MaterialTheme.typography.bodySmall)
            LumaButton(
                onClick = {
                    onStartAttempted()
                    input?.let(onStart)
                },
                enabled = !state.busy && !setup.repeatLoading && !state.operationFailed && setup.canStart && input != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("focus_start"),
            ) {
                Text(if (state.busy) stringResource(R.string.focus_saving) else stringResource(R.string.focus_start))
            }
        }
    }
}
