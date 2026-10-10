package com.luma.lifeos.data.repository

import androidx.room.withTransaction
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.entity.*
import com.luma.lifeos.data.mapping.storageBoundary
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.EventSource
import com.luma.lifeos.domain.model.EventType
import com.luma.lifeos.domain.time.TimeRange
import kotlinx.coroutines.flow.Flow

/** Transaction boundary for an event, typed content, and its complete set of links. */
class RoomEventStore(private val database: LumaDatabase) {
    private val events = database.lifeEvents()
    private val content = database.content()
    private val envelopes = RoomLifeEventRepository(database)

    fun observe(range: TimeRange): Flow<List<EventWithDetails>> =
        events.observeDetails(range.start.storageBoundary(), range.endExclusive.storageBoundary())

    suspend fun get(id: EventId): EventWithDetails? = events.getDetails(id.toString())

    suspend fun save(write: EventWrite): EventWriteResult = database.withTransaction {
        val result = saveInTransaction(write)
        database.derived().invalidateSummaries()
        result
    }

    /** Raw imported records and their checkpoint commit together, or roll back together. */
    suspend fun saveImportBatch(
        writes: List<EventWrite>,
        state: IntegrationStateEntity,
    ): List<EventWriteResult> = database.withTransaction {
        require(writes.all { it.event.source == state.source && it.event.source != EventSource.MANUAL })
        val results = writes.map { saveInTransaction(it) }
        database.derived().upsertIntegration(state)
        database.derived().invalidateSummaries()
        // A later row in this batch may adopt a path removed by an earlier row.
        val stillReferenced = content.referencedMediaPaths().toSet()
        results.map { it.copy(unreferencedMediaPaths = it.unreferencedMediaPaths.filterNot(stillReferenced::contains)) }
    }

    private suspend fun saveInTransaction(write: EventWrite): EventWriteResult {
        validate(write)
        val id = envelopes.upsertInTransaction(write.event)
        val canonicalId = id.toString()
        val oldPaths = content.attachments(canonicalId).map { it.relativePath }
        val envelope = requireNotNull(events.get(canonicalId))
        events.update(envelope.copy(metadataJson = write.metadataJson))

        // Full snapshot replacement is intentional. Parent rows are updated, never replaced.
        content.deleteMood(canonicalId) // cascades only mood-emotion links
        content.deleteJournal(canonicalId)
        content.deleteMemory(canonicalId)
        content.deleteActivities(canonicalId)
        content.deleteAppUsage(canonicalId)
        content.deleteAttachments(canonicalId)
        content.deletePeople(canonicalId)
        content.deleteTags(canonicalId)
        content.deletePlace(canonicalId)

        write.mood?.let { mood ->
            content.insertMood(mood.copy(lifeEventId = canonicalId))
            content.insertEmotions(write.emotionIds.map { MoodEmotionEntity(mood.id, it) })
        }
        write.journal?.let { content.insertJournal(it.copy(lifeEventId = canonicalId)) }
        write.memory?.let { content.insertMemory(it.copy(lifeEventId = canonicalId)) }
        content.insertActivities(write.activities.map { it.copy(lifeEventId = canonicalId) })
        write.appUsage?.let { content.insertAppUsage(it.copy(lifeEventId = canonicalId)) }
        content.insertAttachments(write.attachments.map { it.copy(lifeEventId = canonicalId) })
        content.insertPeople(write.personIds.map { EventPersonEntity(canonicalId, it) })
        content.insertTags(write.tagIds.map { EventTagEntity(canonicalId, it) })
        write.placeId?.let { content.insertPlace(EventPlaceEntity(canonicalId, it)) }

        val newPaths = write.attachments.map { it.relativePath }.toSet()
        return EventWriteResult(id, oldPaths.filterNot(newPaths::contains))
    }

    /** Adds a marker to the original event, preserving its type, journal, and all links. */
    suspend fun promoteToMemory(memory: MemoryEntity) {
        EventId.parse(memory.id)
        EventId.parse(memory.lifeEventId)
        database.withTransaction {
            val details = requireNotNull(events.getDetails(memory.lifeEventId)) { "Event does not exist" }
            val byId = content.memoryById(memory.id)
            require(byId == null || byId.lifeEventId == memory.lifeEventId) { "Memory ID belongs to another event" }
            require(details.memory == null || details.memory.id == memory.id) { "Existing memory ID must be preserved" }
            content.upsertMemory(memory)
            database.derived().invalidateSummaries()
        }
    }

    suspend fun removeMemory(id: EventId) {
        database.withTransaction {
            content.deleteMemory(id.toString())
            database.derived().invalidateSummaries()
        }
    }

    /** Deletes database references only; callers can remove returned files after success. */
    suspend fun delete(id: EventId): List<String> = database.withTransaction {
        val paths = content.attachments(id.toString()).map { it.relativePath }
        events.delete(id.toString())
        database.derived().invalidateSummaries()
        paths
    }

    suspend fun referencedMediaPaths(): List<String> = content.referencedMediaPaths()

    private fun validate(write: EventWrite) {
        val id = write.event.id.toString()
        fun child(childId: String, parentId: String) {
            EventId.parse(childId)
            require(parentId == id) { "Child content must belong to the written event" }
        }
        write.mood?.let { child(it.id, it.lifeEventId); require(write.event.type == EventType.MOOD) }
        write.journal?.let { child(it.id, it.lifeEventId); require(write.event.type == EventType.JOURNAL) }
        write.memory?.let { child(it.id, it.lifeEventId) }
        require(write.emotionIds.isEmpty() || write.mood != null)
        write.activities.forEach {
            child(it.id, it.lifeEventId)
            require(write.event.type == EventType.ACTIVITY && it.source == write.event.source)
        }
        write.appUsage?.let {
            child(it.id, it.lifeEventId)
            require(write.event.type == EventType.APP_USAGE && it.source == write.event.source)
            require(it.bucketStart == write.event.occurredAt.toEpochMilli())
            require(it.bucketEnd == write.event.endedAt?.toEpochMilli())
        }
        write.attachments.forEach { child(it.id, it.lifeEventId) }
        require(write.attachments.map { it.id }.distinct().size == write.attachments.size)
        require(write.attachments.map { it.relativePath }.distinct().size == write.attachments.size)
        require(write.activities.map { it.id }.distinct().size == write.activities.size)
        (write.emotionIds + write.personIds + write.tagIds + listOfNotNull(write.placeId)).forEach { EventId.parse(it) }
    }
}
