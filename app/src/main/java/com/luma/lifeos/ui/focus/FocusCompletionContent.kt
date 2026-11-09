package com.luma.lifeos.ui.focus

import com.luma.lifeos.ui.common.LumaUi
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import com.luma.lifeos.ui.common.LumaOutlinedButton
import com.luma.lifeos.ui.common.LumaOutlinedTextField
import androidx.compose.material3.Text
import com.luma.lifeos.ui.common.LumaTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.focus.FocusProductRepository
import com.luma.lifeos.domain.focus.FocusRepository
import com.luma.lifeos.domain.focus.FocusSession
import com.luma.lifeos.domain.focus.FocusState
import com.luma.lifeos.domain.focus.FocusTerminalReason
import com.luma.lifeos.domain.focus.FocusTimerMode
import com.luma.lifeos.focus.FocusBreakState
import com.luma.lifeos.focus.FocusScreenState
import com.luma.lifeos.ui.common.LumaAlertDialog
import com.luma.lifeos.ui.common.LumaButton
import java.util.UUID

@Composable
internal fun FocusCompletionContent(
    state: FocusScreenState,
    session: FocusSession,
    focusRepository: FocusRepository?,
    focusProductRepository: FocusProductRepository?,
    taskContentReadable: Boolean,
    interrupted: Boolean,
    hasPendingDestination: Boolean,
    onDone: () -> Unit,
    onStartAnother: () -> Unit,
    onRepeatSession: (UUID) -> Unit,
    onStartBreak: (Int) -> Unit,
    onEndBreak: () -> Unit,
    onRetry: () -> Unit,
    onEmergencyExit: () -> Unit,
    onOpenPendingDestination: () -> Unit,
    onOpenHistory: () -> Unit,
    onTerminalSessionMissing: (UUID, UUID) -> Unit,
    onTaskSessionChanged: (UUID, Long, UUID?, UUID?) -> Unit,
    onMarkTaskComplete: (UUID, Long, UUID, UUID) -> Unit,
    onRetryTaskCompletion: (UUID, Long, UUID, UUID) -> Unit,
    onUnlinkTask: (UUID, Long, UUID, UUID) -> Unit,
    onRetryTaskUnlink: (UUID, Long, UUID, UUID) -> Unit,
    navigationState: FocusReflectionNavigationState? = null,
) {
    val reflectionNavigation = navigationState ?: remember(session.id, state.runtime.databaseGeneration) {
        FocusReflectionNavigationState()
    }
    var pendingNavigation by remember(session.id, state.runtime.databaseGeneration) {
        mutableStateOf<(() -> Unit)?>(null)
    }
    var showReflectionLeaveDialog by remember(session.id, state.runtime.databaseGeneration) {
        mutableStateOf(false)
    }

    fun finishPendingNavigation() {
        val navigation = pendingNavigation
        pendingNavigation = null
        showReflectionLeaveDialog = false
        navigation?.invoke()
    }

    fun keepEditing() {
        pendingNavigation = null
        showReflectionLeaveDialog = false
    }

    fun requestNavigation(navigation: () -> Unit) {
        if (reflectionNavigation.hasUnsavedChanges) {
            pendingNavigation = navigation
            showReflectionLeaveDialog = true
        } else {
            navigation()
        }
    }

    reflectionNavigation.requestNavigation = ::requestNavigation
    LaunchedEffect(
        reflectionNavigation.hasUnsavedChanges,
        reflectionNavigation.isSaving,
        pendingNavigation,
    ) {
        if (pendingNavigation != null && !reflectionNavigation.hasUnsavedChanges &&
            !reflectionNavigation.isSaving
        ) finishPendingNavigation()
    }

    val terminalLabel = when {
        interrupted || session.state == FocusState.INTERRUPTED -> R.string.focus_terminal_interrupted
        session.terminalReason == FocusTerminalReason.TARGET_REACHED -> R.string.focus_terminal_target_reached
        session.timer.mode == FocusTimerMode.TIMED -> R.string.focus_stopped_before_target
        else -> R.string.focus_count_up_ended
    }
    val repeatId = state.setup.repeatSourceSessionId ?: session.id
    val breakRunning = state.runtime.breakState is FocusBreakState.Running
    val breakExpired = state.runtime.breakState is FocusBreakState.Expired

    if (breakRunning) {
        FocusBreakCountdown(
            state = state.runtime.breakState,
            busy = state.breakBusy,
            onEndBreak = onEndBreak,
        )
    }
    if (breakExpired) {
        Text(
            stringResource(R.string.focus_break_expired),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.fillMaxWidth().semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            }.testTag("focus_break_expired"),
        )
        LumaButton(
            onClick = { requestNavigation(onDone) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("focus_done"),
        ) {
            Text(stringResource(R.string.focus_done))
        }
        LumaOutlinedButton(
            onClick = { requestNavigation { onRepeatSession(repeatId) } },
            enabled = !state.busy && !state.setup.repeatLoading,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                .testTag("focus_break_use_previous_settings"),
        ) {
            Text(stringResource(R.string.focus_break_use_previous_settings))
        }
    }
    Text(
        stringResource(R.string.focus_finished),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.semantics {
            heading()
            liveRegion = LiveRegionMode.Polite
        }.testTag(if (interrupted) "focus_interrupted" else "focus_finished"),
    )
    Text(
        stringResource(terminalLabel),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.testTag("focus_terminal_label"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            session.metadata.name ?: stringResource(R.string.focus_session_fallback),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag("focus_session_name"),
        )
        if (!breakRunning) {
            Text(
                session.metadata.tag?.name ?: stringResource(R.string.focus_tag_untagged),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("focus_session_tag"),
            )
        }
    }
    Text(
        stringResource(
            R.string.focus_saved_duration,
            DateUtils.formatElapsedTime(session.creditedMillis / 1_000),
        ),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.testTag("focus_saved_duration"),
    )
    if (interrupted || session.state == FocusState.INTERRUPTED) {
        Text(stringResource(R.string.focus_interrupted_gap))
    }

    if (breakRunning) {
        LumaTextButton(
            onClick = { requestNavigation(onDone) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("focus_done"),
        ) {
            Text(stringResource(R.string.focus_done))
        }
    } else if (!breakExpired) {
        LumaButton(
            onClick = { requestNavigation(onDone) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("focus_done"),
        ) {
            Text(stringResource(R.string.focus_done))
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(LumaUi.ContentGap),
        modifier = Modifier.fillMaxWidth().testTag("focus_follow_up_group"),
    ) {
        Text(stringResource(R.string.focus_follow_up_title), style = MaterialTheme.typography.titleMedium)
        if (state.runtime.breakState == FocusBreakState.Idle) {
            BreakActions(
                sessionId = session.id,
                busy = state.breakBusy,
                onStartBreak = onStartBreak,
            )
        }
        if (state.breakOperationFailed) {
            Text(
                stringResource(R.string.focus_break_operation_error),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        focusRepository?.let { repository ->
            FocusReflectionEditor(
                session = session,
                repository = repository,
                productRepository = focusProductRepository,
                generation = state.runtime.databaseGeneration,
                isFocusReadable = state.focusContentReadable && (!session.taskOrigin || taskContentReadable),
                navigationState = reflectionNavigation,
                onSessionMissing = onTerminalSessionMissing,
            )
        }

        FocusTaskCompletionAction(
            session = session,
            generation = state.runtime.databaseGeneration,
            state = state.taskCompletion,
            canAccessTaskActions = state.taskActionsAllowed && state.focusContentReadable && taskContentReadable,
            onSessionChanged = onTaskSessionChanged,
            onMarkComplete = onMarkTaskComplete,
            onRetryCompletion = onRetryTaskCompletion,
            onUnlinkTask = onUnlinkTask,
            onRetryUnlink = onRetryTaskUnlink,
            onChanged = {},
        )

        if (state.setup.repeatLoading) {
            Text(stringResource(R.string.focus_repeat_loading), modifier = Modifier.testTag("focus_repeat_loading"))
        }
        if (state.setup.repeatLoadFailed) {
            Text(
                stringResource(R.string.focus_repeat_error),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (!breakExpired) {
            LumaOutlinedButton(
                onClick = { requestNavigation { onRepeatSession(repeatId) } },
                enabled = !state.busy && !state.setup.repeatLoading,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("focus_repeat"),
            ) {
                Text(stringResource(R.string.focus_use_session_settings))
            }
        }
        LumaOutlinedButton(
            onClick = { requestNavigation(onStartAnother) },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("focus_start_another"),
        ) {
            Text(stringResource(R.string.focus_start_another))
        }
        LumaTextButton(
            onClick = { requestNavigation(onOpenHistory) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("focus_history_open"),
        ) {
            Text(stringResource(R.string.focus_open_history))
        }
        if (hasPendingDestination) {
            Text(stringResource(R.string.focus_pending_destination))
            LumaOutlinedButton(
                onClick = { requestNavigation(onOpenPendingDestination) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("focus_open_pending_destination"),
            ) {
                Text(stringResource(R.string.focus_open_pending))
            }
        }
        FailureContent(state, onRetry, onEmergencyExit = { requestNavigation(onEmergencyExit) })
    }

    if (showReflectionLeaveDialog) {
        LumaAlertDialog(
            onDismissRequest = ::keepEditing,
            title = { Text(stringResource(R.string.focus_reflection_unsaved_title)) },
            text = { Text(stringResource(R.string.focus_reflection_unsaved_message)) },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(LumaUi.RelatedGap)) {
                    LumaTextButton(
                        onClick = {
                            if (!reflectionNavigation.isSaving) {
                                showReflectionLeaveDialog = false
                                reflectionNavigation.saveForNavigation(
                                    ::finishPendingNavigation,
                                    ::keepEditing,
                                )
                            }
                        },
                        enabled = !reflectionNavigation.isSaving,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .testTag("focus_reflection_save_and_leave"),
                    ) {
                        Text(stringResource(R.string.focus_reflection_save_and_leave))
                    }
                    LumaTextButton(
                        onClick = {
                            reflectionNavigation.discard()
                            finishPendingNavigation()
                        },
                        enabled = !reflectionNavigation.isSaving,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .testTag("focus_reflection_discard_and_leave"),
                    ) {
                        Text(stringResource(R.string.focus_reflection_discard_and_leave))
                    }
                }
            },
            dismissButton = {
                LumaTextButton(
                    onClick = ::keepEditing,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("focus_reflection_keep_editing"),
                ) {
                    Text(stringResource(R.string.focus_reflection_keep_editing))
                }
            },
            modifier = Modifier.testTag("focus_reflection_leave_dialog"),
        )
    }
}

@Composable
private fun BreakActions(
    sessionId: UUID,
    busy: Boolean,
    onStartBreak: (Int) -> Unit,
) {
    var showBreakOptions by rememberSaveable(sessionId.toString()) { mutableStateOf(false) }
    var showCustomizeDialog by rememberSaveable(sessionId.toString()) { mutableStateOf(false) }
    var customMinutes by rememberSaveable(sessionId.toString()) { mutableStateOf("") }
    var customMinutesAttempted by rememberSaveable(sessionId.toString()) { mutableStateOf(false) }
    val customDuration = customMinutes.toIntOrNull()?.takeIf { it in 1..60 }

    Column(verticalArrangement = Arrangement.spacedBy(LumaUi.ContentGap)) {
        val breakOptionsState = stringResource(
            if (showBreakOptions) R.string.focus_disclosure_expanded else R.string.focus_disclosure_collapsed,
        )
        LumaOutlinedButton(
            onClick = { showBreakOptions = !showBreakOptions },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .semantics { stateDescription = breakOptionsState }
                .testTag("focus_break_options_toggle"),
        ) {
            Text(stringResource(if (showBreakOptions) R.string.focus_break_options_hide else R.string.focus_break_options))
        }
        if (showBreakOptions) {
            Text(stringResource(R.string.focus_alert_limitations), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.focus_break_default_label), style = MaterialTheme.typography.bodyMedium)
            LumaOutlinedButton(
                onClick = { onStartBreak(5) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("focus_start_five_minute_break"),
            ) {
                Text(stringResource(R.string.focus_break_take_five))
            }
            LumaOutlinedButton(
                onClick = {
                    customMinutesAttempted = false
                    showCustomizeDialog = true
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("focus_customize_break"),
            ) {
                Text(stringResource(R.string.focus_break_customize))
            }
        }
    }

    if (showCustomizeDialog) {
        LumaAlertDialog(
            onDismissRequest = { showCustomizeDialog = false },
            title = { Text(stringResource(R.string.focus_break_custom_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(LumaUi.RelatedGap)) {
                    LumaOutlinedTextField(
                        value = customMinutes,
                        onValueChange = { value ->
                            customMinutes = value.mapNotNull { character ->
                                Character.digit(character, 10).takeIf { it >= 0 }?.digitToChar()
                            }.take(2).joinToString("")
                        },
                        label = { Text(stringResource(R.string.focus_break_minutes_label)) },
                        singleLine = true,
                        isError = customMinutesAttempted && customDuration == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        supportingText = {
                            Text(stringResource(R.string.focus_break_minutes_invalid))
                        },
                        modifier = Modifier.fillMaxWidth().testTag("focus_break_minutes"),
                    )
                }
            },
            confirmButton = {
                LumaTextButton(
                    onClick = {
                        val duration = customDuration
                        if (duration == null) {
                            customMinutesAttempted = true
                        } else {
                            onStartBreak(duration)
                            showCustomizeDialog = false
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("focus_custom_break_start"),
                ) {
                    Text(stringResource(R.string.focus_start_break))
                }
            },
            dismissButton = {
                LumaTextButton(
                    onClick = { showCustomizeDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("focus_custom_break_cancel"),
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
            modifier = Modifier.testTag("focus_custom_break_dialog"),
        )
    }
}
