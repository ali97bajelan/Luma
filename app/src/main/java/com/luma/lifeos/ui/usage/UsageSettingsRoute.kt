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
import java.time.format.FormatStyle

@Composable
fun UsageSettingsRoute(integration: UsageIntegration, date: LocalDate, zone: ZoneId,
    onSelectDate: (LocalDate) -> Unit, onToday: () -> Unit, onPlaces: () -> Unit,
    health: com.luma.lifeos.domain.health.HealthIntegration? = null,
    backup: com.luma.lifeos.domain.backup.BackupService? = null, onPrivacy: (() -> Unit)? = null) {
    val factory = remember(integration) { viewModelFactory { initializer { UsageSettingsViewModel(integration) } } }
    val vm: UsageSettingsViewModel = viewModel(factory = factory)
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showAccessExplanation by rememberSaveable { mutableStateOf(false) }
    var settingsMissing by rememberSaveable { mutableStateOf(false) }
    var showTimeline by rememberSaveable { mutableStateOf(false) }
    key(date, zone) {
        var retry by remember { mutableIntStateOf(0) }
        val flow = remember(integration, retry) { observeUsageDay(integration, date, zone) }
        val day by flow.collectAsStateWithLifecycle(initialValue = UsageDayState.Loading)
        LazyColumn(Modifier.fillMaxSize().testTag("screen_settings"), contentPadding = PaddingValues(20.dp, 24.dp, 20.dp, 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineLarge)
                if (onPrivacy != null) TextButton(onClick = onPrivacy, modifier = Modifier.testTag("settings_privacy")) { Text(stringResource(R.string.privacy_title)) }
                TextButton(onClick = onPlaces, modifier = Modifier.testTag("settings_places")) { Text(stringResource(R.string.places)) }
            }
            item { com.luma.lifeos.language.LanguageSettingsCard() }
            if (backup != null) item { com.luma.lifeos.ui.backup.BackupSettingsCard(backup) }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.usage_title), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.usage_access_explanation))
                        Text(stringResource(R.string.usage_privacy_retention))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.usage_enable), modifier = Modifier.weight(1f))
                            Switch(checked = state.current.enabled, enabled = !state.loading && (!state.busy || state.current.enabled),
                                onCheckedChange = vm::setEnabled, modifier = Modifier.testTag("usage_enabled"))
                        }
                        if (state.loading || state.busy) CircularProgressIndicator(Modifier.testTag("usage_busy"))
                        Text(stringResource(state.current.status.label()), modifier = Modifier.testTag("usage_status"))
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
                        if (settingsMissing) Text(stringResource(R.string.usage_settings_missing))
                        if (state.actionError) Text(stringResource(R.string.usage_action_error), modifier = Modifier.testTag("usage_action_error"))
                        TextButton(onClick = vm::refresh, enabled = !state.busy, modifier = Modifier.testTag("usage_refresh")) { Text(stringResource(R.string.usage_refresh)) }
                    }
                }
            }
            if (health != null) item { com.luma.lifeos.ui.health.HealthSettingsCard(health) }
            item {
                Text(stringResource(R.string.usage_daily_title), style = MaterialTheme.typography.titleLarge)
                DayNavigation(date, onSelectDate, onToday)
                Text(zone.id, style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.usage_daily_explanation))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = showTimeline, onClick = { showTimeline = true },
                        label = { Text(stringResource(R.string.usage_view_timeline)) },
                        modifier = Modifier.weight(1f).testTag("usage_view_timeline"))
                    FilterChip(selected = !showTimeline, onClick = { showTimeline = false },
                        label = { Text(stringResource(R.string.usage_view_totals)) },
                        modifier = Modifier.weight(1f).testTag("usage_view_totals"))
                }
            }
            when (val current = day) {
                UsageDayState.Loading -> item { CircularProgressIndicator() }
                UsageDayState.Empty -> item {
                    Text(stringResource(R.string.usage_daily_empty), modifier = Modifier.testTag("usage_daily_empty"))
                    if (showTimeline) Text(stringResource(R.string.usage_history_unavailable), modifier = Modifier.testTag("usage_history_unavailable"))
                }
                UsageDayState.Error -> item {
                    Text(stringResource(R.string.usage_daily_error))
                    TextButton(onClick = { retry++ }) { Text(stringResource(R.string.retry)) }
                }
                is UsageDayState.Data -> {
                    when (current.report.history) {
                        UsageHistory.PARTIAL -> item { Text(stringResource(R.string.usage_history_partial), modifier = Modifier.testTag("usage_history_partial")) }
                        UsageHistory.UNAVAILABLE -> item { Text(stringResource(if (showTimeline) R.string.usage_history_unavailable else R.string.usage_legacy_totals),
                            modifier = Modifier.testTag("usage_history_unavailable")) }
                        UsageHistory.COMPLETE -> Unit
                    }
                    if (current.report.apps.isEmpty()) item {
                        Text(stringResource(R.string.usage_daily_empty), modifier = Modifier.testTag("usage_daily_empty"))
                    } else if (!showTimeline || current.report.history != UsageHistory.UNAVAILABLE) item {
                        Text(usageDuration(current.report.totalMillis), style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("usage_daily_total"))
                    }
                    if (showTimeline) items(current.report.sessions, key = { it.identity }) { session ->
                        Card(Modifier.fillMaxWidth().testTag("usage_session_${session.packageName}_${session.openedAt.toEpochMilli()}")) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(session.appLabel ?: session.packageName, style = MaterialTheme.typography.titleMedium)
                                if (session.appLabel != null) Text(session.packageName, style = MaterialTheme.typography.bodySmall)
                                val formatter = com.luma.lifeos.language.localizedDateTime(FormatStyle.SHORT)
                                Text(stringResource(R.string.usage_session_times, session.start.atZone(zone).format(formatter),
                                    session.end.atZone(zone).format(formatter)))
                                if (session.openedAt < session.start) Text(stringResource(R.string.usage_session_continued,
                                    session.openedAt.atZone(zone).format(formatter)), style = MaterialTheme.typography.bodySmall)
                                Text(usageDuration(session.durationMillis))
                                if (session.ongoing) Text(stringResource(R.string.usage_session_ongoing), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else items(current.report.apps, key = { it.packageName }) { app ->
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
    if (showAccessExplanation) com.luma.lifeos.ui.privacy.PermissionExplanationDialog(
        R.string.usage_title, R.string.usage_access_explanation,
        onContinue = { showAccessExplanation = false; settingsMissing = !openUsageAccessSettings(context) },
        onCancel = { showAccessExplanation = false })
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
