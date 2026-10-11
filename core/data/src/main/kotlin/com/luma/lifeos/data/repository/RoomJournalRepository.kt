package com.luma.lifeos.data.repository

import androidx.room.withTransaction
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.entity.EventTagEntity
import com.luma.lifeos.data.entity.EventWithDetails
import com.luma.lifeos.data.entity.JournalEntryEntity
import com.luma.lifeos.data.entity.TagEntity
import com.luma.lifeos.data.mapping.toRecord
import com.luma.lifeos.domain.model.*
import com.luma.lifeos.domain.repository.JournalRepository
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.flow.map

class RoomJournalRepository(private val database: LumaDatabase, private val clock: Clock = Clock.systemUTC()) : JournalRepository {
    private val envelopes = RoomLifeEventRepository(database)
    private val content = database.content()

    override fun observeTags() = database.catalog().observeTags()
        .map { rows -> rows.map { Tag(UUID.fromString(it.id), it.name) } }

    override suspend fun get(id: EventId): LifeEventRecord? =
        database.lifeEvents().getDetails(id.toString())?.takeIf { it.journal != null }?.toRecord()

    override suspend fun create(id: EventId, input: JournalInput): EventId = database.withTransaction {
        val title = input.title.cleanTitle()
        val previous = database.lifeEvents().getDetails(id.toString())
        if (previous != null) {
            val record = editable(previous).toRecord()
            val journal = requireNotNull(record.journal)
            require(record.event.occurredAt.toEpochMilli() == input.occurredAt.toEpochMilli() &&
                journal.title == title && journal.body == input.body &&
                journal.tags.map { Tag.normalize(it.name) }.toSet() == input.tagNames.map(Tag::normalize).toSet() &&
                (input.personIds == null || record.people.map { it.id }.toSet() == input.personIds)) {
                "This capture ID already belongs to a different journal"
            }
            return@withTransaction id
        }
        val event = LifeEvent(id, EventType.JOURNAL, input.occurredAt, clock.instant(), title = title)
        envelopes.upsertInTransaction(event)
        content.insertJournal(JournalEntryEntity(EventId.new().toString(), id.toString(), input.body, title))
        replaceTags(id, input.tagNames)
        replacePeopleInTransaction(database, id, input.personIds)
        database.derived().invalidateSummaries()
        id
    }

    override suspend fun update(id: EventId, input: JournalInput) = database.withTransaction {
        val previous = editable(requireNotNull(database.lifeEvents().getDetails(id.toString())) { "Journal was deleted" })
        val event = previous.toRecord().event
        val title = input.title.cleanTitle()
        envelopes.upsertInTransaction(event.copy(occurredAt = input.occurredAt, endedAt = null,
            updatedAt = maxOf(clock.instant(), event.updatedAt), title = title))
        content.updateJournal(requireNotNull(previous.journal).copy(body = input.body, title = title))
        replaceTags(id, input.tagNames)
        replacePeopleInTransaction(database, id, input.personIds)
        database.derived().invalidateSummaries()
    }

    override suspend fun delete(id: EventId) = database.withTransaction {
        val previous = database.lifeEvents().getDetails(id.toString()) ?: return@withTransaction
        editable(previous)
        require(content.attachments(id.toString()).isEmpty()) { "Attached media requires file cleanup" }
        database.lifeEvents().delete(id.toString())
        database.derived().invalidateSummaries()
    }

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

    private fun editable(graph: EventWithDetails): EventWithDetails {
        require(graph.event.type == EventType.JOURNAL && graph.journal != null && graph.toRecord().event.isEditable) {
            "Only complete manual journals can be changed"
        }
        return graph
    }

    private fun String?.cleanTitle(): String? = this?.trim()?.takeIf(String::isNotEmpty)
}
