package com.luma.lifeos.data.calendar

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
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

    @RawQuery(observedEntities = [LifeEventEntity::class, MemoryEntity::class])
    fun observeMemoryMarkers(query: SupportSQLiteQuery): Flow<List<CalendarDateCountRow>>

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
        WITH saved_memories AS (
            SELECT event.id AS event_id, event.occurred_at,
                COALESCE(NULLIF(TRIM(event.title), ''), journal.title) AS title,
                substr(memory.note, 1, 160) AS memory_note,
                substr(journal.body, 1, 160) AS journal_body,
                substr(mood.note, 1, 160) AS mood_note,
                substr(event.note, 1, 160) AS event_note
            FROM life_event AS event
            INNER JOIN memory ON memory.life_event_id = event.id
            LEFT JOIN journal_entry AS journal ON journal.life_event_id = event.id
            LEFT JOIN mood_entry AS mood ON mood.life_event_id = event.id
            WHERE event.occurred_at >= :startMillis AND event.occurred_at < :endMillis
        )
        SELECT totals.total_count, saved.event_id, saved.title,
            saved.memory_note, saved.journal_body, saved.mood_note, saved.event_note
        FROM (SELECT COUNT(*) AS total_count FROM saved_memories) AS totals
        LEFT JOIN saved_memories AS saved ON 1 = 1
        ORDER BY saved.occurred_at DESC, saved.event_id ASC
        LIMIT :limit
    """)
    fun observeMemories(startMillis: Long, endMillis: Long, limit: Int): Flow<List<CalendarMemoryRow>>
}
