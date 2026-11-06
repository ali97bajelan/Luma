package com.luma.lifeos.ui.photos

import androidx.compose.ui.geometry.Rect
import com.luma.lifeos.domain.model.PhotoCrop
import com.luma.lifeos.domain.model.PhotoWorkingLimits

/** Maps crop geometry between the displayed image rectangle and normalized domain coordinates. */
object PhotoCropGeometry {
    enum class Edge { LEFT, TOP, RIGHT, BOTTOM }

    /** Center-fits an image in a viewport, preserving its displayed aspect ratio. */
    fun fitImage(viewport: Rect, imageWidth: Int, imageHeight: Int): Rect {
        require(viewport.width > 0f && viewport.height > 0f && imageWidth > 0 && imageHeight > 0)
        val scale = minOf(viewport.width / imageWidth, viewport.height / imageHeight)
        val width = imageWidth * scale
        val height = imageHeight * scale
        val left = viewport.left + (viewport.width - width) / 2f
        val top = viewport.top + (viewport.height - height) / 2f
        return Rect(left, top, left + width, top + height)
    }

    fun toViewportRect(crop: PhotoCrop, imageBounds: Rect): Rect {
        require(imageBounds.width > 0f && imageBounds.height > 0f)
        return Rect(
            left = imageBounds.left + imageBounds.width * crop.left.toFloat(),
            top = imageBounds.top + imageBounds.height * crop.top.toFloat(),
            right = imageBounds.left + imageBounds.width * crop.right.toFloat(),
            bottom = imageBounds.top + imageBounds.height * crop.bottom.toFloat(),
        )
    }

    /** Clamps a handle rectangle to the visible image before mapping it into normalized space. */
    fun fromViewportRect(selection: Rect, imageBounds: Rect): PhotoCrop {
        require(imageBounds.width > 0f && imageBounds.height > 0f)
        val left = maxOf(selection.left, imageBounds.left)
        val top = maxOf(selection.top, imageBounds.top)
        val right = minOf(selection.right, imageBounds.right)
        val bottom = minOf(selection.bottom, imageBounds.bottom)
        require(right > left && bottom > top) { "Photo crop does not intersect the image" }
        return PhotoCrop(
            left = ((left - imageBounds.left) / imageBounds.width).toDouble().coerceIn(0.0, 1.0),
            top = ((top - imageBounds.top) / imageBounds.height).toDouble().coerceIn(0.0, 1.0),
            right = ((right - imageBounds.left) / imageBounds.width).toDouble().coerceIn(0.0, 1.0),
            bottom = ((bottom - imageBounds.top) / imageBounds.height).toDouble().coerceIn(0.0, 1.0),
        )
    }

    /** Moves one normalized edge while preserving a valid nonempty crop. */
    fun moveEdge(crop: PhotoCrop, edge: Edge, coordinate: Double): PhotoCrop {
        require(coordinate.isFinite()) { "Photo crop coordinate must be finite" }
        val value = coordinate.coerceIn(0.0, 1.0)
        val minimumGap = 1.0 / PhotoWorkingLimits.EDGE
        return when (edge) {
            Edge.LEFT -> crop.copy(left = value.coerceIn(0.0, crop.right - minOf(minimumGap, crop.right)))
            Edge.TOP -> crop.copy(top = value.coerceIn(0.0, crop.bottom - minOf(minimumGap, crop.bottom)))
            Edge.RIGHT -> crop.copy(right = value.coerceIn(crop.left + minOf(minimumGap, 1.0 - crop.left), 1.0))
            Edge.BOTTOM -> crop.copy(bottom = value.coerceIn(crop.top + minOf(minimumGap, 1.0 - crop.top), 1.0))
        }
    }
}
