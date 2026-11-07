package com.luma.lifeos.ui.focus

import com.luma.lifeos.ui.common.LumaButton

import com.luma.lifeos.ui.common.LumaAlertDialog

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import com.luma.lifeos.R
import com.luma.lifeos.domain.focus.FocusAction
import com.luma.lifeos.domain.focus.FocusRecoveryChoice
import com.luma.lifeos.domain.focus.FocusSession
import com.luma.lifeos.domain.focus.FocusProductRepository
import com.luma.lifeos.domain.focus.FocusRepository
import com.luma.lifeos.domain.focus.FocusState
import com.luma.lifeos.domain.focus.FocusStartInput
import com.luma.lifeos.domain.focus.FocusTimerMode
import com.luma.lifeos.focus.FocusBreakState
import com.luma.lifeos.domain.focus.normalizeFocusStartInput
import com.luma.lifeos.focus.AllowedFocusApps
import com.luma.lifeos.focus.FocusScreenState
import com.luma.lifeos.ui.common.AppPageScaffold
import com.luma.lifeos.ui.common.PageNavigation

@Composable
internal fun FocusScreen(
    state: FocusScreenState,
    allowedApps: List<AllowedFocusApps.App>,
    hasPendingDestination: Boolean,
    onBack: () -> Unit,
    onShowBackPrompt: () -> Unit,
    onDismissBackPrompt: () -> Unit,
    onStart: (FocusStartInput) -> Unit,
    onRepeatSession: (java.util.UUID) -> Unit = {},
    onStartAnother: () -> Unit = {},
    onNameChange: (String) -> Unit,
    onTagChange: (String) -> Unit,
    onPeriodChange: (FocusPeriod) -> Unit,
    onRetryInsights: () -> Unit,
    onAction: (FocusAction) -> Unit,
    onLeaveAfterAction: (FocusAction) -> Unit,
    onResolveRecovery: (FocusRecoveryChoice) -> Unit,
    onRetry: () -> Unit,
    onRetryInitialization: () -> Unit,
    onEmergencyExit: () -> Unit,
    onDismissEmergencyExit: () -> Unit,
    onConfirmEmergencyExit: () -> Unit,
    onLaunchApp: (String) -> Unit,
    onOpenPendingDestination: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenTaskPicker: (() -> Unit)? = null,
    onClearTaskSelection: () -> Unit = {},
    onTimerModeChange: (FocusTimerMode) -> Unit = {},
    onTimedMinutesChange: (String) -> Unit = {},
    onStartBreak: (Int) -> Unit = {},
    onEndBreak: () -> Unit = {},
    focusRepository: FocusRepository? = null,
    focusProductRepository: FocusProductRepository? = null,
    onTerminalSessionMissing: (java.util.UUID, java.util.UUID) -> Unit = { _, _ -> },
) {
    val runtime = state.runtime
    val session = runtime.session
    var setupRequestedForSession by rememberSaveable { mutableStateOf<String?>(null) }
    val showTerminal = session?.let {
        shouldShowFocusTerminal(it.id, setupRequestedForSession, state.setup.databaseGeneration)
    } == true && !state.setup.isRepeatDraft
    BackHandler {
        when {
            runtime.navigationGuarded -> onShowBackPrompt()
            runtime.recoveryRequired -> Unit
            else -> onBack()
        }
    }

    AppPageScaffold(
        title = stringResource(R.string.focus_title),
        navigation = PageNavigation.BACK,
        onNavigate = {
            when {
                runtime.navigationGuarded -> onShowBackPrompt()
                runtime.recoveryRequired -> Unit
                else -> onBack()
            }
        },
        modifier = Modifier.fillMaxSize().testTag("focus_screen"),
    ) { pagePadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(pagePadding).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (runtime.breakState != FocusBreakState.Idle) {
                FocusBreakCountdown(
                    state = runtime.breakState,
                    busy = state.breakBusy,
                    onEndBreak = onEndBreak,
                )
            }
            when {
                runtime.loading -> {
                    CircularProgressIndicator(modifier = Modifier.testTag("focus_loading"))
                    Text(stringResource(R.string.focus_loading_message))
                    TextButton(onClick = onEmergencyExit, modifier = Modifier.testTag("focus_emergency_exit")) {
                        Text(stringResource(R.string.focus_emergency_exit))
                    }
                }
                runtime.loadFailed -> {
                    Text(stringResource(R.string.focus_load_error), color = MaterialTheme.colorScheme.error)
                    LumaButton(onClick = onRetryInitialization, modifier = Modifier.testTag("focus_retry_load")) {
                        Text(stringResource(R.string.retry))
                    }
                    TextButton(onClick = onEmergencyExit, modifier = Modifier.testTag("focus_emergency_exit")) {
                        Text(stringResource(R.string.focus_emergency_exit))
                    }
                }
                runtime.recoveryRequired && session != null -> RecoveryContent(
                    session = session,
                    busy = state.busy,
                    onResolveRecovery = onResolveRecovery,
                    onEmergencyExit = onEmergencyExit,
                )
                session?.state == FocusState.RUNNING -> RunningContent(
                    state = state,
                    allowedApps = allowedApps,
                    hasPendingDestination = hasPendingDestination,
                    onAction = onAction,
                    onRetry = onRetry,
                    onEmergencyExit = onEmergencyExit,
                    onLaunchApp = onLaunchApp,
                    onOpenPendingDestination = onOpenPendingDestination,
                )
                session?.state == FocusState.PAUSED -> PausedContent(
                    state = state,
                    session = session,
                    hasPendingDestination = hasPendingDestination,
                    onAction = onAction,
                    onRetry = onRetry,
                    onEmergencyExit = onEmergencyExit,
                    onOpenPendingDestination = onOpenPendingDestination,
                    onOpenHistory = onOpenHistory,
                )
                session?.state == FocusState.FINISHED && showTerminal -> TerminalContent(
                    state = state,
                    session = session,
                    focusRepository = focusRepository,
                    focusProductRepository = focusProductRepository,
                    interrupted = false,
                    hasPendingDestination = hasPendingDestination,
                    onBack = onBack,
                    onStartAnother = {
                        onStartAnother()
                        session?.let { setupRequestedForSession = focusTerminalRequestKey(it.id, state.setup.databaseGeneration) }
                    },
                    onRepeatSession = onRepeatSession,
                    onStartBreak = onStartBreak,
                    onRetry = onRetry,
                    onEmergencyExit = onEmergencyExit,
                    onOpenPendingDestination = onOpenPendingDestination,
                    onOpenHistory = onOpenHistory,
                    onTerminalSessionMissing = onTerminalSessionMissing,
                )
                session?.state == FocusState.INTERRUPTED && showTerminal -> TerminalContent(
                    state = state,
                    session = session,
                    focusRepository = focusRepository,
                    focusProductRepository = focusProductRepository,
                    interrupted = true,
                    hasPendingDestination = hasPendingDestination,
                    onBack = onBack,
                    onStartAnother = {
                        onStartAnother()
                        session?.let { setupRequestedForSession = focusTerminalRequestKey(it.id, state.setup.databaseGeneration) }
                    },
                    onRepeatSession = onRepeatSession,
                    onStartBreak = onStartBreak,
                    onRetry = onRetry,
                    onEmergencyExit = onEmergencyExit,
                    onOpenPendingDestination = onOpenPendingDestination,
                    onOpenHistory = onOpenHistory,
                    onTerminalSessionMissing = onTerminalSessionMissing,
                )
                else -> SetupContent(
                    state = state,
                    onNameChange = onNameChange,
                    onTagChange = onTagChange,
                    onTimerModeChange = onTimerModeChange,
                    onTimedMinutesChange = onTimedMinutesChange,
                    onPeriodChange = onPeriodChange,
                    onRetryInsights = onRetryInsights,
                    onStart = onStart,
                    onRepeatSession = onRepeatSession,
                    onRetry = onRetry,
                    onEmergencyExit = onEmergencyExit,
                    onOpenHistory = onOpenHistory,
                    onOpenTaskPicker = onOpenTaskPicker,
                    onClearTaskSelection = onClearTaskSelection,
                )
            }
        }
    }

    if (state.backPromptVisible && runtime.navigationGuarded) {
        LumaAlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.focus_back_title)) },
            text = { Text(stringResource(R.string.focus_back_message)) },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            onLeaveAfterAction(FocusAction.PAUSE)
                        },
                        enabled = !state.busy && session?.state == FocusState.RUNNING,
                        modifier = Modifier.fillMaxWidth().testTag("focus_back_pause"),
                    ) { Text(stringResource(R.string.focus_pause)) }
                    TextButton(
                        onClick = { onLeaveAfterAction(FocusAction.STOP) },
                        enabled = !state.busy && session != null && session.state in setOf(FocusState.RUNNING, FocusState.PAUSED),
                        modifier = Modifier.fillMaxWidth().testTag("focus_back_stop"),
                    ) { Text(stringResource(R.string.focus_stop)) }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissBackPrompt, modifier = Modifier.testTag("focus_back_stay")) {
                    Text(stringResource(R.string.cancel))
                }
            },
            modifier = Modifier.testTag("focus_back_dialog"),
        )
    }

    if (state.emergencyPromptVisible) {
        val warning = if (state.runtime.emergencyMarkerSaved == true) {
            R.string.focus_emergency_marker_saved
        } else {
            R.string.focus_emergency_marker_failed
        }
        LumaAlertDialog(
            onDismissRequest = onDismissEmergencyExit,
            title = { Text(stringResource(R.string.focus_emergency_exit)) },
            text = { Text(stringResource(warning)) },
            confirmButton = {
                TextButton(onClick = onConfirmEmergencyExit, modifier = Modifier.testTag("focus_emergency_confirm")) {
                    Text(stringResource(R.string.focus_exit_now))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissEmergencyExit) { Text(stringResource(R.string.cancel)) }
            },
            modifier = Modifier.testTag("focus_emergency_dialog"),
        )
    }
}

