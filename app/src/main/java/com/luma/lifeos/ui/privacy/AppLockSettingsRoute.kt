package com.luma.lifeos.ui.privacy

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.luma.lifeos.R
import com.luma.lifeos.domain.lock.LockCoverage
import com.luma.lifeos.domain.lock.LockPolicy
import com.luma.lifeos.domain.lock.LockSection
import com.luma.lifeos.domain.lock.RelockMode
import com.luma.lifeos.lock.AppLockCoordinator
import com.luma.lifeos.lock.AuthPurpose
import com.luma.lifeos.lock.CredentialResult
import com.luma.lifeos.lock.LockCredentialService
import com.luma.lifeos.lock.OperationProof
import com.luma.lifeos.lock.RecoveryPreparation
import com.luma.lifeos.ui.common.AccessibleFeedback
import com.luma.lifeos.ui.common.AppPageScaffold
import com.luma.lifeos.ui.common.LumaReadingPane
import com.luma.lifeos.ui.common.PageNavigation
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class LockSettingsAction { POLICY, PASSWORD, DISABLE, RECOVERY }
private enum class RecoveryDisplayPurpose { SETUP, ROTATION }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppLockSettingsRoute(
    credentials: LockCredentialService,
    coordinator: AppLockCoordinator,
    onBack: () -> Unit,
    onForgotPassword: () -> Unit = {},
) {
    val factory = remember(credentials, coordinator) {
        viewModelFactory { initializer { AppLockViewModel(credentials, coordinator) } }
    }
    val vm: AppLockViewModel = viewModel(factory = factory)
    val snapshot by coordinator.snapshot.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val storedPolicy = snapshot.policy ?: LockPolicy()
    var policy by remember(storedPolicy) { mutableStateOf(storedPolicy) }
    var opened by remember(storedPolicy.generation, storedPolicy.enabled) { mutableStateOf(!storedPolicy.enabled) }
    var setupPassword by remember { mutableStateOf("") }
    var setupConfirmation by remember { mutableStateOf("") }
    var unlockPassword by remember { mutableStateOf("") }
    var actionPassword by remember { mutableStateOf("") }
    var replacementPassword by remember { mutableStateOf("") }
    var replacementConfirmation by remember { mutableStateOf("") }
    var action by remember { mutableStateOf<LockSettingsAction?>(null) }
    var recoveryPreparation by remember { mutableStateOf<RecoveryPreparation?>(null) }
    var recoveryProof by remember { mutableStateOf<OperationProof?>(null) }
    var recoveryOperationId by remember { mutableStateOf<UUID?>(null) }
    var recoveryDisplay by remember { mutableStateOf<String?>(null) }
    var recoveryPurpose by remember { mutableStateOf<RecoveryDisplayPurpose?>(null) }
    var pendingSetupPassword by remember { mutableStateOf<CharArray?>(null) }
    var recoveryAcknowledged by remember { mutableStateOf(false) }
    var pendingSecrets by remember { mutableStateOf(emptyList<CharArray>()) }
    var busy by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    fun clearVisibleSecrets() {
        setupPassword = ""
        setupConfirmation = ""
        unlockPassword = ""
        actionPassword = ""
        replacementPassword = ""
        replacementConfirmation = ""
        recoveryDisplay = null
        recoveryAcknowledged = false
    }
    fun clearSecrets() {
        pendingSecrets.forEach { it.fill('\u0000') }
        pendingSecrets = emptyList()
        pendingSetupPassword?.fill('\u0000')
        pendingSetupPassword = null
        clearVisibleSecrets()
        recoveryProof = null
        recoveryOperationId = null
    }
    fun clearClipboard() {
        runCatching { context.getSystemService(ClipboardManager::class.java)?.clearPrimaryClip() }
    }
    fun cancelPendingRecovery() {
        val pending = recoveryPreparation ?: return
        recoveryPreparation = null
        scope.launch { vm.cancelRecovery(pending) }
        clearClipboard()
    }
    DisposableEffect(lifecycle, vm) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                clearSecrets()
                cancelPendingRecovery()
                action = null
                recoveryProof = null
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            cancelPendingRecovery()
            clearSecrets()
        }
    }
    SecurePrivacyWindow()

    fun describeResult(result: CredentialResult) {
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
    suspend fun authenticate(password: CharArray, purpose: AuthPurpose): OperationProof? {
        val result = vm.verify(password, purpose)
        describeResult(result)
        return (result as? CredentialResult.Success)?.proof
    }
    fun displayRecovery(
        purpose: RecoveryDisplayPurpose,
        proof: OperationProof? = null,
        operationId: UUID? = null,
    ) {
        busy = true
        errorText = null
        scope.launch {
            try {
                val preparation = vm.prepareRecovery()
                val code = preparation.takeCodeForDisplay()
                recoveryPreparation = preparation
                recoveryProof = proof
                recoveryOperationId = operationId
                recoveryDisplay = String(code)
                recoveryPurpose = purpose
                recoveryAcknowledged = false
                code.fill('\u0000')
                action = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                clearSecrets()
                errorText = context.getString(R.string.app_lock_error_unavailable)
            } finally { busy = false }
        }
    }
    fun finishRecoveryDisplay() {
        val preparation = recoveryPreparation ?: return
        val purpose = recoveryPurpose ?: return
        if (!recoveryAcknowledged || busy) return
        busy = true
        errorText = null
        val displayProof = recoveryProof
        val displayOperationId = recoveryOperationId
        val setupSecret = pendingSetupPassword ?: charArrayOf()
        if (purpose == RecoveryDisplayPurpose.SETUP) clearVisibleSecrets() else clearSecrets()
        scope.launch {
            try {
                val confirmation = vm.confirmRecovery(preparation)
                val result = when (purpose) {
                    RecoveryDisplayPurpose.SETUP -> vm.enable(
                        setupSecret,
                        policy.copy(enabled = true, generation = storedPolicy.generation),
                        confirmation,
                    )
                    RecoveryDisplayPurpose.ROTATION -> {
                        val proof = displayProof
                        val operationId = displayOperationId
                        if (proof == null || operationId == null ||
                            !vm.validatePendingOperation(proof, operationId)
                        ) {
                            vm.cancelRecovery(preparation)
                            recoveryPreparation = null
                            errorText = context.getString(R.string.app_lock_error_unavailable)
                            return@launch
                        }
                        vm.replaceRecoveryCode(proof, confirmation)
                    }
                }
                recoveryPreparation = null
                describeResult(result)
                if (result is CredentialResult.Success) onBack()
                else {
                    vm.cancelRecovery(preparation)
                    recoveryPreparation = null
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                vm.cancelRecovery(preparation)
                recoveryPreparation = null
                errorText = context.getString(R.string.app_lock_error_unavailable)
            } finally {
                recoveryDisplay = null
                recoveryPurpose = null
                recoveryProof = null
                recoveryOperationId = null
                setupSecret.fill('\u0000')
                if (pendingSetupPassword === setupSecret) pendingSetupPassword = null
                clearClipboard()
                busy = false
            }
        }
    }
    fun submitOpenSettings() {
        if (busy || snapshot.loadStatus != com.luma.lifeos.domain.lock.LockLoadStatus.READY) return
        val secret = unlockPassword.toCharArray()
        pendingSecrets = listOf(secret)
        val operationId = UUID.randomUUID()
        unlockPassword = ""
        busy = true
        errorText = null
        scope.launch {
            try {
                val proof = authenticate(secret, AuthPurpose.OPEN_LOCK_SETTINGS)
                opened = proof != null && vm.consumeOpenSettings(proof, operationId)
                if (proof != null && !opened) errorText = context.getString(R.string.app_lock_error_unavailable)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                errorText = context.getString(R.string.app_lock_error_unavailable)
            } finally {
                secret.fill('\u0000')
                pendingSecrets = pendingSecrets.filterNot { it === secret }
                busy = false
            }
        }
    }
    fun submitAction() {
        val selected = action ?: return
        if (busy || snapshot.loadStatus != com.luma.lifeos.domain.lock.LockLoadStatus.READY) return
        val typedCurrent = actionPassword.toCharArray()
        val typedNew = replacementPassword.toCharArray()
        val typedConfirm = replacementConfirmation.toCharArray()
        pendingSecrets = listOf(typedCurrent, typedNew, typedConfirm)
        val operationId = UUID.randomUUID()
        actionPassword = ""
        replacementPassword = ""
        replacementConfirmation = ""
        if (selected == LockSettingsAction.PASSWORD && !typedNew.contentEquals(typedConfirm)) {
            typedCurrent.fill('\u0000')
            typedNew.fill('\u0000')
            typedConfirm.fill('\u0000')
            pendingSecrets = emptyList()
            errorText = context.getString(R.string.app_lock_error_password_mismatch)
            return
        }
        if (selected == LockSettingsAction.PASSWORD && Character.codePointCount(typedNew, 0, typedNew.size) !in 8..128) {
            typedCurrent.fill('\u0000')
            typedNew.fill('\u0000')
            typedConfirm.fill('\u0000')
            pendingSecrets = emptyList()
            errorText = context.getString(R.string.app_lock_error_password_length)
            return
        }
        busy = true
        errorText = null
        var recoveryDisplayStarted = false
        scope.launch {
            try {
                val purpose = when (selected) {
                    LockSettingsAction.POLICY -> AuthPurpose.CHANGE_POLICY
                    LockSettingsAction.PASSWORD -> AuthPurpose.CHANGE_PASSWORD
                    LockSettingsAction.DISABLE -> AuthPurpose.DISABLE_LOCK
                    LockSettingsAction.RECOVERY -> AuthPurpose.REPLACE_RECOVERY_CODE
                }
                val proof = authenticate(typedCurrent, purpose)
                if (proof == null) return@launch
                if (!vm.bindPendingOperation(proof, operationId)) {
                    errorText = context.getString(R.string.app_lock_error_unavailable)
                    return@launch
                }
                when (selected) {
                    LockSettingsAction.POLICY -> {
                        if (!vm.validatePendingOperation(proof, operationId)) {
                            errorText = context.getString(R.string.app_lock_error_unavailable)
                            return@launch
                        }
                        val result = vm.updatePolicy(proof, policy.copy(enabled = true))
                        describeResult(result)
                        if (result is CredentialResult.Success) onBack()
                    }
                    LockSettingsAction.PASSWORD -> {
                        if (!vm.validatePendingOperation(proof, operationId)) {
                            errorText = context.getString(R.string.app_lock_error_unavailable)
                            return@launch
                        }
                        val result = vm.changePassword(proof, typedNew)
                        describeResult(result)
                        if (result is CredentialResult.Success) onBack()
                    }
                    LockSettingsAction.DISABLE -> {
                        if (!vm.validatePendingOperation(proof, operationId)) {
                            errorText = context.getString(R.string.app_lock_error_unavailable)
                            return@launch
                        }
                        val result = vm.disable(proof)
                        describeResult(result)
                        if (result is CredentialResult.Success) onBack()
                    }
                    LockSettingsAction.RECOVERY -> {
                        recoveryDisplayStarted = true
                        displayRecovery(RecoveryDisplayPurpose.ROTATION, proof, operationId)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                errorText = context.getString(R.string.app_lock_error_unavailable)
            } finally {
                listOf(typedCurrent, typedNew, typedConfirm).forEach { it.fill('\u0000') }
                pendingSecrets = pendingSecrets.filterNot { it === typedCurrent || it === typedNew || it === typedConfirm }
                if (!recoveryDisplayStarted) busy = false
            }
        }
    }
    fun submitSetupPassword() {
        if (busy || snapshot.loadStatus != com.luma.lifeos.domain.lock.LockLoadStatus.READY) return
        if (setupPassword != setupConfirmation) {
            errorText = context.getString(R.string.app_lock_error_password_mismatch)
            return
        }
        if (passwordCodePoints(setupPassword) !in 8..128) {
            errorText = context.getString(R.string.app_lock_error_password_length)
            return
        }
        pendingSetupPassword?.fill('\u0000')
        pendingSetupPassword = setupPassword.toCharArray()
        setupPassword = ""
        setupConfirmation = ""
        displayRecovery(RecoveryDisplayPurpose.SETUP)
    }

    AppPageScaffold(
        title = stringResource(R.string.app_lock_settings_title), navigation = PageNavigation.BACK,
        onNavigate = { if (!busy) onBack() }, navigationEnabled = !busy,
        titleTag = "app_lock_settings_heading", navigationTag = "app_lock_settings_back",
    ) { padding ->
        LumaReadingPane(Modifier.fillMaxSize().padding(padding)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)
                    .testTag("screen_app_lock_settings"),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (snapshot.loadStatus != com.luma.lifeos.domain.lock.LockLoadStatus.READY) {
                    AccessibleFeedback(stringResource(R.string.app_lock_error_unavailable),
                        Modifier.testTag("app_lock_settings_unavailable"))
                } else if (recoveryDisplay != null) {
                    Text(stringResource(R.string.app_lock_recovery_code_title),
                        style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.app_lock_recovery_code_body))
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(recoveryDisplay.orEmpty(), Modifier.fillMaxWidth().padding(16.dp)
                            .testTag("app_lock_recovery_code"), style = MaterialTheme.typography.titleMedium)
                    }
                    TextButton(enabled = !busy, onClick = { clipboard.setText(AnnotatedString(recoveryDisplay.orEmpty())) },
                        modifier = Modifier.testTag("app_lock_recovery_copy")) {
                        Text(stringResource(R.string.app_lock_recovery_copy))
                    }
                    Row {
                        androidx.compose.material3.Checkbox(checked = recoveryAcknowledged, enabled = !busy,
                            onCheckedChange = { recoveryAcknowledged = it })
                        Text(stringResource(R.string.app_lock_recovery_ack), Modifier.padding(top = 12.dp))
                    }
                    Button(enabled = recoveryAcknowledged && !busy, onClick = ::finishRecoveryDisplay,
                        modifier = Modifier.testTag("app_lock_recovery_confirm")) {
                        Text(stringResource(R.string.app_lock_recovery_continue))
                    }
                    TextButton(enabled = !busy, onClick = { cancelPendingRecovery(); clearSecrets(); onBack() }) {
                        Text(stringResource(R.string.cancel))
                    }
                } else if (storedPolicy.enabled && !opened) {
                    Text(stringResource(R.string.app_lock_settings_auth_body))
                    SecretField(R.string.app_lock_current_password, unlockPassword, !busy,
                        { unlockPassword = it }, "app_lock_settings_password")
                    Button(enabled = unlockPassword.isNotEmpty() && !busy, onClick = ::submitOpenSettings,
                        modifier = Modifier.testTag("app_lock_settings_auth")) {
                        Text(stringResource(R.string.app_lock_unlock_settings))
                    }
                    TextButton(enabled = !busy, onClick = {
                        clearSecrets()
                        onForgotPassword()
                    }, modifier = Modifier.testTag("app_lock_settings_forgot_password")) {
                        Text(stringResource(R.string.app_lock_forgot_password))
                    }
                    TextButton(onClick = onBack, enabled = !busy) { Text(stringResource(R.string.cancel)) }
                } else if (!storedPolicy.enabled) {
                    Text(stringResource(R.string.app_lock_setup_body))
                    PolicyControls(policy, enabled = !busy, onPolicy = { policy = it })
                    SecretField(R.string.app_lock_new_password, setupPassword, !busy,
                        { setupPassword = it }, "app_lock_setup_password")
                    SecretField(R.string.app_lock_confirm_password, setupConfirmation, !busy,
                        { setupConfirmation = it }, "app_lock_setup_confirm")
                    Button(enabled = !busy, onClick = ::submitSetupPassword,
                        modifier = Modifier.testTag("app_lock_setup_continue")) {
                        Text(stringResource(R.string.app_lock_setup_continue))
                    }
                } else if (opened) {
                    Text(stringResource(R.string.app_lock_settings_body))
                    PolicyControls(policy, enabled = !busy, onPolicy = { policy = it })
                    Button(enabled = !busy, onClick = { action = LockSettingsAction.POLICY },
                        modifier = Modifier.testTag("app_lock_save_policy")) {
                        Text(stringResource(R.string.app_lock_save_policy))
                    }
                    TextButton(enabled = !busy, onClick = { action = LockSettingsAction.PASSWORD },
                        modifier = Modifier.testTag("app_lock_change_password")) {
                        Text(stringResource(R.string.app_lock_change_password))
                    }
                    TextButton(enabled = !busy, onClick = { action = LockSettingsAction.RECOVERY },
                        modifier = Modifier.testTag("app_lock_replace_recovery")) {
                        Text(stringResource(R.string.app_lock_replace_recovery))
                    }
                    TextButton(enabled = !busy, onClick = { action = LockSettingsAction.DISABLE },
                        modifier = Modifier.testTag("app_lock_disable")) {
                        Text(stringResource(R.string.app_lock_disable))
                    }
                    TextButton(enabled = !busy, onClick = { coordinator.lockNow(); onBack() },
                        modifier = Modifier.testTag("app_lock_lock_now")) {
                        Text(stringResource(R.string.app_lock_lock_now))
                    }
                }
                errorText?.let { AccessibleFeedback(it, Modifier.testTag("app_lock_settings_error")) }
                if (busy) androidx.compose.material3.CircularProgressIndicator()
            }
        }
    }

    action?.let { selected ->
        AlertDialog(
            onDismissRequest = { if (!busy) { action = null; actionPassword = ""; replacementPassword = ""; replacementConfirmation = "" } },
            title = { Text(stringResource(when (selected) {
                LockSettingsAction.POLICY -> R.string.app_lock_save_policy
                LockSettingsAction.PASSWORD -> R.string.app_lock_change_password
                LockSettingsAction.DISABLE -> R.string.app_lock_disable
                LockSettingsAction.RECOVERY -> R.string.app_lock_replace_recovery
            })) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecretField(R.string.app_lock_current_password, actionPassword, !busy,
                        { actionPassword = it }, "app_lock_action_password")
                    if (selected == LockSettingsAction.PASSWORD) {
                        SecretField(R.string.app_lock_new_password, replacementPassword, !busy,
                            { replacementPassword = it }, "app_lock_replacement_password")
                        SecretField(R.string.app_lock_confirm_password, replacementConfirmation, !busy,
                            { replacementConfirmation = it }, "app_lock_replacement_confirm")
                    }
                }
            },
            confirmButton = { TextButton(enabled = actionPassword.isNotEmpty() && !busy,
                onClick = ::submitAction, modifier = Modifier.testTag("app_lock_action_confirm")) {
                Text(stringResource(R.string.app_lock_confirm_action))
            } },
            dismissButton = { TextButton(enabled = !busy, onClick = { action = null; clearSecrets() }) {
                Text(stringResource(R.string.cancel))
            } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PolicyControls(policy: LockPolicy, enabled: Boolean, onPolicy: (LockPolicy) -> Unit) {
    Text(stringResource(R.string.app_lock_coverage), style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() })
    Row {
        RadioButton(selected = policy.coverage == LockCoverage.WHOLE_APP, enabled = enabled, onClick = {
            onPolicy(policy.copy(coverage = LockCoverage.WHOLE_APP, sections = emptySet()))
        })
        TextButton(enabled = enabled, onClick = { onPolicy(policy.copy(coverage = LockCoverage.WHOLE_APP, sections = emptySet())) }) {
            Text(stringResource(R.string.app_lock_coverage_whole))
        }
    }
    Row {
        RadioButton(selected = policy.coverage == LockCoverage.SELECTED, enabled = enabled, onClick = {
            onPolicy(policy.copy(coverage = LockCoverage.SELECTED,
                sections = policy.sections.ifEmpty { setOf(LockSection.MEMORIES) }))
        })
        TextButton(enabled = enabled, onClick = { onPolicy(policy.copy(coverage = LockCoverage.SELECTED,
            sections = policy.sections.ifEmpty { setOf(LockSection.MEMORIES) })) }) {
            Text(stringResource(R.string.app_lock_coverage_selected))
        }
    }
    if (policy.coverage == LockCoverage.SELECTED) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LockSection.entries.forEach { section ->
                val selected = section in policy.sections
                FilterChip(selected = selected, enabled = enabled, onClick = {
                    val sections = if (selected) policy.sections - section else policy.sections + section
                    if (sections.isNotEmpty()) onPolicy(policy.copy(sections = sections))
                }, label = { Text(stringResource(section.labelResource())) },
                    modifier = Modifier.testTag("app_lock_section_${section.name.lowercase()}"))
            }
        }
    }
    Text(stringResource(R.string.app_lock_relock), style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() })
    RelockMode.entries.forEach { mode ->
        Row {
            RadioButton(selected = policy.relockMode == mode, enabled = enabled,
                onClick = { onPolicy(policy.copy(relockMode = mode)) })
            TextButton(enabled = enabled, onClick = { onPolicy(policy.copy(relockMode = mode)) }) {
                Text(stringResource(mode.labelResource()))
            }
        }
    }
    Row {
        androidx.compose.material3.Checkbox(checked = policy.screenshotsProtected, enabled = enabled,
            onCheckedChange = { onPolicy(policy.copy(screenshotsProtected = it)) })
        Column(Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.app_lock_screenshot_title))
            Text(stringResource(R.string.app_lock_screenshot_body), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SecretField(label: Int, value: String, enabled: Boolean, onValue: (String) -> Unit, tag: String) {
    var revealed by remember(tag) { mutableStateOf(false) }
    OutlinedTextField(
        value = value, onValueChange = onValue, enabled = enabled,
        label = { Text(stringResource(label)) }, singleLine = true,
        visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { }),
        modifier = Modifier.fillMaxWidth().testTag(tag),
        trailingIcon = {
            TextButton(onClick = { revealed = !revealed }, enabled = enabled,
                modifier = Modifier.testTag("${tag}_visibility")) {
                Text(stringResource(if (revealed) R.string.app_lock_hide_password else R.string.app_lock_show_password))
            }
        },
    )
}

