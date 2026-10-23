package com.luma.lifeos.data.todo

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class TodoPageRow(
    @ColumnInfo(name = "task_id") val taskId: String?,
    val title: String?,
    val note: String?,
    @ColumnInfo(name = "due_date") val dueDate: String?,
    @ColumnInfo(name = "completed_at") val completedAt: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long?,
    @ColumnInfo(name = "revision_token") val revisionToken: String?,
    @ColumnInfo(name = "total_count") val totalCount: Int,
    @ColumnInfo(name = "due_time_minutes") val dueTimeMinutes: Int? = null,
) {
    fun toEntityOrNull(): TodoEntity? = taskId?.let { id ->
        TodoEntity(
            id = id,
            title = requireNotNull(title),
            note = note,
            dueDate = dueDate,
            completedAt = completedAt,
            createdAt = requireNotNull(createdAt),
            updatedAt = requireNotNull(updatedAt),
            revisionToken = requireNotNull(revisionToken),
            dueTimeMinutes = dueTimeMinutes,
        )
    }
}

data class WidgetTodoRow(
    @ColumnInfo(name = "task_id") val taskId: String,
    val title: String,
    @ColumnInfo(name = "due_date") val dueDate: String,
    @ColumnInfo(name = "revision_token") val revisionToken: String,
    @ColumnInfo(name = "due_time_minutes") val dueTimeMinutes: Int? = null,
)

data class WidgetTodoSummary(
    val total: Int,
    val hasAnyItems: Boolean,
)

@Dao
interface TodoDao {
    @Query("SELECT * FROM todo_task WHERE id = :id")
    suspend fun find(id: String): TodoEntity?

    @Query("""
        SELECT COUNT(*) AS total, EXISTS(SELECT 1 FROM todo_task) AS hasAnyItems
        FROM todo_task WHERE completed_at IS NULL AND due_date <= :date
    """)
    suspend fun widgetTodaySummary(date: String): WidgetTodoSummary

    @Query("""
        SELECT id AS task_id, title, due_date, revision_token, due_time_minutes
        FROM todo_task
        WHERE completed_at IS NULL AND due_date <= :date
        ORDER BY due_date ASC, CASE WHEN due_time_minutes IS NULL THEN 1 ELSE 0 END,
            due_time_minutes ASC, created_at ASC, id ASC
        LIMIT :limit
    """)
    suspend fun widgetTodayRows(date: String, limit: Int): List<WidgetTodoRow>

    @Query("""
        SELECT id AS task_id, title, due_date, revision_token, due_time_minutes
        FROM todo_task
        WHERE completed_at IS NULL AND due_date <= :date
        ORDER BY due_date ASC, CASE WHEN due_time_minutes IS NULL THEN 1 ELSE 0 END,
            due_time_minutes ASC, created_at ASC, id ASC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun widgetTodayRowsPage(date: String, limit: Int, offset: Int): List<WidgetTodoRow>

    @Query("""
        SELECT id AS task_id, title, due_date, revision_token, due_time_minutes
        FROM todo_task
        WHERE id = :taskId AND completed_at IS NULL AND due_date <= :date
        LIMIT 1
    """)
    suspend fun widgetTodayRow(taskId: String, date: String): WidgetTodoRow?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: TodoEntity)

    @Query("""
        SELECT task.id AS task_id, task.title, task.note, task.due_date, task.completed_at,
            task.due_time_minutes,
            task.created_at, task.updated_at, task.revision_token, counts.total_count
        FROM (SELECT COUNT(*) AS total_count FROM todo_task WHERE completed_at IS NULL) AS counts
        LEFT JOIN todo_task AS task ON task.completed_at IS NULL
        ORDER BY CASE WHEN task.due_date IS NULL THEN 1 ELSE 0 END,
            task.due_date ASC, CASE WHEN task.due_time_minutes IS NULL THEN 1 ELSE 0 END,
            task.due_time_minutes ASC, task.created_at ASC, task.id ASC
        LIMIT :limit
    """)
    fun observeOpen(limit: Int): Flow<List<TodoPageRow>>

    @Query("""
        SELECT task.id AS task_id, task.title, task.note, task.due_date, task.completed_at,
            task.due_time_minutes,
            task.created_at, task.updated_at, task.revision_token, counts.total_count
        FROM (SELECT COUNT(*) AS total_count FROM todo_task WHERE completed_at IS NOT NULL) AS counts
        LEFT JOIN todo_task AS task ON task.completed_at IS NOT NULL
        ORDER BY task.completed_at DESC, task.id ASC
        LIMIT :limit
    """)
    fun observeCompleted(limit: Int): Flow<List<TodoPageRow>>

    @Query("""
        SELECT task.id AS task_id, task.title, task.note, task.due_date, task.completed_at,
            task.due_time_minutes,
            task.created_at, task.updated_at, task.revision_token, counts.total_count
        FROM (SELECT COUNT(*) AS total_count FROM todo_task
            WHERE completed_at IS NULL AND due_date <= :date) AS counts
        LEFT JOIN todo_task AS task ON task.completed_at IS NULL AND task.due_date <= :date
        ORDER BY task.due_date ASC, CASE WHEN task.due_time_minutes IS NULL THEN 1 ELSE 0 END,
            task.due_time_minutes ASC, task.created_at ASC, task.id ASC
        LIMIT :limit
    """)
    fun observeToday(date: String, limit: Int): Flow<List<TodoPageRow>>

    @Query("""
        UPDATE todo_task SET title = :title, note = :note, due_date = :dueDate,
            due_time_minutes = :dueTimeMinutes,
            updated_at = :updatedAt, revision_token = :nextRevision
        WHERE id = :id AND revision_token = :expectedRevision
    """)
    suspend fun update(
        id: String,
        expectedRevision: String,
        nextRevision: String,
        title: String,
        note: String?,
        dueDate: String?,
        dueTimeMinutes: Int?,
        updatedAt: Long,
    ): Int

    @Query("""
        UPDATE todo_task SET completed_at = :completedAt, updated_at = :updatedAt,
            revision_token = :nextRevision
        WHERE id = :id AND revision_token = :expectedRevision
    """)
    suspend fun setCompletion(
        id: String,
        expectedRevision: String,
        nextRevision: String,
        completedAt: Long?,
        updatedAt: Long,
    ): Int

    @Query("DELETE FROM todo_task WHERE id = :id AND revision_token = :expectedRevision")
    suspend fun delete(id: String, expectedRevision: String): Int
}
