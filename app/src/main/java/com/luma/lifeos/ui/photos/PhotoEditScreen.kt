package com.luma.lifeos.ui.photos

import com.luma.lifeos.ui.common.LumaUi
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.luma.lifeos.ui.common.LumaTextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.luma.lifeos.R
import com.luma.lifeos.domain.model.PhotoAttachment
import com.luma.lifeos.domain.model.PhotoCrop
import com.luma.lifeos.domain.model.PhotoDisplayRequest
import com.luma.lifeos.domain.model.PhotoEdit
import com.luma.lifeos.domain.model.PhotoReadScope
import com.luma.lifeos.domain.model.PhotoWorkingLimits
import com.luma.lifeos.domain.repository.PhotoRepository
import com.luma.lifeos.ui.memory.MemoryPhotoEditError
import com.luma.lifeos.ui.common.AccessibleFeedback
import java.util.UUID
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun PhotoEditScreen(
    repository: PhotoRepository,
    stagingLeaseId: UUID,
    source: PhotoAttachment,
    edit: PhotoEdit,
    failure: MemoryPhotoEditError? = null,
    accessEpoch: Any? = null,
    onCrop: (PhotoCrop) -> Unit,
    onRotate: () -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
    onKeepOriginal: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    var retry by remember(source.id, stagingLeaseId) { mutableIntStateOf(0) }
    val cropSummary = stringResource(
        R.string.photo_crop_summary,
        (edit.crop.left * 100).roundToInt(),
        (edit.crop.top * 100).roundToInt(),
        (edit.crop.right * 100).roundToInt(),
        (edit.crop.bottom * 100).roundToInt(),
    )
    BackHandler { onCancel() }
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false, usePlatformDefaultWidth = false),
    ) {
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window
            val wasSecure = ((window?.attributes?.flags ?: 0) and WindowManager.LayoutParams.FLAG_SECURE) != 0
            window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            onDispose { if (!wasSecure) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
        }
        Surface(
            modifier = Modifier.fillMaxSize().testTag("memory_photo_edit"),
            color = MaterialTheme.colorScheme.surface,
        ) {
            BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                val previewHeight = (maxHeight * 0.42f).coerceIn(180.dp, 340.dp)
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = LumaUi.PageGutter, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(LumaUi.ContentGap),
                ) {
                    Text(stringResource(R.string.memory_photo_edit_title), style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() })
                    if (failure != null) {
                        AccessibleFeedback(stringResource(failure.messageResource()), Modifier.testTag("photo_edit_error"))
                        LumaTextButton(onClick = onRetry, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .testTag("photo_edit_retry")) {
                            Text(stringResource(R.string.photo_edit_retry))
                        }
                    }
                    if (edit != PhotoEdit.IDENTITY) {
                        Text(stringResource(R.string.photo_edit_reduce_resolution), style = MaterialTheme.typography.bodySmall)
                    }
                    PhotoCropPreview(
                        repository = repository,
                        source = source,
                        stagingLeaseId = stagingLeaseId,
                        edit = edit,
                        accessEpoch = accessEpoch,
                        retry = retry,
                        previewHeight = previewHeight,
                        cropSummary = cropSummary,
                        onCrop = onCrop,
                        onRetry = { retry++ },
                    )
                    LumaTextButton(
                        onClick = onRotate,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .testTag("photo_edit_rotate"),
                    ) { Text(stringResource(R.string.photo_edit_rotate)) }
                    LumaTextButton(
                        onClick = onReset,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .testTag("photo_edit_reset"),
                    ) { Text(stringResource(R.string.photo_edit_reset)) }
                    PhotoCropEdgeSlider(
                        label = stringResource(R.string.photo_crop_left), value = edit.crop.left,
                        range = 0.0..(edit.crop.right - 1.0 / PhotoWorkingLimits.EDGE).coerceAtLeast(0.0),
                        summary = cropSummary,
                        tag = "photo_crop_left",
                    ) { onCrop(PhotoCropGeometry.moveEdge(edit.crop, PhotoCropGeometry.Edge.LEFT, it)) }
                    PhotoCropEdgeSlider(
                        label = stringResource(R.string.photo_crop_top), value = edit.crop.top,
                        range = 0.0..(edit.crop.bottom - 1.0 / PhotoWorkingLimits.EDGE).coerceAtLeast(0.0),
                        summary = cropSummary,
                        tag = "photo_crop_top",
                    ) { onCrop(PhotoCropGeometry.moveEdge(edit.crop, PhotoCropGeometry.Edge.TOP, it)) }
                    PhotoCropEdgeSlider(
                        label = stringResource(R.string.photo_crop_right), value = edit.crop.right,
                        range = (edit.crop.left + 1.0 / PhotoWorkingLimits.EDGE).coerceAtMost(1.0)..1.0,
                        summary = cropSummary,
                        tag = "photo_crop_right",
                    ) { onCrop(PhotoCropGeometry.moveEdge(edit.crop, PhotoCropGeometry.Edge.RIGHT, it)) }
                    PhotoCropEdgeSlider(
                        label = stringResource(R.string.photo_crop_bottom), value = edit.crop.bottom,
                        range = (edit.crop.top + 1.0 / PhotoWorkingLimits.EDGE).coerceAtMost(1.0)..1.0,
                        summary = cropSummary,
                        tag = "photo_crop_bottom",
                    ) { onCrop(PhotoCropGeometry.moveEdge(edit.crop, PhotoCropGeometry.Edge.BOTTOM, it)) }
                    LumaTextButton(
                        onClick = onApply,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("photo_edit_apply"),
                    ) { Text(stringResource(R.string.photo_edit_apply)) }
                    LumaTextButton(
                        onClick = onKeepOriginal,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("photo_edit_keep_original"),
                    ) { Text(stringResource(R.string.photo_edit_keep_original)) }
                    LumaTextButton(
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("photo_edit_cancel"),
                    ) { Text(stringResource(R.string.photo_edit_cancel)) }
                }
            }
        }
    }
}

