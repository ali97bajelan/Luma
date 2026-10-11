package com.luma.lifeos.data.repository

import androidx.room.withTransaction
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.mapping.storageBoundary
import com.luma.lifeos.data.mapping.toDomain
import com.luma.lifeos.data.mapping.toEntity
import com.luma.lifeos.data.mapping.toRecord
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.LifeEvent
import com.luma.lifeos.domain.model.LifeEventRecord
import com.luma.lifeos.domain.model.SourceIdentity
import com.luma.lifeos.domain.repository.LifeEventRepository
import com.luma.lifeos.domain.time.TimeRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomLifeEventRepository(private val database: LumaDatabase) : LifeEventRepository {
    private val dao = database.lifeEvents()

    override fun observe(range: TimeRange): Flow<List<LifeEvent>> =
        dao.observe(range.start.storageBoundary(), range.endExclusive.storageBoundary())
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun get(id: EventId): LifeEvent? = dao.get(id.toString())?.toDomain()

    override fun observeRecords(range: TimeRange): Flow<List<LifeEventRecord>> =
        dao.observeDetails(range.start.storageBoundary(), range.endExclusive.storageBoundary())
            .map { rows -> rows.map { it.toRecord() } }

    override suspend fun findBySource(identity: SourceIdentity): LifeEvent? =
        dao.findBySource(identity.source, identity.reference)?.toDomain()

    override suspend fun upsert(event: LifeEvent): EventId = database.withTransaction {
        val id = upsertInTransaction(event)
        database.derived().invalidateSummaries()
        id
    }

    /** Used by aggregate writes inside the same Room transaction as their content and links. */
    internal suspend fun upsertInTransaction(event: LifeEvent): EventId {
        val byId = dao.get(event.id.toString())
        val bySource = event.sourceIdentity?.let { dao.findBySource(it.source, it.reference) }
        require(byId == null || bySource == null || byId.id == bySource.id) {
            "Event ID and source identity refer to different existing records"
        }
        val previous = bySource ?: byId
        val incoming = event.toEntity(previous?.metadataJson ?: "{}")
        if (previous == null) {
            dao.insert(incoming)
            return event.id
        }
        require(previous.source == event.source && previous.sourceReference == event.sourceReference) {
            "An event's source identity cannot change"
        }
        require(previous.type == event.type) { "An existing event's type cannot change" }
        require(incoming.updatedAt >= previous.updatedAt) { "An update cannot move audit time backward" }
        // SQLite UPDATE keeps dependent rows intact; REPLACE would cascade-delete them.
        dao.update(incoming.copy(id = previous.id, createdAt = previous.createdAt))
        return EventId.parse(previous.id)
    }

    override suspend fun delete(id: EventId) {
        database.withTransaction {
            dao.delete(id.toString())
            database.derived().invalidateSummaries()
        }
    }
}
