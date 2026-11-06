package com.luma.lifeos.ui.memory

import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.luma.lifeos.R
import com.luma.lifeos.domain.model.PhotoAttachment
import com.luma.lifeos.domain.model.PhotoLimits
import com.luma.lifeos.domain.model.PhotoReadScope
import com.luma.lifeos.domain.repository.PhotoRepository
import com.luma.lifeos.ui.photos.PhotoEditScreen
import com.luma.lifeos.ui.photos.PhotoGallery
import com.luma.lifeos.ui.photos.PhotoViewer
import com.luma.lifeos.ui.photos.messageResource
import java.util.UUID

@Composable
internal fun MemoryPhotoEditor(
    repository: PhotoRepository,
    photos: List<PhotoAttachment>,
    readScope: PhotoReadScope,
    enabled: Boolean,
    photoState: MemoryPhotoEditState,
    accessEpoch: Any? = null,
    onPickerActiveChange: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
    onRemove: (UUID) -> Unit,
    onCrop: (com.luma.lifeos.domain.model.PhotoCrop) -> Unit,
    onRotate: () -> Unit,
    onReset: () -> Unit,
    onConfirm: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onRegisterViewer: (UUID) -> Boolean,
    onPrepareViewer: suspend (UUID, List<PhotoAttachment>) -> Boolean,
    onCloseViewer: (UUID) -> Unit,
) {
    var pickerActive by rememberSaveable { mutableStateOf(false) }
    fun setPickerActive(active: Boolean) {
        pickerActive = active
        onPickerActiveChange(active)
    }
    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        setPickerActive(false)
        uri?.let { onSelect(it.toString()) }
    }
    DisposableEffect(Unit) {
        onDispose { onPickerActiveChange(false) }
    }

    var viewerPhotoId by rememberSaveable { mutableStateOf<String?>(null) }
    var viewerLeaseId by rememberSaveable { mutableStateOf<String?>(null) }
    var viewerReady by remember { mutableStateOf(false) }
    var viewerFailed by remember { mutableStateOf(false) }
    var viewerAttempt by rememberSaveable { mutableIntStateOf(0) }

    val canEditPhotos = enabled && photoState is MemoryPhotoEditState.Idle && !pickerActive
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.photos), style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }.testTag("photo_heading"))
        Text(stringResource(R.string.photo_limits), style = MaterialTheme.typography.bodySmall)
        TextButton(
            enabled = canEditPhotos && photos.size < PhotoLimits.COUNT,
            onClick = {
                if (!pickerActive && photoState is MemoryPhotoEditState.Idle) {
                    setPickerActive(true)
                    picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                }
            },
            modifier = Modifier.testTag("photo_add").heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.photo_add)) }
        PhotoGallery(
            repository = repository,
            photos = photos,
            enabled = canEditPhotos,
            onRemove = onRemove,
            onOpen = { photoId ->
                val leaseId = UUID.randomUUID()
                viewerPhotoId = photoId.toString()
                viewerLeaseId = leaseId.toString()
                viewerReady = false
                viewerFailed = !onRegisterViewer(leaseId)
                viewerAttempt = 0
            },
            readScope = readScope,
            accessEpoch = accessEpoch,
        )
    }

    when (photoState) {
        MemoryPhotoEditState.Idle -> Unit
        is MemoryPhotoEditState.Importing -> PhotoEditProgressDialog(
            message = R.string.memory_photo_edit_importing,
            onCancel = onCancel,
        )
        is MemoryPhotoEditState.Applying -> PhotoEditProgressDialog(
            message = R.string.memory_photo_edit_applying,
            canCancel = !photoState.attaching,
            onCancel = onCancel,
        )
        is MemoryPhotoEditState.Editing -> PhotoEditScreen(
            repository = repository,
            stagingLeaseId = photoState.stagingLeaseId,
            source = photoState.source,
            edit = photoState.edit,
            accessEpoch = accessEpoch,
            onCrop = onCrop,
            onRotate = onRotate,
            onReset = onReset,
            onApply = { onConfirm(false) },
            onKeepOriginal = { onConfirm(true) },
            onCancel = onCancel,
            onRetry = onRetry,
        )
        is MemoryPhotoEditState.Failed -> {
            val source = photoState.source
            if (source != null) {
                PhotoEditScreen(
                    repository = repository,
                    stagingLeaseId = photoState.stagingLeaseId,
                    source = source,
                    edit = photoState.edit,
                    failure = photoState.error,
                    accessEpoch = accessEpoch,
                    onCrop = onCrop,
                    onRotate = onRotate,
                    onReset = onReset,
                    onApply = { onConfirm(false) },
                    onKeepOriginal = { onConfirm(true) },
                    onCancel = onCancel,
                    onRetry = onRetry,
                )
            } else {
                PhotoImportFailureDialog(
                    error = photoState.error,
                    canRetry = photoState.sourceUri != null,
                    onRetry = onRetry,
                    onSelectAgain = {
                        if (!pickerActive) {
                            setPickerActive(true)
                            picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                        }
                    },
                    onCancel = onCancel,
                )
            }
        }
    }

    val photoIds = remember(photos) { photos.map(PhotoAttachment::id) }
    LaunchedEffect(viewerPhotoId, viewerLeaseId, photoIds, accessEpoch, viewerAttempt) {
        val leaseId = viewerLeaseId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        if (viewerPhotoId == null || leaseId == null) return@LaunchedEffect
        viewerReady = false
        viewerFailed = false
        if (!onRegisterViewer(leaseId)) {
            viewerFailed = true
            return@LaunchedEffect
        }
        viewerReady = onPrepareViewer(leaseId, photos)
        viewerFailed = !viewerReady
    }
    val parsedViewerLease = viewerLeaseId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    val parsedViewerPhoto = viewerPhotoId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    if (parsedViewerPhoto != null && parsedViewerLease != null && photos.any { it.id == parsedViewerPhoto }) {
        PhotoViewer(
            repository = repository,
            photos = photos,
            initialId = parsedViewerPhoto,
            readScope = PhotoReadScope.Draft(parsedViewerLease),
            onDismiss = {
                onCloseViewer(parsedViewerLease)
                viewerPhotoId = null
                viewerLeaseId = null
                viewerReady = false
                viewerFailed = false
            },
            accessReady = viewerReady,
            accessFailed = viewerFailed,
            onRetryAccess = { viewerAttempt++ },
        )
    } else if (viewerPhotoId != null && parsedViewerLease != null) {
        LaunchedEffect(viewerPhotoId, viewerLeaseId) {
            onCloseViewer(parsedViewerLease)
            viewerPhotoId = null
            viewerLeaseId = null
        }
    }
}