@Composable
private fun SetupContent(
    state: FocusScreenState,
    onNameChange: (String) -> Unit,
    onTagChange: (String) -> Unit,
    onTimerModeChange: (FocusTimerMode) -> Unit,
    onTimedMinutesChange: (String) -> Unit,
    onPeriodChange: (FocusPeriod) -> Unit,
    onRetryInsights: () -> Unit,
    onStart: (FocusStartInput) -> Unit,
    onRepeatSession: (java.util.UUID) -> Unit,
    onRetry: () -> Unit,
    onEmergencyExit: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenTaskPicker: (() -> Unit)?,
    onClearTaskSelection: () -> Unit,
) {
    val setup = state.setup
    val metadataValid = runCatching {
        normalizeFocusStartInput(FocusStartInput(setup.name, setup.tag.takeIf(String::isNotEmpty)))
    }.isSuccess
    val nameValid = runCatching { normalizeFocusStartInput(FocusStartInput(setup.name, null)) }.isSuccess
    val input = setup.normalizedInput
    val tagInvalid = nameValid && setup.tag.isNotEmpty() && !metadataValid
    val fieldsEnabled = !state.busy && !setup.repeatLoading
    Text(stringResource(R.string.focus_intro), style = MaterialTheme.typography.bodyLarge)
    if (setup.repeatLoading) {
        Text(stringResource(R.string.focus_repeat_loading), modifier = Modifier.testTag("focus_repeat_loading"))
    }
    if (setup.repeatLoadFailed) {
        Text(stringResource(R.string.focus_repeat_error), color = MaterialTheme.colorScheme.error)
        setup.repeatSourceSessionId?.let { sourceId ->
            TextButton(
                onClick = { onRepeatSession(sourceId) },
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
        value = setup.name, onValueChange = onNameChange, label = { Text(stringResource(R.string.focus_name_label)) },
        singleLine = true, enabled = fieldsEnabled, isError = !nameValid,
        supportingText = { if (!nameValid) Text(stringResource(R.string.focus_name_invalid)) },
        modifier = Modifier.fillMaxWidth().testTag("focus_name"),
    )
    OutlinedTextField(
        value = setup.tag, onValueChange = onTagChange, label = { Text(stringResource(R.string.focus_tag_label)) },
        singleLine = true, enabled = fieldsEnabled, isError = tagInvalid,
        supportingText = { if (tagInvalid) Text(stringResource(R.string.focus_tag_invalid)) },
        modifier = Modifier.fillMaxWidth().testTag("focus_tag"),
    )
    Text(stringResource(R.string.focus_timer_mode_label), style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
    if (setup.timerMode == FocusTimerMode.TIMED) {
        OutlinedTextField(
            value = setup.timedMinutesText,
            onValueChange = onTimedMinutesChange,
            label = { Text(stringResource(R.string.focus_timed_minutes_label)) },
            singleLine = true,
            enabled = fieldsEnabled,
            isError = setup.timedMinutesText.toIntOrNull()?.let { it !in 1..180 } != false,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            supportingText = {
                if (setup.timedMinutesText.toIntOrNull()?.let { it in 1..180 } != true) {
                    Text(stringResource(R.string.focus_timed_minutes_invalid))
                }
            },
            modifier = Modifier.fillMaxWidth().testTag("focus_timed_minutes"),
        )
        TextButton(
            onClick = {
                onTimerModeChange(FocusTimerMode.TIMED)
                onTimedMinutesChange("25")
            },
            enabled = fieldsEnabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("focus_25_minute_shortcut"),
        ) { Text(stringResource(R.string.focus_25_minute_shortcut)) }
        Text(stringResource(R.string.focus_alert_limitations), style = MaterialTheme.typography.bodyMedium)
    }
    if (state.tagSuggestions.isNotEmpty()) {
        Text(stringResource(R.string.focus_tag_suggestions), style = MaterialTheme.typography.titleSmall)
        state.tagSuggestions.forEach { suggestion ->
            TextButton(
                onClick = { onTagChange(suggestion.name) },
                enabled = fieldsEnabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("focus_tag_suggestion_${suggestion.id}"),
            ) { Text(suggestion.name) }
        }
    }
    if (setup.startFailure == FocusSetupFailure.VALIDATION) {
        Text(stringResource(R.string.focus_setup_invalid), color = MaterialTheme.colorScheme.error)
    }
    LumaButton(
        onClick = { input?.let(onStart) },
        enabled = !state.busy && !setup.repeatLoading && !state.operationFailed && setup.canStart && input != null,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("focus_start"),
    ) {
        Text(if (state.busy) stringResource(R.string.focus_saving) else stringResource(R.string.focus_start))
    }
    FocusInsightsCard(
        state = state.insights,
        period = setup.period,
        onPeriodChange = onPeriodChange,
        onRetry = onRetryInsights,
    )
    OpenHistoryButton(onOpenHistory)
    FailureContent(state, onRetry, onEmergencyExit)
}

@Composable
private fun OpenHistoryButton(onOpenHistory: () -> Unit) {
    TextButton(onClick = onOpenHistory, modifier = Modifier.fillMaxWidth().testTag("focus_history_open")) {
        Text(stringResource(R.string.focus_open_history))
    }
}

@Composable
private fun SessionMetadata(session: FocusSession) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            session.metadata.name ?: stringResource(R.string.focus_session_fallback),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag("focus_session_name"),
        )
        Text(
            session.metadata.tag?.name ?: stringResource(R.string.focus_tag_untagged),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("focus_session_tag"),
        )
    }
}

