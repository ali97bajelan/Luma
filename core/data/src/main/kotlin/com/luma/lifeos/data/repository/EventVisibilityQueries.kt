package com.luma.lifeos.data.repository

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.habit.HabitTimelineDetail
import com.luma.lifeos.data.mapping.storageBoundary
import com.luma.lifeos.data.mapping.toRecord
import com.luma.lifeos.data.mapping.toDomainPerson
import com.luma.lifeos.data.mapping.toDomainPlace
import com.luma.lifeos.data.review.ReviewSql
import com.luma.lifeos.data.search.SearchSql
import com.luma.lifeos.domain.habit.TimelineFeedItem
import com.luma.lifeos.domain.habit.TimelineFeedPage
import com.luma.lifeos.domain.habit.legacyTimelineFeedRange
import com.luma.lifeos.domain.model.TimelinePage
import com.luma.lifeos.domain.model.PersonHistory
import com.luma.lifeos.domain.model.PlaceHistory
import com.luma.lifeos.domain.model.LifeEvent
import com.luma.lifeos.domain.lock.LockRecordClassification
import com.luma.lifeos.domain.summary.DailySummary
import com.luma.lifeos.domain.search.SearchOptions
import com.luma.lifeos.domain.search.SearchOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.map
import com.luma.lifeos.domain.review.ReviewDates
import com.luma.lifeos.domain.review.ReviewMetric
import com.luma.lifeos.domain.review.ReviewPeriod
import com.luma.lifeos.domain.review.WeeklyReview
import com.luma.lifeos.domain.search.SearchPage
import com.luma.lifeos.domain.search.SearchQuery
import com.luma.lifeos.domain.time.TimeRange
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface EventVisibilityQueries {
    fun observeRecords(range: TimeRange, visibility: EventVisibility): Flow<List<com.luma.lifeos.domain.model.LifeEventRecord>>
    suspend fun timelinePage(range: TimeRange, offset: Int, limit: Int, showAppUsage: Boolean,
        visibility: EventVisibility): TimelinePage
    suspend fun timelineFeed(date: LocalDate, zone: ZoneId, offset: Int, limit: Int, showAppUsage: Boolean,
        visibility: EventVisibility): TimelineFeedPage
    suspend fun searchPage(query: SearchQuery, zone: ZoneId, offset: Int, limit: Int,
        visibility: EventVisibility): SearchPage
    suspend fun reviewSources(dates: ReviewDates, zone: ZoneId, metric: ReviewMetric, offset: Int, limit: Int,
        visibility: EventVisibility): SearchPage
    fun observeSummary(date: LocalDate, zone: ZoneId, visibility: EventVisibility): Flow<DailySummary>
    suspend fun summary(date: LocalDate, zone: ZoneId, visibility: EventVisibility): DailySummary
    fun observeReview(period: ReviewPeriod, visibility: EventVisibility): Flow<WeeklyReview>
    fun observeSearchOptions(visibility: EventVisibility): Flow<SearchOptions>
    fun observePersonHistory(id: java.util.UUID, visibility: EventVisibility): Flow<PersonHistory?>
    fun observePlaceHistory(id: java.util.UUID, visibility: EventVisibility): Flow<PlaceHistory?>
    suspend fun classifyRecord(id: com.luma.lifeos.domain.model.EventId): LockRecordClassification?
}

/** Performs bounded count/page work in Room before hydrating the selected event graphs. */
@kotlinx.coroutines.ExperimentalCoroutinesApi
class RoomEventVisibilityQueries(private val database: LumaDatabase) : EventVisibilityQueries {
    override fun observeRecords(
        range: TimeRange,
        visibility: EventVisibility,
    ): Flow<List<com.luma.lifeos.domain.model.LifeEventRecord>> {
        val includeMemory = visibility == EventVisibility.ALL
        return database.lifeEvents()
            .observeVisibleDetails(range.start.storageBoundary(), range.endExclusive.storageBoundary(), includeMemory)
            .map { rows -> rows.map { it.toRecord() } }
    }

