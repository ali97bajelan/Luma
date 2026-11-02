package com.luma.lifeos.data.repository

import androidx.room.withTransaction
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.entity.EventWithDetails
import com.luma.lifeos.data.entity.EventTagEntity
import com.luma.lifeos.data.entity.MemoryEntity
import com.luma.lifeos.data.entity.TagEntity
import com.luma.lifeos.data.mapping.storageBoundary
import com.luma.lifeos.data.mapping.toRecord
import com.luma.lifeos.domain.model.*
import com.luma.lifeos.domain.repository.MemoryRepository
import com.luma.lifeos.domain.time.TimeRange
import java.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.UUID

class RoomMemoryRepository(private val database: LumaDatabase, private val clock: Clock = Clock.systemUTC()) : MemoryRepository {
    private val events = database.lifeEvents()
    private val content = database.content()
    private val envelopes = RoomLifeEventRepository(database)

    override fun observeTags() = database.catalog().observeTags()
        .map { rows -> rows.map { Tag(UUID.fromString(it.id), it.name) } }

    override suspend fun get(id: EventId): LifeEventRecord? = events.getDetails(id.toString())?.toRecord()
    override fun observe(range: TimeRange) = events.observeMemories(range.start.storageBoundary(), range.endExclusive.storageBoundary())
        .map { rows -> rows.map { it.toRecord() } }
    override fun observeLibrary(limit: Int, eventTypes: Set<EventType>): Flow<List<LifeEventRecord>> {
        require(limit > 0) { "Library limit must be positive" }
        val supported = setOf(EventType.MEMORY, EventType.MOOD)
        require(eventTypes.all { it in supported }) { "Only memory and mood events can be listed as memories" }
        if (eventTypes.isEmpty()) return flowOf(emptyList())
        return events.observeMemoryLibrary(eventTypes.map(EventType::name), limit)
            .map { rows -> rows.map { it.toRecord() } }
    }

    override suspend fun create(id: EventId, input: MemoryInput): EventId = database.withTransaction {
        val title = input.title.clean()
        val note = input.note.trim()
        val previous = events.getDetails(id.toString())
        if (previous != null) {
            val record = direct(previous).toRecord()
            val memory = requireNotNull(record.memory)
            require(record.event.occurredAt.toEpochMilli() == input.occurredAt.toEpochMilli() &&
                record.event.title == title && record.event.note == note && memory.note == input.contextNote.clean() &&
                memory.importance == input.importance && tagNames(record) == normalizedTags(input.tagNames) &&
                (input.personIds == null || record.people.map { it.id }.toSet() == input.personIds) &&
                (input.placeSelection == null || record.place?.id == input.placeSelection?.id) &&
                (input.photos == null || record.photos.map { it.id to it.relativePath }.toSet() == input.photos?.map { it.id to it.relativePath }?.toSet())) {
                "This capture ID already belongs to a different memory"
            }
            return@withTransaction id
        }
        envelopes.upsertInTransaction(LifeEvent(id, EventType.MEMORY, input.occurredAt, clock.instant(), title = title, note = note))
        content.insertMemory(MemoryEntity(EventId.new().toString(), id.toString(), input.importance, input.contextNote.clean()))
        replaceTags(id, input.tagNames)
        replacePeopleInTransaction(database, id, input.personIds)
        replacePlaceInTransaction(database, id, input.placeSelection)
        replacePhotosInTransaction(database, id, input.photos)
        database.derived().invalidateSummaries()
        id
    }

    override suspend fun update(id: EventId, input: MemoryInput) = database.withTransaction {
        val previous = direct(requireNotNull(events.getDetails(id.toString())) { "Memory was deleted" })
        val event = previous.toRecord().event
        val note = input.note.trim()
        envelopes.upsertInTransaction(event.copy(occurredAt = input.occurredAt, endedAt = null,
            updatedAt = maxOf(clock.instant(), event.updatedAt), title = input.title.clean(), note = note))
        content.upsertMemory(requireNotNull(previous.memory).copy(
            importance = input.importance, note = input.contextNote.clean(),
        ))
        replaceTags(id, input.tagNames)
        replacePeopleInTransaction(database, id, input.personIds)
        replacePlaceInTransaction(database, id, input.placeSelection)
        replacePhotosInTransaction(database, id, input.photos)
        database.derived().invalidateSummaries()
    }

    override suspend fun delete(id: EventId) = database.withTransaction {
        val previous = events.getDetails(id.toString()) ?: return@withTransaction
        direct(previous)
        events.delete(id.toString())
        database.derived().invalidateSummaries()
    }

    override suspend fun saveMarker(id: EventId, input: MemoryMarkerInput) = database.withTransaction {
        val previous = requireNotNull(events.getDetails(id.toString())) { "Original event was deleted" }
        require(previous.toRecord().canPromoteToMemory) { "Only complete manual moods and journals can be promoted" }
        val marker = previous.memory ?: MemoryEntity(EventId.new().toString(), id.toString())
        // Source content remains intact; explicitly supplied people update only event links.
        content.upsertMemory(marker.copy(importance = input.importance, note = input.note.clean()))
        replaceTags(id, input.tagNames)
        replacePeopleInTransaction(database, id, input.personIds)
        replacePlaceInTransaction(database, id, input.placeSelection)
        replacePhotosInTransaction(database, id, input.photos)
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
    private fun tagNames(record: LifeEventRecord) = normalizedTags(record.tags.map(Tag::name).toSet())
    private fun normalizedTags(names: Set<String>) = names.map(Tag::normalize).toSet()
    private suspend fun replaceTags(id: EventId, names: Set<String>) {
        val tags = names.map(String::trim).distinctBy(Tag::normalize).map { name ->
            val normalized = Tag.normalize(name)
            database.catalog().tagNamed(normalized) ?: TagEntity(EventId.new().toString(), name).also {
                database.catalog().upsertTag(it)
            }
        }
        content.deleteTags(id.toString())
        content.insertTags(tags.map { EventTagEntity(id.toString(), it.id) })
    }
    private fun String?.clean(): String? = this?.trim()?.takeIf(String::isNotEmpty)
}