@Composable
private fun RunningContent(
    state: FocusScreenState,
    allowedApps: List<AllowedFocusApps.App>,
    hasPendingDestination: Boolean,
    onAction: (FocusAction) -> Unit,
    onRetry: () -> Unit,
    onEmergencyExit: () -> Unit,
    onLaunchApp: (String) -> Unit,
    onOpenPendingDestination: () -> Unit,
) {
    Text(
        stringResource(R.string.focus_running),
        modifier = Modifier.semantics { heading() }.testTag("focus_running_label"),
        style = MaterialTheme.typography.titleLarge,
    )
    state.runtime.session?.let { SessionMetadata(it) }
    val session = state.runtime.session
    val targetMillis = session?.timer?.targetMillis
    if (targetMillis != null) {
        val remaining = (targetMillis - state.runtime.displayedMillis).coerceAtLeast(0L)
        Text(stringResource(R.string.focus_remaining_label), style = MaterialTheme.typography.titleSmall)
        Text(
            DateUtils.formatElapsedTime(remaining / 1_000),
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.fillMaxWidth().testTag("focus_remaining"),
        )
        Text(stringResource(R.string.focus_alert_limitations), style = MaterialTheme.typography.bodyMedium)
    } else {
        Text(
            DateUtils.formatElapsedTime(state.runtime.displayedMillis / 1_000),
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.fillMaxWidth().testTag("focus_elapsed"),
        )
    }
    Text(stringResource(R.string.focus_background_note), style = MaterialTheme.typography.bodyMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LumaButton(
            onClick = { onAction(FocusAction.PAUSE) },
            enabled = !state.busy,
            modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("focus_pause"),
        ) { Text(stringResource(R.string.focus_pause)) }
        OutlinedButton(
            onClick = { onAction(FocusAction.STOP) },
            enabled = !state.busy,
            modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("focus_stop"),
        ) { Text(stringResource(R.string.focus_stop)) }
    }
    TextButton(
        onClick = onEmergencyExit,
        modifier = Modifier.fillMaxWidth().testTag("focus_emergency_exit"),
    ) { Text(stringResource(R.string.focus_emergency_exit)) }
    if (allowedApps.isNotEmpty()) {
        Text(stringResource(R.string.focus_shortcuts), style = MaterialTheme.typography.titleMedium)
        allowedApps.forEach { app ->
            OutlinedButton(
                onClick = { onLaunchApp(app.packageName) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("focus_app_${app.packageName}"),
            ) { Text("${app.label} · ${app.packageName}") }
        }
    }
    if (hasPendingDestination) PendingDestinationButton(
        enabled = !state.runtime.navigationGuarded && !state.runtime.recoveryRequired,
        onOpenPendingDestination = onOpenPendingDestination,
    )
    FailureContent(state, onRetry, onEmergencyExit, showEmergencyAction = false)
}

@Composable
private fun PausedContent(
    state: FocusScreenState,
    session: FocusSession,
    hasPendingDestination: Boolean,
    onAction: (FocusAction) -> Unit,
    onRetry: () -> Unit,
    onEmergencyExit: () -> Unit,
    onOpenPendingDestination: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    Text(stringResource(R.string.focus_paused), style = MaterialTheme.typography.titleLarge)
    SessionMetadata(session)
    val targetMillis = session.timer.targetMillis
    if (targetMillis != null) {
        Text(stringResource(R.string.focus_remaining_label), style = MaterialTheme.typography.titleSmall)
        Text(DateUtils.formatElapsedTime((targetMillis - session.creditedMillis).coerceAtLeast(0L) / 1_000),
            style = MaterialTheme.typography.displaySmall, modifier = Modifier.testTag("focus_remaining"))
    } else {
        Text(DateUtils.formatElapsedTime(session.creditedMillis / 1_000), style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.testTag("focus_elapsed"))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LumaButton(onClick = { onAction(FocusAction.RESUME) }, enabled = !state.busy,
            modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("focus_resume")) {
            Text(stringResource(R.string.focus_resume))
        }
        OutlinedButton(onClick = { onAction(FocusAction.STOP) }, enabled = !state.busy,
            modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("focus_stop")) {
            Text(stringResource(R.string.focus_stop))
        }
    }
    Text(stringResource(R.string.focus_paused_leave_hint))
    OpenHistoryButton(onOpenHistory)
    if (hasPendingDestination) PendingDestinationButton(onOpenPendingDestination = onOpenPendingDestination)
    FailureContent(state, onRetry, onEmergencyExit)
}

@Composable
private fun RecoveryContent(
    session: FocusSession,
    busy: Boolean,
    onResolveRecovery: (FocusRecoveryChoice) -> Unit,
    onEmergencyExit: () -> Unit,
) {
    Text(stringResource(R.string.focus_recovery_title), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.focus_recovery_body))
    SessionMetadata(session)
    val recoveryTime = session.timer.targetMillis?.let { target ->
        Text(stringResource(R.string.focus_remaining_label), style = MaterialTheme.typography.titleSmall)
        (target - session.creditedMillis).coerceAtLeast(0L)
    } ?: session.creditedMillis
    Text(DateUtils.formatElapsedTime(recoveryTime / 1_000), style = MaterialTheme.typography.displaySmall)
    LumaButton(onClick = { onResolveRecovery(FocusRecoveryChoice.CONTINUE_ELAPSED) }, enabled = !busy,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("focus_recovery_continue")) {
        Text(stringResource(R.string.focus_recovery_continue))
    }
    OutlinedButton(onClick = { onResolveRecovery(FocusRecoveryChoice.END_AT_CHECKPOINT) }, enabled = !busy,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("focus_recovery_end")) {
        Text(stringResource(R.string.focus_recovery_end))
    }
    TextButton(onClick = onEmergencyExit, modifier = Modifier.testTag("focus_emergency_exit")) {
        Text(stringResource(R.string.focus_emergency_exit))
    }
}

