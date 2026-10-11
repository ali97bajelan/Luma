package com.luma.lifeos.domain.model

import java.util.UUID

object PersonTextLimits {
    const val NAME = 200
    const val NOTE = 10_000
    const val SELECTION = 100
}

data class Person(val id: UUID, val name: String, val note: String? = null, val photoUri: String? = null) {
    init { require(name.isNotBlank()) }
}

data class PersonInput(val name: String, val note: String? = null) {
    init {
        require(name.isNotBlank()) { "A person needs a name" }
        require(name.length <= PersonTextLimits.NAME) { "Person name is too long" }
        require(note == null || note.length <= PersonTextLimits.NOTE) { "Person note is too long" }
    }
}

data class PersonHistory(val person: Person, val events: List<LifeEventRecord>)

/** Null preserves links; an empty set explicitly removes them. */
fun validatePersonSelection(ids: Set<UUID>?) {
    require(ids == null || ids.size <= PersonTextLimits.SELECTION) { "Too many selected people" }
}
