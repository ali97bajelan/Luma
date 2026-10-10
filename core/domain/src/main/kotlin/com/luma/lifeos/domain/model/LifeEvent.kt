package com.luma.lifeos.domain.model

import java.time.Instant

enum class EventType { MOOD, JOURNAL, MEMORY, ACTIVITY, APP_USAGE }
enum class EventSource { MANUAL, HEALTH_CONNECT, USAGE_STATS }

/** An import's stable upstream identity, used to make retrying a sync safe. */
data class SourceIdentity(val source: EventSource, val reference: String) {
    init {
        require(source != EventSource.MANUAL) { "Manual events have no upstream identity" }
        require(reference.isNotBlank()) { "A source reference must not be blank" }
    }
}

/** Shared timeline envelope. Typed content and links are owned by later domain slices. */
data class LifeEvent(
    val id: EventId,
    val type: EventType,
    val occurredAt: Instant,
    val createdAt: Instant,
    val updatedAt: Instant = createdAt,
    val endedAt: Instant? = null,
    val source: EventSource = EventSource.MANUAL,
    val sourceReference: String? = null,
    val title: String? = null,
    val note: String? = null,
) {
    init {
        require(endedAt == null || !endedAt.isBefore(occurredAt)) { "End precedes occurrence" }
        require(!updatedAt.isBefore(createdAt)) { "Update precedes creation" }
        require(sourceReference == null || sourceReference.isNotBlank()) { "Blank source reference" }
        require((source == EventSource.MANUAL) == (sourceReference == null)) {
            "Imported events require an upstream identity; manual events must not have one"
        }
    }

    val isEditable: Boolean get() = source == EventSource.MANUAL
    val sourceIdentity: SourceIdentity?
        get() = sourceReference?.let { SourceIdentity(source, it) }

    companion object {
        /** Oldest first, with a stable tie breaker, independent of database row order. */
        val chronologicalOrder: Comparator<LifeEvent> =
            compareBy<LifeEvent> { it.occurredAt }.thenBy { it.id.toString() }
    }
}