@Composable
private fun TerminalContent(
    state: FocusScreenState,
    session: FocusSession,
    focusRepository: FocusRepository?,
    focusProductRepository: FocusProductRepository?,
    interrupted: Boolean,
    hasPendingDestination: Boolean,
    onBack: () -> Unit,
    onStartAnother: () -> Unit,
    onRepeatSession: (java.util.UUID) -> Unit,
    onStartBreak: (Int) -> Unit,
    onRetry: () -> Unit,
    onEmergencyExit: () -> Unit,
    onOpenPendingDestination: () -> Unit,
    onOpenHistory: () -> Unit,
    onTerminalSessionMissing: (java.util.UUID, java.util.UUID) -> Unit,
) {
    val title = when {
        interrupted -> R.string.focus_interrupted
        session.terminalReason == com.luma.lifeos.domain.focus.FocusTerminalReason.TARGET_REACHED -> R.string.focus_target_reached
        else -> R.string.focus_finished
    }
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.testTag(if (interrupted) "focus_interrupted" else "focus_finished"),
    )
    SessionMetadata(session)
    Text(DateUtils.formatElapsedTime(session.creditedMillis / 1_000), style = MaterialTheme.typography.displaySmall)
    focusRepository?.let { repository ->
        FocusReflectionEditor(
            session = session,
            repository = repository,
            productRepository = focusProductRepository,
            generation = state.runtime.databaseGeneration,
            isFocusReadable = state.focusContentReadable,
            showWhenEmpty = true,
            onSessionMissing = onTerminalSessionMissing,
        )
    }
    if (interrupted) Text(stringResource(R.string.focus_interrupted_gap))
    if (state.runtime.breakState == FocusBreakState.Idle) {
        var breakMinutes by rememberSaveable(session.id.toString()) { mutableStateOf("5") }
        val duration = breakMinutes.toIntOrNull()?.takeIf { it in 1..60 }
        OutlinedTextField(
            value = breakMinutes,
            onValueChange = { value ->
                breakMinutes = value.mapNotNull { character ->
                    Character.digit(character, 10).takeIf { it >= 0 }?.digitToChar()
                }.take(2).joinToString("")
            },
            label = { Text(stringResource(R.string.focus_break_minutes_label)) },
            singleLine = true,
            enabled = !state.breakBusy,
            isError = duration == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            supportingText = { if (duration == null) Text(stringResource(R.string.focus_break_minutes_invalid)) },
            modifier = Modifier.fillMaxWidth().testTag("focus_break_minutes"),
        )
        Text(stringResource(R.string.focus_alert_limitations), style = MaterialTheme.typography.bodyMedium)
        LumaButton(
            onClick = { duration?.let(onStartBreak) },
            enabled = !state.breakBusy && duration != null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("focus_start_break"),
        ) { Text(stringResource(R.string.focus_start_break)) }
        if (state.breakOperationFailed) {
            Text(stringResource(R.string.focus_break_operation_error), color = MaterialTheme.colorScheme.error)
        }
    }
    val repeatId = state.setup.repeatSourceSessionId ?: session.id
    if (state.setup.repeatLoading) {
        Text(stringResource(R.string.focus_repeat_loading), modifier = Modifier.testTag("focus_repeat_loading"))
    }
    if (state.setup.repeatLoadFailed) {
        Text(stringResource(R.string.focus_repeat_error), color = MaterialTheme.colorScheme.error)
    }
    OutlinedButton(
        onClick = { onRepeatSession(repeatId) },
        enabled = !state.busy && !state.setup.repeatLoading,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("focus_repeat"),
    ) { Text(stringResource(R.string.focus_repeat)) }
    LumaButton(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("focus_done")) {
        Text(stringResource(R.string.focus_done))
    }
    OutlinedButton(onClick = onStartAnother, enabled = !state.busy,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("focus_start_another")) {
        Text(stringResource(R.string.focus_start_another))
    }
    OpenHistoryButton(onOpenHistory)
    if (hasPendingDestination) PendingDestinationButton(onOpenPendingDestination = onOpenPendingDestination)
    FailureContent(state, onRetry, onEmergencyExit)
}

