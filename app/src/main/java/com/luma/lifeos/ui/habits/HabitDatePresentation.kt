package com.luma.lifeos.ui.habits

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.luma.lifeos.R
import com.luma.lifeos.language.localizedDate
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.FormatStyle

@Composable
internal fun HabitAffectedDateLabel(
    date: LocalDate,
    trackingZone: ZoneId,
    deviceZone: ZoneId,
    modifier: Modifier = Modifier,
) {
    val formattedDate = localizedDate(date, FormatStyle.MEDIUM)
    val label = if (trackingZone != deviceZone) {
        stringResource(R.string.habit_tracking_elsewhere, formattedDate, trackingZone.id)
    } else {
        stringResource(R.string.habit_affected_date, formattedDate)
    }
    Text(
        text = label,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun HabitTrackingZoneNote(
    trackingZone: ZoneId,
    deviceZone: ZoneId,
    modifier: Modifier = Modifier,
) {
    if (trackingZone != deviceZone) {
        Text(
            text = stringResource(R.string.habit_tracking_zone, trackingZone.id),
            modifier = modifier,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
