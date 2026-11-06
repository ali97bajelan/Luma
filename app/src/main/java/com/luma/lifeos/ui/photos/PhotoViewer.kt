package com.luma.lifeos.ui.photos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.luma.lifeos.R
import com.luma.lifeos.domain.model.PhotoAttachment
import com.luma.lifeos.domain.model.PhotoDisplayRequest
import com.luma.lifeos.domain.model.PhotoReadScope
import com.luma.lifeos.domain.model.PhotoWorkingLimits
import com.luma.lifeos.domain.repository.PhotoRepository
import com.luma.lifeos.ui.common.AccessibleFeedback
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

@Composable
fun PhotoViewer(
    repository: PhotoRepository,
    photos: List<PhotoAttachment>,
    initialId: UUID,
    readScope: PhotoReadScope,
    onDismiss: () -> Unit,
    accessReady: Boolean = true,
    accessFailed: Boolean = false,
    photosReady: Boolean = true,
    onRetryAccess: () -> Unit = {},
) {
    val state = rememberPhotoViewerState(initialId)
    val selectedIndex = photos.indexOfFirst { it.id == state.selectedId }
    val selectedPhoto = photos.getOrNull(selectedIndex)
    val photoIds = remember(photos) { photos.map(PhotoAttachment::id) }

    LaunchedEffect(photoIds, state.selectedId, photosReady) {
        if (photosReady && (state.selectedId == null || selectedIndex < 0)) onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window
            val wasSecure = ((window?.attributes?.flags ?: 0) and WindowManager.LayoutParams.FLAG_SECURE) != 0
            window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            onDispose {
                if (!wasSecure) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize().testTag("photo_viewer"),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                var imageReady by remember(selectedPhoto?.id) { mutableStateOf(false) }
                val closeDescription = stringResource(R.string.photo_viewer_close)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("photo_viewer_close")
                            .semantics { contentDescription = closeDescription },
                    ) {
                        Text("×", fontSize = 28.sp, modifier = Modifier.clearAndSetSemantics {})
                    }
                    Text(
                        text = if (selectedIndex >= 0) {
                            stringResource(R.string.photo_viewer_position, selectedIndex + 1, photos.size)
                        } else if (!photosReady) {
                            stringResource(R.string.photo_viewer_loading)
                        } else {
                            stringResource(R.string.photo_viewer_unavailable)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }

                BoxWithConstraints(
                    modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds(),
                    contentAlignment = Alignment.Center,
                ) {
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    val viewportWidth = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
                    val viewportHeight = with(density) { maxHeight.roundToPx() }.coerceAtLeast(1)
                    var retry by remember(selectedPhoto?.id) { mutableIntStateOf(0) }
                    var decodeZoom by remember(selectedPhoto?.id) { mutableFloatStateOf(state.zoom) }
                    LaunchedEffect(selectedPhoto?.id, state.zoom) {
                        delay(160)
                        decodeZoom = state.zoom
                    }
                    val request = remember(viewportWidth, viewportHeight, decodeZoom) {
                        PhotoDisplayRequest(viewportWidth, viewportHeight, decodeZoom.coerceIn(1f, 4f))
                    }
                    var bitmap by remember(selectedPhoto?.id, retry) { mutableStateOf<Bitmap?>(null) }
                    var loading by remember(selectedPhoto?.id, request, retry, accessReady) { mutableStateOf(true) }
                    val lifecycle = LocalLifecycleOwner.current.lifecycle

                    val bitmapToRelease = bitmap
                    DisposableEffect(bitmapToRelease) {
                        onDispose { bitmapToRelease?.takeUnless(Bitmap::isRecycled)?.recycle() }
                    }
                    SideEffect { imageReady = bitmap != null && accessReady && photosReady }

                    LaunchedEffect(selectedPhoto?.id, request, readScope, retry, accessReady, lifecycle) {
                        if (selectedPhoto == null || !accessReady) {
                            bitmap = null
                            loading = false
                            return@LaunchedEffect
                        }
                        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                            loading = true
                            try {
                                var encoded: ByteArray? = null
                                var decoded: Bitmap? = null
                                try {
                                    encoded = withContext(Dispatchers.IO) {
                                        repository.display(selectedPhoto, request, readScope)
                                    }
                                    decoded = withContext(Dispatchers.Default) {
                                        encoded?.let { bytes ->
                                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.also { image ->
                                                val pixels = image.width.toLong() * image.height
                                                if (pixels > PhotoWorkingLimits.PIXELS ||
                                                    max(image.width, image.height) > PhotoWorkingLimits.EDGE
                                                ) {
                                                    image.recycle()
                                                    error("Photo display exceeded its working-image budget")
                                                }
                                                try {
                                                    currentCoroutineContext().ensureActive()
                                                } catch (failure: CancellationException) {
                                                    image.recycle()
                                                    throw failure
                                                }
                                            }
                                        }
                                    }
                                    currentCoroutineContext().ensureActive()
                                    if (state.selectedId == selectedPhoto.id) {
                                        bitmap = decoded
                                        decoded = null
                                    }
                                } finally {
                                    encoded?.fill(0)
                                    decoded?.takeUnless(Bitmap::isRecycled)?.recycle()
                                }
                                loading = false
                                awaitCancellation()
                            } catch (failure: CancellationException) {
                                bitmap = null
                                loading = true
                                throw failure
                            } catch (_: Exception) {
                                bitmap = null
                                loading = false
                            }
                        }
                    }

                    when {
                        !photosReady -> Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator()
                            Text(stringResource(R.string.photo_viewer_loading))
                        }
                        accessFailed -> PhotoUnavailable(
                            onRetry = { retry++; onRetryAccess() },
                            onDismiss = onDismiss,
                        )
                        !accessReady || (loading && bitmap == null) -> Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator()
                            Text(stringResource(R.string.photo_viewer_loading))
                        }
                        bitmap == null -> PhotoUnavailable(
                            onRetry = { retry++; onRetryAccess() },
                            onDismiss = onDismiss,
                        )
                        else -> {
                            val image = requireNotNull(bitmap)
                            val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                                state.transformTo(
                                    nextZoom = state.zoom * zoomChange,
                                    panDeltaX = panChange.x,
                                    panDeltaY = panChange.y,
                                    imageWidth = image.width,
                                    imageHeight = image.height,
                                    viewportWidth = viewportWidth,
                                    viewportHeight = viewportHeight,
                                )
                            }
                            val fitScale = min(
                                viewportWidth.toFloat() / image.width,
                                viewportHeight.toFloat() / image.height,
                            )
                            val fitWidth = with(density) { (image.width * fitScale).toDp() }
                            val fitHeight = with(density) { (image.height * fitScale).toDp() }
                            val panLimitX = max(0f, (image.width * fitScale * state.zoom - viewportWidth) / 2f)
                            val panLimitY = max(0f, (image.height * fitScale * state.zoom - viewportHeight) / 2f)
                            val boundedPanX = state.panX.coerceIn(-panLimitX, panLimitX)
                            val boundedPanY = state.panY.coerceIn(-panLimitY, panLimitY)
                            SideEffect {
                                state.updateViewport(image.width, image.height, viewportWidth, viewportHeight)
                            }
                            val imageDescription = stringResource(R.string.photo_number, selectedIndex + 1)
                            val gestureModifier = Modifier
                                .pointerInput(selectedPhoto?.id, viewportWidth, viewportHeight, image.width, image.height) {
                                    detectTapGestures(onDoubleTap = {
                                        val target = if (state.zoom > 1.01f) 1f else 2f
                                        state.transformTo(
                                            nextZoom = target,
                                            panDeltaX = 0f,
                                            panDeltaY = 0f,
                                            imageWidth = image.width,
                                            imageHeight = image.height,
                                            viewportWidth = viewportWidth,
                                            viewportHeight = viewportHeight,
                                        )
                                    })
                                }
                                .transformable(transformState)
                            Image(
                                bitmap = image.asImageBitmap(),
                                contentDescription = imageDescription,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(fitWidth, fitHeight)
                                    .graphicsLayer {
                                        scaleX = state.zoom
                                        scaleY = state.zoom
                                        translationX = boundedPanX
                                        translationY = boundedPanY
                                    }
                                    .then(gestureModifier)
                                    .testTag("photo_viewer_image_${selectedPhoto?.id}"),
                            )
                        }
                    }
                }

                val previousDescription = stringResource(R.string.photo_viewer_previous)
                val nextDescription = stringResource(R.string.photo_viewer_next)
                val zoomOutDescription = stringResource(R.string.photo_viewer_zoom_out)
                val zoomInDescription = stringResource(R.string.photo_viewer_zoom_in)
                val resetDescription = stringResource(R.string.photo_viewer_reset)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ViewerControl(
                        symbol = if (LocalLayoutDirection.current == LayoutDirection.Rtl) "›" else "‹",
                        description = previousDescription,
                        enabled = selectedIndex > 0,
                        tag = "photo_viewer_previous",
                    ) {
                        photos.getOrNull(selectedIndex - 1)?.let { state.select(it.id) }
                    }
                    ViewerControl(
                        symbol = "−",
                        description = zoomOutDescription,
                        enabled = imageReady && state.zoom > 1f,
                        tag = "photo_viewer_zoom_out",
                    ) {
                        state.setZoomFromControl(max(1f, state.zoom - 0.5f))
                    }
                    ViewerControl(
                        symbol = "+",
                        description = zoomInDescription,
                        enabled = imageReady && state.zoom < 4f,
                        tag = "photo_viewer_zoom_in",
                    ) {
                        state.setZoomFromControl(min(4f, state.zoom + 0.5f))
                    }
                    ViewerControl(
                        symbol = "1:1",
                        description = resetDescription,
                        enabled = imageReady && (state.zoom != 1f || state.panX != 0f || state.panY != 0f),
                        tag = "photo_viewer_reset",
                        onClick = state::reset,
                    )
                    ViewerControl(
                        symbol = if (LocalLayoutDirection.current == LayoutDirection.Rtl) "‹" else "›",
                        description = nextDescription,
                        enabled = selectedIndex >= 0 && selectedIndex < photos.lastIndex,
                        tag = "photo_viewer_next",
                    ) {
                        photos.getOrNull(selectedIndex + 1)?.let { state.select(it.id) }
                    }
                }

                val panLeftDescription = stringResource(R.string.photo_viewer_pan_left)
                val panRightDescription = stringResource(R.string.photo_viewer_pan_right)
                val panUpDescription = stringResource(R.string.photo_viewer_pan_up)
                val panDownDescription = stringResource(R.string.photo_viewer_pan_down)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ViewerControl("←", panLeftDescription, imageReady && state.canPanLeft, "photo_viewer_pan_left") {
                        state.panByViewportFraction(-0.25f, 0f)
                    }
                    ViewerControl("↑", panUpDescription, imageReady && state.canPanUp, "photo_viewer_pan_up") {
                        state.panByViewportFraction(0f, -0.25f)
                    }
                    ViewerControl("↓", panDownDescription, imageReady && state.canPanDown, "photo_viewer_pan_down") {
                        state.panByViewportFraction(0f, 0.25f)
                    }
                    ViewerControl("→", panRightDescription, imageReady && state.canPanRight, "photo_viewer_pan_right") {
                        state.panByViewportFraction(0.25f, 0f)
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoUnavailable(onRetry: () -> Unit, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AccessibleFeedback(
            stringResource(R.string.photo_viewer_unavailable),
            Modifier.testTag("photo_viewer_unavailable"),
        )
        TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp).testTag("photo_viewer_retry")) {
            Text(stringResource(R.string.photo_viewer_retry))
        }
        TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp).testTag("photo_viewer_close_unavailable")) {
            Text(stringResource(R.string.photo_viewer_close))
        }
    }
}

@Composable
private fun ViewerControl(
    symbol: String,
    description: String,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    IconButton(
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .testTag(tag)
            .semantics { contentDescription = description },
    ) {
        Text(symbol, modifier = Modifier.clearAndSetSemantics {})
    }
}
