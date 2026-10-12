package com.luma.lifeos.data.repository

import androidx.room.withTransaction
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.entity.*
import com.luma.lifeos.data.mapping.usageLabel
import com.luma.lifeos.data.mapping.storageBoundary
import com.luma.lifeos.domain.model.*
import com.luma.lifeos.domain.usage.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Owns daily imports and separate session observations; unrelated content and links remain intact. */
class RoomUsageRepository(private val database: LumaDatabase) : UsageRepository {
    private val source = EventSource.USAGE_STATS
    private val derived = database.derived()
    override fun observeState() = derived.observeIntegrations().map { rows -> rows.firstOrNull { it.source == source }.state() }
    override suspend fun state() = withContext(Dispatchers.IO) { derived.integration(source).state() }
    override suspend fun setEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val previous = derived.integration(source).state()
            saveState(previous.copy(enabled = enabled, status = if (enabled) UsageSyncStatus.READY else UsageSyncStatus.DISABLED))
        }
    }
    override suspend fun recordStatus(status: UsageSyncStatus) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val previous = derived.integration(source).state()
            if (previous.enabled) saveState(previous.copy(status = status))
        }
    }
    override suspend fun recordClockSafety(anchor: UsageClockAnchor, minimumBucketStart: Instant) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val previous = derived.integration(source).state()
            if (previous.enabled) saveState(previous.copy(clockAnchor = anchor,
                minimumBucketStart = maxOf(previous.minimumBucketStart ?: minimumBucketStart, minimumBucketStart),
                status = UsageSyncStatus.INTERVAL_CHANGED))
        }
    }
    override suspend fun commit(buckets: List<UsageBucket>, syncedAt: Instant, checkpoint: Instant, anchor: UsageClockAnchor?) =
        commitImport(buckets, null, syncedAt, checkpoint, anchor)

    override suspend fun commitImport(buckets: List<UsageBucket>, sessions: UsageSessionBatch?, syncedAt: Instant,
        checkpoint: Instant, anchor: UsageClockAnchor?) = withContext(Dispatchers.IO) {
        val rows = distinctUsageBuckets(buckets)
        require(rows.isNotEmpty() || sessions != null)
        database.withTransaction {
            val previousState = derived.integration(source).state()
            check(previousState.enabled) { "Usage imports are disabled" }
            if (previousState.minimumBucketStart?.let { floor -> rows.any { it.start <= floor } } == true)
                throw UsageIntervalChangedException()
            if (previousState.minimumBucketStart?.let { floor -> sessions != null && sessions.range.start <= floor } == true)
                throw UsageIntervalChangedException()
            val stored = if (rows.isEmpty()) emptyList() else database.content().overlappingImportedUsage(rows.minOf { it.start }.toEpochMilli(), rows.maxOf { it.end }.toEpochMilli())
                .map { UsageBucket(it.packageName, Instant.ofEpochMilli(it.bucketStart), Instant.ofEpochMilli(it.bucketEnd), it.durationMs) }
            assertUnambiguousUsage(rows, stored)
            rows.forEach { row ->
                val previous = database.lifeEvents().findBySource(source, row.sourceReference)
                val id = previous?.id?.let(EventId::parse) ?: EventId(stableId(row.sourceReference))
                val audit = maxOf(syncedAt.toEpochMilli(), previous?.updatedAt ?: Long.MIN_VALUE)
                RoomLifeEventRepository(database).upsertInTransaction(LifeEvent(id, EventType.APP_USAGE, row.start,
                    Instant.ofEpochMilli(previous?.createdAt ?: audit), Instant.ofEpochMilli(audit), row.end,
                    source, row.sourceReference, row.appLabel ?: row.packageName))
                val oldChild = database.lifeEvents().getDetails(id.toString())?.appUsage
                val metadata = (previous?.metadataJson ?: "{}").json()
                if (row.appLabel != null) metadata.put("app_label", row.appLabel)
                else metadata.remove("app_label")
                database.lifeEvents().get(id.toString())!!.let { database.lifeEvents().update(it.copy(metadataJson = metadata.toString())) }
                database.content().deleteAppUsage(id.toString())
                database.content().insertAppUsage(AppUsageRecordEntity(oldChild?.id ?: stableId("content:${row.sourceReference}").toString(),
                    id.toString(), row.start.toEpochMilli(), row.end.toEpochMilli(), row.packageName, row.durationMillis))
            }
            sessions?.let { batch ->
                // Keep old observations when Android has already discarded their transitions.
                database.usageSessions().upsert(batch.sessions.map { UsageSessionEntity(it.packageName,
                    it.start.toEpochMilli(), it.end.toEpochMilli(), it.appLabel, it.ongoing) })
                database.usageSessions().upsertWindow(UsageSessionWindowEntity(batch.range.start.toEpochMilli(),
                    batch.range.endExclusive.toEpochMilli(), batch.history == UsageHistory.COMPLETE))
            }
            saveState(previousState.copy(status = UsageSyncStatus.READY, lastSync = syncedAt, checkpoint = checkpoint,
                clockAnchor = previousState.clockAnchor ?: anchor))
            derived.invalidateSummaries()
        }
    }
    override fun observeDaily(date: LocalDate, zone: ZoneId) = database.invalidationTracker.createFlow(
        "life_event", "app_usage_record", "usage_session", "usage_session_window",
    ).map {
        withContext(Dispatchers.IO) { database.withTransaction { loadDaily(date, zone) } }
    }.distinctUntilChanged()

    private suspend fun loadDaily(date: LocalDate, zone: ZoneId): DailyUsageReport? {
        val start = date.atStartOfDay(zone).toInstant(); val end = date.plusDays(1).atStartOfDay(zone).toInstant()
        if (start == end) return null
        val visits = database.usageSessions().forDay(start.toEpochMilli(), end.toEpochMilli())
        val windows = database.usageSessions().windows(start.toEpochMilli(), end.toEpochMilli())
        var coveredUntil = start.toEpochMilli()
        windows.filter { it.complete }.forEach { window ->
            if (window.queryStart <= coveredUntil) coveredUntil = maxOf(coveredUntil, window.queryEnd)
        }
        val history = if (coveredUntil >= end.toEpochMilli()) UsageHistory.COMPLETE else UsageHistory.PARTIAL
        val report = sessionUsageReport(visits.map { UsageSession(it.packageName,
            Instant.ofEpochMilli(it.startedAt), Instant.ofEpochMilli(it.endedAt), it.appLabel, it.ongoing) }, date, zone, history)
        if (visits.isNotEmpty() || history == UsageHistory.COMPLETE) return report

        val rows = database.lifeEvents().details(start.storageBoundary(), end.storageBoundary())
            .filter { it.event.source == source && it.event.sourceReference?.startsWith("usage:daily:") == true }
            .mapNotNull { record -> record.appUsage?.let { usage ->
                UsageBucket(usage.packageName, Instant.ofEpochMilli(usage.bucketStart), Instant.ofEpochMilli(usage.bucketEnd),
                    usage.durationMs, record.event.metadataJson.usageLabel())
            } }
        val legacy = dailyUsageReport(rows, date, zone)
        // Requested query bounds do not establish retention. An empty partial query
        // must not hide real saved totals when Android has already lost the events.
        return when {
            legacy?.apps?.isNotEmpty() == true -> legacy
            windows.isNotEmpty() -> report
            else -> legacy
        }
    }
    private suspend fun saveState(state: UsageImportState) {
        val checkpoint = JSONObject().put("v", 1).put("enabled", state.enabled).put("status", state.status.name)
        state.checkpoint?.let { checkpoint.put("end", it.toEpochMilli()) }
        state.minimumBucketStart?.let { checkpoint.put("native_floor", it.toEpochMilli()) }
        state.clockAnchor?.let { checkpoint.put("clock", JSONObject().put("wall", it.wallTime.toEpochMilli())
            .put("elapsed", it.elapsedMillis).put("boot", it.bootCount)) }
        derived.upsertIntegration(IntegrationStateEntity(source, when (state.status) {
            UsageSyncStatus.DISABLED -> IntegrationStatus.NOT_CONFIGURED
            UsageSyncStatus.ACCESS_REQUIRED -> IntegrationStatus.PERMISSION_DENIED
            UsageSyncStatus.LOCKED, UsageSyncStatus.UNAVAILABLE, UsageSyncStatus.NO_DATA -> IntegrationStatus.UNAVAILABLE
            UsageSyncStatus.ERROR, UsageSyncStatus.INTERVAL_CHANGED -> IntegrationStatus.ERROR
            UsageSyncStatus.READY -> IntegrationStatus.READY
        }, state.lastSync?.toEpochMilli(), checkpoint.toString()))
    }
    private fun IntegrationStateEntity?.state(): UsageImportState {
        if (this == null) return UsageImportState()
        val json = checkpoint?.json() ?: JSONObject()
        val enabled = json.optBoolean("enabled", false)
        val status = runCatching { UsageSyncStatus.valueOf(json.getString("status")) }.getOrDefault(UsageSyncStatus.DISABLED)
        val end = if (json.has("end")) runCatching { Instant.ofEpochMilli(json.getLong("end")) }.getOrNull() else null
        val floor = if (json.has("native_floor")) runCatching { Instant.ofEpochMilli(json.getLong("native_floor")) }.getOrNull() else null
        val anchor = json.optJSONObject("clock")?.let { clock -> runCatching {
            UsageClockAnchor(Instant.ofEpochMilli(clock.getLong("wall")), clock.getLong("elapsed"), clock.getInt("boot"))
        }.getOrNull() }
        return UsageImportState(enabled, if (enabled) status else UsageSyncStatus.DISABLED, lastSyncAt?.let(Instant::ofEpochMilli), end, anchor, floor)
    }
    private fun stableId(value: String) = UUID.nameUUIDFromBytes("luma:$value".toByteArray(Charsets.UTF_8))
    private fun String.json() = runCatching { JSONObject(this) }.getOrElse { JSONObject() }
}
