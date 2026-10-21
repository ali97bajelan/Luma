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
import com.luma.lifeos.ui.integrations.IntegrationAccessState
import com.luma.lifeos.ui.integrations.IntegrationAvailabilityState
import com.luma.lifeos.ui.integrations.IntegrationSettingsOperation
import com.luma.lifeos.ui.integrations.needsAccessSecondary
import java.time.ZoneId
import java.time.format.FormatStyle

/** Import configuration only. Saved per-day evidence is displayed by ReviewUsageRoute. */
@Composable
fun UsageIntegrationSettingsCard(integration: UsageIntegration, zone: ZoneId,
    availability: IntegrationAvailabilityState = IntegrationAvailabilityState(), onRefreshAccess: () -> Unit = {}) {
    val factory = remember(integration) { viewModelFactory { initializer { UsageSettingsViewModel(integration) } } }
    val vm: UsageSettingsViewModel = viewModel(factory = factory)
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val displayTime = com.luma.lifeos.language.rememberDisplayTimePolicy()
    var showAccessExplanation by rememberSaveable { mutableStateOf(false) }
    var settingsMissing by rememberSaveable { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().testTag("usage_integration_card")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.usage_title), style = MaterialTheme.typography.titleLarge)
            val currentAvailability = availability.usage
            val canToggle = !state.loading && (!state.busy || state.current.enabled) &&
                currentAvailability.enabled != null && !currentAvailability.configurationFailed
            Row(Modifier.fillMaxWidth().toggleable(value = state.current.enabled, enabled = canToggle,
                role = Role.Switch, onValueChange = vm::setEnabled).testTag("usage_enabled"),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(stringResource(R.string.usage_enable), modifier = Modifier.weight(1f))
                Switch(checked = state.current.enabled, enabled = canToggle, onCheckedChange = null)
            }
            Text(stringResource(if (currentAvailability.enabled == null) R.string.usage_imports_loading
                else if (currentAvailability.enabled) R.string.usage_imports_on else R.string.usage_imports_off),
                modifier = Modifier.testTag("usage_import_preference"), style = MaterialTheme.typography.bodyMedium)
            val accessMessage = stringResource(currentAvailability.access.label())
            AccessibleFeedback(stringResource(R.string.integration_android_access, accessMessage),
                Modifier.testTag("usage_status"), isError = currentAvailability.access in setOf(
                    IntegrationAccessState.REQUIRED, IntegrationAccessState.LOCKED, IntegrationAccessState.UNAVAILABLE,
                    IntegrationAccessState.UPDATE_REQUIRED, IntegrationAccessState.ERROR))
            Text(stringResource(R.string.integration_saved_history), style = MaterialTheme.typography.bodySmall)
            if (state.current.status == UsageSyncStatus.NO_DATA &&
                currentAvailability.access == IntegrationAccessState.ALLOWED) {
                AccessibleFeedback(stringResource(R.string.usage_no_data), Modifier.testTag("usage_no_data"))
            }
            if (state.loading || state.busy || currentAvailability.access == IntegrationAccessState.CHECKING)
                CircularProgressIndicator(Modifier.testTag("usage_busy"))
            currentAvailability.lastSync?.let { Text(stringResource(R.string.usage_last_sync,
                it.atZone(zone).format(displayTime.dateTime(FormatStyle.MEDIUM)))) }
            when {
                currentAvailability.access == IntegrationAccessState.REQUIRED ->
                    Button(onClick = { showAccessExplanation = true }, enabled = !state.busy,
                        modifier = Modifier.testTag("usage_grant")) { Text(stringResource(R.string.usage_grant)) }
                state.loading || currentAvailability.configurationFailed || currentAvailability.enabled == null ||
                    currentAvailability.access == IntegrationAccessState.ERROR || currentAvailability.access == IntegrationAccessState.CHECKING ->
                    Button(onClick = { vm.refresh(); onRefreshAccess() }, enabled = !state.busy,
                        modifier = Modifier.testTag("usage_refresh_primary")) { Text(stringResource(R.string.usage_refresh)) }
                currentAvailability.access == IntegrationAccessState.LOCKED ->
                    Text(stringResource(R.string.usage_locked), modifier = Modifier.testTag("usage_locked_message"))
                currentAvailability.access == IntegrationAccessState.UNAVAILABLE ->
                    Text(stringResource(R.string.usage_service_unavailable), modifier = Modifier.testTag("usage_unavailable_message"))
                currentAvailability.access == IntegrationAccessState.ALLOWED && currentAvailability.enabled == true ->
                    Button(onClick = vm::importNow, enabled = !state.busy && !state.loading,
                        modifier = Modifier.testTag("usage_import")) {
                        Text(stringResource(if (currentAvailability.importFailed ||
                            state.errors.containsKey(IntegrationSettingsOperation.IMPORT)) R.string.usage_retry_import else R.string.usage_import_now))
                    }
                currentAvailability.access == IntegrationAccessState.ALLOWED ->
                    Button(onClick = { showAccessExplanation = true }, enabled = !state.busy,
                        modifier = Modifier.testTag("usage_manage_primary")) { Text(stringResource(R.string.usage_manage_access)) }
                else -> Unit
            }
            if ((currentAvailability.importFailed || state.errors.containsKey(IntegrationSettingsOperation.IMPORT)) &&
                currentAvailability.access == IntegrationAccessState.ALLOWED) {
                key(state.errors[IntegrationSettingsOperation.IMPORT]?.requestId) {
                    AccessibleFeedback(stringResource(R.string.usage_sync_error), Modifier.testTag("usage_import_error"), isError = true)
                }
            }
            if (currentAvailability.needsAccessSecondary()) {
                Text(stringResource(R.string.integration_access_also_needed), modifier = Modifier.testTag("usage_access_secondary"),
                    style = MaterialTheme.typography.bodySmall)
            }
            if (currentAvailability.access == IntegrationAccessState.ALLOWED && currentAvailability.enabled == true) {
                TextButton(onClick = { showAccessExplanation = true }, modifier = Modifier.testTag("usage_access")) {
                    Text(stringResource(R.string.usage_manage_access))
                }
            }
            if (settingsMissing) AccessibleFeedback(stringResource(R.string.usage_settings_missing), Modifier.testTag("usage_settings_missing"))
            state.errors.values.sortedBy { it.operation.ordinal }.forEach { error ->
                val operation = error.operation
                val message = when (operation) {
                    IntegrationSettingsOperation.OBSERVE -> R.string.usage_observe_error
                    IntegrationSettingsOperation.ACCESS_REFRESH -> R.string.usage_access_refresh_error
                    IntegrationSettingsOperation.IMPORT -> R.string.usage_sync_error
                    IntegrationSettingsOperation.PREFERENCE -> R.string.usage_preference_error
                }
                val retry: () -> Unit = when (operation) {
                    IntegrationSettingsOperation.OBSERVE -> vm::retryConfiguration
                    IntegrationSettingsOperation.ACCESS_REFRESH -> ({ vm.refresh(); onRefreshAccess() })
                    IntegrationSettingsOperation.IMPORT -> vm::importNow
                    IntegrationSettingsOperation.PREFERENCE -> vm::retryPreference
                }
                if (operation != IntegrationSettingsOperation.IMPORT ||
                    currentAvailability.access != IntegrationAccessState.ALLOWED) {
                    key(error.requestId) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            AccessibleFeedback(stringResource(message),
                                Modifier.testTag("usage_${operation.name.lowercase()}_error"))
                            if (operation != IntegrationSettingsOperation.IMPORT) {
                                TextButton(onClick = retry, enabled = !state.busy,
                                    modifier = Modifier.testTag("usage_${operation.name.lowercase()}_retry")) {
                                    Text(stringResource(R.string.retry))
                                }
                            }
                        }
                    }
                }
            }
            TextButton(onClick = { vm.refresh(); onRefreshAccess() }, enabled = !state.busy,
                modifier = Modifier.testTag("usage_refresh")) { Text(stringResource(R.string.usage_refresh)) }
            DetailsDisclosure(R.string.usage_title, "usage_details") {
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
private fun IntegrationAccessState.label(): Int = when (this) {
    IntegrationAccessState.CHECKING -> R.string.integration_access_checking
    IntegrationAccessState.ALLOWED -> R.string.integration_access_allowed
    IntegrationAccessState.REQUIRED -> R.string.integration_access_required
    IntegrationAccessState.LOCKED -> R.string.integration_access_locked
    IntegrationAccessState.UPDATE_REQUIRED -> R.string.integration_access_update_required
    IntegrationAccessState.UNAVAILABLE -> R.string.integration_access_unavailable
    IntegrationAccessState.ERROR -> R.string.integration_access_error
}
