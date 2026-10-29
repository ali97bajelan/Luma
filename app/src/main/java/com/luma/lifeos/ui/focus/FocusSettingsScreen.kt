package com.luma.lifeos.ui.focus

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.focus.AllowedFocusApps
import com.luma.lifeos.focus.displayName
import com.luma.lifeos.ui.settings.SettingsSectionPage

@Composable
internal fun FocusSettingsScreen(
    apps: List<AllowedFocusApps.App>,
    selectedPackages: Set<String>,
    isRunning: Boolean,
    loading: Boolean,
    discoveryFailed: Boolean,
    writeFailed: Boolean,
    onRefresh: () -> Unit,
    onToggle: (AllowedFocusApps.App, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    var search by rememberSaveable { mutableStateOf("") }
    val normalizedSearch = search.trim()
    val filteredApps = remember(apps, normalizedSearch) {
        apps.filter { app ->
            normalizedSearch.isEmpty() || app.displayName(apps).contains(normalizedSearch, ignoreCase = true) ||
                app.packageName.contains(normalizedSearch, ignoreCase = true)
        }
    }
    val unavailablePackages = if (loading || discoveryFailed) emptyList() else {
        (selectedPackages - apps.map { it.packageName }.toSet()).sorted()
    }

    SettingsSectionPage(
        title = R.string.focus_settings_title,
        onBack = onBack,
        screenTag = "focus_settings_screen",
    ) {
        Text(stringResource(R.string.focus_settings_intro), style = MaterialTheme.typography.bodyLarge)
        Text(
            stringResource(R.string.focus_settings_profile_scope),
            modifier = Modifier.testTag("focus_settings_scope"),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(stringResource(R.string.focus_settings_enforcement_limit), style = MaterialTheme.typography.bodyMedium)
        if (isRunning) {
            Text(
                stringResource(R.string.focus_settings_running),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("focus_settings_running"),
            )
        }
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            label = { Text(stringResource(R.string.focus_settings_search)) },
            modifier = Modifier.fillMaxWidth().testTag("focus_settings_search"),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            enabled = !isRunning,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.focus_settings_available_apps),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(onClick = onRefresh, modifier = Modifier.testTag("focus_settings_refresh")) {
                Text(stringResource(R.string.focus_settings_refresh))
            }
        }
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.testTag("focus_settings_loading"))
        } else if (discoveryFailed) {
            Text(
                stringResource(R.string.focus_settings_discovery_error),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("focus_settings_discovery_error"),
            )
        } else if (filteredApps.isEmpty()) {
            Text(
                stringResource(if (normalizedSearch.isEmpty()) R.string.focus_settings_no_apps else R.string.focus_settings_no_results),
                modifier = Modifier.testTag("focus_settings_no_apps"),
            )
        } else {
            filteredApps.forEach { app ->
                val selected = app.packageName in selectedPackages
                AppChoiceRow(
                    app = app,
                    allApps = apps,
                    selected = selected,
                    enabled = !isRunning,
                    onToggle = { onToggle(app, it) },
                )
            }
        }
        if (selectedPackages.isEmpty()) {
            Text(
                stringResource(R.string.focus_settings_empty_selection),
                modifier = Modifier.testTag("focus_settings_empty_selection"),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (unavailablePackages.isNotEmpty()) {
            Text(
                stringResource(R.string.focus_settings_unavailable_heading),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
            )
            unavailablePackages.forEach { packageName ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .testTag("focus_settings_unavailable_$packageName"),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.focus_settings_unavailable_app))
                        Text(packageName, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(
                        onClick = {
                            onToggle(AllowedFocusApps.App(packageName, packageName), false)
                        },
                        enabled = !isRunning,
                        modifier = Modifier.testTag("focus_settings_remove_$packageName"),
                    ) { Text(stringResource(R.string.focus_settings_remove)) }
                }
            }
        }
        if (writeFailed) {
            Text(
                stringResource(R.string.focus_settings_save_error),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("focus_settings_save_error"),
            )
        }
    }
}

@Composable
private fun AppChoiceRow(
    app: AllowedFocusApps.App,
    allApps: List<AllowedFocusApps.App>,
    selected: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = 64.dp)
            .testTag("focus_settings_app_${app.packageName}")
            .toggleable(value = selected, enabled = enabled, role = Role.Checkbox, onValueChange = onToggle)
            .padding(vertical = 4.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.icon, app.label)
        Column(modifier = Modifier.weight(1f)) {
            Text(app.displayName(allApps), style = MaterialTheme.typography.bodyLarge)
            Text(app.packageName, style = MaterialTheme.typography.bodySmall)
        }
        Checkbox(
            checked = selected,
            onCheckedChange = null,
            enabled = enabled,
            modifier = Modifier.testTag("focus_settings_choice_${app.packageName}"),
        )
    }
}

@Composable
private fun AppIcon(icon: Drawable?, label: String) {
    val bitmap = remember(icon) {
        runCatching { icon?.toBitmap() }.getOrNull()
    }
    if (bitmap != null) {
        Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.size(40.dp))
    } else {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(label.take(1).uppercase(), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

private fun Drawable.toBitmap(): Bitmap {
    val width = intrinsicWidth.coerceIn(1, 96)
    val height = intrinsicHeight.coerceIn(1, 96)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val drawable = constantState?.newDrawable()?.mutate() ?: mutate()
    drawable.setBounds(0, 0, width, height)
    drawable.draw(Canvas(bitmap))
    return bitmap
}
