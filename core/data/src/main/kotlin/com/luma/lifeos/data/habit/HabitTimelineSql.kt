package com.luma.lifeos.data.habit

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import com.luma.lifeos.domain.usage.APP_USAGE_DISPLAY_THRESHOLD_MILLIS

data class TimelineFeedKey(
    @ColumnInfo(name = "row_kind") val rowKind: String,
    @ColumnInfo(name = "entry_id") val entryId: String?,
    @ColumnInfo(name = "habit_log_id") val habitLogId: String?,
)

data class TimelineFeedCounts(
    @ColumnInfo(name = "total_count") val totalCount: Int,
    @ColumnInfo(name = "event_count") val eventCount: Int,
    @ColumnInfo(name = "habit_log_count") val habitLogCount: Int,
)

data class HabitTimelineDetail(
    val id: String,
    @ColumnInfo(name = "habit_id") val habitId: String,
    @ColumnInfo(name = "habit_date") val habitDate: String,
    @ColumnInfo(name = "zone_id") val zoneId: String,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long?,
    @ColumnInfo(name = "recorded_at") val recordedAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "habit_name") val name: String,
    @ColumnInfo(name = "unit_label") val unitLabel: String?,
)

/** One keys-only projection applies both indexed sources, visibility and windowing before hydration. */
@Dao
interface HabitTimelineSql {
    @Query("""
        WITH feed AS (
            SELECT 'ENTRY' AS row_kind, e.id AS entry_id, NULL AS habit_log_id,
                1 AS precision_group, e.occurred_at AS sort_time, 0 AS source_order, e.id AS stable_id
            FROM life_event e
            WHERE (:start IS NOT NULL AND e.occurred_at >= :start AND e.occurred_at < :end)
                AND (e.type != 'APP_USAGE' OR (:showAppUsage AND EXISTS (
                    SELECT 1 FROM app_usage_record u
                    WHERE u.life_event_id = e.id AND u.duration_ms > $APP_USAGE_DISPLAY_THRESHOLD_MILLIS
                )))
            UNION ALL
            SELECT 'HABIT' AS row_kind, NULL AS entry_id, l.id AS habit_log_id,
                CASE WHEN l.occurred_at IS NULL THEN 0 ELSE 1 END AS precision_group,
                COALESCE(l.occurred_at, l.recorded_at) AS sort_time, 1 AS source_order, l.id AS stable_id
            FROM habit_log l WHERE l.habit_date = :date
        )
        SELECT row_kind, entry_id, habit_log_id FROM feed
        ORDER BY precision_group, sort_time DESC, source_order, stable_id DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun pageKeys(date: String, start: Long?, end: Long?, showAppUsage: Boolean,
        offset: Int, limit: Int): List<TimelineFeedKey>

    @Query("""
        SELECT
            (SELECT COUNT(*) FROM life_event e
                WHERE (:start IS NOT NULL AND e.occurred_at >= :start AND e.occurred_at < :end)
                AND (e.type != 'APP_USAGE' OR (:showAppUsage AND EXISTS (
                    SELECT 1 FROM app_usage_record u
                    WHERE u.life_event_id = e.id AND u.duration_ms > $APP_USAGE_DISPLAY_THRESHOLD_MILLIS
                ))))
            + (SELECT COUNT(*) FROM habit_log WHERE habit_date = :date) AS total_count,
            (SELECT COUNT(*) FROM life_event e WHERE (:start IS NOT NULL AND e.occurred_at >= :start
                AND e.occurred_at < :end) AND e.type != 'APP_USAGE') AS event_count,
            (SELECT COUNT(*) FROM habit_log WHERE habit_date = :date) AS habit_log_count
    """)
    suspend fun counts(date: String, start: Long?, end: Long?, showAppUsage: Boolean): TimelineFeedCounts

    @Query("""
        SELECT l.id, l.habit_id, l.habit_date, l.zone_id, l.occurred_at, l.recorded_at, l.updated_at,
            h.name AS habit_name, h.unit_label AS unit_label
        FROM habit_log l INNER JOIN habit h ON h.id = l.habit_id
        WHERE l.id IN (:ids)
    """)
    suspend fun details(ids: List<String>): List<HabitTimelineDetail>
}
