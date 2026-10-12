package com.luma.lifeos.data.repository

import androidx.room.withTransaction
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.entity.*
import com.luma.lifeos.domain.health.*
import com.luma.lifeos.domain.model.*
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Owns only Health Connect daily step snapshots in the existing v1 graph. */
class RoomHealthRepository(private val database: LumaDatabase) : HealthRepository {
    private val source = EventSource.HEALTH_CONNECT
    private val derived = database.derived()
    override fun observeState() = derived.observeIntegrations().map { rows -> rows.firstOrNull { it.source == source }.state() }
    override suspend fun state() = withContext(Dispatchers.IO) { derived.integration(source).state() }
    override suspend fun setEnabled(enabled: Boolean, zone: ZoneId) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val previous = derived.integration(source).state()
            if (previous.enabled != enabled) save(previous.copy(enabled = enabled, importZone = previous.importZone ?: zone,
                revision = Math.addExact(previous.revision, 1), status = if (enabled) HealthStatus.READY else HealthStatus.DISABLED))
        }
    }
    override suspend fun recordStatus(status: HealthStatus, revision: Long) = withContext(Dispatchers.IO) {
        require(status != HealthStatus.DISABLED)
        database.withTransaction {
            val previous = derived.integration(source).state()
            if (previous.enabled && previous.revision == revision) save(previous.copy(status = status))
        }
    }
    override suspend fun commit(rows: List<StepObservation>, syncedAt: Instant, expected: HealthState) = withContext(Dispatchers.IO) {
        require(rows.size in 1..7 && rows.map { it.window.sourceReference }.distinct().size == rows.size)
        require(syncedAt.nano % 1_000_000 == 0 && rows.all { it.window.end <= syncedAt && it.window.zone == expected.importZone })
        database.withTransaction {
            val current = derived.integration(source).state()
            if (!current.enabled || current.revision != expected.revision || current.importZone != expected.importZone)
                throw HealthImportChangedException()
            require(current.lastSync == null || syncedAt >= current.lastSync)
            rows.forEach { row ->
                val count = row.count
                val reference = row.window.sourceReference
                val old = database.lifeEvents().findBySource(source, reference)
                require(old == null || old.type == EventType.ACTIVITY)
                val oldChildren = old?.let { database.lifeEvents().getDetails(it.id)!!.activities }.orEmpty()
                require(oldChildren.isEmpty() || (oldChildren.size == 1 && oldChildren.single().let {
                    it.source == source && it.sourceReference == reference && it.activityType == "steps" && it.unit == "count"
                }))
                if (count == null) {
                    if (old != null) database.lifeEvents().delete(old.id)
                } else {
                    val id = old?.id?.let(EventId::parse) ?: EventId(stableId(reference))
                    val audit = maxOf(syncedAt.toEpochMilli(), old?.updatedAt ?: Long.MIN_VALUE)
                    RoomLifeEventRepository(database).upsertInTransaction(LifeEvent(id, EventType.ACTIVITY, row.window.start,
                        Instant.ofEpochMilli(old?.createdAt ?: audit), Instant.ofEpochMilli(audit), row.window.end, source, reference, old?.title))
                    val event = database.lifeEvents().get(id.toString())!!
                    val metadata = JSONObject(event.metadataJson).put("health_date", row.window.date.toString())
                        .put("health_zone", row.window.zone.id).put("health_aggregate", "steps")
                    database.lifeEvents().update(event.copy(metadataJson = metadata.toString()))
                    database.content().deleteActivities(id.toString())
                    database.content().insertActivities(listOf(ActivityRecordEntity(oldChildren.firstOrNull()?.id ?: stableId("content:$reference").toString(),
                        id.toString(), "steps", count.toDouble(), "count", source, reference)))
                }
            }
            save(current.copy(lastSync = syncedAt, status = if (rows.any { it.count != null }) HealthStatus.READY else HealthStatus.NO_DATA))
            derived.invalidateSummaries()
        }
    }
    private suspend fun save(state: HealthState) {
        val json = JSONObject().put("v", 1).put("enabled", state.enabled).put("status", state.status.name).put("revision", state.revision)
        state.importZone?.let { json.put("zone", it.id) }
        derived.upsertIntegration(IntegrationStateEntity(source, when (state.status) {
            HealthStatus.DISABLED -> IntegrationStatus.NOT_CONFIGURED
            HealthStatus.ACCESS_REQUIRED -> IntegrationStatus.PERMISSION_DENIED
            HealthStatus.UPDATE_REQUIRED, HealthStatus.UNAVAILABLE, HealthStatus.NO_DATA -> IntegrationStatus.UNAVAILABLE
            HealthStatus.ERROR -> IntegrationStatus.ERROR
            HealthStatus.READY -> IntegrationStatus.READY
        }, state.lastSync?.toEpochMilli(), json.toString()))
    }
    private fun IntegrationStateEntity?.state(): HealthState {
        if (this == null) return HealthState()
        return try {
            val json = JSONObject(requireNotNull(checkpoint))
            require(json.getInt("v") == 1)
            val enabled = json.getBoolean("enabled")
            HealthState(enabled, if (enabled) HealthStatus.valueOf(json.getString("status")) else HealthStatus.DISABLED,
                lastSyncAt?.let(Instant::ofEpochMilli), json.optString("zone").takeIf { it.isNotBlank() }?.let(ZoneId::of), json.getLong("revision"))
        } catch (_: Exception) {
            // Fail closed on unknown/corrupt state; enabling must not choose a new overlapping zone.
            throw IllegalStateException("Health import state is unreadable")
        }
    }
    private fun stableId(value: String) = UUID.nameUUIDFromBytes("luma:$value".toByteArray(Charsets.UTF_8))
}