@Composable
private fun FocusBreakCountdown(
    state: FocusBreakState,
    busy: Boolean,
    onEndBreak: () -> Unit,
) {
    when (state) {
        FocusBreakState.Idle -> Unit
        is FocusBreakState.Running -> Text(
            stringResource(R.string.focus_break_remaining, DateUtils.formatElapsedTime((state.remainingMillis + 999L) / 1_000L)),
            modifier = Modifier.fillMaxWidth().testTag("focus_break_countdown"),
            style = MaterialTheme.typography.titleLarge,
        )
        is FocusBreakState.Expired -> Text(
            stringResource(R.string.focus_break_expired),
            modifier = Modifier.fillMaxWidth().testTag("focus_break_expired"),
            style = MaterialTheme.typography.titleLarge,
        )
    }
    if (state != FocusBreakState.Idle) {
        OutlinedButton(
            onClick = onEndBreak,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("focus_end_break"),
        ) { Text(stringResource(R.string.focus_end_break)) }
    }
}

@Composable
private fun PendingDestinationButton(enabled: Boolean = true, onOpenPendingDestination: () -> Unit) {
    Text(stringResource(R.string.focus_pending_destination))
    LumaButton(onClick = onOpenPendingDestination, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        .testTag("focus_open_pending_destination")) {
        Text(stringResource(R.string.focus_open_pending))
    }
}

