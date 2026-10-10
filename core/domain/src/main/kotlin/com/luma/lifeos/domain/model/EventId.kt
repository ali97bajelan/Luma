package com.luma.lifeos.domain.model

import java.util.UUID

@JvmInline
value class EventId(val value: UUID) {
    override fun toString(): String = value.toString()

    companion object {
        fun new(): EventId = EventId(UUID.randomUUID())

        fun parse(value: String): EventId {
            val uuid = UUID.fromString(value)
            require(uuid.toString().equals(value, ignoreCase = true)) { "Expected a canonical UUID" }
            return EventId(uuid)
        }
    }
}
