package com.luma.lifeos.ui.backup

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
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
import com.luma.lifeos.R
import com.luma.lifeos.domain.backup.BackupService
import com.luma.lifeos.language.localizedDateTime
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.FormatStyle

@Composable
fun BackupSettingsCard(service: BackupService, onRestored: () -> Unit = {}) {
    val factory = remember(service) { viewModelFactory { initializer { BackupViewModel(service) } } }
    val vm: BackupViewModel = viewModel(factory = factory)
    val state by vm.state.collectAsStateWithLifecycle()
    val currentOnRestored by rememberUpdatedState(onRestored)
    val resolver = LocalContext.current.applicationContext.contentResolver
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.export { requireNotNull(resolver.openOutputStream(uri, "wt")) }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.prepare { requireNotNull(resolver.openInputStream(uri)) }
    }
    BackupCardContent(state, onExport = {
        try { export.launch("Luma-${LocalDate.now()}.zip") } catch (_: ActivityNotFoundException) { vm.pickerUnavailable() }
    }, onRestore = {
        try { restore.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed")) }
        catch (_: ActivityNotFoundException) { vm.pickerUnavailable() }
    }, onConfirm = { vm.confirmRestore { currentOnRestored() } }, onCancel = vm::cancelRestore)
}

@Composable
internal fun BackupCardContent(state: BackupUiState, onExport: () -> Unit, onRestore: () -> Unit,
    onConfirm: () -> Unit, onCancel: () -> Unit) {
    Card(Modifier.fillMaxWidth().testTag("backup_settings")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.backup_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.backup_description))
            Text(stringResource(R.string.backup_private))
            Button(onClick = onExport, enabled = !state.busy && state.preview == null, modifier = Modifier.testTag("backup_export")) {
                Text(stringResource(R.string.backup_export))
            }
            OutlinedButton(onClick = onRestore, enabled = !state.busy && state.preview == null, modifier = Modifier.testTag("backup_choose")) {
                Text(stringResource(R.string.backup_choose))
            }
            if (state.busy) { CircularProgressIndicator(Modifier.testTag("backup_busy")); Text(stringResource(R.string.backup_working)) }
            state.message?.let { message ->
                val text = when (message) {
                    BackupMessage.EXPORTED -> R.string.backup_exported
                    BackupMessage.RESTORED -> R.string.backup_restored
                    BackupMessage.EXPORT_FAILED -> R.string.backup_export_failed
                    BackupMessage.INVALID_BACKUP -> R.string.backup_invalid
                    BackupMessage.RESTORE_FAILED -> R.string.backup_restore_failed
                    BackupMessage.PICKER_UNAVAILABLE -> R.string.backup_picker_unavailable
                }
                Text(stringResource(text), modifier = Modifier.testTag("backup_message"))
            }
        }
    }
    state.preview?.let { preview ->
        AlertDialog(onDismissRequest = onCancel,
            title = { Text(stringResource(R.string.backup_confirm_title)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.backup_preview, NumberFormat.getIntegerInstance().format(preview.eventCount),
                    NumberFormat.getIntegerInstance().format(preview.photoCount),
                    NumberFormat.getIntegerInstance().format(preview.habitCount),
                    NumberFormat.getIntegerInstance().format(preview.habitLogCount),
                    NumberFormat.getIntegerInstance().format(preview.todoCount),
                    preview.createdAt.atZone(ZoneId.systemDefault()).format(localizedDateTime(FormatStyle.MEDIUM))))
                Text(stringResource(R.string.backup_replace_warning))
                if (preview.todoCount == 0L) Text(stringResource(R.string.backup_no_tasks_warning))
            } },
            confirmButton = { TextButton(onClick = onConfirm, modifier = Modifier.testTag("backup_confirm")) { Text(stringResource(R.string.backup_replace)) } },
            dismissButton = { TextButton(onClick = onCancel, modifier = Modifier.testTag("backup_cancel")) { Text(stringResource(R.string.cancel)) } })
    }
}
