package com.luma.lifeos.domain.model

import java.util.UUID

/** Bounds for full-screen photo work; the smaller gallery preview remains unchanged. */
object PhotoWorkingLimits {
    const val PIXELS = 8_000_000L
    const val EDGE = 4096
}

data class PhotoDisplayRequest(
    val viewportWidth: Int,
    val viewportHeight: Int,
    val zoom: Float = 1f,
) {
    init {
        require(viewportWidth > 0 && viewportHeight > 0) { "Photo viewport dimensions must be positive" }
        require(zoom.isFinite() && zoom in 1f..4f) { "Photo zoom must be between 1x and 4x" }
    }
}

/** A saved image uses its classified event owner; a draft image also needs a live route lease. */
sealed interface PhotoReadScope {
    data object Saved : PhotoReadScope
    data class Draft(val leaseId: UUID) : PhotoReadScope
}
