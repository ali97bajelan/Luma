package com.luma.lifeos.domain.model

import java.util.UUID

object PlaceTextLimits {
    const val NAME = 200
    const val NOTE = 10_000
}

data class Place(val id: UUID, val name: String, val note: String? = null,
    val latitude: Double? = null, val longitude: Double? = null) {
    init {
        require(name.isNotBlank())
        require((latitude == null) == (longitude == null))
        require(latitude == null || latitude in -90.0..90.0)
        require(longitude == null || longitude in -180.0..180.0)
    }
}

data class PlaceInput(val name: String, val note: String? = null) {
    init {
        require(name.isNotBlank()) { "A place needs a name" }
        require(name.length <= PlaceTextLimits.NAME) { "Place name is too long" }
        require(note == null || note.length <= PlaceTextLimits.NOTE) { "Place note is too long" }
    }
}

/** Absent selection preserves a link; a selection with a null ID explicitly clears it. */
data class PlaceSelection(val id: UUID?)

/** A committed catalog snapshot and the saved records linked to it, oldest first. */
data class PlaceHistory(val place: Place, val events: List<LifeEventRecord>)
