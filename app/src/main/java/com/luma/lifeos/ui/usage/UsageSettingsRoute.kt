package com.luma.lifeos.ui.usage

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.luma.lifeos.domain.usage.*
import com.luma.lifeos.integration.usage.openUsageAccessSettings
import com.luma.lifeos.ui.timeline.DayNavigation
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun UsageSettingsRoute(integration: UsageIntegration, date: LocalDate, zone: ZoneId,
    onSelectDate: (LocalDate) -> Unit, onToday: () -> Unit, onPlaces: () -> Unit) {
    val factory = remember(integration) { viewModelFactory { initializer { UsageSettingsViewModel(integration) } } }
    val vm: UsageSettingsViewModel = viewModel(factory = factory)
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var settingsMissing by rememberSaveable { mutableStateOf(false) }
    key(date, zone) {
        var retry by remember { mutableIntStateOf(0) }
        val flow = remember(integration, retry) { observeUsageDay(integration, date, zone) }
        val day by flow.collectAsStateWithLifecycle(initialValue = UsageDayState.Loading)
        LazyColumn(Modifier.fillMaxSize().testTag("screen_settings"), contentPadding = PaddingValues(20.dp, 24.dp, 20.dp, 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineLarge)
                TextButton(onClick = onPlaces, modifier = Modifier.testTag("settings_places")) { Text(stringResource(R.string.places)) }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.usage_title), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.usage_access_explanation))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.usage_enable), modifier = Modifier.weight(1f))
                            Switch(checked = state.current.enabled, enabled = !state.loading && (!state.busy || state.current.enabled),
                                onCheckedChange = vm::setEnabled, modifier = Modifier.testTag("usage_enabled"))
                        }
                        if (state.loading || state.busy) CircularProgressIndicator(Modifier.testTag("usage_busy"))
                        Text(stringResource(state.current.status.label()), modifier = Modifier.testTag("usage_status"))
                        state.current.lastSync?.let { Text(stringResource(R.string.usage_last_sync,
                            it.atZone(zone).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)))) }
                        if (state.current.enabled) {
                            TextButton(onClick = { settingsMissing = !openUsageAccessSettings(context) }, modifier = Modifier.testTag("usage_access")) {
                                Text(stringResource(R.string.usage_manage_access))
                            }
                            Button(onClick = vm::importNow, enabled = !state.busy && !state.loading && state.current.status in
                                setOf(UsageSyncStatus.READY, UsageSyncStatus.NO_DATA, UsageSyncStatus.ERROR, UsageSyncStatus.INTERVAL_CHANGED), modifier = Modifier.testTag("usage_import")) {
                                Text(stringResource(R.string.usage_import_now))
                            }
                        }
                        if (settingsMissing) Text(stringResource(R.string.usage_settings_missing))
                        if (state.actionError) Text(stringResource(R.string.usage_action_error), modifier = Modifier.testTag("usage_action_error"))
                        if (state.actionError || state.current.status in setOf(UsageSyncStatus.ERROR, UsageSyncStatus.UNAVAILABLE, UsageSyncStatus.LOCKED))
                            TextButton(onClick = vm::refresh, enabled = !state.busy, modifier = Modifier.testTag("usage_refresh")) { Text(stringResource(R.string.retry)) }
                    }
                }
            }
            item {
                Text(stringResource(R.string.usage_daily_title), style = MaterialTheme.typography.titleLarge)
                DayNavigation(date, onSelectDate, onToday)
                Text(zone.id, style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.usage_daily_explanation))
            }
            when (val current = day) {
                UsageDayState.Loading -> item { CircularProgressIndicator() }
                UsageDayState.Empty -> item { Text(stringResource(R.string.usage_daily_empty), modifier = Modifier.testTag("usage_daily_empty")) }
                UsageDayState.Error -> item {
                    Text(stringResource(R.string.usage_daily_error))
                    TextButton(onClick = { retry++ }) { Text(stringResource(R.string.retry)) }
                }
                is UsageDayState.Data -> {
                    item { Text(usageDuration(current.report.totalMillis), style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("usage_daily_total")) }
                    items(current.report.apps, key = { it.packageName }) { app ->
                        Card(Modifier.fillMaxWidth().testTag("usage_app_${app.packageName}")) {
                            Column(Modifier.padding(16.dp)) {
                                Text(app.appLabel ?: app.packageName, style = MaterialTheme.typography.titleMedium)
                                if (app.appLabel != null) Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                                Text(usageDuration(app.durationMillis))
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun usageDuration(millis: Long): String {
    val number = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 3 }
    return stringResource(R.string.usage_duration, millis / 3_600_000, (millis / 60_000) % 60, number.format((millis % 60_000) / 1000.0))
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
