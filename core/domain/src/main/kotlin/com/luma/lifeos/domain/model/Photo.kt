package com.luma.lifeos.domain.model

import java.nio.file.Paths
import java.util.UUID

object PhotoLimits {
    const val COUNT = 20
    const val BYTES = 25L * 1024 * 1024
    const val PIXELS = 40_000_000L
    const val PREVIEW_EDGE = 640
}
data class PhotoMetadata(val mimeType: String, val byteCount: Long, val width: Int, val height: Int) {
    init { require(mimeType.startsWith("image/") && mimeType.length <= 100); require(byteCount > 0); require(width > 0 && height > 0) }
}
data class PhotoAttachment(val id: UUID, val relativePath: String, val metadata: PhotoMetadata? = null) {
    init {
        val path = Paths.get(relativePath)
        require(relativePath.isNotBlank() && relativePath.length <= 512 && !path.isAbsolute)
        require(path.none { it.toString() == ".." || it.toString() == "." })
        require(path.normalize().toString() == relativePath)
    }
}
fun validatePhotos(photos: List<PhotoAttachment>?) {
    if (photos == null) return
    require(photos.size <= PhotoLimits.COUNT) { "Too many photos" }
    require(photos.map { it.id }.distinct().size == photos.size) { "Duplicate photo IDs" }
    require(photos.map { it.relativePath }.distinct().size == photos.size) { "Duplicate photo paths" }
}
