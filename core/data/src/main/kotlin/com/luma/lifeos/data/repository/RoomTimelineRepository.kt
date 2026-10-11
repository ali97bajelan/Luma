package com.luma.lifeos.data.repository

import androidx.room.withTransaction
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.mapping.storageBoundary
import com.luma.lifeos.data.mapping.toRecord
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.TimelinePage
import com.luma.lifeos.domain.repository.TimelineRepository
import com.luma.lifeos.domain.time.TimeRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RoomTimelineRepository(private val database: LumaDatabase) : TimelineRepository {
    override fun observeChanges() = database.invalidationTracker.createFlow(
        "life_event", "mood_entry", "mood_emotion", "emotion", "journal_entry", "event_tag", "tag", "memory",
        "event_person", "person", "event_place", "place", "media_attachment", "activity_record", "app_usage_record",
    ).map { Unit }

    override suspend fun page(range: TimeRange, offset: Int, limit: Int): TimelinePage {
        require(offset >= 0 && limit in 1..300)
        return withContext(Dispatchers.IO) {
            database.withTransaction {
                val start = range.start.storageBoundary(); val end = range.endExclusive.storageBoundary()
                val rows = database.lifeEvents().pageDetails(start, end, offset, limit)
                TimelinePage(rows.map { it.toRecord() }, offset, database.lifeEvents().countDay(start, end))
            }
        }
    }
    override suspend fun get(id: EventId) = withContext(Dispatchers.IO) {
        database.lifeEvents().getDetails(id.toString())?.toRecord()
    }
}