@Composable
private fun PhotoCropPreview(
    repository: PhotoRepository,
    source: PhotoAttachment,
    stagingLeaseId: UUID,
    edit: PhotoEdit,
    accessEpoch: Any?,
    retry: Int,
    previewHeight: androidx.compose.ui.unit.Dp,
    cropSummary: String,
    onCrop: (PhotoCrop) -> Unit,
    onRetry: () -> Unit,
) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(previewHeight).clipToBounds().testTag("photo_edit_preview"),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val viewportWidth = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val viewportHeight = with(density) { maxHeight.roundToPx() }.coerceAtLeast(1)
        var bitmap by remember(source.id, stagingLeaseId, accessEpoch) { mutableStateOf<Bitmap?>(null) }
        var loading by remember(source.id, stagingLeaseId, accessEpoch) { mutableStateOf(true) }
        val lifecycleKey = accessEpoch
        val bitmapToRelease = bitmap
        DisposableEffect(bitmapToRelease) {
            onDispose { bitmapToRelease?.takeUnless(Bitmap::isRecycled)?.recycle() }
        }
        LaunchedEffect(repository, source, stagingLeaseId, viewportWidth, viewportHeight, lifecycleKey, retry) {
            bitmap = null
            loading = true
            var bytes: ByteArray? = null
            var decoded: Bitmap? = null
            try {
                bytes = withContext(Dispatchers.IO) {
                    repository.display(
                        source,
                        PhotoDisplayRequest(viewportWidth, viewportHeight),
                        PhotoReadScope.Draft(stagingLeaseId),
                    )
                }
                decoded = withContext(Dispatchers.Default) {
                    bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                }
                val image = decoded
                if (image == null) Unit
                else {
                    val pixels = image.width.toLong() * image.height
                    if (pixels > PhotoWorkingLimits.PIXELS || maxOf(image.width, image.height) > PhotoWorkingLimits.EDGE) {
                        image.recycle()
                        decoded = null
                        error("Photo preview exceeded its working-image budget")
                    }
                    bitmap = image
                    decoded = null
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
            } finally {
                bytes?.fill(0)
                decoded?.takeUnless(Bitmap::isRecycled)?.recycle()
                loading = false
            }
        }

        when {
            loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            bitmap == null -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(LumaUi.ContentGap),
            ) {
                Text(stringResource(R.string.photo_edit_preview_unavailable))
                LumaTextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp).testTag("photo_edit_preview_retry")) {
                    Text(stringResource(R.string.retry))
                }
            }
            else -> {
                val image = requireNotNull(bitmap)
                val turns = edit.clockwiseQuarterTurns
                val orientedWidth = if (turns % 2 == 0) image.width else image.height
                val orientedHeight = if (turns % 2 == 0) image.height else image.width
                val scale = min(
                    viewportWidth.toFloat() / orientedWidth,
                    viewportHeight.toFloat() / orientedHeight,
                )
                val imageWidth = with(density) { (image.width * scale).toDp() }
                val imageHeight = with(density) { (image.height * scale).toDp() }
                val imageBounds = PhotoCropGeometry.fitImage(
                    Rect(0f, 0f, viewportWidth.toFloat(), viewportHeight.toFloat()),
                    orientedWidth,
                    orientedHeight,
                )
                Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = stringResource(R.string.photo_edit_preview_description),
                    modifier = Modifier.requiredSize(imageWidth, imageHeight)
                        .graphicsLayer { rotationZ = turns * 90f },
                )
                CropOverlay(
                    crop = edit.crop,
                    imageBounds = imageBounds,
                    viewportWidth = viewportWidth,
                    viewportHeight = viewportHeight,
                    cropSummary = cropSummary,
                    modifier = Modifier.fillMaxSize(),
                    onCrop = onCrop,
                )
            }
        }
    }
}

