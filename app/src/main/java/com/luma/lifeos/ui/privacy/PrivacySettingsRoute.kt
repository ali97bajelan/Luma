package com.luma.lifeos.ui.privacy

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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

@Composable
fun PrivacySettingsRoute(reset: AppDataReset, onBack: () -> Unit) {
    val factory = remember(reset) { viewModelFactory { initializer { PrivacyViewModel(reset) } } }
    val vm: PrivacyViewModel = viewModel(factory = factory)
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var settingsMissing by rememberSaveable { mutableStateOf(false) }
    PrivacySettingsContent(state, onBack, vm::requestDelete, vm::confirmDelete, vm::cancelDelete,
        onAppSettings = { settingsMissing = !openLumaAppSettings(context) }, settingsMissing)
}

/** Shared with the native Health Connect rationale; contains no actions or database access. */
@Composable
fun PrivacyPolicyContent() {
    Text(stringResource(R.string.privacy_local_storage), modifier = Modifier.testTag("privacy_local_storage"))
    Text(stringResource(R.string.privacy_optional_access), modifier = Modifier.testTag("privacy_optional_access"))
    Text(stringResource(R.string.privacy_backup_policy), modifier = Modifier.testTag("privacy_backup_policy"))
}

@Composable
internal fun PrivacySettingsContent(state: PrivacyUiState, onBack: () -> Unit,
    onDelete: () -> Unit, onConfirm: () -> Unit, onCancel: () -> Unit,
    onAppSettings: () -> Unit, settingsMissing: Boolean = false) {
    Column(Modifier.fillMaxSize().testTag("screen_privacy").verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = onBack, enabled = !state.busy, modifier = Modifier.testTag("privacy_back")) {
            Text(stringResource(R.string.back))
        }
        Text(stringResource(R.string.privacy_title), style = MaterialTheme.typography.headlineLarge)
        PrivacyPolicyContent()
        Text(stringResource(R.string.privacy_delete_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.privacy_delete_description))
        Button(onClick = onDelete, enabled = !state.busy && !state.requestAccepted,
            modifier = Modifier.testTag("privacy_delete"),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError)) {
            Text(stringResource(R.string.privacy_delete))
        }
        if (state.busy) CircularProgressIndicator(Modifier.testTag("privacy_busy"))
        if (state.requestAccepted) Text(stringResource(R.string.privacy_reset_requested), modifier = Modifier.testTag("privacy_requested"))
        if (state.actionError) Text(stringResource(R.string.privacy_reset_failed),
            color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("privacy_error"))
        TextButton(onClick = onAppSettings, enabled = !state.busy, modifier = Modifier.testTag("privacy_app_settings")) {
            Text(stringResource(R.string.privacy_app_settings))
        }
        if (settingsMissing) Text(stringResource(R.string.privacy_settings_missing))
    }
    if (state.confirming) AlertDialog(onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.privacy_delete_title)) },
        text = { Text(stringResource(R.string.privacy_delete_warning)) },
        confirmButton = { TextButton(onClick = onConfirm, modifier = Modifier.testTag("privacy_confirm")) {
            Text(stringResource(R.string.privacy_delete_confirm), color = MaterialTheme.colorScheme.error)
        } },
        dismissButton = { TextButton(onClick = onCancel, modifier = Modifier.testTag("privacy_cancel")) { Text(stringResource(R.string.cancel)) } })
}

@Composable
internal fun PermissionExplanationDialog(@StringRes title: Int, @StringRes body: Int,
    onContinue: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(onDismissRequest = onCancel,
        title = { Text(stringResource(title)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(body))
            Text(stringResource(R.string.integration_permission_retention))
        } },
        confirmButton = { TextButton(onClick = onContinue, modifier = Modifier.testTag("permission_continue")) {
            Text(stringResource(R.string.permission_continue))
        } },
        dismissButton = { TextButton(onClick = onCancel, modifier = Modifier.testTag("permission_cancel")) { Text(stringResource(R.string.cancel)) } })
}
