package com.luma.lifeos.data.media

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import com.luma.lifeos.domain.model.PhotoLimits
import com.luma.lifeos.domain.model.PhotoDisplayRequest
import com.luma.lifeos.domain.model.PhotoWorkingLimits
import com.luma.lifeos.domain.model.PhotoMetadata
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream

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

    /** Decodes a source at working resolution after ImageDecoder applies its EXIF orientation. */
    fun decodeWorking(file: File): Bitmap {
        require(file.length() in 1..PhotoLimits.BYTES) { "Photo is empty or too large" }
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            val width = info.size.width
            val height = info.size.height
            val sourcePixels = width.toLong() * height.toLong()
            require(width > 0 && height > 0 && sourcePixels <= PhotoLimits.PIXELS) {
                "Photo dimensions are too large"
            }
            val scale = minOf(
                1.0,
                PhotoWorkingLimits.EDGE.toDouble() / maxOf(width, height),
                kotlin.math.sqrt(PhotoWorkingLimits.PIXELS.toDouble() / sourcePixels),
            )
            decoder.setTargetSize(
                maxOf(1, (width * scale).toInt()),
                maxOf(1, (height * scale).toInt()),
            )
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        if (bitmap.width <= 0 || bitmap.height <= 0 ||
            maxOf(bitmap.width, bitmap.height) > PhotoWorkingLimits.EDGE ||
            bitmap.width.toLong() * bitmap.height.toLong() > PhotoWorkingLimits.PIXELS
        ) {
            bitmap.recycle()
            throw IllegalArgumentException("Photo working dimensions are too large")
        }
        return bitmap
    }

    /** Decodes only the source detail needed by the requested viewport, within strict work bounds. */
    fun display(file: File, request: PhotoDisplayRequest): ByteArray {
        require(file.length() in 1..PhotoLimits.BYTES) { "Photo is empty or too large" }
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            val width = info.size.width
            val height = info.size.height
            val sourcePixels = width.toLong() * height.toLong()
            require(width > 0 && height > 0 && sourcePixels <= PhotoLimits.PIXELS) {
                "Photo dimensions are too large"
            }
            val zoom = request.zoom.toDouble()
            val scale = minOf(
                1.0,
                request.viewportWidth.toDouble() * zoom / width,
                request.viewportHeight.toDouble() * zoom / height,
                PhotoWorkingLimits.EDGE.toDouble() / maxOf(width, height),
                kotlin.math.sqrt(PhotoWorkingLimits.PIXELS.toDouble() / sourcePixels),
            )
            val targetWidth = maxOf(1, (width * scale).toInt())
            val targetHeight = maxOf(1, (height * scale).toInt())
            require(maxOf(targetWidth, targetHeight) <= PhotoWorkingLimits.EDGE &&
                targetWidth.toLong() * targetHeight.toLong() <= PhotoWorkingLimits.PIXELS
            ) { "Photo display dimensions are too large" }
            decoder.setTargetSize(targetWidth, targetHeight)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        if (bitmap.width <= 0 || bitmap.height <= 0 ||
            maxOf(bitmap.width, bitmap.height) > PhotoWorkingLimits.EDGE ||
            bitmap.width.toLong() * bitmap.height.toLong() > PhotoWorkingLimits.PIXELS
        ) {
            bitmap.recycle()
            throw IllegalArgumentException("Photo display dimensions are too large")
        }
        return try {
            val output = BoundedOutputStream(PhotoLimits.BYTES.toInt())
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            output.toByteArray()
        } finally {
            bitmap.recycle()
        }
    }

    private class BoundedOutputStream(private val maximumBytes: Int) : OutputStream() {
        private val output = ByteArrayOutputStream(32 * 1024)

        override fun write(value: Int) {
            requireCapacity(1)
            output.write(value)
        }

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            if (offset < 0 || length < 0 || length > bytes.size - offset) throw IndexOutOfBoundsException()
            requireCapacity(length)
            output.write(bytes, offset, length)
        }

        fun toByteArray(): ByteArray = output.toByteArray()

        private fun requireCapacity(additionalBytes: Int) {
            if (additionalBytes > maximumBytes - output.size()) throw IOException("Photo display output is too large")
        }
    }
}
