package com.luma.lifeos.ui.backup

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavBackStackEntry
import com.luma.lifeos.R
import com.luma.lifeos.domain.backup.BackupService
import com.luma.lifeos.language.rememberDisplayTimePolicy
import com.luma.lifeos.ui.common.AccessibleFeedback
import com.luma.lifeos.ui.navigation.AppRoutes
import com.luma.lifeos.ui.settings.SettingsSectionPage
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.FormatStyle
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

const val BACKUP_DEPARTURE_FEEDBACK_KEY = "backup_departure_feedback"
internal object BackupDepartureSession { val id: String = UUID.randomUUID().toString() }

@Composable
fun BackupSettingsCard(
    service: BackupService,
    onRestored: () -> Unit = {},
    onBack: (BackupDeparture?) -> Unit,
) {
    val factory = remember(service) { viewModelFactory { initializer { BackupViewModel(service) } } }
    val vm: BackupViewModel = viewModel(factory = factory)
    val state by vm.state.collectAsStateWithLifecycle()
    val currentOnRestored by rememberUpdatedState(onRestored)
    val currentOnBack by rememberUpdatedState(onBack)
    val context = LocalContext.current.applicationContext
    val resolver = context.contentResolver
    val scope = rememberCoroutineScope()
    var leavePromptFor by remember { mutableStateOf<BackupOperation?>(null) }
    var showRestoreInProgress by remember { mutableStateOf(false) }

    LaunchedEffect(state.operation) {
        if (state.operation != BackupOperation.RESTORING) showRestoreInProgress = false
    }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            vm.export(
                open = { requireNotNull(resolver.openOutputStream(uri, "wt")) },
                discardOutput = { discardCreatedDocument(resolver, uri) },
            )
        }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.prepare { requireNotNull(resolver.openInputStream(uri)) }
    }

    val requestBack: () -> Unit = {
        when (state.operation) {
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
            showRestoreConfirmation = leavePromptFor == null && !showRestoreInProgress,
            onExport = {
                try {
                    export.launch("Luma-${LocalDate.now()}.zip")
                } catch (_: ActivityNotFoundException) {
                    vm.pickerUnavailable()
                }
            },
            onRestore = {
                try {
                    restore.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed"))
                } catch (_: ActivityNotFoundException) {
                    vm.pickerUnavailable()
                }
            },
            onConfirmRestore = { vm.confirmRestore { currentOnRestored() } },
            onCancelRestore = vm::cancelRestore,
        )
    }

    leavePromptFor?.let { operation ->
        val cancelling = state.operation == BackupOperation.CANCELLING
        val title = if (operation == BackupOperation.EXPORTING) R.string.backup_leave_export_title
            else R.string.backup_leave_prepare_title
        val message = if (operation == BackupOperation.EXPORTING) R.string.backup_leave_export_message
            else R.string.backup_leave_prepare_message
        AlertDialog(
            onDismissRequest = { if (!cancelling) leavePromptFor = null },
            title = { Text(stringResource(title)) },
            text = { Text(stringResource(message)) },
            confirmButton = {
                TextButton(
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
                TextButton(
                    onClick = { leavePromptFor = null },
                    enabled = !cancelling,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("backup_stay"),
                ) { Text(stringResource(R.string.backup_stay)) }
            },
        )
    }

    if (showRestoreInProgress) {
        AlertDialog(
            onDismissRequest = { showRestoreInProgress = false },
            title = { Text(stringResource(R.string.backup_restore_in_progress_title)) },
            text = { Text(stringResource(R.string.backup_restore_in_progress_message)) },
            confirmButton = {
                TextButton(onClick = { showRestoreInProgress = false }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.backup_stay))
                }
            },
        )
    }
}

@Composable
internal fun BackupCardContent(
    state: BackupUiState,
    onExport: () -> Unit,
    onRestore: () -> Unit,
    onConfirmRestore: () -> Unit,
    onCancelRestore: () -> Unit,
    showRestoreConfirmation: Boolean = true,
) {
    val displayTime = rememberDisplayTimePolicy()
    Card(Modifier.fillMaxWidth().testTag("backup_settings")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.backup_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.backup_description))
            Text(stringResource(R.string.backup_private))
            Button(onClick = onExport, enabled = !state.busy && state.preview == null,
                modifier = Modifier.testTag("backup_export")) {
                Text(stringResource(R.string.backup_export))
            }
            OutlinedButton(onClick = onRestore, enabled = !state.busy && state.preview == null,
                modifier = Modifier.testTag("backup_choose")) {
                Text(stringResource(R.string.backup_choose))
            }
            if (state.busy) {
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
        AlertDialog(
            onDismissRequest = onCancelRestore,
            title = { Text(stringResource(R.string.backup_confirm_title)) },
            text = {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth()
                            .heightIn(max = maxHeight * 0.42f)
                            .verticalScroll(rememberScrollState())
                            .testTag("backup_warning_scroll"),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(stringResource(R.string.backup_preview,
                            NumberFormat.getIntegerInstance().format(preview.eventCount),
                            NumberFormat.getIntegerInstance().format(preview.photoCount),
                            NumberFormat.getIntegerInstance().format(preview.habitCount),
                            NumberFormat.getIntegerInstance().format(preview.habitLogCount),
                            NumberFormat.getIntegerInstance().format(preview.todoCount),
                            preview.createdAt.atZone(ZoneId.systemDefault()).format(displayTime.dateTime(FormatStyle.MEDIUM))))
                        Text(stringResource(R.string.backup_replace_warning))
                        if (preview.todoCount == 0L) Text(stringResource(R.string.backup_no_tasks_warning))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirmRestore, modifier = Modifier.heightIn(min = 48.dp)
                    .testTag("backup_confirm")) {
                    Text(stringResource(R.string.backup_replace))
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelRestore, modifier = Modifier.heightIn(min = 48.dp)
                    .testTag("backup_cancel")) {
                    Text(stringResource(R.string.cancel))
                }
            },
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

private fun discardCreatedDocument(resolver: android.content.ContentResolver, uri: Uri): Boolean {
    return try {
        if (DocumentsContract.deleteDocument(resolver, uri)) true
        else resolver.delete(uri, null, null) > 0
    } catch (_: Exception) {
        runCatching { resolver.delete(uri, null, null) > 0 }.getOrDefault(false)
    }
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
