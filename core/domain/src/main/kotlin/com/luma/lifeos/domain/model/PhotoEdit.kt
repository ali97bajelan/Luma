package com.luma.lifeos.domain.model

import kotlin.math.ceil
import kotlin.math.floor

/** A validated freeform crop in normalized, EXIF-oriented image coordinates. */
data class PhotoCrop(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
) {
    init {
        require(left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite()) {
            "Photo crop coordinates must be finite"
        }
        require(left >= 0.0 && top >= 0.0 && right <= 1.0 && bottom <= 1.0 && left < right && top < bottom) {
            "Photo crop must be nonempty and inside the image"
        }
    }

    companion object {
        val FULL = PhotoCrop(left = 0.0, top = 0.0, right = 1.0, bottom = 1.0)
    }
}

/** Crop and clockwise quarter-turns applied after the source image's EXIF orientation. */
data class PhotoEdit(
    val clockwiseQuarterTurns: Int = 0,
    val crop: PhotoCrop = PhotoCrop.FULL,
) {
    init {
        require(clockwiseQuarterTurns in 0..3) { "Photo rotation must be a clockwise quarter-turn count" }
    }

    fun rotateClockwise(): PhotoEdit = copy(
        clockwiseQuarterTurns = (clockwiseQuarterTurns + 1) % 4,
        crop = rotateCropClockwise(crop),
    )

    companion object {
        val IDENTITY = PhotoEdit()
    }
}

/** Pixel-exclusive crop bounds: left/top are included; right/bottom are excluded. */
data class PhotoPixelBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    init {
        require(left >= 0 && top >= 0 && right > left && bottom > top)
    }

    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/** Rotates the displayed image and its crop together by 90 degrees clockwise. */
fun rotateCropClockwise(crop: PhotoCrop): PhotoCrop = PhotoCrop(
    left = (1.0 - crop.bottom).coerceAtMost(Math.nextDown(1.0 - crop.top)),
    top = crop.left,
    right = 1.0 - crop.top,
    bottom = crop.right,
)

/** Converts normalized bounds to pixel-exclusive bounds without creating an empty crop. */
fun cropPixelBounds(crop: PhotoCrop, width: Int, height: Int): PhotoPixelBounds {
    require(width > 0 && height > 0) { "Photo dimensions must be positive" }
    val left = floor(crop.left * width).toInt().coerceIn(0, width - 1)
    val top = floor(crop.top * height).toInt().coerceIn(0, height - 1)
    val right = ceil(crop.right * width).toInt().coerceIn(left + 1, width)
    val bottom = ceil(crop.bottom * height).toInt().coerceIn(top + 1, height)
    return PhotoPixelBounds(left, top, right, bottom)
}
