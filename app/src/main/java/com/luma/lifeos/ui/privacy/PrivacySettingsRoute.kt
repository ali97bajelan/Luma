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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.luma.lifeos.R
import com.luma.lifeos.ui.common.AccessibleFeedback
import com.luma.lifeos.ui.common.AppPageScaffold
import com.luma.lifeos.ui.common.LumaReadingPane
import com.luma.lifeos.ui.common.LumaSectionSurface
import com.luma.lifeos.ui.common.PageNavigation

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
fun PrivacyPolicyContent(accessAction: (@Composable () -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PrivacySection(R.string.privacy_storage_heading, R.string.privacy_local_storage, "privacy_local_storage")
        PrivacySection(R.string.privacy_access_heading, R.string.privacy_optional_access, "privacy_optional_access") {
            accessAction?.invoke()
        }
        PrivacySection(R.string.privacy_backup_heading, R.string.privacy_backup_policy, "privacy_backup_policy")
    }
}

@Composable
private fun PrivacySection(@StringRes heading: Int, @StringRes description: Int, tag: String,
    content: @Composable ColumnScope.() -> Unit = {}) {
    LumaSectionSurface {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(heading), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() })
            Text(stringResource(description), modifier = Modifier.testTag(tag))
            content()
        }
    }
}

@Composable
internal fun PrivacySettingsContent(state: PrivacyUiState, onBack: () -> Unit,
    onDelete: () -> Unit, onConfirm: () -> Unit, onCancel: () -> Unit,
    onAppSettings: () -> Unit, settingsMissing: Boolean = false) {
    AppPageScaffold(
        title = stringResource(R.string.privacy_title),
        navigation = PageNavigation.BACK,
        onNavigate = onBack,
        navigationEnabled = !state.busy,
        titleTag = "privacy_heading",
        navigationTag = "privacy_back",
    ) { pagePadding ->
      LumaReadingPane(Modifier.fillMaxSize().padding(pagePadding)) {
       Column(Modifier.fillMaxSize().testTag("screen_privacy")
          .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PrivacyPolicyContent {
            TextButton(onClick = onAppSettings, enabled = !state.busy, modifier = Modifier.testTag("privacy_app_settings")) {
                Text(stringResource(R.string.privacy_app_settings))
            }
            if (settingsMissing) AccessibleFeedback(stringResource(R.string.privacy_settings_missing), Modifier.testTag("privacy_settings_missing"))
        }
        LumaSectionSurface {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.privacy_delete_section), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.privacy_delete_description))
                Button(onClick = onDelete, enabled = !state.busy && !state.requestAccepted,
                    modifier = Modifier.testTag("privacy_delete"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError)) {
                    Text(stringResource(R.string.privacy_delete))
                }
                if (state.busy) CircularProgressIndicator(Modifier.testTag("privacy_busy"))
                if (state.requestAccepted) AccessibleFeedback(stringResource(R.string.privacy_reset_requested),
                    Modifier.testTag("privacy_requested"), isError = false)
                if (state.actionError) AccessibleFeedback(stringResource(R.string.privacy_reset_failed), Modifier.testTag("privacy_error"))
            }
        }
      }
      }
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