    override fun observeSummary(date: LocalDate, zone: ZoneId, visibility: EventVisibility): Flow<DailySummary> =
        RoomDailySummaryRepository(database).observeVisible(
            date, zone, visibility == EventVisibility.ALL,
        )

    override suspend fun summary(date: LocalDate, zone: ZoneId, visibility: EventVisibility): DailySummary =
        RoomDailySummaryRepository(database).getVisible(
            date, zone, visibility == EventVisibility.ALL,
        )

    override fun observeReview(period: ReviewPeriod, visibility: EventVisibility): Flow<WeeklyReview> =
        RoomWeeklyReviewRepository(database).observeVisible(
            period, visibility == EventVisibility.ALL,
        )

    override fun observeSearchOptions(visibility: EventVisibility): Flow<SearchOptions> {
        val catalog = database.catalog()
        val tags = if (visibility == EventVisibility.ALL) catalog.observeTags() else flowOf(emptyList())
        return combine(tags, catalog.observePeople(), catalog.observePlaces()) { tagRows, people, places ->
            SearchOptions(
                tags = tagRows.map { SearchOption(java.util.UUID.fromString(it.id), it.name) },
                people = people.map { SearchOption(java.util.UUID.fromString(it.id), it.name) },
                places = places.map { SearchOption(java.util.UUID.fromString(it.id), it.name) },
            )
        }
    }

    override fun observePersonHistory(id: java.util.UUID, visibility: EventVisibility): Flow<PersonHistory?> {
        val catalog = database.catalog()
        val includeMemory = visibility == EventVisibility.ALL
        return combine(
            catalog.observePersonHeader(id.toString()),
            catalog.observePersonEventIds(id.toString(), includeMemory),
        ) { person, eventIds -> person to eventIds }.mapLatest { (person, eventIds) ->
            if (person == null) return@mapLatest null
            val records = withContext(Dispatchers.IO) {
                database.withTransaction {
                    if (eventIds.isEmpty()) emptyList() else database.lifeEvents()
                        .detailsByIdsVisible(eventIds, includeMemory).map { it.toRecord() }
                }
            }
            PersonHistory(
                person.toDomainPerson(),
                records.sortedWith { first, second -> LifeEvent.chronologicalOrder.compare(first.event, second.event) },
            )
        }
    }

    override fun observePlaceHistory(id: java.util.UUID, visibility: EventVisibility): Flow<PlaceHistory?> {
        val catalog = database.catalog()
        val includeMemory = visibility == EventVisibility.ALL
        return combine(
            catalog.observePlaceHeader(id.toString()),
            catalog.observePlaceEventIds(id.toString(), includeMemory),
        ) { place, eventIds -> place to eventIds }.mapLatest { (place, eventIds) ->
            if (place == null) return@mapLatest null
            val records = withContext(Dispatchers.IO) {
                database.withTransaction {
                    if (eventIds.isEmpty()) emptyList() else database.lifeEvents()
                        .detailsByIdsVisible(eventIds, includeMemory).map { it.toRecord() }
                }
            }
            PlaceHistory(
                place.toDomainPlace(),
                records.sortedWith { first, second -> LifeEvent.chronologicalOrder.compare(first.event, second.event) },
            )
        }
    }

    override suspend fun classifyRecord(id: com.luma.lifeos.domain.model.EventId): LockRecordClassification? =
        withContext(Dispatchers.IO) {
            database.lifeEvents().classification(id.toString())?.let {
                LockRecordClassification(it.type, it.hasMemoryMarker, it.hasPeople)
            }
        }

    override suspend fun timelinePage(range: TimeRange, offset: Int, limit: Int, showAppUsage: Boolean,
        visibility: EventVisibility): TimelinePage = withContext(Dispatchers.IO) {
        require(offset >= 0 && limit in 1..300)
        val includeMemory = visibility == EventVisibility.ALL
        database.withTransaction {
            val dao = database.lifeEvents()
            val start = range.start.storageBoundary()
            val end = range.endExclusive.storageBoundary()
            val rows = dao.pageDetails(start, end, offset, limit, showAppUsage, includeMemory)
            TimelinePage(
                records = rows.map { it.toRecord() },
                offset = offset,
                totalCount = dao.countTimelineRows(start, end, showAppUsage, includeMemory),
                eventCount = dao.countDayEventsVisible(start, end, includeMemory),
            )
        }
    }

