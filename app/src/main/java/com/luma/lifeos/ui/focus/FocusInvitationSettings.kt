package com.luma.lifeos.ui.focus

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import com.luma.lifeos.R
import com.luma.lifeos.domain.focus.FocusInvitationConfig
import com.luma.lifeos.domain.focus.FocusInvitationRepository
import com.luma.lifeos.domain.focus.FocusProductContext
import com.luma.lifeos.domain.focus.FocusProductResult
import com.luma.lifeos.reminders.enqueueReminderReconciliation
import com.luma.lifeos.reminders.rememberReminderNotificationAccess
import com.luma.lifeos.reminders.notificationPermissionGranted
import com.luma.lifeos.reminders.ReminderPreferences
import com.luma.lifeos.ui.common.LumaButton
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun FocusInvitationSettings(
    repository: FocusInvitationRepository,
    preferences: ReminderPreferences?,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) enqueueReminderReconciliation(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val persisted by repository.observeConfig().collectAsStateWithLifecycle(FocusInvitationConfig())
    val notificationAccess = rememberReminderNotificationAccess()
    var draft by remember { mutableStateOf(persisted) }
    var busy by remember { mutableStateOf(false) }
    var writeFailed by remember { mutableStateOf(false) }
    var weekdayRequired by remember { mutableStateOf(false) }

    LaunchedEffect(persisted, busy) {
        if (!busy) draft = persisted
    }

    fun save(next: FocusInvitationConfig, requestPermission: Boolean = false) {
        if (busy) return
        if (next.enabled && next.weekdays.isEmpty()) {
            weekdayRequired = true
            return
        }
        weekdayRequired = false
        writeFailed = false
        draft = next
        busy = true
        scope.launch {
            val result = try {
                withContext(Dispatchers.IO) {
                    val generation = repository.currentGeneration()
                    repository.saveConfig(
                        config = next.copy(revision = persisted.revision),
                        expectedRevision = persisted.revision,
                        context = FocusProductContext(UUID.randomUUID(), generation),
                    )
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                null
            }
            busy = false
            when (result) {
                FocusProductResult.Applied, FocusProductResult.AlreadyApplied -> {
                    enqueueReminderReconciliation(context)
                    if (requestPermission && !notificationPermissionGranted(context)) {
                        notificationAccess.request()
                    }
                }
                else -> {
                    draft = persisted
                    writeFailed = true
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().testTag("focus_invitation_settings"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.focus_invitation_settings_heading),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(stringResource(R.string.focus_invitation_settings_intro), style = MaterialTheme.typography.bodyMedium)
        if (preferences?.quietHours()?.enabled == true) {
            Text(stringResource(R.string.focus_invitation_quiet_hours_hint), style = MaterialTheme.typography.bodySmall)
        }
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(stringResource(R.string.focus_invitation_enable), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.focus_invitation_enable_hint), style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = draft.enabled,
                enabled = !busy && (draft.enabled || draft.weekdays.isNotEmpty()),
                onCheckedChange = { enabled -> save(draft.copy(enabled = enabled), requestPermission = enabled) },
                modifier = Modifier.testTag("focus_invitation_enable"),
            )
        }
        Text(stringResource(R.string.focus_invitation_weekdays), style = MaterialTheme.typography.titleSmall)
        val weekdays = DayOfWeek.entries
        val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
        weekdays.chunked(4).forEach { rowDays ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                rowDays.forEach { day ->
                    val selected = day in draft.weekdays
                    val short = day.getDisplayName(TextStyle.SHORT, locale)
                    FilterChip(
                        selected = selected,
                        enabled = !busy,
                        onClick = {
                            val selectedDays = if (selected) draft.weekdays - day else draft.weekdays + day
                            val enabled = draft.enabled && selectedDays.isNotEmpty()
                            save(draft.copy(enabled = enabled, weekdays = selectedDays))
                        },
                        label = { Text(short) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            .semantics {
                                contentDescription = day.getDisplayName(TextStyle.FULL, locale)
                            }
                            .testTag("focus_invitation_day_${day.value}"),
                    )
                }
            }
        }
        if (weekdayRequired || (draft.enabled && draft.weekdays.isEmpty())) {
            Text(
                stringResource(R.string.focus_invitation_weekday_required),
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.focus_invitation_time), Modifier.weight(1f))
            TextButton(
                onClick = {
                    TimePickerDialog(
                        context,
                        { _, hour, minute -> save(draft.copy(time = LocalTime.of(hour, minute))) },
                        draft.time.hour,
                        draft.time.minute,
                        DateFormat.is24HourFormat(context),
                    ).show()
                },
                enabled = !busy,
                modifier = Modifier.testTag("focus_invitation_time"),
            ) {
                Text(DateFormat.getTimeFormat(context).format(
                    java.util.Calendar.getInstance().apply {
                        set(java.util.Calendar.HOUR_OF_DAY, draft.time.hour)
                        set(java.util.Calendar.MINUTE, draft.time.minute)
                    }.time,
                ))
            }
        }
        if (draft.enabled) {
            Text(
                stringResource(
                    if (notificationAccess.granted) R.string.focus_invitation_notifications_allowed
                    else R.string.focus_invitation_notifications_blocked,
                ),
                modifier = Modifier.testTag("focus_invitation_notification_status"),
                style = MaterialTheme.typography.bodySmall,
            )
            if (!notificationAccess.granted) {
                LumaButton(
                    onClick = notificationAccess.request,
                    modifier = Modifier.testTag("focus_invitation_notification_access"),
                ) {
                    Text(stringResource(R.string.focus_invitation_allow_notifications))
                }
            }
        }
        if (writeFailed) {
            Text(
                stringResource(R.string.focus_invitation_save_error),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("focus_invitation_save_error"),
            )
        }
    }
}
