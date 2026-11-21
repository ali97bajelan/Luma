package com.luma.lifeos.data.repository

/** SQL projection mode selected from the current authorization snapshot by the app layer. */
enum class EventVisibility {
    ALL,
    WITHOUT_MEMORY,
}

/** Fixed SQL predicate shared by event-list and aggregate queries. */
internal fun EventVisibility.predicate(alias: String): String = when (this) {
    EventVisibility.ALL -> "1"
    EventVisibility.WITHOUT_MEMORY -> """$alias.type NOT IN ('MEMORY', 'JOURNAL')
        AND NOT EXISTS (SELECT 1 FROM memory memory_visibility
            WHERE memory_visibility.life_event_id = $alias.id)"""
}
