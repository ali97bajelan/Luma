package com.luma.lifeos.reminders

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.luma.lifeos.R
import com.luma.lifeos.ui.common.LumaOutlinedButton
import com.luma.lifeos.ui.common.LumaSectionSurface
import com.luma.lifeos.ui.common.LumaUi
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

@Composable
internal fun ReminderReadinessBlock(
    access: ReminderNotificationAccess,
    preview: ReminderSchedulePreview? = null,
    description: String? = null,
    descriptionTag: String? = null,
    quietHoursEnabled: Boolean = false,
    modifier: Modifier = Modifier,
    accessStatusTag: String = "reminder_notification_access_status",
    accessActionTag: String = "reminder_notification_access_request",
    previewTag: String = "reminder_schedule_preview",
    testActionTag: String = "reminder_test_notification",
    testStatusTag: String = "reminder_test_notification_status",
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    var testResult by rememberSaveable { mutableStateOf("") }
    val accessStatus = when (access.blockReason) {
        ReminderAccessBlockReason.NONE -> stringResource(R.string.reminder_notifications_allowed)
        ReminderAccessBlockReason.APP -> stringResource(R.string.reminder_status_blocked)
        ReminderAccessBlockReason.CHANNEL -> stringResource(R.string.reminder_access_channel_blocked)
    }
    val accessAction = if (access.blockReason == ReminderAccessBlockReason.APP) {
        R.string.reminder_allow_notifications
    } else {
        R.string.reminder_notification_settings
    }

    LumaSectionSurface(
        modifier = modifier,
        contentPadding = LumaUi.SectionPadding,
    ) {
        Text(
            stringResource(R.string.reminder_readiness_heading),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        description?.let {
            val tagModifier = if (descriptionTag != null) Modifier.testTag(descriptionTag) else Modifier
            Text(it, modifier = tagModifier, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            accessStatus,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag(accessStatusTag),
            style = MaterialTheme.typography.bodyMedium,
        )
        LumaOutlinedButton(
            onClick = access.request,
            modifier = Modifier.fillMaxWidth().testTag(accessActionTag),
        ) {
            Text(stringResource(accessAction))
        }
        preview?.let {
            Text(
                reminderSchedulePreviewText(it, locale),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag(previewTag),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (quietHoursEnabled) {
            Text(
                stringResource(R.string.reminder_quiet_hours_effect),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        LumaOutlinedButton(
            onClick = {
                testResult = ReminderPlatform(context).postGenericTestNotification().name
                access.refresh()
            },
            modifier = Modifier.fillMaxWidth().testTag(testActionTag),
        ) {
            Text(stringResource(R.string.reminder_test_send))
        }
        when (testResult) {
            ReminderTestNotificationResult.ATTEMPTED.name -> Text(
                stringResource(R.string.reminder_test_requested),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag(testStatusTag),
                style = MaterialTheme.typography.bodySmall,
            )
            ReminderTestNotificationResult.BLOCKED.name -> Text(
                stringResource(R.string.reminder_test_blocked),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag(testStatusTag),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun reminderSchedulePreviewText(
    preview: ReminderSchedulePreview,
    locale: Locale,
): String = when (preview) {
    is ReminderSchedulePreview.NextEligible -> stringResource(
        R.string.reminder_preview_next_eligible,
        previewDateTime(preview.at, preview.zone, locale),
        preview.zone.getDisplayName(TextStyle.FULL, locale),
    )
    is ReminderSchedulePreview.QuietHoursSuppressed -> stringResource(
        R.string.reminder_preview_quiet_hours_suppressed,
        previewDateTime(preview.at, preview.zone, locale),
        preview.zone.getDisplayName(TextStyle.FULL, locale),
    )
    ReminderSchedulePreview.MissingDueDate -> stringResource(R.string.reminder_preview_missing_due_date)
    ReminderSchedulePreview.MissingDueTime -> stringResource(R.string.reminder_preview_missing_due_time)
    ReminderSchedulePreview.NoSchedule -> stringResource(R.string.reminder_preview_no_schedule)
    ReminderSchedulePreview.NoEligibleSlot -> stringResource(R.string.reminder_preview_no_eligible_slot)
}

private fun previewDateTime(
    at: java.time.Instant,
    zone: ZoneId,
    locale: Locale,
): String = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    .withLocale(locale)
    .format(at.atZone(zone))
