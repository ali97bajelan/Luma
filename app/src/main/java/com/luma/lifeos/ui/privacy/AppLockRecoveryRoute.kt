package com.luma.lifeos.ui.privacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luma.lifeos.R
import com.luma.lifeos.lock.AppLockCoordinator
import com.luma.lifeos.lock.CredentialResult
import com.luma.lifeos.lock.LockCredentialService
import com.luma.lifeos.lock.RecoveryPreparation
import com.luma.lifeos.ui.common.AccessibleFeedback
import com.luma.lifeos.ui.common.AppPageScaffold
import com.luma.lifeos.ui.common.LumaReadingPane
import com.luma.lifeos.ui.common.PageNavigation
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** The final erase callback is supplied by T08's fresh-auth/reset operation boundary. */
@Composable
fun AppLockRecoveryRoute(
    credentials: LockCredentialService,
    coordinator: AppLockCoordinator,
    onBack: () -> Unit,
    onCompleted: () -> Unit,
    onForgottenResetConfirmed: (() -> Unit)? = null,
) {
    val snapshot by coordinator.snapshot.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var recoveryCode by remember { mutableStateOf("") }
    var replacementPassword by remember { mutableStateOf("") }
    var replacementConfirmation by remember { mutableStateOf("") }
    var replacementCode by remember { mutableStateOf<String?>(null) }
    var replacementPreparation by remember { mutableStateOf<RecoveryPreparation?>(null) }
    var recoveryAcknowledged by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var eraseDialogStep by remember { mutableStateOf(0) }
    var revealRecoveryCode by remember { mutableStateOf(false) }
    var revealReplacementPassword by remember { mutableStateOf(false) }
    var pendingCode by remember { mutableStateOf<CharArray?>(null) }
    var pendingPassword by remember { mutableStateOf<CharArray?>(null) }

    fun clearSecrets() {
        recoveryCode = ""
        replacementPassword = ""
        replacementConfirmation = ""
        replacementCode = null
        pendingCode?.fill('\u0000')
        pendingPassword?.fill('\u0000')
        pendingCode = null
        pendingPassword = null
        recoveryAcknowledged = false
    }
    fun clearClipboard() { runCatching { context.getSystemService(android.content.ClipboardManager::class.java)?.clearPrimaryClip() } }
    fun cancelPreparation() {
        val preparation = replacementPreparation ?: return
        replacementPreparation = null
        scope.launch { credentials.cancelRecovery(preparation) }
        clearClipboard()
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                clearSecrets()
                cancelPreparation()
                eraseDialogStep = 0
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            clearSecrets()
            cancelPreparation()
        }
    }
    SecurePrivacyWindow()

    fun report(result: CredentialResult) {
        errorText = when (result) {
            CredentialResult.InvalidInput -> context.getString(R.string.app_lock_error_invalid)
            CredentialResult.Incorrect -> context.getString(R.string.app_lock_error_incorrect)
            is CredentialResult.Cooldown -> context.getString(
                R.string.app_lock_error_cooldown, ((result.remainingMs + 999) / 1000).coerceAtLeast(1),
            )
            CredentialResult.Cancelled -> context.getString(R.string.app_lock_error_cancelled)
            CredentialResult.Unavailable -> context.getString(R.string.app_lock_error_unavailable)
            is CredentialResult.Success -> null
        }
    }
    fun beginRecovery() {
        if (busy) return
        if (replacementPassword != replacementConfirmation) {
            errorText = context.getString(R.string.app_lock_error_password_mismatch)
            return
        }
        val points = replacementPassword.codePointCount(0, replacementPassword.length)
        if (points !in 8..128) {
            errorText = context.getString(R.string.app_lock_error_password_length)
            return
        }
        pendingCode?.fill('\u0000')
        pendingPassword?.fill('\u0000')
        pendingCode = recoveryCode.toCharArray()
        pendingPassword = replacementPassword.toCharArray()
        recoveryCode = ""
        replacementPassword = ""
        replacementConfirmation = ""
        busy = true
        errorText = null
        scope.launch {
            try {
                val preparation = credentials.prepareRecovery()
                val code = preparation.takeCodeForDisplay()
                replacementPreparation = preparation
                replacementCode = String(code)
                code.fill('\u0000')
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                clearSecrets()
                errorText = context.getString(R.string.app_lock_error_unavailable)
            } finally { busy = false }
        }
    }
    fun finishRecovery() {
        val preparation = replacementPreparation ?: return
        if (!recoveryAcknowledged || busy) return
        busy = true
        errorText = null
        scope.launch {
            try {
                val confirmation = credentials.confirmRecovery(preparation)
                val code = pendingCode ?: charArrayOf()
                val password = pendingPassword ?: charArrayOf()
                val result = credentials.recover(code, password, confirmation)
                replacementPreparation = null
                pendingCode = null
                pendingPassword = null
                report(result)
                if (result is CredentialResult.Success) {
                    clearClipboard()
                    clearSecrets()
                    onCompleted()
                } else {
                    credentials.cancelRecovery(preparation)
                    replacementPreparation = null
                    clearSecrets()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                credentials.cancelRecovery(preparation)
                replacementPreparation = null
                clearSecrets()
                errorText = context.getString(R.string.app_lock_error_unavailable)
            } finally {
                replacementCode = null
                recoveryAcknowledged = false
                clearClipboard()
                busy = false
            }
        }
    }

    AppPageScaffold(
        title = stringResource(R.string.app_lock_recovery_title), navigation = PageNavigation.BACK,
        onNavigate = { if (!busy) onBack() }, navigationEnabled = !busy,
        titleTag = "app_lock_recovery_heading", navigationTag = "app_lock_recovery_back",
    ) { padding ->
        LumaReadingPane(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)
                .testTag("screen_app_lock_recovery"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.app_lock_recovery_body))
                if (snapshot.loadStatus != com.luma.lifeos.domain.lock.LockLoadStatus.READY ||
                    snapshot.policy?.enabled != true
                ) {
                    AccessibleFeedback(stringResource(R.string.app_lock_error_unavailable),
                        Modifier.testTag("app_lock_recovery_unavailable"))
                } else if (replacementCode == null) {
                    OutlinedTextField(
                        value = recoveryCode, onValueChange = { recoveryCode = it }, enabled = !busy,
                        label = { Text(stringResource(R.string.app_lock_recovery_enter_code)) },
                        singleLine = true,
                        visualTransformation = if (revealRecoveryCode) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth().testTag("app_lock_recovery_input"),
                        trailingIcon = { TextButton(enabled = !busy, onClick = { revealRecoveryCode = !revealRecoveryCode }) {
                            Text(stringResource(if (revealRecoveryCode) R.string.app_lock_hide_password else R.string.app_lock_show_password))
                        } },
                    )
                    OutlinedTextField(
                        value = replacementPassword, onValueChange = { replacementPassword = it }, enabled = !busy,
                        label = { Text(stringResource(R.string.app_lock_new_password)) }, singleLine = true,
                        visualTransformation = if (revealReplacementPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth().testTag("app_lock_recovery_new_password"),
                        trailingIcon = { TextButton(enabled = !busy, onClick = { revealReplacementPassword = !revealReplacementPassword }) {
                            Text(stringResource(if (revealReplacementPassword) R.string.app_lock_hide_password else R.string.app_lock_show_password))
                        } },
                    )
                    OutlinedTextField(
                        value = replacementConfirmation, onValueChange = { replacementConfirmation = it }, enabled = !busy,
                        label = { Text(stringResource(R.string.app_lock_confirm_password)) }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth().testTag("app_lock_recovery_confirm_password"),
                    )
                    Button(enabled = !busy && recoveryCode.isNotEmpty() && replacementPassword.isNotEmpty(),
                        onClick = ::beginRecovery, modifier = Modifier.testTag("app_lock_recovery_continue")) {
                        Text(stringResource(R.string.app_lock_recovery_continue))
                    }
                    TextButton(enabled = !busy, onClick = { eraseDialogStep = 1 },
                        modifier = Modifier.testTag("app_lock_forgot_no_code")) {
                        Text(stringResource(R.string.app_lock_no_recovery_code))
                    }
                } else {
                    Text(stringResource(R.string.app_lock_recovery_replacement_body),
                        style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(replacementCode.orEmpty(), Modifier.fillMaxWidth().padding(16.dp)
                            .testTag("app_lock_recovery_replacement_code"), style = MaterialTheme.typography.titleMedium)
                    }
                    TextButton(enabled = !busy, onClick = { clipboard.setText(AnnotatedString(replacementCode.orEmpty())) },
                        modifier = Modifier.testTag("app_lock_recovery_copy_new")) {
                        Text(stringResource(R.string.app_lock_recovery_copy))
                    }
                    Row {
                        Checkbox(checked = recoveryAcknowledged, enabled = !busy, onCheckedChange = { recoveryAcknowledged = it })
                        Text(stringResource(R.string.app_lock_recovery_ack), Modifier.padding(top = 12.dp))
                    }
                    Button(enabled = !busy && recoveryAcknowledged, onClick = ::finishRecovery,
                        modifier = Modifier.testTag("app_lock_recovery_finish")) {
                        Text(stringResource(R.string.app_lock_recovery_finish))
                    }
                    TextButton(enabled = !busy, onClick = { cancelPreparation(); clearSecrets(); onBack() }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
                errorText?.let { AccessibleFeedback(it, Modifier.testTag("app_lock_recovery_error")) }
                if (busy) androidx.compose.material3.CircularProgressIndicator()
            }
        }
    }
    if (eraseDialogStep == 1) AlertDialog(
        onDismissRequest = { eraseDialogStep = 0 },
        title = { Text(stringResource(R.string.app_lock_erase_title)) },
        text = { Text(stringResource(R.string.app_lock_erase_warning)) },
        confirmButton = { TextButton(onClick = { eraseDialogStep = 2 }, modifier = Modifier.testTag("app_lock_erase_first_confirm")) {
            Text(stringResource(R.string.app_lock_erase_continue))
        } },
        dismissButton = { TextButton(onClick = { eraseDialogStep = 0 }) { Text(stringResource(R.string.cancel)) } },
    )
    if (eraseDialogStep == 2) AlertDialog(
        onDismissRequest = { eraseDialogStep = 0 },
        title = { Text(stringResource(R.string.app_lock_erase_final_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.app_lock_erase_final_warning))
                if (onForgottenResetConfirmed == null) {
                    AccessibleFeedback(stringResource(R.string.app_lock_erase_unavailable),
                        Modifier.testTag("app_lock_erase_unavailable"))
                }
            }
        },
        confirmButton = { TextButton(enabled = onForgottenResetConfirmed != null,
            onClick = { eraseDialogStep = 0; onForgottenResetConfirmed?.invoke() },
            modifier = Modifier.testTag("app_lock_erase_final_confirm")) {
            Text(stringResource(R.string.app_lock_erase_final_confirm))
        } },
        dismissButton = { TextButton(onClick = { eraseDialogStep = 0 }) { Text(stringResource(R.string.cancel)) } },
    )
}
