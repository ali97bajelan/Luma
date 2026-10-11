package com.luma.lifeos.ui.photos

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.luma.lifeos.R
import com.luma.lifeos.domain.model.PhotoAttachment
import com.luma.lifeos.domain.model.PhotoLimits
import com.luma.lifeos.domain.repository.PhotoRepository
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PhotoEditor(repository: PhotoRepository, photos: List<PhotoAttachment>, enabled: Boolean,
    onImport: (String) -> Unit, onRemove: (UUID) -> Unit, onRetry: () -> Unit, importFailed: Boolean) {
    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri -> uri?.let { onImport(it.toString()) } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.photos), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.photo_limits), style = MaterialTheme.typography.bodySmall)
        TextButton(enabled = enabled && photos.size < PhotoLimits.COUNT, onClick = {
            picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
        }, modifier = Modifier.testTag("photo_add")) { Text(stringResource(R.string.photo_add)) }
        if (importFailed) TextButton(enabled = enabled, onClick = onRetry) { Text(stringResource(R.string.photo_retry_import)) }
        PhotoGallery(repository, photos, enabled, onRemove)
    }
}

@Composable
fun PhotoGallery(repository: PhotoRepository, photos: List<PhotoAttachment>, enabled: Boolean = true,
    onRemove: ((UUID) -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        photos.forEachIndexed { index, photo ->
            key(photo.id) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PhotoPreview(repository, photo, index + 1)
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
private fun PhotoPreview(repository: PhotoRepository, photo: PhotoAttachment, number: Int) {
    var preview by remember(repository, photo) { mutableStateOf<ImageBitmap?>(null) }
    var loading by remember { mutableStateOf(true) }
    var retry by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(repository, photo, lifecycle, retry) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            loading = true
            try {
                val bytes = repository.preview(photo)
                preview = withContext(Dispatchers.Default) {
                    bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                preview = null
            } finally { loading = false }
        }
    }
    if (loading) CircularProgressIndicator()
    else preview?.let { bitmap ->
        Image(bitmap, contentDescription = stringResource(R.string.photo_number, number),
            modifier = Modifier.fillMaxWidth().height(240.dp).testTag("photo_preview_${photo.id}"), contentScale = ContentScale.Fit)
    } ?: Column(Modifier.testTag("photo_unavailable_${photo.id}")) {
        Text(stringResource(R.string.photo_unavailable))
        TextButton(onClick = { retry++ }) { Text(stringResource(R.string.retry)) }
    }
}

@Composable
fun PhotoCount(count: Int) {
    if (count > 0) Text(pluralStringResource(R.plurals.photo_count, count, count), style = MaterialTheme.typography.labelMedium)
}
