package com.luma.lifeos.ui.privacy

import com.luma.lifeos.ui.common.LumaUi
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.luma.lifeos.lock.CredentialDescriptorResult
import com.luma.lifeos.lock.CredentialResult
import com.luma.lifeos.lock.LockCredentialService
import com.luma.lifeos.lock.LockCredentialType
import com.luma.lifeos.lock.OperationProof
import com.luma.lifeos.lock.RecoveryPreparation
import com.luma.lifeos.ui.common.AccessibleFeedback
import com.luma.lifeos.ui.common.AppPageScaffold
import com.luma.lifeos.ui.common.LumaAlertDialog
import com.luma.lifeos.ui.common.LumaButton
import com.luma.lifeos.ui.common.LumaFilterChip
import com.luma.lifeos.ui.common.LumaReadingPane
import com.luma.lifeos.ui.common.PageNavigation
import com.luma.lifeos.ui.theme.onPrimaryFixed
import com.luma.lifeos.ui.theme.primaryFixed
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
    val settingsScrollState = rememberScrollState()
    val storedPolicy = snapshot.policy ?: LockPolicy()
    var observedGeneration by remember { mutableStateOf(storedPolicy.generation) }
    var policy by remember(storedPolicy) { mutableStateOf(storedPolicy) }
    var opened by remember(storedPolicy.generation, storedPolicy.enabled) { mutableStateOf(!storedPolicy.enabled) }
    var descriptorResult by remember { mutableStateOf<CredentialDescriptorResult?>(null) }
    val descriptor = (descriptorResult as? CredentialDescriptorResult.Ready)?.descriptor
    var setupType by remember { mutableStateOf(LockCredentialType.PASSWORD) }
    val setupEditor = rememberLockCredentialEditorState(setupType, requireConfirmation = true)
    val unlockEditor: LockCredentialEditorState? = key(descriptor?.type, descriptor?.generation) {
        if (descriptor == null) null else rememberLockCredentialEditorState(descriptor.type)
    }
    val actionEditor: LockCredentialEditorState? = key(descriptor?.type, descriptor?.generation) {
        if (descriptor == null) null else rememberLockCredentialEditorState(descriptor.type)
    }
    var replacementType by remember(descriptor?.type) { mutableStateOf(descriptor?.type ?: LockCredentialType.PASSWORD) }
    val replacementEditor = key(replacementType, descriptor?.generation) {
        rememberLockCredentialEditorState(replacementType, requireConfirmation = true)
    }
    var action by remember { mutableStateOf<LockSettingsAction?>(null) }
    var recoveryPreparation by remember { mutableStateOf<RecoveryPreparation?>(null) }
    var recoveryRequestGeneration by remember { mutableStateOf(0L) }
    var recoveryProof by remember { mutableStateOf<OperationProof?>(null) }
    var recoveryOperationId by remember { mutableStateOf<UUID?>(null) }
    var recoveryDisplay by remember { mutableStateOf<String?>(null) }
    var recoveryPurpose by remember { mutableStateOf<RecoveryDisplayPurpose?>(null) }
    var pendingSetupSecret by remember { mutableStateOf<CharArray?>(null) }
    var pendingSetupType by remember { mutableStateOf(LockCredentialType.PASSWORD) }
    var recoveryAcknowledged by remember { mutableStateOf(false) }
    var pendingSecrets by remember { mutableStateOf(emptyList<CharArray>()) }
    var passwordChangeProof by remember { mutableStateOf<OperationProof?>(null) }
    var passwordChangeOperationId by remember { mutableStateOf<UUID?>(null) }
    var authRequestEpoch by remember { mutableStateOf(0L) }
    var busy by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(errorText, settingsScrollState.maxValue) {
        if (errorText != null) settingsScrollState.animateScrollTo(settingsScrollState.maxValue)
    }

    fun clearPasswordChangeAuthorization() {
        passwordChangeProof = null
        passwordChangeOperationId = null
    }

    fun clearVisibleSecrets() {
        setupEditor.clear()
        unlockEditor?.clear()
        actionEditor?.clear()
        replacementEditor.clear()
        recoveryDisplay = null
        recoveryAcknowledged = false
    }
    fun clearSecrets() {
        authRequestEpoch = if (authRequestEpoch == Long.MAX_VALUE) 0L else authRequestEpoch + 1L
        pendingSecrets.forEach { it.fill('\u0000') }
        pendingSecrets = emptyList()
        pendingSetupSecret?.fill('\u0000')
        pendingSetupSecret = null
        pendingSetupType = LockCredentialType.PASSWORD
        clearVisibleSecrets()
        recoveryProof = null
        recoveryOperationId = null
    }
    fun clearClipboard() {
        runCatching { context.getSystemService(ClipboardManager::class.java)?.clearPrimaryClip() }
    }
    fun cancelPendingRecovery() {
        recoveryRequestGeneration++
        val pending = recoveryPreparation
        recoveryPreparation = null
        if (pending != null) scope.launch { vm.cancelRecovery(pending) }
        clearClipboard()
    }

    LaunchedEffect(snapshot.loadStatus, storedPolicy.enabled, storedPolicy.generation) {
        descriptorResult = when {
            snapshot.loadStatus != com.luma.lifeos.domain.lock.LockLoadStatus.READY -> null
            !storedPolicy.enabled -> CredentialDescriptorResult.Disabled
            else -> {
                val loaded = vm.credentialDescriptor()
                if (loaded is CredentialDescriptorResult.Ready &&
                    loaded.descriptor.generation != storedPolicy.generation
                ) CredentialDescriptorResult.Unavailable else loaded
            }
        }
    }
    LaunchedEffect(snapshot.loadStatus, snapshot.policy?.generation) {
        val currentGeneration = snapshot.policy?.generation
        if (snapshot.loadStatus != com.luma.lifeos.domain.lock.LockLoadStatus.READY) {
            cancelPendingRecovery()
            clearSecrets()
            clearPasswordChangeAuthorization()
            action = null
            opened = false
        } else if (currentGeneration != null && currentGeneration != observedGeneration) {
            observedGeneration = currentGeneration
            cancelPendingRecovery()
            clearSecrets()
            clearPasswordChangeAuthorization()
            action = null
            opened = false
        }
    }
    DisposableEffect(lifecycle, vm) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                clearSecrets()
                clearPasswordChangeAuthorization()
                cancelPendingRecovery()
                action = null
                recoveryProof = null
                opened = false
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            clearPasswordChangeAuthorization()
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
    suspend fun authenticate(secret: CharArray, purpose: AuthPurpose, expectedGeneration: Long): OperationProof? {
        val result = vm.verify(secret, purpose, expectedGeneration)
        describeResult(result)
        val current = coordinator.snapshot.value
        if (current.loadStatus != com.luma.lifeos.domain.lock.LockLoadStatus.READY ||
            current.policy?.generation != expectedGeneration
        ) {
            describeResult(CredentialResult.Cancelled)
            return null
        }
        return (result as? CredentialResult.Success)?.proof
    }
    fun displayRecovery(
        purpose: RecoveryDisplayPurpose,
        proof: OperationProof? = null,
        operationId: UUID? = null,
    ) {
        val requestGeneration = recoveryRequestGeneration + 1L
        recoveryRequestGeneration = requestGeneration
        busy = true
        errorText = null
        scope.launch {
            try {
                val preparation = vm.prepareRecovery()
                val code = preparation.takeCodeForDisplay()
                if (requestGeneration != recoveryRequestGeneration) {
                    code.fill('\u0000')
                    vm.cancelRecovery(preparation)
                    return@launch
                }
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
        val setupSecret = if (purpose == RecoveryDisplayPurpose.SETUP) {
            pendingSetupSecret ?: run {
                errorText = context.getString(R.string.app_lock_error_unavailable)
                busy = false
                return
            }
        } else charArrayOf()
        val setupType = pendingSetupType
        if (purpose == RecoveryDisplayPurpose.SETUP) clearVisibleSecrets() else clearSecrets()
        scope.launch {
            try {
                val confirmation = vm.confirmRecovery(preparation)
                val result = when (purpose) {
                    RecoveryDisplayPurpose.SETUP -> vm.enable(
                        setupSecret,
                        setupType,
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
                if (pendingSetupSecret === setupSecret) pendingSetupSecret = null
                if (purpose == RecoveryDisplayPurpose.SETUP) pendingSetupType = LockCredentialType.PASSWORD
                clearClipboard()
                busy = false
            }
        }
    }
    fun submitOpenSettings() {
        val expectedGeneration = descriptor?.generation ?: return
        if (busy || snapshot.loadStatus != com.luma.lifeos.domain.lock.LockLoadStatus.READY) return
        val secret = unlockEditor?.takeSecret() ?: return
        clearPasswordChangeAuthorization()
        pendingSecrets = listOf(secret)
        val requestEpoch = authRequestEpoch
        val operationId = UUID.randomUUID()
        busy = true
        errorText = null
        scope.launch {
            try {
                val proof = authenticate(secret, AuthPurpose.CHANGE_PASSWORD, expectedGeneration)
                if (requestEpoch != authRequestEpoch) {
                    describeResult(CredentialResult.Cancelled)
                    return@launch
                }
                opened = proof != null && vm.bindPendingOperation(proof, operationId) &&
                    vm.validatePendingOperation(proof, operationId)
                if (opened) {
                    passwordChangeProof = proof
                    passwordChangeOperationId = operationId
                }
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
        val expectedGeneration = descriptor?.generation ?: return
        if (busy || snapshot.loadStatus != com.luma.lifeos.domain.lock.LockLoadStatus.READY) return
        if (selected == LockSettingsAction.PASSWORD && !replacementEditor.isComplete) {
            errorText = context.getString(
                if (replacementEditor.patternMismatch ||
                    (replacementEditor.hasEntry && replacementEditor.hasConfirmation)
                )
                    R.string.app_lock_error_credential_mismatch else R.string.app_lock_error_credential_invalid,
            )
            return
        }
        val passwordAuthorization = if (selected == LockSettingsAction.PASSWORD) {
            val proof = passwordChangeProof
            val operationId = passwordChangeOperationId
            if (proof == null || operationId == null) {
                errorText = context.getString(R.string.app_lock_error_unavailable)
                return
            }
            proof to operationId
        } else null
        val typedCurrent = if (selected == LockSettingsAction.PASSWORD) {
            charArrayOf()
        } else {
            actionEditor?.takeSecret() ?: return
        }
        val typedNew = if (selected == LockSettingsAction.PASSWORD) replacementEditor.takeSecret() ?: run {
            typedCurrent.fill('\u0000')
            errorText = context.getString(R.string.app_lock_error_credential_invalid)
            return
        } else charArrayOf()
        pendingSecrets = listOf(typedCurrent, typedNew)
        val requestEpoch = authRequestEpoch
        val operationId = passwordAuthorization?.second ?: UUID.randomUUID()
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
                val proof = passwordAuthorization?.first ?: authenticate(typedCurrent, purpose, expectedGeneration)
                if (requestEpoch != authRequestEpoch) {
                    describeResult(CredentialResult.Cancelled)
                    return@launch
                }
                if (proof == null) return@launch
                if (!vm.bindPendingOperation(proof, operationId)) {
                    if (selected == LockSettingsAction.PASSWORD) clearPasswordChangeAuthorization()
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
                            clearPasswordChangeAuthorization()
                            errorText = context.getString(R.string.app_lock_error_unavailable)
                            return@launch
                        }
                        clearPasswordChangeAuthorization()
                        val result = vm.changePassword(proof, typedNew, replacementType)
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
                listOf(typedCurrent, typedNew).forEach { it.fill('\u0000') }
                pendingSecrets = pendingSecrets.filterNot { it === typedCurrent || it === typedNew }
                if (!recoveryDisplayStarted) busy = false
            }
        }
    }
    fun submitSetupCredential() {
        if (busy || snapshot.loadStatus != com.luma.lifeos.domain.lock.LockLoadStatus.READY) return
        val secret = setupEditor.takeSecret()
        if (secret == null) {
            errorText = context.getString(
                if (setupEditor.patternMismatch || (setupEditor.hasEntry && setupEditor.hasConfirmation))
                    R.string.app_lock_error_credential_mismatch else R.string.app_lock_error_credential_invalid,
            )
            return
        }
        pendingSetupSecret?.fill('\u0000')
        pendingSetupSecret = secret
        pendingSetupType = setupType
        displayRecovery(RecoveryDisplayPurpose.SETUP)
    }

    AppPageScaffold(
        title = stringResource(R.string.app_lock_settings_title), navigation = PageNavigation.BACK,
        onNavigate = { if (!busy) onBack() }, navigationEnabled = !busy,
        titleTag = "app_lock_settings_heading", navigationTag = "app_lock_settings_back",
        actions = {
            Surface(
                modifier = Modifier.padding(end = 12.dp).size(40.dp),
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_person),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        },
    ) { padding ->
        LumaReadingPane(Modifier.fillMaxSize().padding(padding)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(settingsScrollState)
                    .padding(horizontal = LumaUi.PageGutter, vertical = LumaUi.SectionGap)
                    .testTag("screen_app_lock_settings"),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                if (snapshot.loadStatus != com.luma.lifeos.domain.lock.LockLoadStatus.READY) {
                    AccessibleFeedback(stringResource(R.string.app_lock_error_unavailable),
                        Modifier.testTag("app_lock_settings_unavailable"))
                } else if (recoveryDisplay != null) {
                    Text(stringResource(R.string.app_lock_recovery_code_title),
                        style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.app_lock_recovery_code_body))
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(recoveryDisplay.orEmpty(), Modifier.fillMaxWidth().padding(LumaUi.SectionPadding)
                            .testTag("app_lock_recovery_code"), style = MaterialTheme.typography.titleMedium)
                    }
                    LumaTextButton(enabled = !busy, onClick = { clipboard.setText(AnnotatedString(recoveryDisplay.orEmpty())) },
                        modifier = Modifier.testTag("app_lock_recovery_copy")) {
                        Text(stringResource(R.string.app_lock_recovery_copy))
                    }
                    Row {
                        androidx.compose.material3.Checkbox(checked = recoveryAcknowledged, enabled = !busy,
                            onCheckedChange = { recoveryAcknowledged = it })
                        Text(stringResource(R.string.app_lock_recovery_ack), Modifier.padding(top = 12.dp))
                    }
                    LumaButton(enabled = recoveryAcknowledged && !busy, onClick = ::finishRecoveryDisplay,
                        modifier = Modifier.testTag("app_lock_recovery_confirm")) {
                        Text(stringResource(R.string.app_lock_recovery_continue))
                    }
                    LumaTextButton(enabled = !busy, onClick = { cancelPendingRecovery(); clearSecrets(); onBack() }) {
                        Text(stringResource(R.string.cancel))
                    }
                } else if (storedPolicy.enabled && !opened) {
                    Text(stringResource(R.string.app_lock_settings_auth_body))
                    when (descriptorResult) {
                        null -> Text(stringResource(R.string.app_lock_credential_loading))
                        is CredentialDescriptorResult.Ready -> unlockEditor?.let { state ->
                            LockCredentialInput(
                                state = state,
                                enabled = !busy,
                                label = stringResource(R.string.app_lock_current_credential),
                                modifier = Modifier.testTag("app_lock_settings_password"),
                            )
                        }
                        CredentialDescriptorResult.Disabled,
                        CredentialDescriptorResult.Unavailable -> AccessibleFeedback(
                            stringResource(R.string.app_lock_error_unavailable),
                            Modifier.testTag("app_lock_settings_unavailable"),
                        )
                    }
                    LumaButton(enabled = unlockEditor?.isComplete == true && !busy, onClick = ::submitOpenSettings,
                        modifier = Modifier.testTag("app_lock_settings_auth")) {
                        Text(stringResource(R.string.app_lock_unlock_settings))
                    }
                    LumaTextButton(enabled = !busy, onClick = {
                        clearSecrets()
                        onForgotPassword()
                    }, modifier = Modifier.testTag("app_lock_settings_forgot_password")) {
                        Text(stringResource(R.string.app_lock_forgot_password))
                    }
                    LumaTextButton(onClick = onBack, enabled = !busy) { Text(stringResource(R.string.cancel)) }
                } else if (!storedPolicy.enabled) {
                    Text(stringResource(R.string.app_lock_setup_body))
                    PolicyControls(policy, enabled = !busy, onPolicy = { policy = it })
                    LockCredentialTypeSelector(setupType, !busy, onSelected = { setupType = it })
                    LockCredentialEnrollment(
                        state = setupEditor,
                        enabled = !busy,
                        modifier = Modifier.testTag("app_lock_setup_credential"),
                    )
                    LumaButton(enabled = setupEditor.isComplete && !busy, onClick = ::submitSetupCredential,
                        modifier = Modifier.testTag("app_lock_setup_continue")) {
                        Text(stringResource(R.string.app_lock_setup_continue))
                    }
                } else if (opened) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                contentColor = MaterialTheme.colorScheme.primary,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_settings_lock),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                stringResource(R.string.app_lock_settings_body),
                                modifier = Modifier.padding(top = 2.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    descriptor?.let { current ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    modifier = Modifier.size(44.dp),
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                    color = MaterialTheme.colorScheme.primaryFixed,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryFixed,
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            painter = painterResource(
                                                if (current.type == LockCredentialType.PIN) {
                                                    R.drawable.ic_dialpad
                                                } else {
                                                    R.drawable.ic_settings_lock
                                                },
                                            ),
                                            contentDescription = null,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }
                                }
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.app_lock_current_method).uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        letterSpacing = 0.7.sp,
                                    )
                                    Text(
                                        stringResource(current.type.labelResource()),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                }
                                androidx.compose.material3.FilledTonalButton(
                                    onClick = {
                                        replacementType = current.type
                                        action = LockSettingsAction.PASSWORD
                                    },
                                    enabled = !busy,
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        contentColor = MaterialTheme.colorScheme.onSurface,
                                    ),
                                    modifier = Modifier.testTag("app_lock_change_password"),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                        horizontal = 18.dp,
                                        vertical = 0.dp,
                                    ),
                                ) {
                                    Text(stringResource(R.string.app_lock_change_method_short))
                                }
                            }
                        }
                    }
                    PolicyControls(policy, enabled = !busy, onPolicy = { policy = it })
                    LumaButton(enabled = !busy, onClick = { action = LockSettingsAction.POLICY },
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("app_lock_save_policy")) {
                        Text(stringResource(R.string.app_lock_save_policy))
                    }
                    LumaTextButton(enabled = !busy, onClick = { action = LockSettingsAction.RECOVERY },
                        modifier = Modifier.testTag("app_lock_replace_recovery")) {
                        Text(stringResource(R.string.app_lock_replace_recovery))
                    }
                    LumaTextButton(enabled = !busy, onClick = { action = LockSettingsAction.DISABLE },
                        modifier = Modifier.testTag("app_lock_disable")) {
                        Text(stringResource(R.string.app_lock_disable))
                    }
                    LumaTextButton(enabled = !busy, onClick = { coordinator.lockNow(); onBack() },
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
        LumaAlertDialog(
            onDismissRequest = { if (!busy) { action = null; clearSecrets() } },
            title = { Text(stringResource(when (selected) {
                LockSettingsAction.POLICY -> R.string.app_lock_save_policy
                LockSettingsAction.PASSWORD -> R.string.app_lock_change_password
                LockSettingsAction.DISABLE -> R.string.app_lock_disable
                LockSettingsAction.RECOVERY -> R.string.app_lock_replace_recovery
            })) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(LumaUi.ContentGap),
                ) {
                    if (selected != LockSettingsAction.PASSWORD) {
                        when (descriptorResult) {
                            null -> Text(stringResource(R.string.app_lock_credential_loading))
                            is CredentialDescriptorResult.Ready -> actionEditor?.let { state ->
                                LockCredentialInput(
                                    state = state,
                                    enabled = !busy,
                                    label = stringResource(R.string.app_lock_current_credential),
                                    modifier = Modifier.testTag("app_lock_action_password"),
                                )
                            }
                            CredentialDescriptorResult.Disabled,
                            CredentialDescriptorResult.Unavailable -> AccessibleFeedback(
                                stringResource(R.string.app_lock_error_unavailable),
                            )
                        }
                    }
                    if (selected == LockSettingsAction.PASSWORD) {
                        LockCredentialTypeSelector(replacementType, !busy, onSelected = { replacementType = it })
                        LockCredentialEnrollment(
                            state = replacementEditor,
                            enabled = !busy,
                            modifier = Modifier.testTag("app_lock_replacement_credential"),
                        )
                    }
                }
            },
            confirmButton = { LumaTextButton(enabled =
                (selected == LockSettingsAction.PASSWORD || actionEditor?.isComplete == true) &&
                    (selected != LockSettingsAction.PASSWORD ||
                        (passwordChangeProof != null && replacementEditor.isComplete)) && !busy,
                onClick = ::submitAction, modifier = Modifier.testTag("app_lock_action_confirm")) {
                Text(stringResource(R.string.app_lock_confirm_action))
            } },
            dismissButton = { LumaTextButton(enabled = !busy, onClick = { action = null; clearSecrets() }) {
                Text(stringResource(R.string.cancel))
            } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PolicyControls(policy: LockPolicy, enabled: Boolean, onPolicy: (LockPolicy) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(LumaUi.ContentGap)) {
            Text(
                stringResource(R.string.app_lock_coverage),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 4.dp).semantics { heading() },
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = LumaUi.PageGutter, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    LockPolicyChoice(
                        selected = policy.coverage == LockCoverage.WHOLE_APP,
                        enabled = enabled,
                        label = stringResource(R.string.app_lock_coverage_whole),
                        onClick = { onPolicy(policy.copy(coverage = LockCoverage.WHOLE_APP, sections = emptySet())) },
                        testTag = "app_lock_coverage_whole",
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(LumaUi.ContentGap)) {
                        LockPolicyChoice(
                            selected = policy.coverage == LockCoverage.SELECTED,
                            enabled = enabled,
                            label = stringResource(R.string.app_lock_coverage_selected),
                            onClick = {
                                onPolicy(policy.copy(coverage = LockCoverage.SELECTED,
                                    sections = policy.sections.ifEmpty { setOf(LockSection.MEMORIES) }))
                            },
                            testTag = "app_lock_coverage_selected",
                        )
                        if (policy.coverage == LockCoverage.SELECTED) {
                            FlowRow(
                                modifier = Modifier.padding(start = 44.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(LumaUi.ContentGap),
                                verticalArrangement = Arrangement.spacedBy(LumaUi.RelatedGap),
                            ) {
                                LockSection.entries.filterNot { it == LockSection.JOURNAL }.forEach { section ->
                                    val selected = section in policy.sections
                                    LumaFilterChip(
                                        selected = selected,
                                        enabled = enabled,
                                        onClick = {
                                            val sections = if (selected) policy.sections - section else policy.sections + section
                                            if (sections.isNotEmpty()) onPolicy(policy.copy(sections = sections))
                                        },
                                        label = { Text(stringResource(section.labelResource())) },
                                        leadingIcon = if (selected) {
                                            {
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_check),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                            }
                                        } else null,
                                        modifier = Modifier.testTag("app_lock_section_${section.name.lowercase()}"),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(LumaUi.ContentGap)) {
            Text(
                stringResource(R.string.app_lock_relock),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 4.dp).semantics { heading() },
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = LumaUi.PageGutter, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    RelockMode.entries.forEach { mode ->
                        LockPolicyChoice(
                            selected = policy.relockMode == mode,
                            enabled = enabled,
                            label = stringResource(mode.labelResource()),
                            onClick = { onPolicy(policy.copy(relockMode = mode)) },
                            testTag = "app_lock_relock_${mode.name.lowercase()}",
                        )
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().toggleable(
                    value = policy.screenshotsProtected,
                    enabled = enabled,
                    role = Role.Checkbox,
                    onValueChange = { onPolicy(policy.copy(screenshotsProtected = it)) },
                ).padding(18.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Checkbox(
                    checked = policy.screenshotsProtected,
                    enabled = enabled,
                    onCheckedChange = null,
                    modifier = Modifier.padding(top = 1.dp).testTag("app_lock_screenshot_protection"),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.padding(top = 4.dp)) {
                    Text(stringResource(R.string.app_lock_screenshot_title), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.app_lock_screenshot_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun LockPolicyChoice(
    selected: Boolean,
    enabled: Boolean,
    label: String,
    onClick: () -> Unit,
    testTag: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().selectable(
            selected = selected,
            enabled = enabled,
            role = Role.RadioButton,
            onClick = onClick,
        ).testTag(testTag).padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, enabled = enabled, onClick = null)
        Text(
            text = label,
            modifier = Modifier.padding(start = 4.dp),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
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

private fun LockCredentialType.labelResource(): Int = when (this) {
    LockCredentialType.PIN -> R.string.app_lock_credential_pin
    LockCredentialType.PASSWORD -> R.string.app_lock_credential_password
    LockCredentialType.PATTERN -> R.string.app_lock_credential_pattern
}

private fun RelockMode.labelResource(): Int = when (this) {
    RelockMode.EVERY_TIME -> R.string.app_lock_relock_every_time
    RelockMode.AFTER_1_MINUTE -> R.string.app_lock_relock_one_minute
    RelockMode.AFTER_5_MINUTES -> R.string.app_lock_relock_five_minutes
    RelockMode.AFTER_15_MINUTES -> R.string.app_lock_relock_fifteen_minutes
}
