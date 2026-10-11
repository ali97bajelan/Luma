package com.luma.lifeos.domain.model

import java.time.Instant
import java.util.UUID

object MemoryTextLimits {
    const val TITLE = 200
    const val NOTE = 10_000
}

data class MemoryInput(val note: String, val occurredAt: Instant, val title: String? = null, val importance: Int? = null, val personIds: Set<UUID>? = null, val placeSelection: PlaceSelection? = null, val photos: List<PhotoAttachment>? = null) {
    init {
        validatePhotos(photos); validatePersonSelection(personIds)
        require(note.isNotBlank()) { "Describe the memory before saving" }
        require(title == null || title.length <= MemoryTextLimits.TITLE) { "Memory title is too long" }
        validateMarker(importance, note)
    }
}

data class MemoryMarkerInput(val importance: Int? = null, val note: String? = null, val personIds: Set<UUID>? = null, val placeSelection: PlaceSelection? = null, val photos: List<PhotoAttachment>? = null) {
    init { validatePhotos(photos); validatePersonSelection(personIds); validateMarker(importance, note) }
}

/** A marker on the original event. Its source content is never copied. */
data class MemoryDetails(val importance: Int? = null, val note: String? = null) {
    init { require(importance == null || importance in 1..5) }
}

private fun validateMarker(importance: Int?, note: String?) {
    require(importance == null || importance in 1..5) { "Importance must be between 1 and 5" }
    require(note == null || note.length <= MemoryTextLimits.NOTE) { "Memory note is too long" }
}
