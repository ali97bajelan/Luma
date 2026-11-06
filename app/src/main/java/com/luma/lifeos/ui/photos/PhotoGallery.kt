package com.luma.lifeos.ui.photos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.luma.lifeos.R
import com.luma.lifeos.domain.model.PhotoAttachment
import com.luma.lifeos.domain.model.PhotoLimits
import com.luma.lifeos.domain.model.PhotoReadScope
import com.luma.lifeos.domain.repository.PhotoRepository
import com.luma.lifeos.ui.common.AccessibleFeedback
import com.luma.lifeos.ui.common.LumaListSurface
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PhotoEditor(repository: PhotoRepository, photos: List<PhotoAttachment>, enabled: Boolean,
    onImport: (String) -> Unit, onRemove: (UUID) -> Unit, onRetry: () -> Unit, importFailed: Boolean,
    operationFailed: Boolean = false) {
    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri -> uri?.let { onImport(it.toString()) } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.photos), style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }.testTag("photo_heading"))
        Text(stringResource(R.string.photo_limits), style = MaterialTheme.typography.bodySmall)
        TextButton(enabled = enabled && photos.size < PhotoLimits.COUNT, onClick = {
            picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
        }, modifier = Modifier.testTag("photo_add")) { Text(stringResource(R.string.photo_add)) }
        if (importFailed) {
            AccessibleFeedback(stringResource(R.string.photo_error), Modifier.testTag("photo_import_error"))
            TextButton(enabled = enabled, onClick = onRetry, modifier = Modifier.testTag("photo_retry_import")) {
                Text(stringResource(R.string.photo_retry_import))
            }
        } else if (operationFailed) {
            AccessibleFeedback(stringResource(R.string.photo_error), Modifier.testTag("photo_operation_error"))
        }
        PhotoGallery(repository, photos, enabled, onRemove)
    }
}

@Composable
fun PhotoGallery(repository: PhotoRepository, photos: List<PhotoAttachment>, enabled: Boolean = true,
    onRemove: ((UUID) -> Unit)? = null, onOpen: ((UUID) -> Unit)? = null,
    readScope: PhotoReadScope? = null, accessEpoch: Any? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        photos.forEachIndexed { index, photo ->
            key(photo.id) {
                LumaListSurface(Modifier.testTag("photo_card_${photo.id}")) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PhotoPreview(
                            repository,
                            photo,
                            index + 1,
                            enabled = enabled,
                            onOpen = onOpen?.let { open -> { open(photo.id) } },
                            readScope = readScope,
                            accessEpoch = accessEpoch,
                        )
                        onRemove?.let { remove ->
                            TextButton(enabled = enabled, onClick = { remove(photo.id) }, modifier = Modifier.testTag("photo_remove_${photo.id}")) {
                                Text(stringResource(R.string.photo_remove))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoPreview(
    repository: PhotoRepository,
    photo: PhotoAttachment,
    number: Int,
    enabled: Boolean = true,
    onOpen: (() -> Unit)? = null,
    readScope: PhotoReadScope? = null,
    accessEpoch: Any? = null,
) {
    var preview by remember(repository, photo, readScope) { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(true) }
    var retry by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val previewToRelease = preview
    DisposableEffect(previewToRelease) {
        onDispose { previewToRelease?.takeUnless(Bitmap::isRecycled)?.recycle() }
    }
    LaunchedEffect(repository, photo, readScope, accessEpoch, lifecycle, retry) {
        preview = null
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            loading = true
            var bytes: ByteArray? = null
            var decoded: Bitmap? = null
            try {
                bytes = if (readScope == null) repository.preview(photo) else repository.preview(photo, readScope)
                decoded = withContext(Dispatchers.Default) {
                    bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                }
                preview = decoded
                decoded = null
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                preview = null
            } finally {
                bytes?.fill(0)
                decoded?.takeUnless(Bitmap::isRecycled)?.recycle()
                loading = false
            }
        }
    }
    val openLabel = if (onOpen != null) stringResource(R.string.photo_open) else null
    val openModifier = if (onOpen != null) {
        Modifier.clickable(
            enabled = enabled,
            onClickLabel = openLabel,
            role = Role.Button,
        ) { onOpen() }
    } else Modifier
    Box(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).then(openModifier)
            .testTag(if (onOpen != null) "photo_open_${photo.id}" else "photo_preview_${photo.id}"),
    ) {
        if (loading) CircularProgressIndicator()
        else preview?.let { bitmap ->
            Image(
                bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.photo_number, number),
                modifier = Modifier.fillMaxWidth().height(240.dp).testTag("photo_preview_${photo.id}"),
                contentScale = ContentScale.Fit,
            )
        } ?: Column(Modifier.testTag("photo_unavailable_${photo.id}")) {
            AccessibleFeedback(stringResource(R.string.photo_unavailable), Modifier.testTag("photo_preview_error"))
            TextButton(onClick = { retry++ }) { Text(stringResource(R.string.retry)) }
        }
    }
}

@Composable
fun PhotoCount(count: Int) {
    if (count > 0) Text(pluralStringResource(R.plurals.photo_count, count, count), style = MaterialTheme.typography.labelMedium)
}