@Composable
private fun PhotoEditProgressDialog(message: Int, canCancel: Boolean = true, onCancel: () -> Unit) {
    BackHandler(enabled = canCancel) { onCancel() }
    SecurePhotoDialog(onDismiss = if (canCancel) onCancel else ({}), contentTag = "memory_photo_edit",
        dismissOnBackPress = canCancel) {
        CircularProgressIndicator()
        Text(stringResource(message), style = MaterialTheme.typography.titleMedium)
        if (canCancel) TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp).testTag("photo_edit_cancel")) {
            Text(stringResource(R.string.photo_edit_cancel))
        }
    }
}

@Composable
private fun PhotoImportFailureDialog(
    error: MemoryPhotoEditError,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onSelectAgain: () -> Unit,
    onCancel: () -> Unit,
) {
    BackHandler { onCancel() }
    SecurePhotoDialog(onDismiss = onCancel, contentTag = "memory_photo_edit") {
        Text(stringResource(error.messageResource()), style = MaterialTheme.typography.titleMedium)
        if (canRetry) TextButton(onClick = onRetry, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("photo_edit_retry")) {
            Text(stringResource(R.string.photo_edit_retry))
        }
        TextButton(onClick = onSelectAgain, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .testTag("photo_edit_select_again")) {
            Text(stringResource(R.string.photo_edit_select_again))
        }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("photo_edit_cancel")) {
            Text(stringResource(R.string.photo_edit_cancel))
        }
    }
}

@Composable
private fun SecurePhotoDialog(
    onDismiss: () -> Unit,
    contentTag: String,
    dismissOnBackPress: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = dismissOnBackPress, dismissOnClickOutside = false, usePlatformDefaultWidth = false),
    ) {
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window
            val wasSecure = ((window?.attributes?.flags ?: 0) and WindowManager.LayoutParams.FLAG_SECURE) != 0
            window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            onDispose { if (!wasSecure) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
        }
        Surface(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).testTag(contentTag),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                content = content,
            )
        }
    }
}
