package com.luma.lifeos.data.media

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import com.luma.lifeos.domain.model.PhotoLimits
import com.luma.lifeos.domain.model.PhotoMetadata
import java.io.ByteArrayOutputStream
import java.io.File

/** Decode a bounded, orientation-correct bitmap while validating the original image. */
internal object AndroidPhotoCodec {
    private fun decode(file: File): Pair<PhotoMetadata, Bitmap> {
        require(file.length() in 1..PhotoLimits.BYTES) { "Photo is empty or too large" }
        var metadata: PhotoMetadata? = null
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            val width = info.size.width
            val height = info.size.height
            require(width.toLong() * height <= PhotoLimits.PIXELS) { "Photo dimensions are too large" }
            metadata = PhotoMetadata(info.mimeType, file.length(), width, height)
            val scale = minOf(1.0, PhotoLimits.PREVIEW_EDGE.toDouble() / maxOf(width, height))
            decoder.setTargetSize(maxOf(1, (width * scale).toInt()), maxOf(1, (height * scale).toInt()))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        return requireNotNull(metadata) to bitmap
    }
    fun inspect(file: File): PhotoMetadata {
        val (metadata, bitmap) = decode(file)
        bitmap.recycle()
        return metadata
    }
    fun preview(file: File): ByteArray {
        val (_, bitmap) = decode(file)
        return try { ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            output.toByteArray()
        } } finally { bitmap.recycle() }
    }
}
