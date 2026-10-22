package com.luma.lifeos.ui.timeline

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luma.lifeos.R
import com.luma.lifeos.domain.model.*
import com.luma.lifeos.language.activityTypeDisplayName
import com.luma.lifeos.language.activityUnitDisplayName
import com.luma.lifeos.language.emotionDisplayName
import com.luma.lifeos.ui.common.LumaUi
import com.luma.lifeos.ui.mood.moodScoreLabel
import com.luma.lifeos.ui.usage.appUsageDuration
import com.luma.lifeos.ui.common.exactDuration
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.FormatStyle

@Composable
fun EventCard(record: LifeEventRecord, zone: ZoneId, onOpen: () -> Unit,
    @StringRes openActionLabel: Int, onMemory: (() -> Unit)? = null) {
    val displayTime = com.luma.lifeos.language.rememberDisplayTimePolicy()
    val event = record.event
    val action = stringResource(openActionLabel)
    Card(
        modifier = Modifier.fillMaxWidth().testTag("event_${event.id}")
            .clickable(onClickLabel = action, onClick = onOpen),
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
                Text(stringResource(event.type.label()), modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(event.occurredAt.atZone(zone).format(displayTime.time()),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (onMemory != null && (record.canPromoteToMemory || (event.type == EventType.MEMORY && record.canEditContent))) {
                    val memoryState = record.memory?.let { memory ->
                        memory.importance?.let { stringResource(R.string.memory_status_importance, it) }
                            ?: stringResource(R.string.memory_status)
                    }
                    IconButton(onClick = onMemory, modifier = Modifier.testTag("event_memory_${event.id}")
                        .semantics { memoryState?.let { stateDescription = it } }) {
                        Icon(painterResource(if (record.memory == null) R.drawable.ic_bookmark_outline else R.drawable.ic_bookmark),
                            contentDescription = stringResource(if (record.memory == null) R.string.memory_promote else R.string.memory_edit),
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            // Usage already supplies its app name as the primary content.
            if (record.appUsage == null) event.title?.takeIf { it.isNotBlank() }?.let {
                val titleStyle = if (record.journal != null) {
                    MaterialTheme.typography.titleLarge.copy(
                        fontFamily = MaterialTheme.typography.headlineSmall.fontFamily,
                    )
                } else MaterialTheme.typography.titleMedium
                Text(it, style = titleStyle, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            record.mood?.let { mood ->
                Text(stringResource(R.string.mood_score_value, mood.score, stringResource(moodScoreLabel(mood.score))),
                    style = MaterialTheme.typography.titleMedium)
            }
            val body = record.journal?.body ?: record.mood?.note ?: event.note ?: record.memory?.note
            body?.takeIf { it.isNotBlank() }?.let {
                Text(it.take(500), style = MaterialTheme.typography.bodyMedium,
                    color = if (record.journal != null) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            // Some browsing surfaces only open the source mood editor. Keep its marker
            // preview until those callers offer a separate memory action.
            if (onMemory == null && record.mood != null) {
                record.memory?.note?.takeIf { it.isNotBlank() && it != body }?.let {
                    Text(it.take(500), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            SignalContent(record, zone, full = false)
            EventContext(record)
        }
    }
}

@Composable
private fun EventContext(record: LifeEventRecord) {
    val context = buildList {
        record.mood?.emotions?.takeIf { it.isNotEmpty() }?.let { emotions ->
            add(emotions.map { emotionDisplayName(it) }.joinToString(", "))
        }
        if (record.people.isNotEmpty()) add(stringResource(R.string.people_linked, record.people.joinToString(", ") { it.name }))
        record.place?.let { add(stringResource(R.string.place_linked, it.name)) }
        if (record.photos.isNotEmpty()) add(pluralStringResource(R.plurals.photo_count, record.photos.size, record.photos.size))
        record.journal?.tags?.takeIf { it.isNotEmpty() }?.let { add(it.joinToString(", ") { tag -> tag.name }) }
        record.memory?.let { memory ->
            add(memory.importance?.let { stringResource(R.string.memory_status_importance, it) }
                ?: stringResource(R.string.memory_status))
        }
        if (record.event.source != EventSource.MANUAL) add(stringResource(record.event.source.label()))
    }
    if (context.isNotEmpty()) Text(context.joinToString(" · "), style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.testTag("event_context_${record.event.id}"))
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
    val displayTime = com.luma.lifeos.language.rememberDisplayTimePolicy()
    val format = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 3 }
    val activities = if (full) record.activities else record.activities.take(1)
    activities.forEach { Text(stringResource(R.string.activity_value,
        activityTypeDisplayName(it.type, it.unit, it.source), format.format(it.value),
        activityUnitDisplayName(it.type, it.unit, it.source))) }
    if (!full && record.activities.size > 1) Text(stringResource(R.string.activity_more, record.activities.size - 1))
    record.appUsage?.let { usage ->
        if (full) {
            val formatter = displayTime.dateTime(FormatStyle.MEDIUM)
            Card(Modifier.fillMaxWidth().testTag("event_usage_detail"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(usage.appLabel ?: usage.packageName, style = MaterialTheme.typography.titleMedium)
                    Text(appUsageDuration(usage.durationMillis), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.usage_exact_duration, exactDuration(usage.durationMillis)),
                        style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.event_interval, usage.bucketStart.atZone(zone).format(formatter), usage.bucketEnd.atZone(zone).format(formatter)))
                    Text(zone.id, style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(record.event.source.label()), style = MaterialTheme.typography.labelMedium)
                    if (usage.appLabel != null) Text(usage.packageName, style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            Text(usage.appLabel ?: usage.packageName, style = MaterialTheme.typography.titleMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(appUsageDuration(usage.durationMillis))
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
