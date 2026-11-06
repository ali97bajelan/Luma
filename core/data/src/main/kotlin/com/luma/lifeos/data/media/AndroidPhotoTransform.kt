package com.luma.lifeos.data.media

import android.graphics.Bitmap
import android.graphics.Matrix
import com.luma.lifeos.domain.model.PhotoEdit
import com.luma.lifeos.domain.model.PhotoLimits
import com.luma.lifeos.domain.model.PhotoWorkingLimits
import com.luma.lifeos.domain.model.PhotoPixelBounds
import com.luma.lifeos.domain.model.cropPixelBounds
import java.io.IOException
import java.io.OutputStream

/** Produces an EXIF-normalized, cropped PNG without decoding the full source resolution. */
internal object AndroidPhotoTransform {
    fun writePng(
        sourceFile: java.io.File,
        edit: PhotoEdit,
        output: OutputStream,
        ensureActive: () -> Unit = {},
    ) {
        require(sourceFile.length() in 1..PhotoLimits.BYTES) { "Photo is empty or too large" }
        ensureActive()
        val source = AndroidPhotoCodec.decodeWorking(sourceFile)
        var result: Bitmap? = null
        try {
            ensureActive()
            val turns = edit.clockwiseQuarterTurns
            val orientedWidth = if (turns % 2 == 0) source.width else source.height
            val orientedHeight = if (turns % 2 == 0) source.height else source.width
            val crop = cropPixelBounds(edit.crop, orientedWidth, orientedHeight)
            val sourceCrop = sourceBoundsFor(crop, source.width, source.height, turns)
            require(sourceCrop.right <= source.width && sourceCrop.bottom <= source.height)

            val transformed = if (turns == 0) {
                Bitmap.createBitmap(source, sourceCrop.left, sourceCrop.top, sourceCrop.width, sourceCrop.height)
            } else {
                val rotation = Matrix().apply { setRotate(turns * 90f) }
                Bitmap.createBitmap(
                    source,
                    sourceCrop.left,
                    sourceCrop.top,
                    sourceCrop.width,
                    sourceCrop.height,
                    rotation,
                    true,
                )
            }
            result = transformed
            ensureActive()
            require(transformed.width == crop.width && transformed.height == crop.height) {
                "Transformed photo dimensions do not match the crop"
            }
            require(
                maxOf(transformed.width, transformed.height) <= PhotoWorkingLimits.EDGE &&
                    transformed.width.toLong() * transformed.height.toLong() <= PhotoWorkingLimits.PIXELS,
            ) { "Transformed photo dimensions are too large" }
            ensureActive()
            if (!transformed.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw IOException("Could not encode transformed photo")
            }
            ensureActive()
        } finally {
            result?.takeIf { it !== source && !it.isRecycled }?.recycle()
            if (!source.isRecycled) source.recycle()
        }
    }

    /** Inverts the edit rotation using exclusive pixel edges, avoiding a full-size rotated bitmap. */
    private fun sourceBoundsFor(
        crop: PhotoPixelBounds,
        sourceWidth: Int,
        sourceHeight: Int,
        clockwiseQuarterTurns: Int,
    ): PhotoPixelBounds = when (clockwiseQuarterTurns) {
        0 -> crop
        1 -> PhotoPixelBounds(
            left = crop.top,
            top = sourceHeight - crop.right,
            right = crop.bottom,
            bottom = sourceHeight - crop.left,
        )
        2 -> PhotoPixelBounds(
            left = sourceWidth - crop.right,
            top = sourceHeight - crop.bottom,
            right = sourceWidth - crop.left,
            bottom = sourceHeight - crop.top,
        )
        3 -> PhotoPixelBounds(
            left = sourceWidth - crop.bottom,
            top = crop.left,
            right = sourceWidth - crop.top,
            bottom = crop.right,
        )
        else -> error("Photo rotation must be a clockwise quarter-turn count")
    }
}
