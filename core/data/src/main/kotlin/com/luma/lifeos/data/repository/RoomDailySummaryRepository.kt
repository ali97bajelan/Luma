package com.luma.lifeos.data.repository

import androidx.room.withTransaction
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.entity.DailySummaryEntity
import com.luma.lifeos.data.entity.SummaryFreshness
import com.luma.lifeos.data.entity.SummarySource
import com.luma.lifeos.data.mapping.SummaryCacheCodec
import com.luma.lifeos.data.mapping.usageLabel
import com.luma.lifeos.domain.model.*
import com.luma.lifeos.domain.repository.DailySummaryRepository
import com.luma.lifeos.domain.summary.*
import java.time.*
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RoomDailySummaryRepository(private val database: LumaDatabase, private val clock: Clock = Clock.systemUTC()) : DailySummaryRepository {
    override fun observe(date: LocalDate, zone: ZoneId) = database.invalidationTracker.createFlow(
        "life_event", "mood_entry", "journal_entry", "memory", "activity_record", "app_usage_record",
    ).map { recalculate(date, zone) }.distinctUntilChanged()

    override suspend fun get(date: LocalDate, zone: ZoneId) = load(date, zone, force = false)
    override suspend fun recalculate(date: LocalDate, zone: ZoneId) = load(date, zone, force = true)
    private suspend fun load(date: LocalDate, zone: ZoneId, force: Boolean): DailySummary = withContext(Dispatchers.IO) {
        database.withTransaction {
            val derived = database.derived()
            val cached = derived.summary(date.toString(), zone.id)
            if (!force && cached?.freshness == SummaryFreshness.FRESH && cached.algorithmVersion == DAILY_SUMMARY_VERSION) {
                runCatching { SummaryCacheCodec.decode(cached.metricsJson, date, zone) }.getOrNull()?.let { return@withTransaction it }
            }
            // Equal bounds on a skipped civil day produce an empty indexed query, not an invalid TimeRange.
            val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val rows = database.lifeEvents().summarySources(start, end) + database.lifeEvents().summaryUsageSources(start, end)
            val result = calculateDailySummary(date, zone, rows.map { it.snapshot() })
            derived.upsertSummary(DailySummaryEntity(date.toString(), zone.id, SummaryCacheCodec.encode(result), DAILY_SUMMARY_VERSION, clock.millis()))
            result
        }
    }
    private fun SummarySource.snapshot() = SummaryRecord(EventId.parse(event.id), Instant.ofEpochMilli(event.occurredAt),
        mood?.let { MoodSample(it.score, it.energy, it.stress) }, journals.isNotEmpty(), memories.isNotEmpty(),
        activities.map { ActivityDetails(UUID.fromString(it.id), it.activityType, it.value, it.unit, it.source) },
        usage?.let { AppUsageDetails(it.packageName, Instant.ofEpochMilli(it.bucketStart), Instant.ofEpochMilli(it.bucketEnd), it.durationMs, event.metadataJson.usageLabel()) })
}
