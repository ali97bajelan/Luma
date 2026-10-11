package com.luma.lifeos.data.repository

import androidx.room.withTransaction
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.entity.DailySummaryEntity
import com.luma.lifeos.data.entity.EventWithDetails
import com.luma.lifeos.data.entity.IntegrationStateEntity
import com.luma.lifeos.data.entity.SummaryFreshness
import com.luma.lifeos.data.mapping.storageBoundary
import com.luma.lifeos.domain.model.EventSource
import com.luma.lifeos.domain.time.TimeRange
import java.time.LocalDate
import java.time.ZoneId

/** Low-level derived storage. T14 calculation/caching lives in RoomDailySummaryRepository. */
class RoomDerivedDataRepository(private val database: LumaDatabase) {
    private val dao = database.derived()
    fun observeSummary(date: LocalDate, zone: ZoneId) = dao.observeSummary(date.toString(), zone.id)
    suspend fun summary(date: LocalDate, zone: ZoneId) = dao.summary(date.toString(), zone.id)
    suspend fun deleteSummary(date: LocalDate, zone: ZoneId) = dao.deleteSummary(date.toString(), zone.id)

    /** Derivation reads and cache writes share a transaction so concurrent capture cannot stale a fresh cache. */
    suspend fun rebuildSummary(
        date: LocalDate,
        zone: ZoneId,
        derive: (List<EventWithDetails>) -> DailySummaryEntity,
    ): DailySummaryEntity = database.withTransaction {
        val range = TimeRange.forDay(date, zone)
        val rows = database.lifeEvents().details(range.start.storageBoundary(), range.endExclusive.storageBoundary())
        val summary = derive(rows)
        require(summary.date == date.toString() && summary.zoneId == zone.id)
        require(summary.freshness == SummaryFreshness.FRESH)
        dao.upsertSummary(summary)
        summary
    }

    fun observeIntegrations() = dao.observeIntegrations()
    suspend fun integration(source: EventSource) = dao.integration(source)
    suspend fun saveIntegration(state: IntegrationStateEntity) = dao.upsertIntegration(state)
    suspend fun deleteIntegration(source: EventSource) = dao.deleteIntegration(source)
}
