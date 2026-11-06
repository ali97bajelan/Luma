package com.luma.lifeos.ui.photos

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import com.luma.lifeos.domain.lock.LockLoadStatus
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

data class PhotoViewerAccessEpoch(
    val policyGeneration: Long?,
    val sessionGeneration: Long,
    val loadStatus: LockLoadStatus,
)

/** Small, saveable viewer state. Image bytes and decoded bitmaps never enter saved state. */
@Stable
class PhotoViewerState internal constructor(
    selectedId: UUID?,
    zoom: Float = 1f,
    panX: Float = 0f,
    panY: Float = 0f,
) {
    var selectedId by mutableStateOf(selectedId)
        private set
    var zoom by mutableStateOf(zoom.takeIf { it.isFinite() }?.coerceIn(1f, 4f) ?: 1f)
        private set
    var panX by mutableStateOf(panX)
        private set
    var panY by mutableStateOf(panY)
        private set

    private var imageWidth = 0
    private var imageHeight = 0
    private var viewportWidth = 0
    private var viewportHeight = 0
    private var panLimitX = 0f
    private var panLimitY = 0f

    val canPanLeft get() = panX > -panLimitX + 0.5f
    val canPanRight get() = panX < panLimitX - 0.5f
    val canPanUp get() = panY > -panLimitY + 0.5f
    val canPanDown get() = panY < panLimitY - 0.5f

    fun select(id: UUID) {
        selectedId = id
        reset()
    }

    fun reset() {
        zoom = 1f
        panX = 0f
        panY = 0f
        panLimitX = 0f
        panLimitY = 0f
    }

    fun updateViewport(imageWidth: Int, imageHeight: Int, viewportWidth: Int, viewportHeight: Int) {
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
        this.viewportWidth = viewportWidth
        this.viewportHeight = viewportHeight
        transformTo(zoom, 0f, 0f, imageWidth, imageHeight, viewportWidth, viewportHeight)
    }

    fun setZoomFromControl(nextZoom: Float) {
        transformTo(
            nextZoom = nextZoom,
            panDeltaX = 0f,
            panDeltaY = 0f,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
        )
    }

    fun panByViewportFraction(horizontal: Float, vertical: Float) {
        transformTo(
            nextZoom = zoom,
            panDeltaX = viewportWidth * horizontal,
            panDeltaY = viewportHeight * vertical,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
        )
    }

    fun transformTo(
        nextZoom: Float,
        panDeltaX: Float,
        panDeltaY: Float,
        imageWidth: Int,
        imageHeight: Int,
        viewportWidth: Int,
        viewportHeight: Int,
    ) {
        if (imageWidth <= 0 || imageHeight <= 0 || viewportWidth <= 0 || viewportHeight <= 0) return
        zoom = nextZoom.takeIf { it.isFinite() }?.coerceIn(1f, 4f) ?: zoom
        val fitScale = min(
            viewportWidth.toDouble() / imageWidth,
            viewportHeight.toDouble() / imageHeight,
        )
        val maxPanX = max(0.0, (imageWidth * fitScale * zoom - viewportWidth) / 2.0).toFloat()
        val maxPanY = max(0.0, (imageHeight * fitScale * zoom - viewportHeight) / 2.0).toFloat()
        panLimitX = maxPanX
        panLimitY = maxPanY
        panX = (panX + panDeltaX).coerceIn(-maxPanX, maxPanX)
        panY = (panY + panDeltaY).coerceIn(-maxPanY, maxPanY)
        if (zoom == 1f) {
            panX = 0f
            panY = 0f
        }
    }

    companion object {
        val Saver: Saver<PhotoViewerState, Any> = listSaver(
            save = { state ->
                listOf(
                    state.selectedId?.toString().orEmpty(),
                    state.zoom,
                    state.panX,
                    state.panY,
                )
            },
            restore = { values ->
                PhotoViewerState(
                    selectedId = (values[0] as? String)?.takeIf(String::isNotEmpty)?.let {
                        runCatching { UUID.fromString(it) }.getOrNull()
                    },
                    zoom = (values.getOrNull(1) as? Float) ?: 1f,
                    panX = (values.getOrNull(2) as? Float) ?: 0f,
                    panY = (values.getOrNull(3) as? Float) ?: 0f,
                )
            },
        )
    }
}

@Composable
internal fun rememberPhotoViewerState(initialId: UUID): PhotoViewerState =
    rememberSaveable(initialId, saver = PhotoViewerState.Saver) {
        PhotoViewerState(initialId)
    }