@Composable
private fun FailureContent(
    state: FocusScreenState,
    onRetry: () -> Unit,
    onEmergencyExit: () -> Unit,
    showEmergencyAction: Boolean = true,
) {
    when {
        state.operationFailed || state.runtime.writeFailed -> {
            Text(stringResource(R.string.focus_write_error), color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("focus_write_error"))
            TextButton(onClick = onRetry, enabled = !state.busy, modifier = Modifier.testTag("focus_retry")) {
                Text(stringResource(R.string.retry))
            }
            if (showEmergencyAction) TextButton(onClick = onEmergencyExit, modifier = Modifier.testTag("focus_emergency_exit")) {
                Text(stringResource(R.string.focus_emergency_exit))
            }
        }
        state.runtime.checkpointFailed -> {
            Text(stringResource(R.string.focus_checkpoint_error), color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("focus_checkpoint_error"))
            TextButton(onClick = onRetry, enabled = !state.busy, modifier = Modifier.testTag("focus_retry")) {
                Text(stringResource(R.string.retry))
            }
            if (showEmergencyAction) TextButton(onClick = onEmergencyExit, modifier = Modifier.testTag("focus_emergency_exit")) {
                Text(stringResource(R.string.focus_emergency_exit))
            }
        }
        state.shortcutUnavailable || state.runtime.shortcutFailed -> {
            Text(
                stringResource(R.string.focus_app_unavailable),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("focus_app_unavailable"),
            )
            TextButton(
                onClick = onRetry,
                enabled = !state.busy,
                modifier = Modifier.testTag("focus_retry_shortcut"),
            ) { Text(stringResource(R.string.retry)) }
        }
    }
}
