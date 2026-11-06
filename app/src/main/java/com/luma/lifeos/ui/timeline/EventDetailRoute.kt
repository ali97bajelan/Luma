package com.luma.lifeos.ui.timeline

import android.app.Activity
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luma.lifeos.R
import com.luma.lifeos.domain.model.*
import com.luma.lifeos.domain.repository.PhotoRepository
import com.luma.lifeos.domain.repository.TimelineRepository
import com.luma.lifeos.lock.PhotoDraftAccessRegistry
import com.luma.lifeos.ui.memory.MemoryStatus
import com.luma.lifeos.ui.common.AccessibleFeedback
import com.luma.lifeos.ui.common.DetailTopBar
import com.luma.lifeos.ui.common.LumaSectionSurface
import com.luma.lifeos.ui.people.LinkedPeople
import com.luma.lifeos.ui.places.LinkedPlace
import com.luma.lifeos.ui.photos.PhotoGallery
import com.luma.lifeos.ui.photos.PhotoViewer
import com.luma.lifeos.ui.photos.PhotoViewerAccessEpoch
import java.time.ZoneId
import java.time.format.FormatStyle
import com.luma.lifeos.ui.common.LumaReadingPane
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

/** Release work outlives the Activity that owns this route; config changes keep the lease. */
private val photoLeaseCleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

