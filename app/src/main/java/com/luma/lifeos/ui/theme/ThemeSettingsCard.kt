package com.luma.lifeos.ui.theme

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R

@Composable
fun ThemeSettingsCard() {
    val preferences = rememberThemePreferences()
    val selectedTheme = rememberAppTheme(preferences)
    OutlinedCard(Modifier.fillMaxWidth().testTag("theme_settings")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.theme_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.theme_description), style = MaterialTheme.typography.bodyMedium)
            Column(Modifier.selectableGroup()) {
                AppTheme.entries.forEach { theme ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .testTag("theme_${theme.preferenceValue}")
                            .selectable(selected = selectedTheme == theme, role = Role.RadioButton,
                                onClick = { preferences.edit().putString(THEME_KEY, theme.preferenceValue).apply() })
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(selected = selectedTheme == theme, onClick = null)
                        Text(stringResource(when (theme) {
                            AppTheme.SYSTEM -> R.string.theme_system
                            AppTheme.LIGHT -> R.string.theme_light
                            AppTheme.DARK -> R.string.theme_dark
                        }), modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
