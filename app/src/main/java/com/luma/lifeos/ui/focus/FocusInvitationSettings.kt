package com.luma.lifeos.ui.focus

import com.luma.lifeos.ui.common.LumaUi
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import com.luma.lifeos.ui.common.LumaFilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.luma.lifeos.ui.common.LumaTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luma.lifeos.R
import com.luma.lifeos.domain.focus.FocusInvitationConfig
import com.luma.lifeos.domain.focus.FocusInvitationRepository
import com.luma.lifeos.domain.focus.FocusProductContext
import com.luma.lifeos.domain.focus.FocusProductResult
import com.luma.lifeos.reminders.ReminderNotificationAccess
import com.luma.lifeos.reminders.ReminderPreferences
import com.luma.lifeos.reminders.enqueueReminderReconciliation
import com.luma.lifeos.reminders.rememberReminderNotificationAccess
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

private val FocusInvitationConfigSaver = mapSaver<FocusInvitationConfig>(
    save = { config ->
        mapOf(
            "enabled" to config.enabled,
            "revision" to config.revision,
            "weekdays" to config.weekdays.map { it.value },
            "time" to config.time.toString(),
        )
    },
    restore = { saved ->
        val weekdays = (saved["weekdays"] as? List<*>)
            .orEmpty()
            .mapNotNull { value ->
                (value as? Number)?.toInt()?.let { day ->
                    runCatching { DayOfWeek.of(day) }.getOrNull()
                }
            }
            .toSet()
        val time = runCatching {
            LocalTime.parse(saved["time"] as? String ?: "09:00")
        }.getOrDefault(LocalTime.of(9, 0))
        FocusInvitationConfig(
            enabled = saved["enabled"] as? Boolean ?: false,
            revision = (saved["revision"] as? Number)?.toLong() ?: 0L,
            weekdays = weekdays,
            time = time,
        )
    },
)

@Composable
internal fun FocusInvitationSettings(
    repository: FocusInvitationRepository,
    preferences: ReminderPreferences?,
    notificationAccessOverride: ReminderNotificationAccess? = null,
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
    val platformNotificationAccess = rememberReminderNotificationAccess()
    val notificationAccess = notificationAccessOverride ?: platformNotificationAccess
    var draft by rememberSaveable(repository, stateSaver = FocusInvitationConfigSaver) {
        mutableStateOf(persisted)
    }
    var busy by remember { mutableStateOf(false) }
    var writeFailed by remember { mutableStateOf(false) }
    var weekdayRequired by remember { mutableStateOf(false) }

    LaunchedEffect(persisted.revision) {
        if (persisted.revision > draft.revision) {
            draft = persisted
        }
    }

    fun save(next: FocusInvitationConfig) {
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
                }
                else -> writeFailed = true
            }
        }
    }

    val scheduleSummary = stringResource(
        if (persisted.enabled) R.string.focus_invitation_schedule_enabled
        else R.string.focus_invitation_schedule_disabled,
    )
    val notificationSummary = stringResource(
        if (notificationAccess.granted) R.string.focus_invitation_notifications_allowed
        else R.string.focus_invitation_notifications_blocked,
    )
    val statusSummary = stringResource(
        R.string.focus_invitation_settings_summary,
        scheduleSummary,
        notificationSummary,
    )
    val settingsSummary = when {
        writeFailed -> stringResource(R.string.focus_invitation_save_error)
        busy -> stringResource(R.string.focus_invitation_save_pending)
        else -> statusSummary
    }
    FocusSettingsDisclosure(
        title = stringResource(R.string.focus_invitation_settings_heading),
        summary = settingsSummary,
        sectionTag = "focus_settings_reminders",
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().testTag("focus_invitation_settings"),
            verticalArrangement = Arrangement.spacedBy(LumaUi.ContentGap),
        ) {
            Text(stringResource(R.string.focus_invitation_settings_intro), style = MaterialTheme.typography.bodyMedium)
            if (preferences?.quietHours()?.enabled == true) {
                Text(stringResource(R.string.focus_invitation_quiet_hours_hint), style = MaterialTheme.typography.bodySmall)
            }
            val enableInvitationLabel = stringResource(R.string.focus_invitation_enable)
            val disabledReason = when {
                busy -> stringResource(R.string.focus_invitation_save_pending)
                !draft.enabled && draft.weekdays.isEmpty() ->
                    stringResource(R.string.focus_invitation_weekday_required)
                else -> null
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
                    onCheckedChange = { enabled -> save(draft.copy(enabled = enabled)) },
                    modifier = Modifier.semantics {
                        contentDescription = enableInvitationLabel
                        disabledReason?.let { stateDescription = it }
                    }.testTag("focus_invitation_enable"),
                )
            }
            Text(stringResource(R.string.focus_invitation_weekdays), style = MaterialTheme.typography.titleSmall)
            val weekdays = DayOfWeek.entries
            val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
            weekdays.chunked(4).forEach { rowDays ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    rowDays.forEach { day ->
                        val selected = day in draft.weekdays
                        val short = day.getDisplayName(TextStyle.SHORT, locale)
                        LumaFilterChip(
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
            if (weekdayRequired || draft.weekdays.isEmpty()) {
                Text(
                    stringResource(R.string.focus_invitation_weekday_required),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.focus_invitation_time), Modifier.weight(1f))
                LumaTextButton(
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
            Text(
                stringResource(
                    if (notificationAccess.granted) R.string.focus_invitation_notifications_allowed
                    else R.string.focus_invitation_notifications_blocked,
                ),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag("focus_invitation_notification_status"),
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
            if (busy) {
                Text(
                    stringResource(R.string.focus_invitation_save_pending),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        .testTag("focus_invitation_save_pending"),
                )
            }
            if (writeFailed) {
                Text(
                    stringResource(R.string.focus_invitation_save_error),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        .testTag("focus_invitation_save_error"),
                )
            }
        }
    }
}
