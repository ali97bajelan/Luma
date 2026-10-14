package com.luma.lifeos.ui.timeline

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luma.lifeos.R
import com.luma.lifeos.domain.model.*
import com.luma.lifeos.domain.repository.PhotoRepository
import com.luma.lifeos.domain.repository.TimelineRepository
import com.luma.lifeos.ui.memory.MemoryStatus
import com.luma.lifeos.ui.common.DetailTopBar
import com.luma.lifeos.ui.people.LinkedPeople
import com.luma.lifeos.ui.places.LinkedPlace
import com.luma.lifeos.ui.photos.PhotoGallery
import java.time.ZoneId
import java.time.format.FormatStyle

@Composable
fun EventDetailRoute(repository: TimelineRepository, photos: PhotoRepository, id: EventId, zone: ZoneId,
    onBack: () -> Unit, onEdit: (LifeEventRecord) -> Unit, onMemory: () -> Unit) {
    var retry by remember { mutableIntStateOf(0) }
    val flow = remember(repository, id, retry) { observeEventDetail(repository, id) }
    val state by flow.collectAsStateWithLifecycle(initialValue = EventDetailState.Loading)
    BackHandler(onBack = onBack)
    val recordForEdit = (state as? EventDetailState.Data)?.record?.takeIf { it.canEditContent }
    val title = (state as? EventDetailState.Data)?.record?.event?.type?.label()?.let { stringResource(it) }
        ?: stringResource(R.string.event_detail_title)
    val editLabel = recordForEdit?.let { record ->
        stringResource(when (record.event.type) {
            EventType.MOOD -> R.string.mood_edit_action
            EventType.JOURNAL -> R.string.journal_edit
            EventType.MEMORY -> R.string.memory_edit
            EventType.ACTIVITY, EventType.APP_USAGE -> R.string.event_edit
        })
    }
    Scaffold(
        modifier = Modifier.testTag("screen_event_detail"),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            DetailTopBar(
                title = title,
                backTag = "event_detail_back",
                onBack = onBack,
                editTag = if (recordForEdit != null) "event_edit" else null,
                editLabel = editLabel,
                onEdit = recordForEdit?.let { record -> { onEdit(record) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp)
            .testTag("event_detail_content"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (val current = state) {
                EventDetailState.Loading -> CircularProgressIndicator()
                EventDetailState.Missing -> Text(stringResource(R.string.event_missing), modifier = Modifier.testTag("event_missing"))
                EventDetailState.Error -> {
                    Text(stringResource(R.string.event_detail_error))
                    TextButton(onClick = { retry++ }) { Text(stringResource(R.string.retry)) }
                }
                is EventDetailState.Data -> {
                    val record = current.record
                    val groupedUsageDetails = record.appUsage != null
                    if (!groupedUsageDetails) {
                        Text(record.event.title ?: stringResource(record.event.type.label()), style = MaterialTheme.typography.headlineMedium)
                        Text(stringResource(record.event.source.label()))
                    }
                    if (!record.canEditContent) Text(stringResource(R.string.event_read_only))
                    if (!groupedUsageDetails) {
                        val formatter = com.luma.lifeos.language.localizedDateTime(FormatStyle.MEDIUM)
                        Text(record.event.occurredAt.atZone(zone).format(formatter))
                        record.event.endedAt?.let { Text(stringResource(R.string.event_ended, it.atZone(zone).format(formatter))) }
                        Text(zone.id, style = MaterialTheme.typography.bodySmall)
                    }
                    record.mood?.let { MoodContent(it) }
                    val body = record.journal?.body ?: record.mood?.note ?: record.event.note ?: record.memory?.note
                    body?.let { Text(it, modifier = Modifier.testTag("event_detail_body")) }
                    record.journal?.tags?.takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(", ") { tag -> tag.name }) }
                    SignalContent(record, zone, full = true)
                    record.memory?.let { marker ->
                        MemoryStatus(marker)
                        marker.note?.takeUnless { it == body }?.let { Text(it) }
                    }
                    LinkedPeople(record.people); LinkedPlace(record.place)
                    PhotoGallery(photos, record.photos)
                    if (record.canPromoteToMemory) TextButton(onClick = onMemory, modifier = Modifier.testTag("event_promote")) {
                        Text(stringResource(if (record.memory == null) R.string.memory_promote else R.string.memory_edit))
                    }
                }
            }
        }
    }
}
