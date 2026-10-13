package com.luma.lifeos.ui.timeline

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.model.*
import com.luma.lifeos.language.activityTypeDisplayName
import com.luma.lifeos.language.activityUnitDisplayName
import com.luma.lifeos.language.emotionDisplayName
import com.luma.lifeos.ui.memory.MemoryStatus
import com.luma.lifeos.ui.mood.moodScoreLabel
import com.luma.lifeos.ui.people.LinkedPeople
import com.luma.lifeos.ui.places.LinkedPlace
import com.luma.lifeos.ui.photos.PhotoCount
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.FormatStyle

@Composable
fun EventCard(record: LifeEventRecord, zone: ZoneId, onOpen: () -> Unit, onMemory: (() -> Unit)? = null) {
    val event = record.event
    val action = stringResource(when {
        record.canEditContent && event.type == EventType.MOOD -> R.string.mood_edit_action
        record.canEditContent && event.type == EventType.MEMORY -> R.string.memory_edit
        record.journal != null -> R.string.journal_open
        else -> R.string.event_open
    })
    Card(Modifier.fillMaxWidth().testTag("event_${event.id}").clickable(onClickLabel = action, onClick = onOpen)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(event.type.label()), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Text(event.occurredAt.atZone(zone).format(com.luma.lifeos.language.localizedTime()), style = MaterialTheme.typography.labelLarge)
            }
            event.title?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            record.mood?.let { MoodContent(it) }
            (record.journal?.body ?: record.mood?.note ?: event.note ?: record.memory?.note)?.takeIf { it.isNotBlank() }?.let {
                Text(it.take(500), maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            record.journal?.tags?.takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(", ") { tag -> tag.name }, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            SignalContent(record, zone, full = false)
            LinkedPeople(record.people); LinkedPlace(record.place); PhotoCount(record.photos.size)
            record.memory?.let { MemoryStatus(it) }
            if (event.type != EventType.MEMORY) record.memory?.note?.let { Text(it.take(500), maxLines = 3, overflow = TextOverflow.Ellipsis) }
            if (onMemory != null && (record.canPromoteToMemory || (event.type == EventType.MEMORY && record.canEditContent)))
                TextButton(onClick = onMemory, modifier = Modifier.testTag("event_memory_${event.id}")) {
                    Text(stringResource(if (record.memory == null) R.string.memory_promote else R.string.memory_edit))
                }
            Text(action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(event.source.label()), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun MoodContent(mood: MoodDetails) {
    Text(stringResource(R.string.mood_score_value, mood.score, stringResource(moodScoreLabel(mood.score))), style = MaterialTheme.typography.titleMedium)
    if (mood.emotions.isNotEmpty()) Text(mood.emotions.map { emotionDisplayName(it) }.joinToString(", "))
    mood.energy?.let { Text(stringResource(R.string.mood_optional_value, stringResource(R.string.mood_energy), it)) }
    mood.stress?.let { Text(stringResource(R.string.mood_optional_value, stringResource(R.string.mood_stress), it)) }
}

@Composable
fun SignalContent(record: LifeEventRecord, zone: ZoneId, full: Boolean) {
    val format = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 3 }
    val activities = if (full) record.activities else record.activities.take(3)
    activities.forEach { Text(stringResource(R.string.activity_value,
        activityTypeDisplayName(it.type, it.unit, it.source), format.format(it.value),
        activityUnitDisplayName(it.type, it.unit, it.source))) }
    if (!full && record.activities.size > 3) Text(stringResource(R.string.activity_more, record.activities.size - 3))
    record.appUsage?.let { usage ->
        Text(usage.appLabel ?: usage.packageName, style = MaterialTheme.typography.titleMedium)
        if (usage.appLabel != null) Text(usage.packageName, style = MaterialTheme.typography.bodySmall)
        val milliseconds = usage.durationMillis
        Text(stringResource(R.string.usage_duration, milliseconds / 3_600_000, (milliseconds / 60_000) % 60,
            format.format((milliseconds % 60_000) / 1000.0)))
        if (full) {
            val formatter = com.luma.lifeos.language.localizedDateTime(FormatStyle.MEDIUM)
            Text(stringResource(R.string.event_interval, usage.bucketStart.atZone(zone).format(formatter), usage.bucketEnd.atZone(zone).format(formatter)))
        }
    }
    if (record.event.type == EventType.ACTIVITY && record.activities.isEmpty()) Text(stringResource(R.string.activity_unavailable))
    if (record.event.type == EventType.APP_USAGE && record.appUsage == null) Text(stringResource(R.string.usage_unavailable))
}

@StringRes fun EventType.label(): Int = when (this) {
    EventType.MOOD -> R.string.event_mood
    EventType.JOURNAL -> R.string.event_journal
    EventType.MEMORY -> R.string.event_memory
    EventType.ACTIVITY -> R.string.event_activity
    EventType.APP_USAGE -> R.string.event_app_usage
}
@StringRes fun EventSource.label(): Int = when (this) {
    EventSource.MANUAL -> R.string.source_manual
    EventSource.HEALTH_CONNECT -> R.string.source_health
    EventSource.USAGE_STATS -> R.string.source_usage
}
