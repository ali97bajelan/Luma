package com.luma.lifeos.ui.health

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.luma.lifeos.R
import com.luma.lifeos.domain.health.*
import com.luma.lifeos.integration.health.*
import java.time.ZoneId
import java.time.format.FormatStyle

@Composable
fun HealthSettingsCard(integration: HealthIntegration) {
    val factory = remember(integration) { viewModelFactory { initializer { HealthSettingsViewModel(integration) } } }
    val vm: HealthSettingsViewModel = viewModel(factory = factory)
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var settingsMissing by rememberSaveable { mutableStateOf(false) }
    val contract = remember { PermissionController.createRequestPermissionResultContract() }
    val permissions = rememberLauncherForActivityResult(contract) { vm.refresh() }
    Card(Modifier.fillMaxWidth().testTag("health_card")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.health_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.health_explanation))
            Text(stringResource(R.string.health_privacy_retention), modifier = Modifier.testTag("health_retention"))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.health_enable), modifier = Modifier.weight(1f))
                Switch(checked = state.current.enabled, enabled = !state.loading && (!state.busy || state.current.enabled),
                    onCheckedChange = vm::setEnabled, modifier = Modifier.testTag("health_enabled"))
            }
            if (state.loading || state.busy) CircularProgressIndicator(Modifier.testTag("health_busy"))
            Text(stringResource(state.current.status.label()), modifier = Modifier.testTag("health_status"))
            state.current.importZone?.let { Text(stringResource(R.string.health_zone, it.id)) }
            state.current.lastSync?.let { Text(stringResource(R.string.health_last_sync,
                it.atZone(state.current.importZone ?: ZoneId.systemDefault()).format(com.luma.lifeos.language.localizedDateTime(FormatStyle.MEDIUM)))) }
            if (state.current.enabled) {
                when (state.current.status) {
                    HealthStatus.ACCESS_REQUIRED -> Button(onClick = {
                        settingsMissing = try { permissions.launch(stepReadPermissions); false }
                        catch (_: ActivityNotFoundException) { true } catch (_: SecurityException) { true }
                    }, enabled = !state.busy, modifier = Modifier.testTag("health_access")) { Text(stringResource(R.string.health_grant)) }
                    HealthStatus.UPDATE_REQUIRED -> TextButton(onClick = { settingsMissing = !openHealthProviderInstall(context) },
                        modifier = Modifier.testTag("health_install")) { Text(stringResource(R.string.health_install)) }
                    else -> Unit
                }
                Button(onClick = vm::importNow, enabled = !state.busy && !state.loading && state.current.status in
                    setOf(HealthStatus.READY, HealthStatus.NO_DATA, HealthStatus.ERROR), modifier = Modifier.testTag("health_import")) {
                    Text(stringResource(R.string.health_import))
                }
            }
            TextButton(onClick = { settingsMissing = !openHealthSettings(context) }, modifier = Modifier.testTag("health_manage")) {
                Text(stringResource(R.string.health_manage))
            }
            TextButton(onClick = vm::refresh, enabled = !state.busy, modifier = Modifier.testTag("health_refresh")) { Text(stringResource(R.string.health_refresh)) }
            if (settingsMissing) Text(stringResource(R.string.health_settings_missing))
            if (state.actionError) Text(stringResource(R.string.health_action_error), modifier = Modifier.testTag("health_action_error"))
        }
    }
}
private fun HealthStatus.label(): Int = when (this) {
    HealthStatus.DISABLED -> R.string.health_disabled
    HealthStatus.ACCESS_REQUIRED -> R.string.health_required
    HealthStatus.UPDATE_REQUIRED -> R.string.health_update_required
    HealthStatus.UNAVAILABLE -> R.string.health_unavailable
    HealthStatus.READY -> R.string.health_ready
    HealthStatus.NO_DATA -> R.string.health_no_data
    HealthStatus.ERROR -> R.string.health_error
}
