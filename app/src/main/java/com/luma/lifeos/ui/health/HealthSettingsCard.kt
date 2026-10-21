package com.luma.lifeos.ui.health

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.luma.lifeos.R
import com.luma.lifeos.domain.health.*
import com.luma.lifeos.integration.health.*
import com.luma.lifeos.ui.common.AccessibleFeedback
import com.luma.lifeos.ui.common.DetailsDisclosure
import com.luma.lifeos.ui.integrations.IntegrationAccessState
import com.luma.lifeos.ui.integrations.IntegrationAvailabilityState
import com.luma.lifeos.ui.integrations.IntegrationSettingsOperation
import java.time.ZoneId
import java.time.format.FormatStyle

@Composable
fun HealthSettingsCard(integration: HealthIntegration, zone: ZoneId = ZoneId.systemDefault(),
    availability: IntegrationAvailabilityState = IntegrationAvailabilityState(), onRefreshAccess: () -> Unit = {}) {
    val factory = remember(integration) { viewModelFactory { initializer { HealthSettingsViewModel(integration) } } }
    val vm: HealthSettingsViewModel = viewModel(factory = factory)
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val displayTime = com.luma.lifeos.language.rememberDisplayTimePolicy()
    val currentAvailability = availability.health
    var showPermissionExplanation by rememberSaveable { mutableStateOf(false) }
    var settingsMissing by rememberSaveable { mutableStateOf(false) }
    val contract = remember { PermissionController.createRequestPermissionResultContract() }
    val permissions = rememberLauncherForActivityResult(contract) { vm.refresh(); onRefreshAccess() }
    Card(Modifier.fillMaxWidth().testTag("health_card")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.health_title), style = MaterialTheme.typography.titleLarge)
            val canToggle = !state.loading && (!state.busy || state.current.enabled) &&
                currentAvailability.enabled != null && !currentAvailability.configurationFailed
            Row(Modifier.fillMaxWidth().toggleable(value = state.current.enabled, enabled = canToggle,
                role = Role.Switch, onValueChange = vm::setEnabled).testTag("health_enabled"),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(stringResource(R.string.health_enable), modifier = Modifier.weight(1f))
                Switch(checked = state.current.enabled, enabled = canToggle, onCheckedChange = null)
            }
            Text(stringResource(if (currentAvailability.enabled == null) R.string.health_imports_loading
                else if (currentAvailability.enabled) R.string.health_imports_on else R.string.health_imports_off),
                modifier = Modifier.testTag("health_import_preference"), style = MaterialTheme.typography.bodyMedium)
            val accessMessage = stringResource(currentAvailability.access.label())
            AccessibleFeedback(stringResource(R.string.integration_android_access, accessMessage),
                Modifier.testTag("health_status"), isError = currentAvailability.access in setOf(
                    IntegrationAccessState.REQUIRED, IntegrationAccessState.UPDATE_REQUIRED,
                    IntegrationAccessState.UNAVAILABLE, IntegrationAccessState.ERROR))
            Text(stringResource(R.string.integration_saved_history), style = MaterialTheme.typography.bodySmall)
            if (state.current.status == HealthStatus.NO_DATA &&
                currentAvailability.access == IntegrationAccessState.ALLOWED) {
                AccessibleFeedback(stringResource(R.string.health_no_data), Modifier.testTag("health_no_data"))
            }
            if (state.loading || state.busy || currentAvailability.access == IntegrationAccessState.CHECKING)
                CircularProgressIndicator(Modifier.testTag("health_busy"))
            currentAvailability.lastSync?.let { Text(stringResource(R.string.health_last_sync,
                it.atZone(state.current.importZone ?: zone).format(displayTime.dateTime(FormatStyle.MEDIUM)))) }
            state.current.importZone?.let { Text(stringResource(R.string.health_zone, it.id)) }
            if (currentAvailability.enabled == false) Text(stringResource(R.string.health_open_imports_to_refresh),
                style = MaterialTheme.typography.bodySmall)
            when {
                currentAvailability.access == IntegrationAccessState.REQUIRED ->
                    Button(onClick = { showPermissionExplanation = true }, enabled = !state.busy,
                        modifier = Modifier.testTag("health_access")) { Text(stringResource(R.string.health_grant)) }
                currentAvailability.access == IntegrationAccessState.UPDATE_REQUIRED ->
                    Button(onClick = { settingsMissing = !openHealthProviderInstall(context) },
                        modifier = Modifier.testTag("health_install")) { Text(stringResource(R.string.health_install)) }
                state.loading || currentAvailability.configurationFailed || currentAvailability.enabled == null ||
                    currentAvailability.access == IntegrationAccessState.ERROR || currentAvailability.access == IntegrationAccessState.CHECKING ->
                    Button(onClick = { vm.refresh(); onRefreshAccess() }, enabled = !state.busy,
                        modifier = Modifier.testTag("health_refresh_primary")) { Text(stringResource(R.string.health_refresh)) }
                currentAvailability.access == IntegrationAccessState.ALLOWED && currentAvailability.enabled == true ->
                    Button(onClick = vm::importNow, enabled = !state.busy && !state.loading,
                        modifier = Modifier.testTag("health_import")) {
                        Text(stringResource(if (currentAvailability.importFailed ||
                            state.errors.containsKey(IntegrationSettingsOperation.IMPORT)) R.string.health_retry_import else R.string.health_import))
                    }
                currentAvailability.access == IntegrationAccessState.ALLOWED ->
                    Button(onClick = { settingsMissing = !openHealthSettings(context) }, enabled = !state.busy,
                        modifier = Modifier.testTag("health_manage_primary")) { Text(stringResource(R.string.health_manage)) }
                currentAvailability.access == IntegrationAccessState.LOCKED ->
                    Text(stringResource(R.string.integration_access_locked), modifier = Modifier.testTag("health_locked_message"))
                currentAvailability.access == IntegrationAccessState.UNAVAILABLE ->
                    Text(stringResource(R.string.health_unavailable), modifier = Modifier.testTag("health_unavailable_message"))
                else -> Unit
            }
            if ((currentAvailability.importFailed || state.errors.containsKey(IntegrationSettingsOperation.IMPORT)) &&
                currentAvailability.access == IntegrationAccessState.ALLOWED) {
                key(state.errors[IntegrationSettingsOperation.IMPORT]?.requestId) {
                    AccessibleFeedback(stringResource(R.string.health_error), Modifier.testTag("health_import_error"), isError = true)
                }
            }
            TextButton(onClick = { settingsMissing = !openHealthSettings(context) }, modifier = Modifier.testTag("health_manage")) {
                Text(stringResource(R.string.health_manage))
            }
            TextButton(onClick = { vm.refresh(); onRefreshAccess() }, enabled = !state.busy,
                modifier = Modifier.testTag("health_refresh")) { Text(stringResource(R.string.health_refresh)) }
            if (settingsMissing) AccessibleFeedback(stringResource(R.string.health_settings_missing), Modifier.testTag("health_settings_missing"))
            state.errors.values.sortedBy { it.operation.ordinal }.forEach { error ->
                val operation = error.operation
                val message = when (operation) {
                    IntegrationSettingsOperation.OBSERVE -> R.string.health_observe_error
                    IntegrationSettingsOperation.ACCESS_REFRESH -> R.string.health_access_refresh_error
                    IntegrationSettingsOperation.IMPORT -> R.string.health_error
                    IntegrationSettingsOperation.PREFERENCE -> R.string.health_preference_error
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
                                Modifier.testTag("health_${operation.name.lowercase()}_error"))
                            if (operation != IntegrationSettingsOperation.IMPORT) {
                                TextButton(onClick = retry, enabled = !state.busy,
                                    modifier = Modifier.testTag("health_${operation.name.lowercase()}_retry")) {
                                    Text(stringResource(R.string.retry))
                                }
                            }
                        }
                    }
                }
            }
            DetailsDisclosure(R.string.imports_details, "health_details") {
                Text(stringResource(R.string.health_explanation))
                Text(stringResource(R.string.health_privacy_retention), modifier = Modifier.testTag("health_retention"))
            }
        }
    }
    if (showPermissionExplanation) com.luma.lifeos.ui.privacy.PermissionExplanationDialog(
        R.string.health_title, R.string.health_explanation,
        onContinue = {
            showPermissionExplanation = false
            if (!state.busy && !state.loading && currentAvailability.access == IntegrationAccessState.REQUIRED) {
                settingsMissing = try { permissions.launch(stepReadPermissions); false }
                    catch (_: ActivityNotFoundException) { true } catch (_: SecurityException) { true }
            }
        }, onCancel = { showPermissionExplanation = false })

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