@Composable
fun EventDetailRoute(repository: TimelineRepository, photos: PhotoRepository, id: EventId, zone: ZoneId,
    onBack: () -> Unit, onEdit: (LifeEventRecord) -> Unit, onMemory: () -> Unit,
    onShare: (com.luma.lifeos.sharing.EntryShareKind) -> Unit = {},
    photoDraftAccessRegistry: PhotoDraftAccessRegistry? = null,
    photoAccessEpoch: PhotoViewerAccessEpoch? = null) {
    var retry by remember { mutableIntStateOf(0) }
    val flow = remember(repository, id, retry) { observeEventDetail(repository, id) }
    val state by flow.collectAsStateWithLifecycle(initialValue = EventDetailState.Loading)
    val displayTime = com.luma.lifeos.language.rememberDisplayTimePolicy()
    val currentRecord = (state as? EventDetailState.Data)?.record
    val activity = LocalContext.current.findActivity()
    var viewerInitialId by rememberSaveable(id) { mutableStateOf<String?>(null) }
    var viewerLeaseId by rememberSaveable(id) { mutableStateOf<String?>(null) }
    var viewerLeaseRetry by remember { mutableIntStateOf(0) }
    var observedPhotoAccessEpoch by remember { mutableStateOf(photoAccessEpoch) }
    var viewerLeaseReady by remember(viewerLeaseId, photoDraftAccessRegistry) {
        mutableStateOf(viewerLeaseId == null || photoDraftAccessRegistry == null)
    }
    var viewerLeaseFailed by remember(viewerLeaseId, photoDraftAccessRegistry) { mutableStateOf(false) }
    val photoAccessIsCurrent = observedPhotoAccessEpoch == photoAccessEpoch

    fun dismissPhotoViewer() {
        viewerInitialId = null
        viewerLeaseId = null
    }

    fun openPhoto(photoId: UUID) {
        if (viewerInitialId != null) return
        viewerLeaseReady = photoDraftAccessRegistry == null
        viewerLeaseFailed = false
        viewerLeaseId = if (photoDraftAccessRegistry == null) null else UUID.randomUUID().toString()
        viewerInitialId = photoId.toString()
    }

    BackHandler {
        if (viewerInitialId != null) dismissPhotoViewer() else onBack()
    }
    LaunchedEffect(photoAccessEpoch) {
        if (observedPhotoAccessEpoch != photoAccessEpoch) dismissPhotoViewer()
        observedPhotoAccessEpoch = photoAccessEpoch
    }
    LaunchedEffect(viewerInitialId, state) {
        if (viewerInitialId == null) return@LaunchedEffect
        when (val current = state) {
            EventDetailState.Loading -> Unit
            EventDetailState.Missing, EventDetailState.Error -> dismissPhotoViewer()
            is EventDetailState.Data -> if (
                current.record.memory == null && current.record.event.type != EventType.MEMORY
            ) dismissPhotoViewer()
        }
    }
    LaunchedEffect(viewerLeaseId, currentRecord?.event?.id, currentRecord?.photos, photoDraftAccessRegistry, viewerLeaseRetry, state) {
        val lease = viewerLeaseId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val registry = photoDraftAccessRegistry
        val record = currentRecord
        if (lease == null || registry == null) {
            viewerLeaseReady = true
            viewerLeaseFailed = false
            return@LaunchedEffect
        }
        if (record == null) {
            viewerLeaseReady = false
            viewerLeaseFailed = state !is EventDetailState.Loading
            return@LaunchedEffect
        }
        if (viewerInitialId == null) {
            viewerLeaseReady = false
            viewerLeaseFailed = false
            return@LaunchedEffect
        }
        viewerLeaseReady = false
        viewerLeaseFailed = false
        val registration = try {
            registry.open(lease, record.event.id)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            viewerLeaseFailed = true
            return@LaunchedEffect
        }
        try {
            photos.retainDraft(lease, record.photos)
            if (viewerLeaseId == lease.toString()) viewerLeaseReady = true
            awaitCancellation()
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            viewerLeaseFailed = true
        } finally {
            registration.close()
            if (viewerLeaseId == lease.toString()) viewerLeaseReady = false
        }
    }
    DisposableEffect(viewerLeaseId, activity, photos) {
        val lease = viewerLeaseId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        onDispose {
            // A recreated route reuses and reauthorizes its saved lease ID.
            if (lease != null && activity?.isChangingConfigurations != true) {
                photoLeaseCleanupScope.launch {
                    try {
                        photos.releaseDraft(lease)
                        photos.cleanup()
                    } catch (failure: CancellationException) {
                        throw failure
                    } catch (_: Exception) {
                        // Cleanup is best-effort; the persistent owner reference remains protected.
                    }
                }
            }
        }
    }

    val recordForEdit = currentRecord?.takeIf { it.canEditContent }
    val title = (state as? EventDetailState.Data)?.record?.event?.type?.label()?.let { stringResource(it) }
        ?: stringResource(R.string.event_detail_title)
    val editLabel = recordForEdit?.let { record ->
        stringResource(when (record.event.type) {
            EventType.MOOD -> R.string.mood_edit_action
            EventType.JOURNAL -> R.string.memory_edit
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
        LumaReadingPane(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)
            .testTag("event_detail_content"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (val current = state) {
                    EventDetailState.Loading -> CircularProgressIndicator()
                    EventDetailState.Missing -> Text(stringResource(R.string.event_missing), modifier = Modifier.testTag("event_missing"))
                    EventDetailState.Error -> {
                        AccessibleFeedback(stringResource(R.string.event_detail_error), Modifier.testTag("event_detail_error"))
                        TextButton(onClick = { retry++ }) { Text(stringResource(R.string.retry)) }
                    }
                    is EventDetailState.Data -> {
                        val record = current.record
                        val shareKind = when {
                            record.event.type == EventType.MOOD && record.mood != null -> com.luma.lifeos.sharing.EntryShareKind.MOOD
                            record.event.type == EventType.MEMORY && record.memory != null -> com.luma.lifeos.sharing.EntryShareKind.MEMORY
                            else -> null
                        }
                        if (shareKind != null) TextButton(onClick = { onShare(shareKind) }) { Text(stringResource(R.string.share_action)) }
                        LumaSectionSurface(modifier = Modifier.testTag("event_detail_record")) {
                            val groupedUsageDetails = record.appUsage != null
                            if (!groupedUsageDetails) {
                                Text(record.event.title ?: stringResource(record.event.type.label()), style = MaterialTheme.typography.headlineMedium)
                                Text(stringResource(record.event.source.label()))
                            }
                            if (!record.canEditContent) Text(stringResource(R.string.event_read_only))
                            if (!groupedUsageDetails) {
                                val formatter = displayTime.dateTime(FormatStyle.MEDIUM)
                                Text(record.event.occurredAt.atZone(zone).format(formatter))
                                record.event.endedAt?.let { Text(stringResource(R.string.event_ended, it.atZone(zone).format(formatter))) }
                                Text(zone.id, style = MaterialTheme.typography.bodySmall)
                            }
                            record.mood?.let { MoodContent(it) }
                            val body = record.event.note ?: record.mood?.note ?: record.memory?.note
                            body?.let { Text(it, modifier = Modifier.testTag("event_detail_body")) }
                            record.tags.takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(", ") { tag -> tag.name }) }
                            SignalContent(record, zone, full = true)
                            record.memory?.let { marker ->
                                MemoryStatus(marker)
                                marker.note?.takeUnless { it == body }?.let { Text(it) }
                            }
                            LinkedPeople(record.people); LinkedPlace(record.place)
                            val canOpenPhotos = record.memory != null || record.event.type == EventType.MEMORY
                            PhotoGallery(
                                photos,
                                record.photos,
                                onOpen = if (canOpenPhotos) ::openPhoto else null,
                            )
                            if (record.canPromoteToMemory) TextButton(onClick = onMemory, modifier = Modifier.testTag("event_promote")) {
                                Text(stringResource(if (record.memory == null) R.string.memory_promote else R.string.memory_edit))
                            }
                        }
                    }
                }
            }
        }
    }

    val viewerId = viewerInitialId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    val viewerRecord = currentRecord?.takeIf { it.memory != null || it.event.type == EventType.MEMORY }
    if (viewerId != null && (viewerRecord != null || state is EventDetailState.Loading) && photoAccessIsCurrent) {
        val leaseId = viewerLeaseId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val useDraftScope = leaseId != null && photoDraftAccessRegistry != null
        PhotoViewer(
            repository = photos,
            photos = viewerRecord?.photos.orEmpty(),
            initialId = viewerId,
            readScope = if (useDraftScope) PhotoReadScope.Draft(requireNotNull(leaseId)) else PhotoReadScope.Saved,
            onDismiss = ::dismissPhotoViewer,
            accessReady = !useDraftScope || viewerLeaseReady,
            accessFailed = useDraftScope && viewerLeaseFailed,
            photosReady = viewerRecord != null,
            onRetryAccess = { viewerLeaseRetry++ },
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
