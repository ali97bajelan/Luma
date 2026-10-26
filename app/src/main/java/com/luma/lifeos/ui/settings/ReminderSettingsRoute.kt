package com.luma.lifeos.ui.settings

import android.app.TimePickerDialog
import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.habit.ReminderQuietHours
import com.luma.lifeos.reminders.ReminderPreferences
import com.luma.lifeos.reminders.enqueueReminderReconciliation
import java.time.LocalTime

@Composable
fun ReminderSettingsRoute(preferences: ReminderPreferences, onBack: () -> Unit) {
    val context = LocalContext.current
    var quiet by remember { mutableStateOf(preferences.quietHours()) }
    var privatePreviews by remember { mutableStateOf(preferences.privatePreviews()) }
    SettingsSectionPage(R.string.reminder_settings_title, onBack, screenTag = "screen_settings_reminders") {
        Text(stringResource(R.string.reminder_settings_intro))
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.reminder_quiet_hours), Modifier.weight(1f))
            Switch(checked = quiet.enabled, onCheckedChange = { enabled ->
                quiet = quiet.copy(enabled = enabled)
                preferences.setQuietHours(quiet)
                enqueueReminderReconciliation(context)
            })
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.reminder_quiet_start), Modifier.weight(1f))
            Button(onClick = { showMinutePicker(context, quiet.startMinute) { minute ->
                quiet = quiet.copy(startMinute = minute); preferences.setQuietHours(quiet); enqueueReminderReconciliation(context)
            } }, enabled = quiet.enabled) { Text(formatMinute(quiet.startMinute)) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.reminder_quiet_end), Modifier.weight(1f))
            Button(onClick = { showMinutePicker(context, quiet.endMinute) { minute ->
                quiet = quiet.copy(endMinute = minute); preferences.setQuietHours(quiet); enqueueReminderReconciliation(context)
            } }, enabled = quiet.enabled) { Text(formatMinute(quiet.endMinute)) }
        }
        Text(stringResource(R.string.reminder_quiet_hint))
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.reminder_private_previews), Modifier.weight(1f))
            Switch(checked = privatePreviews, onCheckedChange = { enabled ->
                privatePreviews = enabled
                preferences.setPrivatePreviews(enabled)
                enqueueReminderReconciliation(context)
            })
        }
    }
}

private fun showMinutePicker(context: Context, minute: Int, onSelected: (Int) -> Unit) {
    TimePickerDialog(context, { _, hour, selectedMinute -> onSelected(hour * 60 + selectedMinute) },
        minute / 60, minute % 60, DateFormat.is24HourFormat(context)).show()
}

private fun formatMinute(minute: Int): String = LocalTime.of(minute / 60, minute % 60).toString()
