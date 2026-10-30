package com.luma.lifeos.ui.focus

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.luma.lifeos.domain.focus.FocusState
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
    onStart: () -> Unit,
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
) {
    val runtime = state.runtime
    val session = runtime.session
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
                    Button(onClick = onRetryInitialization, modifier = Modifier.testTag("focus_retry_load")) {
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
                session?.state == FocusState.FINISHED -> TerminalContent(
                    state = state,
                    session = session,
                    interrupted = false,
                    hasPendingDestination = hasPendingDestination,
                    onBack = onBack,
                    onStart = onStart,
                    onRetry = onRetry,
                    onEmergencyExit = onEmergencyExit,
                    onOpenPendingDestination = onOpenPendingDestination,
                    onOpenHistory = onOpenHistory,
                )
                session?.state == FocusState.INTERRUPTED -> TerminalContent(
                    state = state,
                    session = session,
                    interrupted = true,
                    hasPendingDestination = hasPendingDestination,
                    onBack = onBack,
                    onStart = onStart,
                    onRetry = onRetry,
                    onEmergencyExit = onEmergencyExit,
                    onOpenPendingDestination = onOpenPendingDestination,
                    onOpenHistory = onOpenHistory,
                )
                else -> SetupContent(state, onStart, onRetry, onEmergencyExit, onOpenHistory)
            }
        }
    }

    if (state.backPromptVisible && runtime.navigationGuarded) {
        AlertDialog(
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
        AlertDialog(
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
    onStart: () -> Unit,
    onRetry: () -> Unit,
    onEmergencyExit: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    Text(stringResource(R.string.focus_intro), style = MaterialTheme.typography.bodyLarge)
    Button(onClick = onStart, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("focus_start")) {
        Text(if (state.busy) stringResource(R.string.focus_saving) else stringResource(R.string.focus_start))
    }
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
    Text(
        DateUtils.formatElapsedTime(state.runtime.displayedMillis / 1_000),
        style = MaterialTheme.typography.displayMedium,
        modifier = Modifier.fillMaxWidth().testTag("focus_elapsed"),
    )
    Text(stringResource(R.string.focus_background_note), style = MaterialTheme.typography.bodyMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
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
    Text(DateUtils.formatElapsedTime(session.creditedMillis / 1_000), style = MaterialTheme.typography.displaySmall,
        modifier = Modifier.testTag("focus_elapsed"))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = { onAction(FocusAction.RESUME) }, enabled = !state.busy,
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
    Text(DateUtils.formatElapsedTime(session.creditedMillis / 1_000), style = MaterialTheme.typography.displaySmall)
    Button(onClick = { onResolveRecovery(FocusRecoveryChoice.CONTINUE_ELAPSED) }, enabled = !busy,
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
    interrupted: Boolean,
    hasPendingDestination: Boolean,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onRetry: () -> Unit,
    onEmergencyExit: () -> Unit,
    onOpenPendingDestination: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    Text(
        stringResource(if (interrupted) R.string.focus_interrupted else R.string.focus_finished),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.testTag(if (interrupted) "focus_interrupted" else "focus_finished"),
    )
    Text(DateUtils.formatElapsedTime(session.creditedMillis / 1_000), style = MaterialTheme.typography.displaySmall)
    if (interrupted) Text(stringResource(R.string.focus_interrupted_gap))
    Button(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("focus_done")) {
        Text(stringResource(R.string.focus_done))
    }
    OutlinedButton(onClick = onStart, enabled = !state.busy,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("focus_start_another")) {
        Text(stringResource(R.string.focus_start_another))
    }
    OpenHistoryButton(onOpenHistory)
    if (hasPendingDestination) PendingDestinationButton(onOpenPendingDestination = onOpenPendingDestination)
    FailureContent(state, onRetry, onEmergencyExit)
}

@Composable
private fun PendingDestinationButton(enabled: Boolean = true, onOpenPendingDestination: () -> Unit) {
    Text(stringResource(R.string.focus_pending_destination))
    Button(onClick = onOpenPendingDestination, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
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
