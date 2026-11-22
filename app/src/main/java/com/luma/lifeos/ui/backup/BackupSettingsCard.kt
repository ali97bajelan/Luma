package com.luma.lifeos.ui.backup

import com.luma.lifeos.ui.common.LumaUi
import com.luma.lifeos.ui.common.LumaOutlinedButton

import com.luma.lifeos.ui.common.LumaTextButton

import com.luma.lifeos.ui.common.LumaButton

import com.luma.lifeos.ui.common.LumaAlertDialog

import android.content.ActivityNotFoundException
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.luma.lifeos.ui.common.LumaOutlinedTextField
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavBackStackEntry
import com.luma.lifeos.R
import com.luma.lifeos.domain.backup.BackupService
import com.luma.lifeos.domain.backup.BackupArchiveFormat
import com.luma.lifeos.domain.backup.BackupEncryptionOptions
import com.luma.lifeos.domain.backup.BackupFormatMarker
import com.luma.lifeos.domain.backup.BackupFocusExportChoice
import com.luma.lifeos.domain.focus.FocusSession
import com.luma.lifeos.domain.focus.FocusState
import com.luma.lifeos.focus.FocusRuntime
import com.luma.lifeos.lock.AuthPurpose
import com.luma.lifeos.lock.LockSensitiveOperationGuard
import com.luma.lifeos.lock.OperationProof
import com.luma.lifeos.lock.PendingSensitiveOperation
import com.luma.lifeos.ui.privacy.LockSensitiveActionDialog
import com.luma.lifeos.language.rememberDisplayTimePolicy
import com.luma.lifeos.ui.common.AccessibleFeedback
import com.luma.lifeos.ui.common.LumaSectionSurface
import com.luma.lifeos.ui.navigation.AppRoutes
import com.luma.lifeos.ui.settings.SettingsSectionPage
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.FormatStyle
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val BACKUP_DEPARTURE_FEEDBACK_KEY = "backup_departure_feedback"
internal object BackupDepartureSession { val id: String = UUID.randomUUID().toString() }

