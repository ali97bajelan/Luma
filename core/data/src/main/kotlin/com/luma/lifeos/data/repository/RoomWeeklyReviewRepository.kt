package com.luma.lifeos.data.repository

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.entity.*
import com.luma.lifeos.data.mapping.toRecord
import com.luma.lifeos.data.review.ReviewSql
import com.luma.lifeos.domain.model.*
import com.luma.lifeos.domain.repository.WeeklyReviewRepository
import com.luma.lifeos.domain.review.*
import com.luma.lifeos.domain.search.SearchPage
import com.luma.lifeos.domain.summary.MoodSample
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RoomWeeklyReviewRepository(private val database: LumaDatabase) : WeeklyReviewRepository {
    override fun observeChanges() = database.invalidationTracker.createFlow(
        "life_event", "mood_entry", "mood_emotion", "emotion", "journal_entry", "memory",
        "event_person", "person", "event_place", "place", "activity_record", "app_usage_record",
        "usage_session", "usage_session_window", "media_attachment", "event_tag", "tag",
    ).map { Unit }
    override fun observe(period: ReviewPeriod) = observeChanges().map {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val rows = database.lifeEvents().reviewSources(
                    previousStart = period.previous.startInstant(period.zone).toEpochMilli(),
                    previousEnd = period.previous.endInstant(period.zone).toEpochMilli(),
                    currentStart = period.current.startInstant(period.zone).toEpochMilli(),
                    currentEnd = period.current.endInstant(period.zone).toEpochMilli(),
                )
                val usage = RoomUsageRepository(database)
                val dates = period.previous.dates + period.current.dates
                val days = usage.loadDailyReports(dates, period.zone).values.filterNotNull()
                calculateWeeklyReview(period, rows.map { it.snapshot() }, days)
            }
        }
    }.distinctUntilChanged()
    override suspend fun sources(dates: ReviewDates, zone: ZoneId, metric: ReviewMetric, offset: Int, limit: Int): SearchPage {
        require(offset >= 0 && limit in 1..50)
        return withContext(Dispatchers.IO) {
            database.withTransaction {
                val sql = ReviewSql(dates, zone, metric)
                val count = database.search().count(SimpleSQLiteQuery(sql.countSql, sql.arguments.toTypedArray()))
                val rows = database.search().page(SimpleSQLiteQuery(sql.pageSql, (sql.arguments + listOf(limit, offset)).toTypedArray()))
                SearchPage(rows.map { it.toRecord() }, offset, count)
            }
        }
    }
    private fun ReviewCatalogName.model() = ReviewName(UUID.fromString(id), name)
    private fun ReviewSource.snapshot() = ReviewRecord(EventId.parse(event.id), Instant.ofEpochMilli(event.occurredAt),
        mood?.values?.let { MoodSample(it.score, it.energy, it.stress) }, mood?.emotions?.map { it.model() }.orEmpty(),
        journals.isNotEmpty(), memories.isNotEmpty(), people.map { it.model() }, places.singleOrNull()?.model(),
        activities.map { ActivityDetails(UUID.fromString(it.id), it.activityType, it.value, it.unit, it.source) })
}
