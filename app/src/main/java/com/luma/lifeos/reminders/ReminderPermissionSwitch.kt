package com.luma.lifeos.reminders

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R

@Composable
fun ReminderPermissionSwitch(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    val context = LocalContext.current
    val notificationAccess = rememberReminderNotificationAccess(onRequestComplete = {
        // Preserve the opt-in after denial so the editor can display a blocked status.
        onCheckedChange(true)
    })
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.reminder_enable), style = MaterialTheme.typography.titleMedium)
                Text(description ?: stringResource(R.string.reminder_enable_description), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, enabled = enabled, onCheckedChange = { requested ->
                if (requested && !notificationPermissionGranted(context)) {
                    notificationAccess.request()
                } else onCheckedChange(requested)
            })
        }
    }
}