@Composable
fun BackupSettingsCard(
    service: BackupService,
    focusRuntime: FocusRuntime? = null,
    lockGuard: LockSensitiveOperationGuard? = null,
    onAppLockSetup: () -> Unit = {},
    onRestored: () -> Unit = {},
    onBack: (BackupDeparture?) -> Unit,
) {
    val factory = remember(service) { viewModelFactory { initializer { BackupViewModel(service) } } }
    val vm: BackupViewModel = viewModel(factory = factory)
    val state by vm.state.collectAsStateWithLifecycle()
    val focusRuntimeState = if (focusRuntime != null) {
        focusRuntime.state.collectAsStateWithLifecycle().value
    } else null
    val openFocusSession = focusRuntimeState?.session?.takeIf { !it.isTerminal }
    val currentOnRestored by rememberUpdatedState(onRestored)
    val currentOnBack by rememberUpdatedState(onBack)
    val context = LocalContext.current.applicationContext
    val resolver = context.contentResolver
    val scope = rememberCoroutineScope()
    var leavePromptFor by remember { mutableStateOf<BackupOperation?>(null) }
    var showRestoreInProgress by remember { mutableStateOf(false) }
    var showPausedExportDisclosure by remember { mutableStateOf(false) }
    var focusActionFailed by remember { mutableStateOf(false) }
    var pendingExportChoice by remember { mutableStateOf<BackupFocusExportChoice?>(null) }
    var restoreActionInProgress by remember { mutableStateOf(false) }
    var pendingSensitive by remember { mutableStateOf<PendingSensitiveOperation?>(null) }
    var pendingSensitiveStep by remember { mutableStateOf<BackupSensitiveStep?>(null) }
    var showSensitivePrompt by remember { mutableStateOf(false) }
    var sensitiveError by remember { mutableStateOf(false) }
    var exportAuthorization by remember { mutableStateOf<BackupSensitiveAuthorization?>(null) }
    var restorePreparationAuthorization by remember { mutableStateOf<BackupSensitiveAuthorization?>(null) }
    var showRestoreProtectionChoice by remember { mutableStateOf(false) }
    var restoreWithoutProtectionConfirmed by remember { mutableStateOf<Boolean?>(null) }
    var showExportFormatChoice by remember { mutableStateOf(false) }
    var showExportPasswordDialog by remember { mutableStateOf(false) }
    var showRestorePasswordDialog by remember { mutableStateOf(false) }
    var formatDetectionInProgress by remember { mutableStateOf(false) }
    var exportPassword by remember { mutableStateOf("") }
    var exportPasswordConfirmation by remember { mutableStateOf("") }
    var restorePassword by remember { mutableStateOf("") }
    var exportPasswordVisible by remember { mutableStateOf(false) }
    var restorePasswordVisible by remember { mutableStateOf(false) }
    var pendingExportFormat by remember { mutableStateOf<BackupArchiveFormat?>(null) }
    var pendingExportPassword by remember { mutableStateOf<CharArray?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var pendingRestoreAuthorization by remember { mutableStateOf<BackupSensitiveAuthorization?>(null) }

    fun clearPendingExportPassword() {
        pendingExportPassword?.fill(Char.MIN_VALUE)
        pendingExportPassword = null
    }

    fun clearExportPasswordFields() {
        exportPassword = ""
        exportPasswordConfirmation = ""
        exportPasswordVisible = false
    }

    fun clearRestorePasswordFields() {
        restorePassword = ""
        restorePasswordVisible = false
    }

    DisposableEffect(Unit) {
        onDispose {
            pendingExportPassword?.fill(Char.MIN_VALUE)
            pendingExportPassword = null
            exportPassword = ""
            exportPasswordConfirmation = ""
            restorePassword = ""
            exportAuthorization?.let { lockGuard?.cancel(it.pending) }
            restorePreparationAuthorization?.let { lockGuard?.cancel(it.pending) }
            pendingRestoreAuthorization?.let { lockGuard?.cancel(it.pending) }
            pendingSensitive?.let { lockGuard?.cancel(it) }
        }
    }

    LaunchedEffect(state.operation) {
        if (state.operation != BackupOperation.RESTORING) showRestoreInProgress = false
    }

    fun finishExportDocument(uri: Uri?) {
        val authorization = exportAuthorization
        exportAuthorization = null
        val format = pendingExportFormat
        pendingExportFormat = null
        if (uri == null || format == null) {
            pendingExportChoice = null
            clearPendingExportPassword()
            clearExportPasswordFields()
            authorization?.let { lockGuard?.cancel(it.pending) }
            return
        }

        val focusChoice = pendingExportChoice
        pendingExportChoice = null
        val open = { requireNotNull(resolver.openOutputStream(uri, "wt")) }
        val discard: suspend () -> Boolean = { discardCreatedDocument(resolver, uri) }
        when (format) {
            BackupArchiveFormat.PLAINTEXT_ZIP -> {
                clearPendingExportPassword()
                vm.export(
                    open = open,
                    discardOutput = discard,
                    focusChoice = focusChoice,
                    authorize = authorization.authorizer(lockGuard, AuthPurpose.EXPORT),
                )
            }
            BackupArchiveFormat.ENCRYPTED -> {
                val password = pendingExportPassword
                pendingExportPassword = null
                if (password == null) {
                    vm.exportCouldNotStart()
                    authorization?.let { lockGuard?.cancel(it.pending) }
                } else {
                    vm.exportEncrypted(
                        open = open,
                        discardOutput = discard,
                        focusChoice = focusChoice,
                        encryption = BackupEncryptionOptions(password),
                        authorize = authorization.authorizer(lockGuard, AuthPurpose.EXPORT),
                    )
                }
            }
        }
        clearExportPasswordFields()
    }

    fun finishRestoreDocument(uri: Uri?) {
        val authorization = restorePreparationAuthorization
        restorePreparationAuthorization = null
        if (uri == null) {
            authorization?.let { lockGuard?.cancel(it.pending) }
            return
        }
        pendingRestoreAuthorization = authorization
        formatDetectionInProgress = true
        scope.launch {
            val encrypted = try {
                withContext(Dispatchers.IO) { isEncryptedBackup(resolver, uri) }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                false
            }
            formatDetectionInProgress = false
            if (encrypted) {
                pendingRestoreUri = uri
                showRestorePasswordDialog = true
            } else {
                pendingRestoreAuthorization = null
                vm.prepare(
                    open = { requireNotNull(resolver.openInputStream(uri)) },
                    authorize = authorization.authorizer(lockGuard, AuthPurpose.RESTORE),
                )
            }
        }
    }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) {
        finishExportDocument(it)
    }
    val encryptedExport = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupFormatMarker.ENCRYPTED_MIME_TYPE),
    ) { finishExportDocument(it) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        finishRestoreDocument(it)
    }

    val launchExportPicker: (BackupFocusExportChoice?, BackupArchiveFormat) -> Unit = { focusChoice, format ->
        try {
            focusActionFailed = false
            pendingExportChoice = focusChoice
            pendingExportFormat = format
            when (format) {
                BackupArchiveFormat.PLAINTEXT_ZIP -> export.launch("Luma-${LocalDate.now()}.zip")
                BackupArchiveFormat.ENCRYPTED -> encryptedExport.launch(
                    "Luma-${LocalDate.now()}.${BackupFormatMarker.ENCRYPTED_FILE_EXTENSION}",
                )
            }
        } catch (_: ActivityNotFoundException) {
            clearPendingExportPassword()
            pendingExportFormat = null
            pendingExportChoice = null
            exportAuthorization?.let { authorization -> lockGuard?.cancel(authorization.pending) }
            exportAuthorization = null
            vm.pickerUnavailable()
        }
    }

    val launchRestorePicker: (BackupSensitiveAuthorization?) -> Unit = { authorization ->
        try {
            restorePreparationAuthorization = authorization
            restore.launch(arrayOf(
                "application/zip",
                "application/octet-stream",
                "application/x-zip-compressed",
                BackupFormatMarker.ENCRYPTED_MIME_TYPE,
            ))
        } catch (_: ActivityNotFoundException) {
            restorePreparationAuthorization = null
            authorization?.let { lockGuard?.cancel(it.pending) }
            vm.pickerUnavailable()
        }
    }

    val performRestore: (Boolean?, BackupSensitiveAuthorization?) -> Unit = { discardOpenSession, authorization ->
        if (!restoreActionInProgress) {
            restoreActionInProgress = true
            scope.launch {
                try {
                    val ready = focusRuntime == null || focusRuntime.prepareDatabaseReplacement(discardOpenSession)
                    if (!ready) {
                        focusActionFailed = true
                        restoreActionInProgress = false
                        authorization?.let { lockGuard?.cancel(it.pending) }
                        return@launch
                    }
                    focusActionFailed = false
                    val started = vm.confirmRestore(
                        onRestored = { currentOnRestored() },
                        onFinished = {
                            focusRuntime?.finishDatabaseReplacement()
                            restoreActionInProgress = false
                        },
                        authorize = authorization.authorizer(lockGuard, AuthPurpose.RESTORE),
                    )
                    if (!started) {
                        focusRuntime?.finishDatabaseReplacement()
                        restoreActionInProgress = false
                        authorization?.let { lockGuard?.cancel(it.pending) }
                    }
                } catch (failure: Exception) {
                    if (failure is CancellationException) {
                        focusRuntime?.finishDatabaseReplacement()
                        restoreActionInProgress = false
                        throw failure
                    }
                    focusRuntime?.finishDatabaseReplacement()
                    focusActionFailed = true
                    restoreActionInProgress = false
                    authorization?.let { lockGuard?.cancel(it.pending) }
                }
            }
        }
    }

    val dispatchSensitiveStep: (BackupSensitiveStep, BackupSensitiveAuthorization?) -> Unit = { step, authorization ->
        when (step) {
            is BackupSensitiveStep.ExportPicker -> {
                scope.launch {
                    val focusChoice = step.focusChoice
                    val ready = when (focusChoice) {
                        BackupFocusExportChoice.STOPPED_AND_INCLUDE -> openFocusSession?.id?.let {
                            focusRuntime?.stopPausedForBackup(it)
                        } == true
                        is BackupFocusExportChoice.EXCLUDE_PAUSED ->
                            focusRuntime?.confirmPausedExportExclusion(focusChoice.sessionId) == true
                        null -> true
                    }
                    if (ready) {
                        val format = pendingExportFormat
                        if (format != null && (format != BackupArchiveFormat.ENCRYPTED || pendingExportPassword != null)) {
                            exportAuthorization = authorization
                            launchExportPicker(focusChoice, format)
                        } else {
                            clearPendingExportPassword()
                            pendingExportFormat = null
                            authorization?.let { lockGuard?.cancel(it.pending) }
                            sensitiveError = true
                        }
                    } else {
                        clearPendingExportPassword()
                        pendingExportFormat = null
                        authorization?.let { lockGuard?.cancel(it.pending) }
                        focusActionFailed = true
                    }
                }
            }
            BackupSensitiveStep.RestorePicker -> launchRestorePicker(authorization)
            is BackupSensitiveStep.RestoreCommit -> performRestore(step.discardOpenSession, authorization)
        }
    }

    val requestSensitiveStep: (BackupSensitiveStep) -> Unit = { step ->
        if (lockGuard?.requiresAuthentication() == true) {
            val pending = lockGuard.begin(step.purpose)
            if (pending == null) {
                if (step is BackupSensitiveStep.ExportPicker) {
                    clearPendingExportPassword()
                    pendingExportFormat = null
                    pendingExportChoice = null
                }
                sensitiveError = true
            } else {
                pendingSensitive = pending
                pendingSensitiveStep = step
                showSensitivePrompt = true
                sensitiveError = false
            }
        } else dispatchSensitiveStep(step, null)
    }

    val startExport: (BackupArchiveFormat) -> Unit = { format ->
        pendingExportFormat = format
        if (format == BackupArchiveFormat.PLAINTEXT_ZIP) clearPendingExportPassword()
        requestSensitiveStep(BackupSensitiveStep.ExportPicker(pendingExportChoice))
    }

    val requestExport: () -> Unit = {
        pendingExportChoice = null
        when (openFocusSession?.state) {
            FocusState.PAUSED -> showPausedExportDisclosure = true
            FocusState.RUNNING -> focusActionFailed = true
            else -> {
                pendingExportChoice = null
                showExportFormatChoice = true
            }
        }
    }

    val confirmRestore: (Boolean?) -> Unit = { discardOpenSession ->
        when (lockGuard?.protectionEnabled()) {
            null -> sensitiveError = true
            false -> when (restoreWithoutProtectionConfirmed) {
                null -> showRestoreProtectionChoice = true
                true -> {
                    restoreWithoutProtectionConfirmed = null
                    onAppLockSetup()
                }
                false -> requestSensitiveStep(BackupSensitiveStep.RestoreCommit(discardOpenSession))
            }
            true -> requestSensitiveStep(BackupSensitiveStep.RestoreCommit(discardOpenSession))
        }
    }

    val requestBack: () -> Unit = {
        if (restoreActionInProgress) {
            showRestoreInProgress = true
        } else when (state.operation) {
            BackupOperation.EXPORTING, BackupOperation.PREPARING -> leavePromptFor = state.operation
            BackupOperation.RESTORING -> showRestoreInProgress = true
            BackupOperation.CANCELLING -> Unit
            BackupOperation.IDLE -> scope.launch {
                val departure = vm.leaveAfterIdle()
                if (departure?.result == BackupLeaveResult.RESTORE_IN_PROGRESS) {
                    showRestoreInProgress = true
                } else {
                    currentOnBack(departure)
                }
            }
        }
    }

    SettingsSectionPage(
        title = R.string.settings_backup_title,
        onBack = requestBack,
        screenTag = "screen_settings_backup",
    ) {
        BackupCardContent(
            state = state,
            openFocusSession = openFocusSession,
            focusActionFailed = focusActionFailed,
            showRestoreConfirmation = leavePromptFor == null && !showRestoreInProgress,
            restoreActionInProgress = restoreActionInProgress,
            formatDetectionInProgress = formatDetectionInProgress,
            onExport = requestExport,
            onRestore = { requestSensitiveStep(BackupSensitiveStep.RestorePicker) },
            onConfirmRestore = confirmRestore,
            onCancelRestore = vm::cancelRestore,
        )
    }

    if (showPausedExportDisclosure) {
        val currentSessionIsPaused = openFocusSession?.state == FocusState.PAUSED
        LumaAlertDialog(
            onDismissRequest = {
                showPausedExportDisclosure = false
                pendingExportChoice = null
            },
            title = { Text(stringResource(R.string.backup_paused_focus_title)) },
            text = { Text(stringResource(R.string.backup_paused_focus_message)) },
            confirmButton = {
                Column {
                    LumaTextButton(
                        onClick = {
                            showPausedExportDisclosure = false
                            if (currentSessionIsPaused) {
                                pendingExportChoice = BackupFocusExportChoice.STOPPED_AND_INCLUDE
                                showExportFormatChoice = true
                            } else focusActionFailed = true
                        },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("backup_stop_focus_export"),
                    ) { Text(stringResource(R.string.backup_stop_focus_export)) }
                    LumaTextButton(
                        onClick = {
                            showPausedExportDisclosure = false
                            val sessionId = openFocusSession?.id
                            if (currentSessionIsPaused && sessionId != null) {
                                pendingExportChoice = BackupFocusExportChoice.EXCLUDE_PAUSED(sessionId)
                                showExportFormatChoice = true
                            } else focusActionFailed = true
                        },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("backup_exclude_paused_focus"),
                    ) { Text(stringResource(R.string.backup_exclude_paused_focus)) }
                    LumaTextButton(onClick = { showPausedExportDisclosure = false },
                        modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            },
        )
    }

    if (showExportFormatChoice) {
        LumaAlertDialog(
            onDismissRequest = {
                showExportFormatChoice = false
                pendingExportChoice = null
            },
            title = { Text(stringResource(R.string.backup_format_title)) },
            text = { Text(stringResource(R.string.backup_format_message)) },
            confirmButton = {
                Column {
                    LumaTextButton(
                        onClick = {
                            showExportFormatChoice = false
                            showExportPasswordDialog = true
                        },
                        modifier = Modifier.heightIn(min = LumaUi.MinTouchTargetHeight)
                            .testTag("backup_export_encrypted"),
                    ) { Text(stringResource(R.string.backup_format_encrypted)) }
                    LumaTextButton(
                        onClick = {
                            showExportFormatChoice = false
                            startExport(BackupArchiveFormat.PLAINTEXT_ZIP)
                        },
                        modifier = Modifier.heightIn(min = LumaUi.MinTouchTargetHeight)
                            .testTag("backup_export_plaintext"),
                    ) { Text(stringResource(R.string.backup_format_plaintext)) }
                }
            },
            dismissButton = {
                LumaTextButton(
                    onClick = {
                        showExportFormatChoice = false
                        pendingExportChoice = null
                    },
                    modifier = Modifier.heightIn(min = LumaUi.MinTouchTargetHeight),
                ) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (showExportPasswordDialog) {
        val passwordLengthValid = Character.codePointCount(exportPassword, 0, exportPassword.length) >=
            BackupFormatMarker.MINIMUM_PASSWORD_CODE_POINTS
        val passwordsMatch = exportPassword == exportPasswordConfirmation
        LumaAlertDialog(
            onDismissRequest = {
                showExportPasswordDialog = false
                clearExportPasswordFields()
                clearPendingExportPassword()
                pendingExportChoice = null
            },
            title = { Text(stringResource(R.string.backup_password_export_title)) },
            text = {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth()
                            .heightIn(max = maxHeight * 0.55f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(LumaUi.ContentGap),
                    ) {
                        Text(stringResource(R.string.backup_password_export_message))
                    LumaOutlinedTextField(
                        value = exportPassword,
                        onValueChange = { exportPassword = it },
                        modifier = Modifier.fillMaxWidth().testTag("backup_export_password"),
                        singleLine = true,
                        label = { Text(stringResource(R.string.backup_password_label)) },
                        supportingText = { Text(stringResource(R.string.backup_password_requirements)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = if (exportPasswordVisible) VisualTransformation.None
                            else PasswordVisualTransformation(),
                        trailingIcon = {
                            LumaTextButton(
                                onClick = { exportPasswordVisible = !exportPasswordVisible },
                                modifier = Modifier.heightIn(min = LumaUi.MinTouchTargetHeight),
                            ) {
                                Text(stringResource(if (exportPasswordVisible) R.string.backup_password_hide
                                    else R.string.backup_password_show))
                            }
                        },
                    )
                    LumaOutlinedTextField(
                        value = exportPasswordConfirmation,
                        onValueChange = { exportPasswordConfirmation = it },
                        modifier = Modifier.fillMaxWidth().testTag("backup_export_password_confirm"),
                        singleLine = true,
                        label = { Text(stringResource(R.string.backup_password_confirm_label)) },
                        supportingText = if (exportPasswordConfirmation.isNotEmpty() && !passwordsMatch) {
                            { Text(stringResource(R.string.backup_password_mismatch)) }
                        } else null,
                        isError = exportPasswordConfirmation.isNotEmpty() && !passwordsMatch,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = if (exportPasswordVisible) VisualTransformation.None
                            else PasswordVisualTransformation(),
                    )
                    }
                }
            },
            confirmButton = {
                LumaButton(
                    onClick = {
                        pendingExportPassword = exportPassword.toCharArray()
                        showExportPasswordDialog = false
                        clearExportPasswordFields()
                        startExport(BackupArchiveFormat.ENCRYPTED)
                    },
                    enabled = passwordLengthValid && passwordsMatch,
                    modifier = Modifier.testTag("backup_export_password_continue"),
                ) { Text(stringResource(R.string.backup_continue)) }
            },
            dismissButton = {
                LumaTextButton(
                    onClick = {
                        showExportPasswordDialog = false
                        clearExportPasswordFields()
                        clearPendingExportPassword()
                        pendingExportChoice = null
                    },
                    modifier = Modifier.heightIn(min = LumaUi.MinTouchTargetHeight),
                ) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (showRestorePasswordDialog) {
        LumaAlertDialog(
            onDismissRequest = {
                showRestorePasswordDialog = false
                clearRestorePasswordFields()
                pendingRestoreUri = null
                pendingRestoreAuthorization?.let { lockGuard?.cancel(it.pending) }
                pendingRestoreAuthorization = null
            },
            title = { Text(stringResource(R.string.backup_password_import_title)) },
            text = {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth()
                            .heightIn(max = maxHeight * 0.55f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(LumaUi.ContentGap),
                    ) {
                        Text(stringResource(R.string.backup_password_import_message))
                    LumaOutlinedTextField(
                        value = restorePassword,
                        onValueChange = { restorePassword = it },
                        modifier = Modifier.fillMaxWidth().testTag("backup_restore_password"),
                        singleLine = true,
                        label = { Text(stringResource(R.string.backup_password_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = if (restorePasswordVisible) VisualTransformation.None
                            else PasswordVisualTransformation(),
                        trailingIcon = {
                            LumaTextButton(
                                onClick = { restorePasswordVisible = !restorePasswordVisible },
                                modifier = Modifier.heightIn(min = LumaUi.MinTouchTargetHeight),
                            ) {
                                Text(stringResource(if (restorePasswordVisible) R.string.backup_password_hide
                                    else R.string.backup_password_show))
                            }
                        },
                    )
                    }
                }
            },
            confirmButton = {
                LumaButton(
                    onClick = {
                        val uri = pendingRestoreUri
                        val authorization = pendingRestoreAuthorization
                        pendingRestoreUri = null
                        pendingRestoreAuthorization = null
                        showRestorePasswordDialog = false
                        val password = restorePassword.toCharArray()
                        clearRestorePasswordFields()
                        if (uri == null || password.isEmpty()) {
                            password.fill(Char.MIN_VALUE)
                            authorization?.let { lockGuard?.cancel(it.pending) }
                            vm.pickerUnavailable()
                        } else {
                            vm.prepareEncrypted(
                                open = { requireNotNull(resolver.openInputStream(uri)) },
                                encryption = BackupEncryptionOptions(password),
                                authorize = authorization.authorizer(lockGuard, AuthPurpose.RESTORE),
                            )
                        }
                    },
                    enabled = restorePassword.isNotEmpty(),
                    modifier = Modifier.testTag("backup_restore_password_continue"),
                ) { Text(stringResource(R.string.backup_continue)) }
            },
            dismissButton = {
                LumaTextButton(
                    onClick = {
                        showRestorePasswordDialog = false
                        clearRestorePasswordFields()
                        pendingRestoreUri = null
                        pendingRestoreAuthorization?.let { lockGuard?.cancel(it.pending) }
                        pendingRestoreAuthorization = null
                    },
                    modifier = Modifier.heightIn(min = LumaUi.MinTouchTargetHeight),
                ) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    leavePromptFor?.let { operation ->
        val cancelling = state.operation == BackupOperation.CANCELLING
        val title = if (operation == BackupOperation.EXPORTING) R.string.backup_leave_export_title
            else R.string.backup_leave_prepare_title
        val message = if (operation == BackupOperation.EXPORTING) R.string.backup_leave_export_message
            else R.string.backup_leave_prepare_message
        LumaAlertDialog(
            onDismissRequest = { if (!cancelling) leavePromptFor = null },
            title = { Text(stringResource(title)) },
            text = { Text(stringResource(message)) },
            confirmButton = {
                LumaTextButton(
                    onClick = {
                        scope.launch {
                            val departure = vm.cancelAndAwait()
                            if (departure.result == BackupLeaveResult.RESTORE_IN_PROGRESS) {
                                leavePromptFor = null
                                showRestoreInProgress = true
                            } else {
                                currentOnBack(departure)
                            }
                        }
                    },
                    enabled = !cancelling,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("backup_cancel_and_leave"),
                ) {
                    if (cancelling) CircularProgressIndicator(Modifier.size(20.dp))
                    else Text(stringResource(R.string.backup_cancel_and_leave))
                }
            },
            dismissButton = {
                LumaTextButton(
                    onClick = { leavePromptFor = null },
                    enabled = !cancelling,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("backup_stay"),
                ) { Text(stringResource(R.string.backup_stay)) }
            },
        )
    }

    if (showRestoreInProgress) {
        LumaAlertDialog(
            onDismissRequest = { showRestoreInProgress = false },
            title = { Text(stringResource(R.string.backup_restore_in_progress_title)) },
            text = { Text(stringResource(R.string.backup_restore_in_progress_message)) },
            confirmButton = {
                LumaTextButton(onClick = { showRestoreInProgress = false }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.backup_stay))
                }
            },
        )
    }

    if (showRestoreProtectionChoice) LumaAlertDialog(
        onDismissRequest = { showRestoreProtectionChoice = false },
        title = { Text(stringResource(R.string.backup_restore_lock_choice_title)) },
        text = { Text(stringResource(R.string.backup_restore_lock_choice_body)) },
        confirmButton = {
            Column {
                LumaTextButton(onClick = {
                    showRestoreProtectionChoice = false
                    restoreWithoutProtectionConfirmed = true
                    onAppLockSetup()
                }) { Text(stringResource(R.string.backup_restore_setup_lock)) }
                LumaTextButton(onClick = {
                    showRestoreProtectionChoice = false
                    restoreWithoutProtectionConfirmed = false
                }) { Text(stringResource(R.string.backup_restore_without_lock)) }
            }
        },
        dismissButton = { LumaTextButton(onClick = { showRestoreProtectionChoice = false }) {
            Text(stringResource(R.string.cancel))
        } },
    )

    val authPending = pendingSensitive
    val authStep = pendingSensitiveStep
    if (showSensitivePrompt && lockGuard != null && authPending != null && authStep != null) {
        LockSensitiveActionDialog(
            guard = lockGuard,
            pending = authPending,
            onAuthorized = { proof ->
                showSensitivePrompt = false
                pendingSensitive = null
                pendingSensitiveStep = null
                dispatchSensitiveStep(authStep, BackupSensitiveAuthorization(authPending, proof))
            },
            onCancel = {
                showSensitivePrompt = false
                pendingSensitive = null
                pendingSensitiveStep = null
                if (authStep is BackupSensitiveStep.ExportPicker) {
                    clearPendingExportPassword()
                    pendingExportFormat = null
                    pendingExportChoice = null
                }
            },
        )
    }
    if (sensitiveError) LumaAlertDialog(
        onDismissRequest = { sensitiveError = false },
        title = { Text(stringResource(R.string.app_lock_error_unavailable)) },
        text = { Text(stringResource(R.string.app_lock_error_unavailable)) },
        confirmButton = { LumaTextButton(onClick = { sensitiveError = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
internal fun BackupCardContent(
    state: BackupUiState,
    onExport: () -> Unit,
    onRestore: () -> Unit,
    onConfirmRestore: (Boolean?) -> Unit,
    onCancelRestore: () -> Unit,
    showRestoreConfirmation: Boolean = true,
    openFocusSession: FocusSession? = null,
    focusActionFailed: Boolean = false,
    restoreActionInProgress: Boolean = false,
    formatDetectionInProgress: Boolean = false,
) {
    val displayTime = rememberDisplayTimePolicy()
    LumaSectionSurface(Modifier.testTag("backup_settings")) {
        Column(verticalArrangement = Arrangement.spacedBy(LumaUi.ChoiceGap)) {
            Text(stringResource(R.string.backup_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.backup_description))
            Text(stringResource(R.string.backup_private))
            if (focusActionFailed) {
                AccessibleFeedback(stringResource(R.string.backup_focus_action_failed),
                    Modifier.testTag("backup_focus_action_failed"))
            }
            LumaButton(onClick = onExport, enabled = !state.busy && state.preview == null && !formatDetectionInProgress,
                modifier = Modifier.testTag("backup_export")) {
                Text(stringResource(R.string.backup_export))
            }
            LumaOutlinedButton(onClick = onRestore, enabled = !state.busy && state.preview == null && !formatDetectionInProgress,
                modifier = Modifier.testTag("backup_choose")) {
                Text(stringResource(R.string.backup_choose))
            }
            if (state.busy || formatDetectionInProgress) {
                CircularProgressIndicator(Modifier.testTag("backup_busy"))
                Text(stringResource(R.string.backup_working))
            }
            state.message?.let { message ->
                val text = when (message) {
                    BackupMessage.EXPORTED -> R.string.backup_exported
                    BackupMessage.RESTORED -> R.string.backup_restored
                    BackupMessage.EXPORT_FAILED -> R.string.backup_export_failed
                    BackupMessage.INVALID_BACKUP -> R.string.backup_invalid
                    BackupMessage.RESTORE_FAILED -> R.string.backup_restore_failed
                    BackupMessage.PICKER_UNAVAILABLE -> R.string.backup_picker_unavailable
                }
                AccessibleFeedback(stringResource(text), Modifier.testTag("backup_message"),
                    isError = message != BackupMessage.EXPORTED && message != BackupMessage.RESTORED)
            }
        }
    }
    state.preview?.takeIf { showRestoreConfirmation }?.let { preview ->
        LumaAlertDialog(
            onDismissRequest = onCancelRestore,
            title = { Text(stringResource(R.string.backup_confirm_title)) },
            text = {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth()
                            .heightIn(max = maxHeight * 0.42f)
                            .verticalScroll(rememberScrollState())
                            .testTag("backup_warning_scroll"),
                        verticalArrangement = Arrangement.spacedBy(LumaUi.ChoiceGap),
                    ) {
                        Text(stringResource(R.string.backup_preview,
                            NumberFormat.getIntegerInstance().format(preview.eventCount),
                            NumberFormat.getIntegerInstance().format(preview.photoCount),
                            NumberFormat.getIntegerInstance().format(preview.habitCount),
                            NumberFormat.getIntegerInstance().format(preview.habitLogCount),
                            NumberFormat.getIntegerInstance().format(preview.todoCount),
                            NumberFormat.getIntegerInstance().format(preview.focusSessionCount),
                            displayTime.formatDateTime(preview.createdAt.atZone(ZoneId.systemDefault()), FormatStyle.MEDIUM)))
                        Text(stringResource(
                            if (preview.format == BackupArchiveFormat.ENCRYPTED) R.string.backup_preview_encrypted
                            else R.string.backup_preview_plaintext,
                        ))
                        Text(stringResource(R.string.backup_replace_warning))
                        Text(stringResource(R.string.backup_portable_exclusions))
                        if (preview.todoCount == 0L) Text(stringResource(R.string.backup_no_tasks_warning))
                        if (preview.focusSessionCount == 0L) Text(stringResource(R.string.backup_no_focus_sessions_warning))
                        if (openFocusSession != null) Text(stringResource(R.string.backup_active_focus_message))
                    }
                }
            },
            confirmButton = {
                if (openFocusSession != null) {
                    Column {
                        LumaTextButton(onClick = { onConfirmRestore(false) },
                            enabled = !restoreActionInProgress,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("backup_stop_focus_replace")) {
                            Text(stringResource(R.string.backup_stop_focus_replace))
                        }
                        LumaTextButton(onClick = { onConfirmRestore(true) },
                            enabled = !restoreActionInProgress,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("backup_discard_focus_replace")) {
                            Text(stringResource(R.string.backup_discard_focus_replace))
                        }
                        LumaTextButton(onClick = onCancelRestore, enabled = !restoreActionInProgress,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("backup_cancel")) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                } else {
                    LumaTextButton(onClick = { onConfirmRestore(null) }, enabled = !restoreActionInProgress,
                        modifier = Modifier.heightIn(min = 48.dp)
                        .testTag("backup_confirm")) {
                        Text(stringResource(R.string.backup_replace))
                    }
                }
            },
            dismissButton = if (openFocusSession == null) {
                {
                    LumaTextButton(onClick = onCancelRestore, enabled = !restoreActionInProgress,
                        modifier = Modifier.heightIn(min = 48.dp)
                            .testTag("backup_cancel")) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            } else null,
        )
    }
}

@Composable
fun BackupDepartureFeedbackHost(
    entry: NavBackStackEntry?,
    snackbarHostState: SnackbarHostState,
    sessionId: String,
) {
    val savedState = entry?.savedStateHandle
    val feedbackFlow = remember(entry) {
        savedState?.getStateFlow<String?>(BACKUP_DEPARTURE_FEEDBACK_KEY, null) ?: MutableStateFlow(null)
    }
    val context = LocalContext.current
    LaunchedEffect(entry) {
        feedbackFlow.collect { encoded ->
            if (entry?.destination?.route != AppRoutes.SETTINGS) return@collect
            val value = encoded ?: return@collect
            savedState?.set<String?>(BACKUP_DEPARTURE_FEEDBACK_KEY, null)
            val separator = value.indexOf(':')
            if (separator < 0 || value.substring(0, separator) != sessionId) return@collect
            val departure = BackupDeparture.decode(value.substring(separator + 1)) ?: return@collect
            snackbarHostState.showSnackbar(context.getString(departure.messageResource()))
        }
    }
}

private fun isEncryptedBackup(resolver: android.content.ContentResolver, uri: Uri): Boolean {
    val input = requireNotNull(resolver.openInputStream(uri)) { "Selected backup cannot be opened" }
    return input.use {
        val prefix = ByteArray(BackupFormatMarker.ENCRYPTED_SIGNATURE.length)
        var length = 0
        while (length < prefix.size) {
            val count = it.read(prefix, length, prefix.size - length)
            if (count <= 0) break
            length += count
        }
        BackupFormatMarker.isEncryptedPrefix(prefix, length)
    }
}

private fun discardCreatedDocument(resolver: android.content.ContentResolver, uri: Uri): Boolean {
    return try {
        if (DocumentsContract.deleteDocument(resolver, uri)) true
        else resolver.delete(uri, null, null) > 0
    } catch (_: Exception) {
        runCatching { resolver.delete(uri, null, null) > 0 }.getOrDefault(false)
    }
}

private data class BackupSensitiveAuthorization(
    val pending: PendingSensitiveOperation,
    val proof: OperationProof,
)

private sealed interface BackupSensitiveStep {
    val purpose: AuthPurpose

    data class ExportPicker(val focusChoice: BackupFocusExportChoice?) : BackupSensitiveStep {
        override val purpose = AuthPurpose.EXPORT
    }

    data object RestorePicker : BackupSensitiveStep {
        override val purpose = AuthPurpose.RESTORE
    }

    data class RestoreCommit(val discardOpenSession: Boolean?) : BackupSensitiveStep {
        override val purpose = AuthPurpose.RESTORE
    }
}

private fun BackupSensitiveAuthorization?.authorizer(
    guard: LockSensitiveOperationGuard?,
    purpose: AuthPurpose,
): suspend (suspend () -> Unit) -> Unit = { operation ->
    val authorization = this
    if (guard != null) {
        guard.executeSensitive(purpose, authorization?.pending, authorization?.proof, operation)
    } else operation()
}

private fun BackupDeparture.messageResource(): Int = when {
    result == BackupLeaveResult.RESTORE_IN_PROGRESS -> R.string.backup_restore_in_progress_message
    operation == BackupOperation.EXPORTING && result == BackupLeaveResult.COMPLETED -> R.string.backup_leave_export_completed
    operation == BackupOperation.EXPORTING && result == BackupLeaveResult.CANCELLED_CLEAN -> R.string.backup_leave_export_cancelled
    operation == BackupOperation.EXPORTING && result == BackupLeaveResult.CANCELLED_OUTPUT_MAY_REMAIN -> R.string.backup_leave_export_may_remain
    operation == BackupOperation.EXPORTING && result == BackupLeaveResult.FAILED_CLEAN -> R.string.backup_leave_export_failed
    operation == BackupOperation.EXPORTING && result == BackupLeaveResult.FAILED_OUTPUT_MAY_REMAIN -> R.string.backup_leave_export_failed_may_remain
    operation == BackupOperation.PREPARING && result == BackupLeaveResult.COMPLETED -> R.string.backup_leave_prepare_completed
    operation == BackupOperation.PREPARING && result == BackupLeaveResult.FAILED_CLEAN -> R.string.backup_leave_prepare_failed
    operation == BackupOperation.PREPARING -> R.string.backup_leave_prepare_cancelled
    operation == BackupOperation.RESTORING && result == BackupLeaveResult.COMPLETED -> R.string.backup_leave_restore_completed
    operation == BackupOperation.RESTORING -> R.string.backup_leave_restore_failed
    else -> R.string.backup_leave_prepare_cancelled
}
