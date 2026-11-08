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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
    onOpenSettings: () -> Unit = {},
    onOpenTaskPicker: (() -> Unit)? = null,
    onClearTaskSelection: () -> Unit = {},
    onRequestProtectedFocusUnlock: () -> Unit = {},
    onTimerModeChange: (FocusTimerMode) -> Unit = {},
    onTimedMinutesChange: (String) -> Unit = {},
    onNameBlur: () -> Unit = {},
    onTagBlur: () -> Unit = {},
    onTagAreaExpandedChange: (Boolean) -> Unit = {},
    onTimedMinutesBlur: () -> Unit = {},
    onStartAttempted: () -> Unit = {},
    onStartBreak: (Int) -> Unit = {},
    onEndBreak: () -> Unit = {},
    focusRepository: FocusRepository? = null,
    focusProductRepository: FocusProductRepository? = null,
    taskContentReadable: Boolean = true,
    onTerminalSessionMissing: (java.util.UUID, java.util.UUID) -> Unit = { _, _ -> },
    onTaskSessionChanged: (java.util.UUID, Long, java.util.UUID?, java.util.UUID?) -> Unit = { _, _, _, _ -> },
    onMarkTaskComplete: (java.util.UUID, Long, java.util.UUID, java.util.UUID) -> Unit = { _, _, _, _ -> },
    onRetryTaskCompletion: (java.util.UUID, Long, java.util.UUID, java.util.UUID) -> Unit = { _, _, _, _ -> },
    onUnlinkTask: (java.util.UUID, Long, java.util.UUID, java.util.UUID) -> Unit = { _, _, _, _ -> },
    onRetryTaskUnlink: (java.util.UUID, Long, java.util.UUID, java.util.UUID) -> Unit = { _, _, _, _ -> },
    showInvitationDraftConfirmation: Boolean = false,
    onConfirmInvitationDraftReplacement: () -> Unit = {},
    onDismissInvitationDraftReplacement: () -> Unit = {},
    onRetryRecentWork: () -> Unit = {},
) {
    val runtime = state.runtime
    val session = runtime.session
    var setupRequestedForSession by rememberSaveable { mutableStateOf<String?>(null) }
    val showTerminal = session?.let {
        shouldShowFocusTerminal(it.id, setupRequestedForSession, state.setup.databaseGeneration)
    } == true && !state.setup.isRepeatDraft
    val showSetupAction = !runtime.loading && !runtime.loadFailed &&
        !(runtime.recoveryRequired && session != null) &&
        session?.state !in setOf(FocusState.RUNNING, FocusState.PAUSED) &&
        !(session?.state in setOf(FocusState.FINISHED, FocusState.INTERRUPTED) && showTerminal)
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
        bottomBar = {
            if (showSetupAction) {
                FocusSetupStartAction(
                    state = state,
                    onStartAttempted = onStartAttempted,
                    onStart = onStart,
                )
            }
        },
    ) { pagePadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(pagePadding).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (runtime.breakState != FocusBreakState.Idle && !showTerminal) {
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
                    taskContentReadable = taskContentReadable,
                    interrupted = false,
                    hasPendingDestination = hasPendingDestination,
                    onBack = onBack,
                    onStartAnother = {
                        onStartAnother()
                        session?.let { setupRequestedForSession = focusTerminalRequestKey(it.id, state.setup.databaseGeneration) }
                    },
                    onRepeatSession = onRepeatSession,
                    onStartBreak = onStartBreak,
                    onEndBreak = onEndBreak,
                    onTaskSessionChanged = onTaskSessionChanged,
                    onMarkTaskComplete = onMarkTaskComplete,
                    onRetryTaskCompletion = onRetryTaskCompletion,
                    onUnlinkTask = onUnlinkTask,
                    onRetryTaskUnlink = onRetryTaskUnlink,
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
                    taskContentReadable = taskContentReadable,
                    interrupted = true,
                    hasPendingDestination = hasPendingDestination,
                    onBack = onBack,
                    onStartAnother = {
                        onStartAnother()
                        session?.let { setupRequestedForSession = focusTerminalRequestKey(it.id, state.setup.databaseGeneration) }
                    },
                    onRepeatSession = onRepeatSession,
                    onStartBreak = onStartBreak,
                    onEndBreak = onEndBreak,
                    onTaskSessionChanged = onTaskSessionChanged,
                    onMarkTaskComplete = onMarkTaskComplete,
                    onRetryTaskCompletion = onRetryTaskCompletion,
                    onUnlinkTask = onUnlinkTask,
                    onRetryTaskUnlink = onRetryTaskUnlink,
                    onRetry = onRetry,
                    onEmergencyExit = onEmergencyExit,
                    onOpenPendingDestination = onOpenPendingDestination,
                    onOpenHistory = onOpenHistory,
                    onTerminalSessionMissing = onTerminalSessionMissing,
                )
                else -> SetupContent(
                    state = state,
                    onNameChange = onNameChange,
                    onNameBlur = onNameBlur,
                    onTagChange = onTagChange,
                    onTagBlur = onTagBlur,
                    onTagAreaExpandedChange = onTagAreaExpandedChange,
                    onTimerModeChange = onTimerModeChange,
                    onTimedMinutesChange = onTimedMinutesChange,
                    onTimedMinutesBlur = onTimedMinutesBlur,
                    onPeriodChange = onPeriodChange,
                    onRetryInsights = onRetryInsights,
                    onRetryRecentWork = onRetryRecentWork,
                    onRepeatSession = onRepeatSession,
                    onRetry = onRetry,
                    onEmergencyExit = onEmergencyExit,
                    onOpenHistory = onOpenHistory,
                    onOpenSettings = onOpenSettings,
                    canOpenSecondaryDestinations = !runtime.navigationGuarded &&
                        !runtime.recoveryRequired && !state.busy &&
                        !state.setup.unresolvedStartPending && state.setup.capturedStart == null &&
                        !hasPendingDestination,
                    onOpenTaskPicker = onOpenTaskPicker,
                    onClearTaskSelection = onClearTaskSelection,
                    onRequestProtectedFocusUnlock = onRequestProtectedFocusUnlock,
                )
            }
        }
    }

    if (showInvitationDraftConfirmation) {
        LumaAlertDialog(
            onDismissRequest = onDismissInvitationDraftReplacement,
            title = { Text(stringResource(R.string.focus_invitation_replace_draft_title)) },
            text = { Text(stringResource(R.string.focus_invitation_replace_draft_body)) },
            confirmButton = {
                TextButton(
                    onClick = onConfirmInvitationDraftReplacement,
                    modifier = Modifier.testTag("focus_invitation_replace_confirm"),
                ) { Text(stringResource(R.string.focus_invitation_replace_setup)) }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissInvitationDraftReplacement,
                    modifier = Modifier.testTag("focus_invitation_keep_draft"),
                ) { Text(stringResource(R.string.focus_invitation_keep_draft)) }
            },
            modifier = Modifier.testTag("focus_invitation_replace_dialog"),
        )
    }

    if (state.backPromptVisible && runtime.navigationGuarded) {
        LumaAlertDialog(
            onDismissRequest = onDismissBackPrompt,
            title = { Text(stringResource(R.string.focus_back_title)) },
            text = { Text(stringResource(R.string.focus_back_message)) },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        onClick = { onLeaveAfterAction(FocusAction.PAUSE) },
                        enabled = !state.busy && session?.state == FocusState.RUNNING,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("focus_back_pause"),
                    ) { Text(stringResource(R.string.focus_back_pause_leave)) }
                    TextButton(
                        onClick = { onLeaveAfterAction(FocusAction.STOP) },
                        enabled = !state.busy && session != null && session.state in setOf(FocusState.RUNNING, FocusState.PAUSED),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("focus_back_stop"),
                    ) { Text(stringResource(R.string.focus_back_end_save)) }
                    TextButton(
                        onClick = onDismissBackPrompt,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("focus_back_stay"),
                    ) { Text(stringResource(R.string.focus_back_keep_focusing)) }
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
    onNameBlur: () -> Unit,
    onTagChange: (String) -> Unit,
    onTagBlur: () -> Unit,
    onTagAreaExpandedChange: (Boolean) -> Unit,
    onTimerModeChange: (FocusTimerMode) -> Unit,
    onTimedMinutesChange: (String) -> Unit,
    onTimedMinutesBlur: () -> Unit,
    onPeriodChange: (FocusPeriod) -> Unit,
    onRetryInsights: () -> Unit,
    onRetryRecentWork: () -> Unit,
    onRepeatSession: (java.util.UUID) -> Unit,
    onRetry: () -> Unit,
    onEmergencyExit: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    canOpenSecondaryDestinations: Boolean,
    onOpenTaskPicker: (() -> Unit)?,
    onClearTaskSelection: () -> Unit,
    onRequestProtectedFocusUnlock: () -> Unit,
) {
    val setup = state.setup
    if (canOpenSecondaryDestinations) {
        FocusDestinationActions(onOpenHistory = onOpenHistory, onOpenSettings = onOpenSettings)
    }
    FocusSetupContent(
        state = state,
        onNameChange = onNameChange,
        onNameBlur = onNameBlur,
        onTagChange = onTagChange,
        onTagBlur = onTagBlur,
        onTagAreaExpandedChange = onTagAreaExpandedChange,
        onTimerModeChange = onTimerModeChange,
        onTimedMinutesChange = onTimedMinutesChange,
        onTimedMinutesBlur = onTimedMinutesBlur,
        onRepeatSession = onRepeatSession,
        onOpenTaskPicker = onOpenTaskPicker,
        onClearTaskSelection = onClearTaskSelection,
        onRetryRecentWork = onRetryRecentWork,
    )
    FocusInsightsCard(
        state = state.insights,
        period = setup.period,
        onPeriodChange = onPeriodChange,
        onRetry = onRetryInsights,
        onRequestUnlock = onRequestProtectedFocusUnlock,
    )
    FailureContent(state, onRetry, onEmergencyExit)
}

