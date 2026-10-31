package com.luma.lifeos.ui.privacy

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.luma.lifeos.R
import com.luma.lifeos.lock.AppLockCoordinator
import com.luma.lifeos.lock.AuthPurpose
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
            Button(
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
        AlertDialog(
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

/** Password-only unlock UI shared by the app gate and the private-draft resume handoff. */
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
    var password by remember { mutableStateOf("") }
    var revealed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, onCancel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                password = ""
                onCancel()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); password = "" }
    }
    fun submit() {
        if (busy || password.isEmpty()) return
        val typed = password
        password = ""
        busy = true
        error = null
        scope.launch {
            try {
                val result = credentials.verify(typed.toCharArray(), AuthPurpose.BROWSE)
                when (result) {
                    is CredentialResult.Success -> {
                        val proof = result.proof
                        if (proof == null) {
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
            } finally { busy = false }
        }
    }
    SecurePrivacyWindow()
    AlertDialog(
        onDismissRequest = { if (!busy) { password = ""; onCancel() } },
        title = { Text(stringResource(com.luma.lifeos.R.string.app_lock_unlock_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(com.luma.lifeos.R.string.app_lock_unlock_body))
                OutlinedTextField(
                    value = password, onValueChange = { password = it }, enabled = !busy,
                    label = { Text(stringResource(com.luma.lifeos.R.string.app_lock_current_password)) },
                    singleLine = true,
                    visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth().testTag("app_lock_unlock_password"),
                    trailingIcon = { TextButton(enabled = !busy, onClick = { revealed = !revealed },
                        modifier = Modifier.testTag("app_lock_unlock_visibility")) {
                        Text(stringResource(if (revealed) com.luma.lifeos.R.string.app_lock_hide_password
                            else com.luma.lifeos.R.string.app_lock_show_password))
                    } },
                )
                error?.let { com.luma.lifeos.ui.common.AccessibleFeedback(it, Modifier.testTag("app_lock_unlock_error")) }
                TextButton(enabled = !busy, onClick = { password = ""; onForgotPassword() },
                    modifier = Modifier.testTag("app_lock_forgot_password")) {
                    Text(stringResource(com.luma.lifeos.R.string.app_lock_forgot_password))
                }
            }
        },
        confirmButton = { TextButton(enabled = !busy && password.isNotEmpty(), onClick = ::submit,
            modifier = Modifier.testTag("app_lock_unlock_submit")) {
            Text(stringResource(com.luma.lifeos.R.string.app_lock_unlock_action))
        } },
        dismissButton = { TextButton(enabled = !busy, onClick = { password = ""; onCancel() }) {
            Text(stringResource(com.luma.lifeos.R.string.cancel))
        } },
    )
}
