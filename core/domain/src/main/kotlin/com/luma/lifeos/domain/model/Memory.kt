package com.luma.lifeos.domain.model

import java.time.Instant
import java.util.UUID

object MemoryTextLimits {
    const val TITLE = 200
    const val NOTE = 50_000
    const val CONTEXT_NOTE = 10_000
    const val TAG = 50
    const val TAG_COUNT = 50
}

data class MemoryInput(
    val note: String,
    val occurredAt: Instant,
    val title: String? = null,
    val importance: Int? = null,
    val personIds: Set<UUID>? = null,
    val placeSelection: PlaceSelection? = null,
    val photos: List<PhotoAttachment>? = null,
    val tagNames: Set<String> = emptySet(),
    val contextNote: String? = null,
) {
    init {
        validatePhotos(photos); validatePersonSelection(personIds)
        require(note.isNotBlank()) { "Describe the memory before saving" }
        require(note.length <= MemoryTextLimits.NOTE) { "Memory text is too long" }
        require(title == null || title.length <= MemoryTextLimits.TITLE) { "Memory title is too long" }
        require(contextNote == null || contextNote.length <= MemoryTextLimits.CONTEXT_NOTE) { "Memory context is too long" }
        require(tagNames.all { it.trim().length <= MemoryTextLimits.TAG }) { "Tag name is too long" }
        require(tagNames.map(Tag::normalize).distinct().size <= MemoryTextLimits.TAG_COUNT) { "Too many tags" }
        require(tagNames.all { it.isNotBlank() }) { "Tag name is required" }
        validateImportance(importance)
    }
}

data class MemoryMarkerInput(
    val importance: Int? = null,
    val note: String? = null,
    val personIds: Set<UUID>? = null,
    val placeSelection: PlaceSelection? = null,
    val photos: List<PhotoAttachment>? = null,
    val tagNames: Set<String> = emptySet(),
) {
    init {
        validatePhotos(photos); validatePersonSelection(personIds); validateMarker(importance, note)
        require(tagNames.all { it.trim().length <= MemoryTextLimits.TAG }) { "Tag name is too long" }
        require(tagNames.map(Tag::normalize).distinct().size <= MemoryTextLimits.TAG_COUNT) { "Too many tags" }
        require(tagNames.all { it.isNotBlank() }) { "Tag name is required" }
    }
}

/** A marker on the original event. Its source content is never copied. */
data class MemoryDetails(val importance: Int? = null, val note: String? = null) {
    init { require(importance == null || importance in 1..5) }
}

private fun validateMarker(importance: Int?, note: String?) {
    validateImportance(importance)
    require(note == null || note.length <= MemoryTextLimits.CONTEXT_NOTE) { "Memory note is too long" }
}

private fun validateImportance(importance: Int?) {
    require(importance == null || importance in 1..5) { "Importance must be between 1 and 5" }
}
