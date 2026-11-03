package com.luma.lifeos.ui.privacy

import com.luma.lifeos.ui.common.LumaButton

import com.luma.lifeos.ui.common.LumaAlertDialog

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luma.lifeos.R
import com.luma.lifeos.lock.AppLockCoordinator
import com.luma.lifeos.lock.AuthPurpose
import com.luma.lifeos.lock.CredentialDescriptorResult
import com.luma.lifeos.lock.CredentialResult
import com.luma.lifeos.lock.LockCredentialService
import com.luma.lifeos.lock.OperationProof
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Preview-free shell shown while authorization is unknown, unavailable, or missing. */
@Composable
fun AppLockGate(
    canRequestUnlock: Boolean,
    focusEmergencyAvailable: Boolean = false,
    focusEmergencyExitReady: Boolean = false,
    addNewEntryAvailable: Boolean = false,
    appLockSettingsAvailable: Boolean = false,
    onUnlock: () -> Unit,
    onBackToSafeDestination: () -> Unit,
    onAddNewEntry: () -> Unit = {},
    onAppLockSettings: () -> Unit = {},
    onRequestFocusEmergencyExit: () -> Unit = {},
    onConfirmFocusEmergencyExit: () -> Unit = {},
    onCancelFocusEmergencyExit: () -> Unit = {},
) {
    var confirmingFocusExit by remember { mutableStateOf(false) }
    var requestingFocusExit by remember { mutableStateOf(false) }
    LaunchedEffect(focusEmergencyExitReady, requestingFocusExit) {
        if (requestingFocusExit && focusEmergencyExitReady) confirmingFocusExit = true
    }
    BackHandler(enabled = !confirmingFocusExit, onBack = onBackToSafeDestination)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.app_lock_gate_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.app_lock_gate_body),
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
            LumaButton(
                onClick = onUnlock,
                enabled = canRequestUnlock,
                modifier = Modifier.padding(top = 20.dp),
            ) { Text(stringResource(R.string.app_lock_gate_unlock)) }
            if (addNewEntryAvailable) {
                TextButton(onClick = onAddNewEntry, modifier = Modifier.padding(top = 4.dp)) {
                    Text(stringResource(R.string.restricted_capture_add_new_entry))
                }
            }
            if (appLockSettingsAvailable) {
                TextButton(onClick = onAppLockSettings, modifier = Modifier.padding(top = 4.dp)) {
                    Text(stringResource(R.string.app_lock_gate_settings))
                }
            }
            TextButton(onClick = onBackToSafeDestination, modifier = Modifier.padding(top = 4.dp)) {
                Text(stringResource(R.string.app_lock_gate_back))
            }
            if (focusEmergencyAvailable) {
                TextButton(
                    onClick = {
                        onRequestFocusEmergencyExit()
                        requestingFocusExit = true
                    },
                    modifier = Modifier.padding(top = 4.dp),
                ) { Text(stringResource(R.string.app_lock_focus_emergency_exit)) }
            }
        }
    }
    if (confirmingFocusExit) {
        LumaAlertDialog(
            onDismissRequest = {
                confirmingFocusExit = false
                requestingFocusExit = false
                onCancelFocusEmergencyExit()
            },
            title = { Text(stringResource(R.string.app_lock_focus_emergency_title)) },
            text = { Text(stringResource(R.string.app_lock_focus_emergency_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingFocusExit = false
                    requestingFocusExit = false
                    onConfirmFocusEmergencyExit()
                }) { Text(stringResource(R.string.app_lock_focus_emergency_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmingFocusExit = false
                    requestingFocusExit = false
                    onCancelFocusEmergencyExit()
                }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/** Stored-method unlock UI shared by the app gate and private-draft resume handoff. */
@Composable
fun AppLockUnlockDialog(
    credentials: LockCredentialService,
    coordinator: AppLockCoordinator,
    proofOnly: Boolean = false,
    onSuccess: (OperationProof) -> Unit,
    onCancel: () -> Unit,
    onForgotPassword: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snapshot by coordinator.snapshot.collectAsStateWithLifecycle()
    var descriptorResult by remember { mutableStateOf<CredentialDescriptorResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var cancelled by remember { mutableStateOf(false) }
    val descriptor = (descriptorResult as? CredentialDescriptorResult.Ready)?.descriptor
    val editorState: LockCredentialEditorState? = key(descriptor?.type, descriptor?.generation) {
        if (descriptor == null) null else rememberLockCredentialEditorState(descriptor.type)
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun cancelPrompt() {
        editorState?.clear()
        if (!cancelled) {
            cancelled = true
            onCancel()
        }
    }
    LaunchedEffect(credentials, snapshot.loadStatus, snapshot.policy?.enabled, snapshot.policy?.generation) {
        descriptorResult = when {
            snapshot.loadStatus != com.luma.lifeos.domain.lock.LockLoadStatus.READY -> null
            snapshot.policy?.enabled != true -> CredentialDescriptorResult.Disabled
            else -> {
                val loaded = credentials.credentialDescriptor()
                if (loaded is CredentialDescriptorResult.Ready &&
                    loaded.descriptor.generation != snapshot.policy?.generation
                ) CredentialDescriptorResult.Unavailable else loaded
            }
        }
    }
    LaunchedEffect(snapshot.loadStatus, snapshot.policy?.generation, descriptorResult) {
        if (descriptorResult != null &&
            (snapshot.loadStatus != com.luma.lifeos.domain.lock.LockLoadStatus.READY ||
                snapshot.policy?.enabled != true ||
                (descriptorResult is CredentialDescriptorResult.Ready &&
                    snapshot.policy?.generation != descriptor?.generation))
        ) cancelPrompt()
    }
    DisposableEffect(lifecycle, onCancel, editorState) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) cancelPrompt()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); editorState?.clear() }
    }
    fun submit() {
        val currentDescriptor = descriptor ?: return
        val typed = editorState?.takeSecret() ?: return
        if (busy) {
            typed.fill('\u0000')
            return
        }
        busy = true
        error = null
        scope.launch {
            try {
                val result = credentials.verify(typed, AuthPurpose.BROWSE, currentDescriptor.generation)
                if (cancelled) {
                    error = context.getString(com.luma.lifeos.R.string.app_lock_error_cancelled)
                    return@launch
                }
                when (result) {
                    is CredentialResult.Success -> {
                        val proof = result.proof
                        val latest = coordinator.snapshot.value
                        if (latest.loadStatus != com.luma.lifeos.domain.lock.LockLoadStatus.READY ||
                            latest.policy?.generation != currentDescriptor.generation
                        ) {
                            error = context.getString(com.luma.lifeos.R.string.app_lock_error_cancelled)
                        } else if (proof == null) {
                            error = context.getString(com.luma.lifeos.R.string.app_lock_error_unavailable)
                        } else if (proofOnly) {
                            onSuccess(proof)
                        } else {
                            val request = coordinator.pendingBrowseRequest()
                            val policy = coordinator.snapshot.value.policy
                            if (request == null || policy == null || !policy.enabled) {
                                error = context.getString(com.luma.lifeos.R.string.app_lock_error_unavailable)
                            } else {
                                coordinator.unlock(
                                    proof = proof,
                                    sections = request.requiredSections,
                                    wholeApp = policy.coverage == com.luma.lifeos.domain.lock.LockCoverage.WHOLE_APP,
                                )
                                val grant = coordinator.snapshot.value.grant
                                val authorized = if (policy.coverage == com.luma.lifeos.domain.lock.LockCoverage.WHOLE_APP) {
                                    grant.wholeApp
                                } else request.requiredSections.all { it in grant.sections }
                                if (authorized) onSuccess(proof)
                                else error = context.getString(com.luma.lifeos.R.string.app_lock_error_unavailable)
                            }
                        }
                    }
                    CredentialResult.InvalidInput -> error = context.getString(com.luma.lifeos.R.string.app_lock_error_invalid)
                    CredentialResult.Incorrect -> error = context.getString(com.luma.lifeos.R.string.app_lock_error_incorrect)
                    is CredentialResult.Cooldown -> error = context.getString(
                        com.luma.lifeos.R.string.app_lock_error_cooldown, ((result.remainingMs + 999) / 1000).coerceAtLeast(1),
                    )
                    CredentialResult.Cancelled -> error = context.getString(com.luma.lifeos.R.string.app_lock_error_cancelled)
                    CredentialResult.Unavailable -> error = context.getString(com.luma.lifeos.R.string.app_lock_error_unavailable)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = context.getString(com.luma.lifeos.R.string.app_lock_error_unavailable)
            } finally {
                typed.fill('\u0000')
                busy = false
            }
        }
    }
    SecurePrivacyWindow()
    LumaAlertDialog(
        onDismissRequest = { if (!busy) cancelPrompt() },
        title = { Text(stringResource(com.luma.lifeos.R.string.app_lock_unlock_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(com.luma.lifeos.R.string.app_lock_unlock_body))
                when (val loaded = descriptorResult) {
                    null -> Text(stringResource(com.luma.lifeos.R.string.app_lock_credential_loading))
                    is CredentialDescriptorResult.Ready -> editorState?.let { state ->
                        LockCredentialInput(
                            state = state,
                            enabled = !busy && !cancelled,
                            label = stringResource(com.luma.lifeos.R.string.app_lock_current_credential),
                            modifier = Modifier.testTag("app_lock_unlock_input"),
                        )
                    }
                    CredentialDescriptorResult.Disabled,
                    CredentialDescriptorResult.Unavailable ->
                        com.luma.lifeos.ui.common.AccessibleFeedback(
                            stringResource(com.luma.lifeos.R.string.app_lock_error_unavailable),
                        )
                }
                error?.let { com.luma.lifeos.ui.common.AccessibleFeedback(it, Modifier.testTag("app_lock_unlock_error")) }
                TextButton(enabled = !busy, onClick = { editorState?.clear(); onForgotPassword() },
                    modifier = Modifier.testTag("app_lock_forgot_password")) {
                    Text(stringResource(com.luma.lifeos.R.string.app_lock_forgot_password))
                }
            }
        },
        confirmButton = { TextButton(enabled = !busy && !cancelled && editorState?.isComplete == true, onClick = ::submit,
            modifier = Modifier.testTag("app_lock_unlock_submit")) {
            Text(stringResource(com.luma.lifeos.R.string.app_lock_unlock_action))
        } },
        dismissButton = { TextButton(enabled = !busy, onClick = ::cancelPrompt) {
            Text(stringResource(com.luma.lifeos.R.string.cancel))
        } },
    )
}
