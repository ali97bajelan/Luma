package com.luma.lifeos.domain.model

import java.time.Instant
import java.util.Locale
import java.util.UUID

data class Tag(val id: UUID, val name: String) {
    init { require(name.isNotBlank()) { "Tag name is required" } }
    companion object {
        fun normalize(name: String): String = name.trim().lowercase(Locale.ROOT)
    }
}

/** Bounds keep navigation saved state small enough for ordinary journal drafts. */
object JournalTextLimits {
    const val BODY = 50_000
    const val TITLE = 200
    const val TAG = 50
    const val TAG_COUNT = 50
}

data class JournalInput(
    val body: String,
    val occurredAt: Instant,
    val title: String? = null,
    val tagNames: Set<String> = emptySet(),
    val personIds: Set<UUID>? = null,
    val placeSelection: PlaceSelection? = null,
) {
    init {
        validatePersonSelection(personIds)
        require(body.isNotBlank()) { "Journal body is required" }
        require(body.length <= JournalTextLimits.BODY) { "Journal body is too long" }
        require(title == null || title.length <= JournalTextLimits.TITLE) { "Journal title is too long" }
        require(tagNames.all { it.trim().length <= JournalTextLimits.TAG }) { "Tag name is too long" }
        require(tagNames.map(Tag::normalize).distinct().size <= JournalTextLimits.TAG_COUNT) { "Too many tags" }
        require(tagNames.all { it.isNotBlank() }) { "Tag name is required" }
    }
}

data class JournalDetails(val title: String?, val body: String, val tags: List<Tag> = emptyList()) {
    init {
        require(body.isNotBlank())
        require(tags.map { it.id }.distinct().size == tags.size)
    }
}
