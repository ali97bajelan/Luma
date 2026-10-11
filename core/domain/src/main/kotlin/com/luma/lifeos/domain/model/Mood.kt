package com.luma.lifeos.domain.model

import java.time.Instant
import java.util.UUID

data class Emotion(val id: UUID, val name: String) {
    init { require(name.isNotBlank()) }
}

data class MoodInput(
    val score: Int,
    val occurredAt: Instant,
    val emotionIds: Set<UUID> = emptySet(),
    val energy: Int? = null,
    val stress: Int? = null,
    val note: String? = null,
) {
    init { validateScales(score, energy, stress) }
}

data class MoodDetails(
    val score: Int,
    val emotions: List<Emotion> = emptyList(),
    val energy: Int? = null,
    val stress: Int? = null,
    val note: String? = null,
) {
    init {
        validateScales(score, energy, stress)
        require(emotions.map { it.id }.distinct().size == emotions.size)
    }
}

/** One committed event graph; typed fields grow with the later feature slices. */
data class LifeEventRecord(val event: LifeEvent, val mood: MoodDetails? = null, val journal: JournalDetails? = null,
    val memory: MemoryDetails? = null) {
    val canPromoteToMemory: Boolean get() = event.isEditable &&
        ((event.type == EventType.MOOD && mood != null) || (event.type == EventType.JOURNAL && journal != null))

    init {
        require(mood == null || event.type == EventType.MOOD)
        require(journal == null || event.type == EventType.JOURNAL)
    }
}

private fun validateScales(score: Int, energy: Int?, stress: Int?) {
    require(score in 1..5) { "Mood must be between 1 and 5" }
    require(energy == null || energy in 1..5) { "Energy must be between 1 and 5" }
    require(stress == null || stress in 1..5) { "Stress must be between 1 and 5" }
}
