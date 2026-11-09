package com.luma.lifeos.ui.privacy

import com.luma.lifeos.ui.common.LumaUi
import com.luma.lifeos.ui.common.LumaAlertDialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import com.luma.lifeos.ui.common.LumaTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luma.lifeos.R
import com.luma.lifeos.domain.lock.LockLoadStatus
import com.luma.lifeos.lock.AuthPurpose
import com.luma.lifeos.lock.CredentialDescriptorResult
import com.luma.lifeos.lock.CredentialResult
import com.luma.lifeos.lock.LockSensitiveOperationGuard
import com.luma.lifeos.lock.OperationProof
import com.luma.lifeos.lock.PendingSensitiveOperation
import com.luma.lifeos.ui.common.AccessibleFeedback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Fresh, stored-method prompt for one sensitive operation. */
@Composable
fun LockSensitiveActionDialog(
    guard: LockSensitiveOperationGuard,
    pending: PendingSensitiveOperation,
    onAuthorized: (OperationProof) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lockSnapshot by guard.lockSnapshot.collectAsStateWithLifecycle()
    var descriptorResult by remember(pending) { mutableStateOf<CredentialDescriptorResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var authorized by remember { mutableStateOf(false) }
    val descriptor = (descriptorResult as? CredentialDescriptorResult.Ready)?.descriptor
    val editorState: LockCredentialEditorState? = key(descriptor?.type, pending.policyGeneration) {
        if (descriptor == null) null else rememberLockCredentialEditorState(descriptor.type)
    }
    val latestEditorState = rememberUpdatedState(editorState)
    val latestOnCancel = rememberUpdatedState(onCancel)

    fun cancel() {
        latestEditorState.value?.clear()
        if (!authorized) {
            guard.cancel(pending)
            latestOnCancel.value()
        }
    }

    LaunchedEffect(guard, pending) {
        descriptorResult = guard.credentialDescriptor(pending)
    }
    LaunchedEffect(lockSnapshot.loadStatus, lockSnapshot.policy?.generation, descriptorResult) {
        if (descriptorResult != null &&
            (lockSnapshot.loadStatus != LockLoadStatus.READY ||
                lockSnapshot.policy?.enabled != true ||
                lockSnapshot.policy?.generation != pending.policyGeneration)
        ) {
            cancel()
        }
    }
    DisposableEffect(lifecycle, pending) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) cancel() }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            latestEditorState.value?.clear()
            if (!authorized) guard.cancel(pending)
        }
    }
    DisposableEffect(editorState) {
        onDispose { editorState?.clear() }
    }

    fun submit() {
        val secret = editorState?.takeSecret() ?: return
        if (busy) {
            secret.fill('\u0000')
            return
        }
        busy = true
        error = null
        scope.launch {
            try {
                when (val result = guard.verify(pending, secret)) {
                    is CredentialResult.Success -> result.proof?.let {
                        authorized = true
                        onAuthorized(it)
                    } ?: run { error = context.getString(R.string.app_lock_error_unavailable) }
                    CredentialResult.InvalidInput -> error = context.getString(R.string.app_lock_error_invalid)
                    CredentialResult.Incorrect -> error = context.getString(R.string.app_lock_error_incorrect)
                    is CredentialResult.Cooldown -> error = context.getString(
                        R.string.app_lock_error_cooldown, ((result.remainingMs + 999) / 1000).coerceAtLeast(1),
                    )
                    CredentialResult.Cancelled -> error = context.getString(R.string.app_lock_error_cancelled)
                    CredentialResult.Unavailable -> error = context.getString(R.string.app_lock_error_unavailable)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = context.getString(R.string.app_lock_error_unavailable)
            } finally {
                secret.fill('\u0000')
                busy = false
            }
        }
    }

    SecurePrivacyWindow()
    LumaAlertDialog(
        onDismissRequest = { if (!busy) cancel() },
        title = { Text(stringResource(R.string.app_lock_sensitive_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(LumaUi.ContentGap),
            ) {
                Text(stringResource(R.string.app_lock_sensitive_body, purposeLabel(pending.purpose)))
                when (val result = descriptorResult) {
                    null -> Text(stringResource(R.string.app_lock_credential_loading))
                    is CredentialDescriptorResult.Ready -> {
                        editorState?.let { state ->
                            LockCredentialInput(
                                state = state,
                                enabled = !busy && !authorized,
                                label = stringResource(R.string.app_lock_current_credential),
                                modifier = Modifier.testTag("app_lock_sensitive_input"),
                            )
                        }
                    }
                    CredentialDescriptorResult.Disabled,
                    CredentialDescriptorResult.Unavailable -> AccessibleFeedback(
                        stringResource(R.string.app_lock_error_unavailable),
                    )
                }
                error?.let { AccessibleFeedback(it, Modifier.testTag("app_lock_sensitive_error")) }
            }
        },
        confirmButton = {
            LumaTextButton(enabled = !busy && !authorized && editorState?.isComplete == true, onClick = ::submit,
                modifier = Modifier.testTag("app_lock_sensitive_submit")) {
                Text(stringResource(R.string.app_lock_unlock_action))
            }
        },
        dismissButton = {
            LumaTextButton(enabled = !busy, onClick = ::cancel) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun purposeLabel(purpose: AuthPurpose): String = stringResource(when (purpose) {
    AuthPurpose.SHARE -> R.string.app_lock_sensitive_share
    AuthPurpose.EXPORT -> R.string.app_lock_sensitive_export
    AuthPurpose.BACKUP -> R.string.app_lock_sensitive_backup
    AuthPurpose.RESTORE -> R.string.app_lock_sensitive_restore
    AuthPurpose.RESET -> R.string.app_lock_sensitive_reset
    else -> R.string.app_lock_sensitive_operation
})
