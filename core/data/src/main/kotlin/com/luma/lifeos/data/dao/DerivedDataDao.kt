package com.luma.lifeos.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.luma.lifeos.data.entity.DailySummaryEntity
import com.luma.lifeos.data.entity.IntegrationStateEntity
import com.luma.lifeos.domain.model.EventSource
import kotlinx.coroutines.flow.Flow

@Dao
interface DerivedDataDao {
    @Query("SELECT * FROM daily_summary WHERE date = :date AND zone_id = :zoneId")
    fun observeSummary(date: String, zoneId: String): Flow<DailySummaryEntity?>
    @Query("SELECT * FROM daily_summary WHERE date = :date AND zone_id = :zoneId")
    suspend fun summary(date: String, zoneId: String): DailySummaryEntity?
    @Upsert suspend fun upsertSummary(summary: DailySummaryEntity)
    @Query("DELETE FROM daily_summary WHERE date = :date AND zone_id = :zoneId")
    suspend fun deleteSummary(date: String, zoneId: String)
    @Query("UPDATE daily_summary SET freshness = 'STALE' WHERE freshness = 'FRESH'")
    suspend fun invalidateSummaries()

    @Query("SELECT * FROM integration_state ORDER BY source")
    fun observeIntegrations(): Flow<List<IntegrationStateEntity>>
    @Query("SELECT * FROM integration_state WHERE source = :source")
    suspend fun integration(source: EventSource): IntegrationStateEntity?
    @Upsert suspend fun upsertIntegration(state: IntegrationStateEntity)
    @Query("DELETE FROM integration_state WHERE source = :source")
    suspend fun deleteIntegration(source: EventSource)
}
