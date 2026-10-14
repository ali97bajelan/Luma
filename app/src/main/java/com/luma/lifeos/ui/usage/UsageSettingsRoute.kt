package com.luma.lifeos.ui.usage

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.luma.lifeos.R
import com.luma.lifeos.domain.usage.*
import com.luma.lifeos.integration.usage.openUsageAccessSettings
import com.luma.lifeos.ui.common.AccessibleFeedback
import com.luma.lifeos.ui.common.DetailsDisclosure
import java.time.ZoneId
import java.time.format.FormatStyle

/** Import configuration only. Saved per-day evidence is displayed by ReviewUsageRoute. */
@Composable
fun UsageIntegrationSettingsCard(integration: UsageIntegration, zone: ZoneId) {
    val factory = remember(integration) { viewModelFactory { initializer { UsageSettingsViewModel(integration) } } }
    val vm: UsageSettingsViewModel = viewModel(factory = factory)
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showAccessExplanation by rememberSaveable { mutableStateOf(false) }
    var settingsMissing by rememberSaveable { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().testTag("usage_integration_card")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.usage_title), style = MaterialTheme.typography.titleLarge)
            val canToggle = !state.loading && (!state.busy || state.current.enabled)
            Row(Modifier.fillMaxWidth().toggleable(value = state.current.enabled, enabled = canToggle,
                role = Role.Switch, onValueChange = vm::setEnabled).testTag("usage_enabled"),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(stringResource(R.string.usage_enable), modifier = Modifier.weight(1f))
                Switch(checked = state.current.enabled, enabled = canToggle, onCheckedChange = null)
            }
            if (state.loading || state.busy) CircularProgressIndicator(Modifier.testTag("usage_busy"))
            val statusMessage = stringResource(state.current.status.label())
            if (!state.loading && !state.busy) AccessibleFeedback(statusMessage,
                Modifier.testTag("usage_status"), isError = state.current.status in setOf(
                    UsageSyncStatus.ACCESS_REQUIRED, UsageSyncStatus.LOCKED, UsageSyncStatus.UNAVAILABLE, UsageSyncStatus.ERROR))
            else Text(statusMessage, modifier = Modifier.testTag("usage_status"))
            state.current.lastSync?.let { Text(stringResource(R.string.usage_last_sync,
                it.atZone(zone).format(com.luma.lifeos.language.localizedDateTime(FormatStyle.MEDIUM)))) }
            TextButton(onClick = { showAccessExplanation = true }, modifier = Modifier.testTag("usage_access")) {
                Text(stringResource(R.string.usage_manage_access))
            }
            if (state.current.enabled) {
                Button(onClick = vm::importNow, enabled = !state.busy && !state.loading && state.current.status in
                    setOf(UsageSyncStatus.READY, UsageSyncStatus.NO_DATA, UsageSyncStatus.ERROR, UsageSyncStatus.INTERVAL_CHANGED), modifier = Modifier.testTag("usage_import")) {
                    Text(stringResource(R.string.usage_import_now))
                }
            }
            if (settingsMissing) AccessibleFeedback(stringResource(R.string.usage_settings_missing), Modifier.testTag("usage_settings_missing"))
            if (state.actionError) AccessibleFeedback(stringResource(R.string.usage_action_error), Modifier.testTag("usage_action_error"))
            TextButton(onClick = vm::refresh, enabled = !state.busy, modifier = Modifier.testTag("usage_refresh")) { Text(stringResource(R.string.usage_refresh)) }
            DetailsDisclosure(R.string.imports_details, "usage_details") {
                Text(stringResource(R.string.usage_access_explanation))
                Text(stringResource(R.string.usage_privacy_retention), modifier = Modifier.testTag("usage_retention"))
            }
        }
    }
    if (showAccessExplanation) com.luma.lifeos.ui.privacy.PermissionExplanationDialog(
        R.string.usage_title, R.string.usage_access_explanation,
        onContinue = { showAccessExplanation = false; settingsMissing = !openUsageAccessSettings(context) },
        onCancel = { showAccessExplanation = false })
}
private fun UsageSyncStatus.label(): Int = when (this) {
    UsageSyncStatus.DISABLED -> R.string.usage_disabled
    UsageSyncStatus.ACCESS_REQUIRED -> R.string.usage_access_required
    UsageSyncStatus.LOCKED -> R.string.usage_locked
    UsageSyncStatus.UNAVAILABLE -> R.string.usage_service_unavailable
    UsageSyncStatus.READY -> R.string.usage_ready
    UsageSyncStatus.NO_DATA -> R.string.usage_no_data
    UsageSyncStatus.ERROR -> R.string.usage_sync_error
    UsageSyncStatus.INTERVAL_CHANGED -> R.string.usage_interval_changed
}
