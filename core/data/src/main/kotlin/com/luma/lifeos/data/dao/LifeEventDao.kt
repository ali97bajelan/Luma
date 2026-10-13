package com.luma.lifeos.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.luma.lifeos.data.entity.EventWithDetails
import com.luma.lifeos.data.entity.LifeEventEntity
import com.luma.lifeos.data.entity.SummarySource
import com.luma.lifeos.domain.model.EventSource
import com.luma.lifeos.domain.usage.APP_USAGE_DISPLAY_THRESHOLD_MILLIS
import kotlinx.coroutines.flow.Flow

// Missing usage details are unavailable, not a measured value. Apply before LIMIT/OFFSET.
private const val TIMELINE_VISIBLE = """(type != 'APP_USAGE' OR (:showAppUsage AND EXISTS (
    SELECT 1 FROM app_usage_record WHERE life_event_id = life_event.id AND duration_ms > $APP_USAGE_DISPLAY_THRESHOLD_MILLIS)))"""

/** Low-level operations: application writes go through the transactional repositories. */
@Dao
interface LifeEventDao {
    @Transaction
    @Query("SELECT id, occurred_at FROM life_event WHERE occurred_at >= :start AND occurred_at < :end AND type != 'APP_USAGE' ORDER BY occurred_at, id")
    suspend fun reviewSources(start: Long, end: Long): List<com.luma.lifeos.data.entity.ReviewSource>

    @Query("""SELECT app_usage_record.*, life_event.metadata_json FROM app_usage_record
        INNER JOIN life_event ON life_event.id = app_usage_record.life_event_id
        WHERE life_event.source = 'USAGE_STATS' AND life_event.source_ref LIKE 'usage:daily:%'
        AND bucket_start >= :start AND bucket_start < :end ORDER BY bucket_start, package_name""")
    suspend fun nativeUsageSources(start: Long, end: Long): List<com.luma.lifeos.data.entity.NativeUsageSource>

    @Transaction
    @Query("SELECT id, occurred_at, metadata_json FROM life_event WHERE occurred_at >= :start AND occurred_at < :end AND type != 'APP_USAGE' ORDER BY occurred_at, id")
    suspend fun summarySources(start: Long, end: Long): List<SummarySource>

    @Transaction
    @Query("""SELECT id, occurred_at, metadata_json FROM life_event WHERE id IN
        (SELECT life_event_id FROM app_usage_record WHERE bucket_start >= :start AND bucket_start < :end)
        ORDER BY occurred_at, id""")
    suspend fun summaryUsageSources(start: Long, end: Long): List<SummarySource>

    @Query("SELECT * FROM life_event WHERE occurred_at >= :start AND occurred_at < :end ORDER BY occurred_at, id")
    fun observe(start: Long, end: Long): Flow<List<LifeEventEntity>>

    @Transaction
    @Query("SELECT * FROM life_event WHERE occurred_at >= :start AND occurred_at < :end ORDER BY occurred_at, id")
    fun observeDetails(start: Long, end: Long): Flow<List<EventWithDetails>>

    @Transaction
    @Query("SELECT * FROM life_event WHERE occurred_at >= :start AND occurred_at < :end ORDER BY occurred_at, id")
    suspend fun details(start: Long, end: Long): List<EventWithDetails>

    @Transaction
    @Query("SELECT * FROM life_event WHERE occurred_at >= :start AND occurred_at < :end AND EXISTS (SELECT 1 FROM memory WHERE memory.life_event_id = life_event.id) ORDER BY occurred_at, id")
    fun observeMemories(start: Long, end: Long): Flow<List<EventWithDetails>>

    @Transaction
    @Query("SELECT * FROM life_event WHERE occurred_at >= :start AND occurred_at < :end AND " + TIMELINE_VISIBLE + " ORDER BY occurred_at DESC, id DESC LIMIT :limit OFFSET :offset")
    suspend fun pageDetails(start: Long, end: Long, offset: Int, limit: Int, showAppUsage: Boolean): List<EventWithDetails>

    @Query("SELECT COUNT(*) FROM life_event WHERE occurred_at >= :start AND occurred_at < :end")
    suspend fun countDay(start: Long, end: Long): Int

    @Query("SELECT COUNT(*) FROM life_event WHERE occurred_at >= :start AND occurred_at < :end AND " + TIMELINE_VISIBLE)
    suspend fun countTimelineRows(start: Long, end: Long, showAppUsage: Boolean): Int

    @Query("SELECT COUNT(*) FROM life_event WHERE occurred_at >= :start AND occurred_at < :end AND type != 'APP_USAGE'")
    suspend fun countDayEvents(start: Long, end: Long): Int

    @Query("SELECT * FROM life_event WHERE id = :id")
    suspend fun get(id: String): LifeEventEntity?

    @Transaction
    @Query("SELECT * FROM life_event WHERE id = :id")
    suspend fun getDetails(id: String): EventWithDetails?

    @Query("SELECT * FROM life_event WHERE source = :source AND source_ref = :reference LIMIT 1")
    suspend fun findBySource(source: EventSource, reference: String): LifeEventEntity?

    @Insert suspend fun insert(event: LifeEventEntity)
    @Update suspend fun update(event: LifeEventEntity)
    @Query("DELETE FROM life_event WHERE id = :id") suspend fun delete(id: String)
}