private enum class CropCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT }

@Composable
private fun CropOverlay(
    crop: PhotoCrop,
    imageBounds: Rect,
    viewportWidth: Int,
    viewportHeight: Int,
    cropSummary: String,
    modifier: Modifier,
    onCrop: (PhotoCrop) -> Unit,
) {
    var dragging by remember { mutableStateOf<CropCorner?>(null) }
    val currentCrop by rememberUpdatedState(crop)
    val currentOnCrop by rememberUpdatedState(onCrop)
    val handleTouchRadius = with(LocalDensity.current) { 24.dp.toPx() }
    Canvas(
        modifier.semantics { contentDescription = cropSummary }.then(modifier).pointerInput(imageBounds) {
            fun handles(rect: Rect) = mapOf(
                CropCorner.TOP_LEFT to Offset(rect.left, rect.top),
                CropCorner.TOP_RIGHT to Offset(rect.right, rect.top),
                CropCorner.BOTTOM_RIGHT to Offset(rect.right, rect.bottom),
                CropCorner.BOTTOM_LEFT to Offset(rect.left, rect.bottom),
            )
            detectDragGestures(
                onDragStart = { point ->
                    val selection = PhotoCropGeometry.toViewportRect(currentCrop, imageBounds)
                    val nearest = handles(selection).minByOrNull { (_, position) -> (position - point).getDistance() }
                    dragging = nearest?.takeIf { (_, position) -> (position - point).getDistance() <= handleTouchRadius }?.key
                },
                onDragEnd = { dragging = null },
                onDragCancel = { dragging = null },
            ) { change, _ ->
                val corner = dragging ?: return@detectDragGestures
                val x = ((change.position.x - imageBounds.left) / imageBounds.width).coerceIn(0f, 1f).toDouble()
                val y = ((change.position.y - imageBounds.top) / imageBounds.height).coerceIn(0f, 1f).toDouble()
                var next = currentCrop
                when (corner) {
                    CropCorner.TOP_LEFT -> {
                        next = PhotoCropGeometry.moveEdge(next, PhotoCropGeometry.Edge.LEFT, x)
                        next = PhotoCropGeometry.moveEdge(next, PhotoCropGeometry.Edge.TOP, y)
                    }
                    CropCorner.TOP_RIGHT -> {
                        next = PhotoCropGeometry.moveEdge(next, PhotoCropGeometry.Edge.RIGHT, x)
                        next = PhotoCropGeometry.moveEdge(next, PhotoCropGeometry.Edge.TOP, y)
                    }
                    CropCorner.BOTTOM_RIGHT -> {
                        next = PhotoCropGeometry.moveEdge(next, PhotoCropGeometry.Edge.RIGHT, x)
                        next = PhotoCropGeometry.moveEdge(next, PhotoCropGeometry.Edge.BOTTOM, y)
                    }
                    CropCorner.BOTTOM_LEFT -> {
                        next = PhotoCropGeometry.moveEdge(next, PhotoCropGeometry.Edge.LEFT, x)
                        next = PhotoCropGeometry.moveEdge(next, PhotoCropGeometry.Edge.BOTTOM, y)
                    }
                }
                currentOnCrop(next)
                change.consume()
            }
        },
    ) {
        val rect = PhotoCropGeometry.toViewportRect(crop, imageBounds)
        val shade = Color.Black.copy(alpha = 0.42f)
        drawRect(shade, Offset(imageBounds.left, imageBounds.top), androidx.compose.ui.geometry.Size(imageBounds.width, rect.top - imageBounds.top))
        drawRect(shade, Offset(imageBounds.left, rect.bottom), androidx.compose.ui.geometry.Size(imageBounds.width, imageBounds.bottom - rect.bottom))
        drawRect(shade, Offset(imageBounds.left, rect.top), androidx.compose.ui.geometry.Size(rect.left - imageBounds.left, rect.height))
        drawRect(shade, Offset(rect.right, rect.top), androidx.compose.ui.geometry.Size(imageBounds.right - rect.right, rect.height))
        drawRect(Color.White, Offset(rect.left, rect.top), androidx.compose.ui.geometry.Size(rect.width, rect.height), style = Stroke(2.dp.toPx()))
        listOf(Offset(rect.left, rect.top), Offset(rect.right, rect.top), Offset(rect.right, rect.bottom), Offset(rect.left, rect.bottom))
            .forEach { handle ->
                drawCircle(Color.Black.copy(alpha = 0.75f), 9.dp.toPx(), handle)
                drawCircle(Color.White, 6.dp.toPx(), handle)
            }
    }
}

@Composable
private fun PhotoCropEdgeSlider(
    label: String,
    value: Double,
    range: ClosedFloatingPointRange<Double>,
    summary: String,
    tag: String,
    onValue: (Double) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val valueLabel = stringResource(R.string.photo_crop_value, label, (value * 100).roundToInt())
        Text(valueLabel, style = MaterialTheme.typography.labelLarge)
        Slider(
            value = value.toFloat(),
            onValueChange = { onValue(it.toDouble()) },
            valueRange = range.start.toFloat()..range.endInclusive.toFloat(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag)
                .semantics { contentDescription = "$valueLabel. $summary" },
        )
    }
}

internal fun MemoryPhotoEditError.messageResource(): Int = when (this) {
    MemoryPhotoEditError.IMPORT -> R.string.photo_edit_import_error
    MemoryPhotoEditError.TRANSFORM -> R.string.photo_edit_transform_error
    MemoryPhotoEditError.ATTACH -> R.string.photo_edit_attach_error
    MemoryPhotoEditError.ACCESS -> R.string.photo_edit_access_error
    MemoryPhotoEditError.RESTORE -> R.string.photo_edit_restore_error
}