    override suspend fun timelineFeed(date: LocalDate, zone: ZoneId, offset: Int, limit: Int, showAppUsage: Boolean,
        visibility: EventVisibility): TimelineFeedPage = withContext(Dispatchers.IO) {
        require(offset >= 0 && limit in 1..300)
        database.withTransaction {
            val range = legacyTimelineFeedRange(date, zone)
            val start = range?.start?.storageBoundary()
            val end = range?.endExclusive?.storageBoundary()
            val dao = database.habitTimeline()
            val includeMemory = visibility == EventVisibility.ALL
            val keys = dao.pageKeys(date.toString(), start, end, showAppUsage, offset, limit, includeMemory)
            val counts = dao.counts(date.toString(), start, end, showAppUsage, includeMemory)
            val entryIds = keys.mapNotNull { it.entryId }.distinct()
            val logIds = keys.mapNotNull { it.habitLogId }.distinct()
            val entries = if (entryIds.isEmpty()) emptyMap() else database.lifeEvents().detailsByIds(entryIds)
                .associate { it.event.id to it.toRecord() }
            val logs = if (logIds.isEmpty()) emptyMap() else dao.details(logIds).associateBy { it.id }
            val items = keys.map { key ->
                when (key.rowKind) {
                    "ENTRY" -> TimelineFeedItem.Entry(requireNotNull(entries[requireNotNull(key.entryId)]))
                    "HABIT" -> requireNotNull(logs[requireNotNull(key.habitLogId)]).toFeedItem()
                    else -> error("Unknown timeline feed source: ${key.rowKind}")
                }
            }
            TimelineFeedPage(items, offset, counts.totalCount, counts.eventCount, counts.habitLogCount)
        }
    }

    override suspend fun searchPage(query: SearchQuery, zone: ZoneId, offset: Int, limit: Int,
        visibility: EventVisibility): SearchPage = withContext(Dispatchers.IO) {
        require(offset >= 0 && limit in 1..50)
        database.withTransaction {
            val sql = SearchSql(query, zone, visibility)
            val dao = database.search()
            val count = dao.count(SimpleSQLiteQuery(sql.countSql, sql.arguments.toTypedArray()))
            val rows = dao.page(SimpleSQLiteQuery(sql.pageSql, (sql.arguments + listOf(limit, offset)).toTypedArray()))
            SearchPage(rows.map { it.toRecord() }, offset, count)
        }
    }

    override suspend fun reviewSources(dates: ReviewDates, zone: ZoneId, metric: ReviewMetric, offset: Int, limit: Int,
        visibility: EventVisibility): SearchPage = withContext(Dispatchers.IO) {
        require(offset >= 0 && limit in 1..50)
        database.withTransaction {
            val sql = ReviewSql(dates, zone, metric, visibility)
            val dao = database.search()
            val count = dao.count(SimpleSQLiteQuery(sql.countSql, sql.arguments.toTypedArray()))
            val rows = dao.page(SimpleSQLiteQuery(sql.pageSql, (sql.arguments + listOf(limit, offset)).toTypedArray()))
            SearchPage(rows.map { it.toRecord() }, offset, count)
        }
    }
}

private fun HabitTimelineDetail.toFeedItem(): TimelineFeedItem.Habit = TimelineFeedItem.Habit(
    log = com.luma.lifeos.domain.habit.HabitLog(
        id = java.util.UUID.fromString(id),
        habitId = java.util.UUID.fromString(habitId),
        date = LocalDate.parse(habitDate),
        zone = ZoneId.of(zoneId),
        occurredAt = occurredAt?.let(java.time.Instant::ofEpochMilli),
        recordedAt = java.time.Instant.ofEpochMilli(recordedAt),
        updatedAt = java.time.Instant.ofEpochMilli(updatedAt),
    ),
    name = name,
    unitLabel = unitLabel,
)
