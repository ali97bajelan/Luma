package com.luma.lifeos.data.repository

import androidx.room.withTransaction
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.entity.EventWithDetails
import com.luma.lifeos.data.entity.MemoryEntity
import com.luma.lifeos.data.mapping.storageBoundary
import com.luma.lifeos.data.mapping.toRecord
import com.luma.lifeos.domain.model.*
import com.luma.lifeos.domain.repository.MemoryRepository
import com.luma.lifeos.domain.time.TimeRange
import java.time.Clock
import kotlinx.coroutines.flow.map

class RoomMemoryRepository(private val database: LumaDatabase, private val clock: Clock = Clock.systemUTC()) : MemoryRepository {
    private val events = database.lifeEvents()
    private val content = database.content()
    private val envelopes = RoomLifeEventRepository(database)

    override suspend fun get(id: EventId): LifeEventRecord? = events.getDetails(id.toString())?.toRecord()
    override fun observe(range: TimeRange) = events.observeMemories(range.start.storageBoundary(), range.endExclusive.storageBoundary())
        .map { rows -> rows.map { it.toRecord() } }

    override suspend fun create(id: EventId, input: MemoryInput): EventId = database.withTransaction {
        val title = input.title.clean()
        val note = input.note.trim()
        val previous = events.getDetails(id.toString())
        if (previous != null) {
            val record = direct(previous).toRecord()
            val memory = requireNotNull(record.memory)
            require(record.event.occurredAt.toEpochMilli() == input.occurredAt.toEpochMilli() &&
                record.event.title == title && memory.note == note && memory.importance == input.importance) {
                "This capture ID already belongs to a different memory"
            }
            return@withTransaction id
        }
        envelopes.upsertInTransaction(LifeEvent(id, EventType.MEMORY, input.occurredAt, clock.instant(), title = title, note = note))
        content.insertMemory(MemoryEntity(EventId.new().toString(), id.toString(), input.importance, note))
        database.derived().invalidateSummaries()
        id
    }

    override suspend fun update(id: EventId, input: MemoryInput) = database.withTransaction {
        val previous = direct(requireNotNull(events.getDetails(id.toString())) { "Memory was deleted" })
        val event = previous.toRecord().event
        val note = input.note.trim()
        envelopes.upsertInTransaction(event.copy(occurredAt = input.occurredAt, endedAt = null,
            updatedAt = maxOf(clock.instant(), event.updatedAt), title = input.title.clean(), note = note))
        content.upsertMemory(requireNotNull(previous.memory).copy(importance = input.importance, note = note))
        database.derived().invalidateSummaries()
    }

    override suspend fun delete(id: EventId) = database.withTransaction {
        val previous = events.getDetails(id.toString()) ?: return@withTransaction
        direct(previous)
        require(content.attachments(id.toString()).isEmpty()) { "Attached media requires file cleanup" }
        events.delete(id.toString())
        database.derived().invalidateSummaries()
    }

    override suspend fun saveMarker(id: EventId, input: MemoryMarkerInput) = database.withTransaction {
        val previous = requireNotNull(events.getDetails(id.toString())) { "Original event was deleted" }
        require(previous.toRecord().canPromoteToMemory) { "Only complete manual moods and journals can be promoted" }
        val marker = previous.memory ?: MemoryEntity(EventId.new().toString(), id.toString())
        // Only the marker changes. The original envelope and all source content remain intact.
        content.upsertMemory(marker.copy(importance = input.importance, note = input.note.clean()))
        database.derived().invalidateSummaries()
    }

    override suspend fun removeMarker(id: EventId) = database.withTransaction {
        val previous = events.getDetails(id.toString()) ?: return@withTransaction
        require(previous.toRecord().canPromoteToMemory) { "This event cannot be unmarked" }
        content.deleteMemory(id.toString())
        database.derived().invalidateSummaries()
    }

    private fun direct(graph: EventWithDetails): EventWithDetails {
        require(graph.event.type == EventType.MEMORY && graph.memory != null && graph.toRecord().event.isEditable) {
            "Only complete manual standalone memories can be changed"
        }
        return graph
    }
    private fun String?.clean(): String? = this?.trim()?.takeIf(String::isNotEmpty)
}