@Composable
internal fun SecurePrivacyWindow() {
    val context = LocalContext.current
    val activity = remember(context) { context.findSecureWindowActivity() }
    DisposableEffect(activity) {
        val window = activity?.window
        val wasSecure = (window?.attributes?.flags ?: 0) and WindowManager.LayoutParams.FLAG_SECURE != 0
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (!wasSecure) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

private fun Context.findSecureWindowActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findSecureWindowActivity()
    else -> null
}

private fun passwordCodePoints(value: String): Int = value.codePointCount(0, value.length)

private fun LockSection.labelResource(): Int = when (this) {
    LockSection.MEMORIES -> R.string.app_lock_section_memories
    LockSection.JOURNAL -> R.string.app_lock_section_journal
    LockSection.MOOD -> R.string.app_lock_section_mood
    LockSection.TASKS -> R.string.app_lock_section_tasks
    LockSection.HABITS -> R.string.app_lock_section_habits
    LockSection.PEOPLE -> R.string.app_lock_section_people
    LockSection.FOCUS -> R.string.app_lock_section_focus
    LockSection.HEALTH_USAGE -> R.string.app_lock_section_health_usage
}

private fun RelockMode.labelResource(): Int = when (this) {
    RelockMode.EVERY_TIME -> R.string.app_lock_relock_every_time
    RelockMode.AFTER_1_MINUTE -> R.string.app_lock_relock_one_minute
    RelockMode.AFTER_5_MINUTES -> R.string.app_lock_relock_five_minutes
    RelockMode.AFTER_15_MINUTES -> R.string.app_lock_relock_fifteen_minutes
}