@Composable
private fun FocusDestinationActions(
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(
            onClick = onOpenHistory,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("focus_history_open"),
        ) {
            Text(stringResource(R.string.focus_open_history))
        }
        TextButton(
            onClick = onOpenSettings,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("focus_settings_open"),
        ) {
            Text(stringResource(R.string.settings))
        }
    }
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
    taskContentReadable: Boolean,
    interrupted: Boolean,
    hasPendingDestination: Boolean,
    onBack: () -> Unit,
    onStartAnother: () -> Unit,
    onRepeatSession: (java.util.UUID) -> Unit,
    onStartBreak: (Int) -> Unit,
    onEndBreak: () -> Unit,
    onRetry: () -> Unit,
    onEmergencyExit: () -> Unit,
    onOpenPendingDestination: () -> Unit,
    onOpenHistory: () -> Unit,
    onTerminalSessionMissing: (java.util.UUID, java.util.UUID) -> Unit,
    onTaskSessionChanged: (java.util.UUID, Long, java.util.UUID?, java.util.UUID?) -> Unit,
    onMarkTaskComplete: (java.util.UUID, Long, java.util.UUID, java.util.UUID) -> Unit,
    onRetryTaskCompletion: (java.util.UUID, Long, java.util.UUID, java.util.UUID) -> Unit,
    onUnlinkTask: (java.util.UUID, Long, java.util.UUID, java.util.UUID) -> Unit,
    onRetryTaskUnlink: (java.util.UUID, Long, java.util.UUID, java.util.UUID) -> Unit,
) {
    FocusCompletionContent(
        state = state,
        session = session,
        focusRepository = focusRepository,
        focusProductRepository = focusProductRepository,
        taskContentReadable = taskContentReadable,
        interrupted = interrupted,
        hasPendingDestination = hasPendingDestination,
        onDone = onBack,
        onStartAnother = onStartAnother,
        onRepeatSession = onRepeatSession,
        onStartBreak = onStartBreak,
        onEndBreak = onEndBreak,
        onRetry = onRetry,
        onEmergencyExit = onEmergencyExit,
        onOpenPendingDestination = onOpenPendingDestination,
        onOpenHistory = onOpenHistory,
        onTerminalSessionMissing = onTerminalSessionMissing,
        onTaskSessionChanged = onTaskSessionChanged,
        onMarkTaskComplete = onMarkTaskComplete,
        onRetryTaskCompletion = onRetryTaskCompletion,
        onUnlinkTask = onUnlinkTask,
        onRetryTaskUnlink = onRetryTaskUnlink,
    )
}

@Composable
internal fun FocusBreakCountdown(
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
internal fun FailureContent(
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
