package com.luma.lifeos.data.calendar

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.luma.lifeos.data.entity.JournalEntryEntity
import com.luma.lifeos.data.entity.LifeEventEntity
import com.luma.lifeos.data.entity.MemoryEntity
import com.luma.lifeos.data.todo.TodoPageRow
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarDao {
    @Query("""
        SELECT due_date, COUNT(*) AS task_count,
            SUM(CASE WHEN due_date < :today THEN 1 ELSE 0 END) AS overdue_count
        FROM todo_task
        WHERE completed_at IS NULL AND due_date BETWEEN :start AND :through
        GROUP BY due_date
    """)
    fun observeTaskMarkers(start: String, through: String, today: String): Flow<List<CalendarTaskMarkerRow>>

    @Query("""
        WITH RECURSIVE calendar_dates(date) AS (
            SELECT :start
            UNION ALL
            SELECT date(date, '+1 day') FROM calendar_dates WHERE date < :through
        ), scheduled_habits(date, habit_id) AS (
            SELECT calendar_dates.date, h.id
            FROM calendar_dates
            JOIN habit h ON h.start_date <= calendar_dates.date
            JOIN habit_schedule schedule ON schedule.habit_id = h.id
                AND schedule.effective_from = (
                    SELECT MAX(previous.effective_from)
                    FROM habit_schedule previous
                    WHERE previous.habit_id = h.id
                        AND previous.effective_from <= calendar_dates.date
                )
            WHERE schedule.state = 'ACTIVE'
                AND (schedule.weekday_mask &
                    (1 << ((CAST(strftime('%w', calendar_dates.date) AS INTEGER) + 6) % 7))) <> 0
        )
        SELECT date, COUNT(DISTINCT habit_id) AS count
        FROM scheduled_habits
        GROUP BY date
    """)
    fun observeHabitMarkers(start: String, through: String): Flow<List<CalendarDateCountRow>>

    @RawQuery(observedEntities = [LifeEventEntity::class, JournalEntryEntity::class, MemoryEntity::class])
    fun observeJournalMarkers(query: SupportSQLiteQuery): Flow<List<CalendarDateCountRow>>

    @Query("""
        SELECT task.id AS task_id, task.title, task.note, task.due_date, task.completed_at,
            task.due_time_minutes, task.created_at, task.updated_at, task.revision_token,
            totals.total_count
        FROM (
            SELECT COUNT(*) AS total_count FROM todo_task
            WHERE completed_at IS NULL AND due_date IS NOT NULL AND (
                (:overdueOnly = 1 AND due_date < :today) OR
                (:overdueOnly = 0 AND (
                    (:date = :today AND due_date <= :today) OR
                    (:date <> :today AND due_date = :date)
                ))
            )
        ) AS totals
        LEFT JOIN todo_task AS task ON task.completed_at IS NULL AND task.due_date IS NOT NULL AND (
            (:overdueOnly = 1 AND task.due_date < :today) OR
            (:overdueOnly = 0 AND (
                (:date = :today AND task.due_date <= :today) OR
                (:date <> :today AND task.due_date = :date)
            ))
        )
        ORDER BY task.due_date ASC,
            CASE WHEN task.due_time_minutes IS NULL THEN 1 ELSE 0 END,
            task.due_time_minutes ASC, task.created_at ASC, task.id ASC
        LIMIT :limit
    """)
    fun observeTasks(date: String, today: String, overdueOnly: Boolean, limit: Int): Flow<List<TodoPageRow>>

    @Query("SELECT COUNT(*) FROM todo_task WHERE completed_at IS NULL AND due_date < :today")
    fun observeOverdueCount(today: String): Flow<Int>

    @Query("""
        SELECT totals.total_count AS total_count, latest.event_id AS latest_event_id,
            latest.title AS latest_title, latest.body AS latest_body
        FROM (
            SELECT COUNT(DISTINCT event.id) AS total_count
            FROM life_event AS event
            INNER JOIN journal_entry AS journal ON journal.life_event_id = event.id
            LEFT JOIN memory AS promoted ON promoted.life_event_id = event.id
            WHERE event.source = 'MANUAL'
                AND event.occurred_at >= :startMillis AND event.occurred_at < :endMillis
        ) AS totals
        LEFT JOIN (
            SELECT event.id AS event_id, journal.title AS title,
                substr(journal.body, 1, 160) AS body
            FROM life_event AS event
            INNER JOIN journal_entry AS journal ON journal.life_event_id = event.id
            LEFT JOIN memory AS promoted ON promoted.life_event_id = event.id
            WHERE event.source = 'MANUAL'
                AND event.occurred_at >= :startMillis AND event.occurred_at < :endMillis
            ORDER BY event.occurred_at DESC, event.id ASC
            LIMIT 1
        ) AS latest ON 1 = 1
    """)
    fun observeReflections(startMillis: Long, endMillis: Long): Flow<CalendarReflectionsRow>
}
