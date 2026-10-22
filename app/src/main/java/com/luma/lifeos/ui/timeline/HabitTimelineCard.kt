package com.luma.lifeos.ui.timeline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.habit.TimelineFeedItem
import com.luma.lifeos.language.rememberDisplayTimePolicy
import com.luma.lifeos.ui.common.LumaUi
import java.time.ZoneId

@Composable
fun HabitTimelineCard(item: TimelineFeedItem.Habit, screenZone: ZoneId, onOpen: () -> Unit) {
    val displayTime = rememberDisplayTimePolicy()
    val log = item.log
    val unit = item.unitLabel ?: pluralStringResource(R.plurals.habit_unit_default, 1)
    val time = log.occurredAt?.atZone(log.zone)?.format(displayTime.time())
        ?: stringResource(R.string.habit_time_not_recorded)
    Card(
        modifier = Modifier.fillMaxWidth().testTag("habit_timeline_${log.id}")
            .clickable(onClickLabel = stringResource(R.string.habit_open_accessibility, item.name), onClick = onOpen),
        shape = RoundedCornerShape(LumaUi.SectionRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(stringResource(R.string.habit_title), modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(time, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(item.name, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.habit_timeline_completion, unit), style = MaterialTheme.typography.bodyMedium)
            if (log.zone != screenZone) Text(log.zone.id, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("habit_timeline_zone_${log.id}"))
        }
    }
}
