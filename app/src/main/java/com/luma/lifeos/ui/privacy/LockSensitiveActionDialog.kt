package com.luma.lifeos.ui.privacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.luma.lifeos.R
import com.luma.lifeos.lock.AuthPurpose
import com.luma.lifeos.lock.CredentialResult
import com.luma.lifeos.lock.LockSensitiveOperationGuard
import com.luma.lifeos.lock.OperationProof
import com.luma.lifeos.lock.PendingSensitiveOperation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Fresh password prompt for one sensitive operation. No proof or password enters saved state. */
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
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var authorized by remember { mutableStateOf(false) }

    fun cancel() {
        password = ""
        if (!authorized) guard.cancel(pending)
        onCancel()
    }

    DisposableEffect(lifecycle, pending) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) cancel() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); password = ""; if (!authorized) guard.cancel(pending) }
    }

    fun submit() {
        if (busy || password.isEmpty()) return
        val typed = password
        password = ""
        busy = true
        error = null
        scope.launch {
            try {
                when (val result = guard.verify(pending, typed.toCharArray())) {
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
                busy = false
            }
        }
    }

    SecurePrivacyWindow()
    AlertDialog(
        onDismissRequest = { if (!busy) cancel() },
        title = { Text(stringResource(R.string.app_lock_sensitive_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.app_lock_sensitive_body, purposeLabel(pending.purpose)))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    enabled = !busy,
                    label = { Text(stringResource(R.string.app_lock_current_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth().testTag("app_lock_sensitive_password"),
                )
                error?.let { com.luma.lifeos.ui.common.AccessibleFeedback(it, Modifier.testTag("app_lock_sensitive_error")) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && password.isNotEmpty(), onClick = ::submit,
                modifier = Modifier.testTag("app_lock_sensitive_submit")) {
                Text(stringResource(R.string.app_lock_unlock_action))
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = ::cancel) { Text(stringResource(R.string.cancel)) }
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
