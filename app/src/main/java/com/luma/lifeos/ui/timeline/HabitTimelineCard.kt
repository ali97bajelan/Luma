package com.luma.lifeos.ui.timeline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.habit.TimelineFeedItem
import com.luma.lifeos.language.rememberDisplayTimePolicy
import java.time.ZoneId

@Composable
fun HabitTimelineCard(item: TimelineFeedItem.Habit, screenZone: ZoneId, onOpen: () -> Unit) {
    val displayTime = rememberDisplayTimePolicy()
    val log = item.log
    val unit = item.unitLabel ?: pluralStringResource(R.plurals.habit_unit_default, 1)
    val time = log.occurredAt?.atZone(log.zone)?.format(displayTime.time())
        ?: stringResource(R.string.habit_time_not_recorded)
    Card(Modifier.fillMaxWidth().testTag("habit_timeline_${log.id}")
        .clickable(onClickLabel = stringResource(R.string.habit_open_accessibility, item.name), onClick = onOpen)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.habit_title), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text(time, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(item.name, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.habit_timeline_completion, unit), style = MaterialTheme.typography.bodyMedium)
            if (log.zone != screenZone) Text(log.zone.id, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("habit_timeline_zone_${log.id}"))
        }
    }
}
