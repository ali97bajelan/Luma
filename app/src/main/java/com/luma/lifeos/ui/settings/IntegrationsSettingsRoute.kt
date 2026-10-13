package com.luma.lifeos.ui.settings

import androidx.compose.runtime.Composable
import com.luma.lifeos.R
import com.luma.lifeos.domain.health.HealthIntegration
import com.luma.lifeos.domain.usage.UsageIntegration
import com.luma.lifeos.ui.health.HealthSettingsCard
import com.luma.lifeos.ui.usage.UsageIntegrationSettingsCard
import java.time.ZoneId

@Composable
fun IntegrationsSettingsRoute(usage: UsageIntegration?, health: HealthIntegration?, zone: ZoneId, onBack: () -> Unit) {
    SettingsSectionPage(R.string.settings_integrations_title, onBack, screenTag = "screen_settings_integrations") {
        usage?.let { UsageIntegrationSettingsCard(it, zone) }
        health?.let { HealthSettingsCard(it) }
    }
}
