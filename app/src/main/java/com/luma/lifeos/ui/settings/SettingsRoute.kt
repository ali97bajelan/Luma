package com.luma.lifeos.ui.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.ui.common.AppPageScaffold
import com.luma.lifeos.ui.common.LumaChoiceRow
import com.luma.lifeos.ui.common.LumaReadingPane
import com.luma.lifeos.ui.common.PageNavigation

@Composable
fun SettingsRoute(backupAvailable: Boolean, privacyAvailable: Boolean, integrationsAvailable: Boolean,
    onLanguage: () -> Unit, onBackup: () -> Unit, onPrivacy: () -> Unit, onIntegrations: () -> Unit,
    onBack: (() -> Unit)? = null, widgetsAvailable: Boolean = false, onWidgets: () -> Unit = {},
    remindersAvailable: Boolean = false, onReminders: () -> Unit = {},
    focusAvailable: Boolean = false, onFocus: () -> Unit = {}) {
    BackHandler(enabled = onBack != null) { onBack?.invoke() }
    AppPageScaffold(
        title = stringResource(R.string.settings),
        navigation = if (onBack != null) PageNavigation.BACK else PageNavigation.NONE,
        onNavigate = onBack,
        navigationTag = "settings_hub_back",
    ) { pagePadding ->
      LumaReadingPane(Modifier.fillMaxSize().padding(pagePadding)) {
       Column(Modifier.fillMaxSize().testTag("screen_settings")
            .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.settings_hub_description))
            if (widgetsAvailable) SettingsRow(
                R.string.settings_widgets_title,
                R.string.settings_widgets_description,
                "settings_widgets",
                onWidgets,
            )
            if (focusAvailable) SettingsRow(
                R.string.focus_settings_title,
                R.string.focus_settings_description,
                "settings_focus",
                onFocus,
            )
            if (remindersAvailable) SettingsRow(R.string.reminder_settings_title, R.string.reminder_settings_description,
                "settings_reminders", onReminders)
            SettingsRow(R.string.settings_language_title, R.string.settings_language_description, "settings_language", onLanguage)
            com.luma.lifeos.ui.theme.ThemeSettingsCard()
            if (backupAvailable) SettingsRow(R.string.settings_backup_title, R.string.settings_backup_description, "settings_backup", onBackup)
            if (privacyAvailable) SettingsRow(R.string.settings_privacy_title, R.string.settings_privacy_description, "settings_privacy", onPrivacy)
            if (integrationsAvailable) SettingsRow(R.string.settings_integrations_title, R.string.settings_integrations_description,
                "settings_integrations", onIntegrations)
        }
      }
    }
}

@Composable
private fun SettingsRow(@StringRes title: Int, @StringRes description: Int, tag: String, onClick: () -> Unit) {
    LumaChoiceRow(title = stringResource(title), icon = R.drawable.ic_nav_settings, onClick = onClick,
        modifier = Modifier.testTag(tag), supportingText = stringResource(description), showChevron = true)
}

@Composable
fun SettingsSectionPage(@StringRes title: Int, onBack: () -> Unit,
    screenTag: String = "screen_settings_section", content: @Composable ColumnScope.() -> Unit) {
    BackHandler(onBack = onBack)
    AppPageScaffold(
        title = stringResource(title), navigation = PageNavigation.BACK, onNavigate = onBack,
        headerTag = "settings_page_header", titleTag = "settings_page_title", navigationTag = "settings_back",
    ) { pagePadding ->
      LumaReadingPane(Modifier.fillMaxSize().padding(pagePadding)) {
       Column(Modifier.fillMaxSize().testTag(screenTag).verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
      }
    }
}
