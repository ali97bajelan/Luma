package com.luma.lifeos.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.luma.lifeos.R
import com.luma.lifeos.domain.health.HealthIntegration
import com.luma.lifeos.domain.usage.UsageIntegration
import com.luma.lifeos.ui.health.HealthSettingsCard
import com.luma.lifeos.ui.usage.UsageIntegrationSettingsCard
import com.luma.lifeos.ui.integrations.IntegrationAvailabilityState
import com.luma.lifeos.ui.integrations.IntegrationAvailabilityRefresh
import com.luma.lifeos.ui.integrations.IntegrationAvailabilityViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.time.ZoneId

@Composable
fun IntegrationsSettingsRoute(usage: UsageIntegration?, health: HealthIntegration?, zone: ZoneId,
    onBack: () -> Unit, availability: IntegrationAvailabilityState? = null,
    onRefreshAccess: (() -> Unit)? = null) {
    if (availability != null) {
        SettingsSectionPage(R.string.settings_integrations_title, onBack, screenTag = "screen_settings_integrations") {
            IntegrationsSettingsContent(usage, health, zone, availability, onRefreshAccess ?: {})
        }
    } else {
        val factory = remember(usage, health) { viewModelFactory { initializer { IntegrationAvailabilityViewModel(usage, health) } } }
        val vm: IntegrationAvailabilityViewModel = viewModel(factory = factory)
        val state by vm.state.collectAsStateWithLifecycle()
        IntegrationAvailabilityRefresh(vm)
        SettingsSectionPage(R.string.settings_integrations_title, onBack, screenTag = "screen_settings_integrations") {
            IntegrationsSettingsContent(usage, health, zone, state, vm::refreshAccess)
        }
    }
}

@Composable
private fun IntegrationsSettingsContent(usage: UsageIntegration?, health: HealthIntegration?, zone: ZoneId,
    availability: IntegrationAvailabilityState, onRefreshAccess: () -> Unit) {
    usage?.let { UsageIntegrationSettingsCard(it, zone, availability, onRefreshAccess) }
    health?.let { HealthSettingsCard(it, zone, availability, onRefreshAccess) }
}
