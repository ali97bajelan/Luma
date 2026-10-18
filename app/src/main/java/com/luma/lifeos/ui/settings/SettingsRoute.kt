package com.luma.lifeos.ui.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R

@Composable
fun SettingsRoute(backupAvailable: Boolean, privacyAvailable: Boolean, integrationsAvailable: Boolean,
    onLanguage: () -> Unit, onBackup: () -> Unit, onPrivacy: () -> Unit, onIntegrations: () -> Unit,
    onBack: (() -> Unit)? = null) {
    BackHandler(enabled = onBack != null) { onBack?.invoke() }
    Column(Modifier.fillMaxSize()) {
        if (onBack != null) {
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .testTag("settings_hub_back")) {
                Text(stringResource(R.string.back))
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth().testTag("screen_settings")
            .verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.settings_hub_description))
            SettingsRow(R.string.settings_language_title, R.string.settings_language_description, "settings_language", onLanguage)
            if (backupAvailable) SettingsRow(R.string.settings_backup_title, R.string.settings_backup_description, "settings_backup", onBackup)
            if (privacyAvailable) SettingsRow(R.string.settings_privacy_title, R.string.settings_privacy_description, "settings_privacy", onPrivacy)
            if (integrationsAvailable) SettingsRow(R.string.settings_integrations_title, R.string.settings_integrations_description,
                "settings_integrations", onIntegrations)
        }
    }
}

@Composable
private fun SettingsRow(@StringRes title: Int, @StringRes description: Int, tag: String, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag(tag)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(description), style = MaterialTheme.typography.bodyMedium)
            }
            Icon(painterResource(R.drawable.ic_chevron_forward), contentDescription = null, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun SettingsSectionPage(@StringRes title: Int, onBack: () -> Unit,
    screenTag: String = "screen_settings_section", content: @Composable ColumnScope.() -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBack, modifier = Modifier.testTag("settings_back")) { Text(stringResource(R.string.back)) }
            Text(stringResource(title), style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.semantics { heading() })
        }
        Column(Modifier.weight(1f).fillMaxWidth().testTag(screenTag).verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}
