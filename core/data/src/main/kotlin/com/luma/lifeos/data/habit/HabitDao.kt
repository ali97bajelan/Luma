package com.luma.lifeos.data.habit

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class HabitDayRow(
    @ColumnInfo(name = "habit_id") val habitId: String,
    @ColumnInfo(name = "habit_name") val name: String,
    @ColumnInfo(name = "unit_label") val unitLabel: String?,
    @ColumnInfo(name = "start_date") val startDate: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "schedule_id") val scheduleId: String?,
    @ColumnInfo(name = "effective_from") val effectiveFrom: String?,
    @ColumnInfo(name = "schedule_target") val target: Int?,
    @ColumnInfo(name = "weekday_mask") val weekdayMask: Int?,
    @ColumnInfo(name = "schedule_state") val scheduleState: String?,
    @ColumnInfo(name = "log_count") val count: Int,
    @ColumnInfo(name = "skip_remaining") val skipRemaining: Boolean,
    @ColumnInfo(name = "tracking_zone_id") val trackingZoneId: String,
)

data class WidgetHabitSummary(
    val total: Int,
    val completed: Int,
    val hasAnyItems: Boolean,
)

@Dao
interface HabitDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSettings(settings: HabitSettingsEntity): Long

    @Query("SELECT * FROM habit_settings WHERE setting_id = 1")
    suspend fun settings(): HabitSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertHabit(habit: HabitEntity)

    @Query("SELECT * FROM habit WHERE id = :id")
    suspend fun habit(id: String): HabitEntity?

    @Query("SELECT * FROM habit ORDER BY created_at, id")
    suspend fun habits(): List<HabitEntity>

    @Query("UPDATE habit SET name = :name, unit_label = :unitLabel, updated_at = :updatedAt WHERE id = :id")
    suspend fun rename(id: String, name: String, unitLabel: String?, updatedAt: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSchedule(schedule: HabitScheduleEntity)

    @Upsert
    suspend fun upsertSchedule(schedule: HabitScheduleEntity)

    @Query("SELECT * FROM habit_schedule WHERE habit_id = :habitId ORDER BY effective_from")
    suspend fun schedules(habitId: String): List<HabitScheduleEntity>

    @Query("DELETE FROM habit_schedule WHERE habit_id = :habitId AND effective_from = :effectiveFrom")
    suspend fun deleteSchedule(habitId: String, effectiveFrom: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLog(log: HabitLogEntity)

    @Query("SELECT * FROM habit_log WHERE id = :id")
    suspend fun log(id: String): HabitLogEntity?

    @Query("UPDATE habit_log SET habit_date = :date, zone_id = :zoneId, occurred_at = :occurredAt, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateLog(id: String, date: String, zoneId: String, occurredAt: Long?, updatedAt: Long): Int

    @Query("DELETE FROM habit_log WHERE id = :id")
    suspend fun deleteLog(id: String): Int

    @Upsert
    suspend fun upsertChoice(choice: HabitDayChoiceEntity)

    @Query("DELETE FROM habit_day_choice WHERE habit_id = :habitId AND habit_date = :date")
    suspend fun deleteChoice(habitId: String, date: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReminder(reminder: HabitReminderEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReminderRuntime(runtime: HabitReminderRuntimeEntity)

    @Query("SELECT * FROM habit_reminder WHERE habit_id = :habitId")
    suspend fun reminder(habitId: String): HabitReminderEntity?

    @Query("SELECT * FROM habit_reminder_runtime WHERE habit_id = :habitId")
    suspend fun reminderRuntime(habitId: String): HabitReminderRuntimeEntity?

    @Query("""
        SELECT h.id AS habit_id, h.name AS habit_name, h.unit_label AS unit_label,
            h.start_date AS start_date, h.created_at AS created_at, h.updated_at AS updated_at,
            s.id AS schedule_id, s.effective_from AS effective_from, s.target AS schedule_target,
            s.weekday_mask AS weekday_mask, s.state AS schedule_state,
            (SELECT COUNT(*) FROM habit_log l WHERE l.habit_id = h.id AND l.habit_date = :date) AS log_count,
            COALESCE(c.skip_remaining, 0) AS skip_remaining,
            COALESCE(z.tracking_zone_id, 'UTC') AS tracking_zone_id
        FROM habit h
        LEFT JOIN habit_settings z ON z.setting_id = 1
        LEFT JOIN habit_schedule s ON s.habit_id = h.id AND s.effective_from = (
            SELECT MAX(previous.effective_from) FROM habit_schedule previous
            WHERE previous.habit_id = h.id AND previous.effective_from <= :date
        )
        LEFT JOIN habit_day_choice c ON c.habit_id = h.id AND c.habit_date = :date
        ORDER BY h.created_at, h.id
    """)
    fun observeDayRows(date: String): Flow<List<HabitDayRow>>

    @Query("""
        SELECT h.id AS habit_id, h.name AS habit_name, h.unit_label AS unit_label,
            h.start_date AS start_date, h.created_at AS created_at, h.updated_at AS updated_at,
            s.id AS schedule_id, s.effective_from AS effective_from, s.target AS schedule_target,
            s.weekday_mask AS weekday_mask, s.state AS schedule_state,
            (SELECT COUNT(*) FROM habit_log l WHERE l.habit_id = h.id AND l.habit_date = :date) AS log_count,
            COALESCE(c.skip_remaining, 0) AS skip_remaining,
            COALESCE(z.tracking_zone_id, 'UTC') AS tracking_zone_id
        FROM habit h
        LEFT JOIN habit_settings z ON z.setting_id = 1
        LEFT JOIN habit_schedule s ON s.habit_id = h.id AND s.effective_from = (
            SELECT MAX(previous.effective_from) FROM habit_schedule previous
            WHERE previous.habit_id = h.id AND previous.effective_from <= :date
        )
        LEFT JOIN habit_day_choice c ON c.habit_id = h.id AND c.habit_date = :date
        ORDER BY h.created_at, h.id
    """)
    suspend fun dayRows(date: String): List<HabitDayRow>

    @Query("""
        SELECT COUNT(*) AS total,
            COALESCE(SUM(CASE WHEN eligible.log_count >= eligible.target THEN 1 ELSE 0 END), 0) AS completed,
            EXISTS(SELECT 1 FROM habit) AS hasAnyItems
        FROM (
            SELECT s.target AS target,
                (SELECT COUNT(*) FROM habit_log l WHERE l.habit_id = h.id AND l.habit_date = :date) AS log_count
            FROM habit h
            JOIN habit_schedule s ON s.habit_id = h.id AND s.effective_from = (
                SELECT MAX(previous.effective_from) FROM habit_schedule previous
                WHERE previous.habit_id = h.id AND previous.effective_from <= :date
            )
            WHERE h.start_date <= :date AND s.state = 'ACTIVE'
                AND (s.weekday_mask & (1 << ((CAST(strftime('%w', :date) AS INTEGER) + 6) % 7))) <> 0
        ) AS eligible
    """)
    suspend fun widgetSummary(date: String): WidgetHabitSummary

    @Query("""
        SELECT h.id AS habit_id, h.name AS habit_name, h.unit_label AS unit_label,
            h.start_date AS start_date, h.created_at AS created_at, h.updated_at AS updated_at,
            s.id AS schedule_id, s.effective_from AS effective_from, s.target AS schedule_target,
            s.weekday_mask AS weekday_mask, s.state AS schedule_state,
            (SELECT COUNT(*) FROM habit_log l WHERE l.habit_id = h.id AND l.habit_date = :date) AS log_count,
            COALESCE(c.skip_remaining, 0) AS skip_remaining,
            COALESCE(z.tracking_zone_id, 'UTC') AS tracking_zone_id
        FROM habit h
        JOIN habit_schedule s ON s.habit_id = h.id AND s.effective_from = (
            SELECT MAX(previous.effective_from) FROM habit_schedule previous
            WHERE previous.habit_id = h.id AND previous.effective_from <= :date
        )
        LEFT JOIN habit_settings z ON z.setting_id = 1
        LEFT JOIN habit_day_choice c ON c.habit_id = h.id AND c.habit_date = :date
        WHERE h.start_date <= :date AND s.state = 'ACTIVE'
            AND (s.weekday_mask & (1 << ((CAST(strftime('%w', :date) AS INTEGER) + 6) % 7))) <> 0
        ORDER BY h.created_at, h.id
        LIMIT :limit
    """)
    suspend fun widgetDayRows(date: String, limit: Int): List<HabitDayRow>

    @Query("""
        SELECT h.id AS habit_id, h.name AS habit_name, h.unit_label AS unit_label,
            h.start_date AS start_date, h.created_at AS created_at, h.updated_at AS updated_at,
            s.id AS schedule_id, s.effective_from AS effective_from, s.target AS schedule_target,
            s.weekday_mask AS weekday_mask, s.state AS schedule_state,
            (SELECT COUNT(*) FROM habit_log l WHERE l.habit_id = h.id AND l.habit_date = :date) AS log_count,
            COALESCE(c.skip_remaining, 0) AS skip_remaining,
            COALESCE(z.tracking_zone_id, 'UTC') AS tracking_zone_id
        FROM habit h
        LEFT JOIN habit_settings z ON z.setting_id = 1
        LEFT JOIN habit_schedule s ON s.habit_id = h.id AND s.effective_from = (
            SELECT MAX(previous.effective_from) FROM habit_schedule previous
            WHERE previous.habit_id = h.id AND previous.effective_from <= :date
        )
        LEFT JOIN habit_day_choice c ON c.habit_id = h.id AND c.habit_date = :date
        WHERE h.id = :habitId
        LIMIT 1
    """)
    suspend fun widgetHabitState(habitId: String, date: String): HabitDayRow?

    @Query("SELECT * FROM habit_log WHERE habit_id = :habitId AND (:date IS NULL OR habit_date = :date) ORDER BY habit_date DESC, (occurred_at IS NULL), occurred_at DESC, recorded_at DESC, id DESC LIMIT :limit OFFSET :offset")
    suspend fun history(habitId: String, date: String?, offset: Int, limit: Int): List<HabitLogEntity>

    @Query("SELECT COUNT(*) FROM habit_log WHERE habit_id = :habitId AND (:date IS NULL OR habit_date = :date)")
    suspend fun historyCount(habitId: String, date: String?): Int

    @Query("SELECT (SELECT COUNT(*) FROM habit) + (SELECT COUNT(*) FROM habit_schedule) + (SELECT COUNT(*) FROM habit_log) + (SELECT COUNT(*) FROM habit_day_choice) + (SELECT COUNT(*) FROM habit_reminder) + (SELECT COUNT(*) FROM habit_reminder_runtime) + (SELECT COUNT(*) FROM habit_settings)")
    fun observeChanges(): Flow<Long>

    @Query("DELETE FROM habit WHERE id = :id")
    suspend fun deleteHabit(id: String): Int
}
