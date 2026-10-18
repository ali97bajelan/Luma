package com.luma.lifeos.data.repository

import androidx.room.withTransaction
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.habit.HabitTimelineDetail
import com.luma.lifeos.data.mapping.storageBoundary
import com.luma.lifeos.data.mapping.toRecord
import com.luma.lifeos.domain.habit.TimelineFeedItem
import com.luma.lifeos.domain.habit.TimelineFeedPage
import com.luma.lifeos.domain.habit.HabitLog
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.TimelinePage
import com.luma.lifeos.domain.repository.TimelineRepository
import com.luma.lifeos.domain.habit.legacyTimelineFeedRange
import com.luma.lifeos.domain.time.TimeRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RoomTimelineRepository(private val database: LumaDatabase) : TimelineRepository {
    override fun observeChanges() = database.invalidationTracker.createFlow(
        "life_event", "mood_entry", "mood_emotion", "emotion", "journal_entry", "event_tag", "tag", "memory",
        "event_person", "person", "event_place", "place", "media_attachment", "activity_record", "app_usage_record",
        "habit_settings", "habit", "habit_schedule", "habit_log", "habit_day_choice", "habit_reminder", "habit_reminder_runtime",
    ).map { Unit }

    override suspend fun page(range: TimeRange, offset: Int, limit: Int, showAppUsage: Boolean): TimelinePage {
        require(offset >= 0 && limit in 1..300)
        return withContext(Dispatchers.IO) {
            database.withTransaction {
                val start = range.start.storageBoundary(); val end = range.endExclusive.storageBoundary()
                val rows = database.lifeEvents().pageDetails(start, end, offset, limit, showAppUsage)
                TimelinePage(rows.map { it.toRecord() }, offset, database.lifeEvents().countTimelineRows(start, end, showAppUsage),
                    eventCount = database.lifeEvents().countDayEvents(start, end))
            }
        }
    }

    override suspend fun feed(date: java.time.LocalDate, zone: java.time.ZoneId, offset: Int, limit: Int,
        showAppUsage: Boolean): TimelineFeedPage {
        require(offset >= 0 && limit in 1..300)
        return withContext(Dispatchers.IO) {
            database.withTransaction {
                val range = legacyTimelineFeedRange(date, zone)
                val start = range?.start?.storageBoundary()
                val end = range?.endExclusive?.storageBoundary()
                val dao = database.habitTimeline()
                val keys = dao.pageKeys(date.toString(), start, end, showAppUsage, offset, limit)
                val counts = dao.counts(date.toString(), start, end, showAppUsage)
                val entryIds = keys.mapNotNull { it.entryId }.distinct()
                val logIds = keys.mapNotNull { it.habitLogId }.distinct()
                val entries = if (entryIds.isEmpty()) emptyMap() else database.lifeEvents().detailsByIds(entryIds)
                    .associate { it.event.id to it.toRecord() }
                val logs = if (logIds.isEmpty()) emptyMap() else dao.details(logIds).associateBy { it.id }
                val items = keys.map { key ->
                    when (key.rowKind) {
                        "ENTRY" -> TimelineFeedItem.Entry(requireNotNull(entries[requireNotNull(key.entryId)]) {
                            "Timeline entry disappeared while its feed page was read"
                        })
                        "HABIT" -> requireNotNull(logs[requireNotNull(key.habitLogId)]) {
                            "Habit log disappeared while its feed page was read"
                        }.toFeedItem()
                        else -> error("Unknown timeline feed source: ${key.rowKind}")
                    }
                }
                TimelineFeedPage(items, offset, counts.totalCount, counts.eventCount, counts.habitLogCount)
            }
        }
    }

    override suspend fun get(id: EventId) = withContext(Dispatchers.IO) {
        database.lifeEvents().getDetails(id.toString())?.toRecord()
    }
}

private fun HabitTimelineDetail.toFeedItem(): TimelineFeedItem.Habit = TimelineFeedItem.Habit(
    log = HabitLog(
        id = java.util.UUID.fromString(id),
        habitId = java.util.UUID.fromString(habitId),
        date = java.time.LocalDate.parse(habitDate),
        zone = java.time.ZoneId.of(zoneId),
        occurredAt = occurredAt?.let(java.time.Instant::ofEpochMilli),
        recordedAt = java.time.Instant.ofEpochMilli(recordedAt),
        updatedAt = java.time.Instant.ofEpochMilli(updatedAt),
    ),
    name = name,
    unitLabel = unitLabel,
)
