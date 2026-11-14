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
    @ColumnInfo(name = "importance_code") val importanceCode: Int = 0,
    @ColumnInfo(name = "parent_id") val parentId: String? = null,
    @ColumnInfo(name = "sibling_position") val siblingPosition: Long? = null,
    @ColumnInfo(name = "family_revision_token") val familyRevisionToken: String? = null,
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
            importanceCode = importanceCode,
            parentId = parentId,
            siblingPosition = siblingPosition,
            familyRevisionToken = requireNotNull(familyRevisionToken),
        )
    }
}

/** Family-aware projection with batched parent and full progress context. */
data class TodoFamilyPageRow(
    @ColumnInfo(name = "task_id") val taskId: String?,
    val title: String?,
    val note: String?,
    @ColumnInfo(name = "due_date") val dueDate: String?,
    @ColumnInfo(name = "completed_at") val completedAt: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long?,
    @ColumnInfo(name = "revision_token") val revisionToken: String?,
    @ColumnInfo(name = "due_time_minutes") val dueTimeMinutes: Int?,
    @ColumnInfo(name = "importance_code") val importanceCode: Int?,
    @ColumnInfo(name = "parent_id") val parentId: String?,
    @ColumnInfo(name = "sibling_position") val siblingPosition: Long?,
    @ColumnInfo(name = "family_revision_token") val familyRevisionToken: String?,
    @ColumnInfo(name = "parent_title") val parentTitle: String?,
    @ColumnInfo(name = "child_total") val childTotal: Int?,
    @ColumnInfo(name = "child_completed") val childCompleted: Int?,
    @ColumnInfo(name = "total_count") val totalCount: Int,
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
            importanceCode = requireNotNull(importanceCode),
            parentId = parentId,
            siblingPosition = siblingPosition,
            familyRevisionToken = requireNotNull(familyRevisionToken),
        )
    }
}

data class TodoReminderCandidate(
    @ColumnInfo(name = "task_id") val taskId: String,
    @ColumnInfo(name = "task_revision") val taskRevision: String,
)

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

    @Query("SELECT * FROM todo_task WHERE id = :rootId OR parent_id = :rootId ORDER BY sibling_position, id")
    suspend fun family(rootId: String): List<TodoEntity>

    @Query("SELECT * FROM todo_task WHERE parent_id = :parentId ORDER BY sibling_position, id")
    suspend fun children(parentId: String): List<TodoEntity>

    @Query("SELECT COUNT(*) FROM todo_task WHERE parent_id = :parentId")
    suspend fun childCount(parentId: String): Int

    @Query("SELECT COUNT(*) FROM todo_task WHERE parent_id = :parentId AND completed_at IS NULL")
    suspend fun openChildCount(parentId: String): Int

    @Query("""
        SELECT id AS task_id, revision_token AS task_revision FROM todo_task
        WHERE completed_at IS NULL AND due_date IS NOT NULL AND due_time_minutes IS NOT NULL
        ORDER BY id
    """)
    suspend fun reminderCandidates(): List<TodoReminderCandidate>

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

    @Query("""
        SELECT COUNT(*) FROM todo_task
        WHERE completed_at IS NULL
          AND (:escapedQuery = '' OR title LIKE '%' || :escapedQuery || '%' ESCAPE '\')
    """)
    suspend fun countOpenSearch(escapedQuery: String): Int

    @Query("""
        SELECT id AS task_id, title, note, due_date, completed_at, created_at, updated_at,
            revision_token, 0 AS total_count, due_time_minutes, importance_code, parent_id,
            sibling_position, family_revision_token
        FROM todo_task
        WHERE completed_at IS NULL
          AND (:escapedQuery = '' OR title LIKE '%' || :escapedQuery || '%' ESCAPE '\')
        ORDER BY created_at DESC, id DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun searchOpenPage(escapedQuery: String, offset: Int, limit: Int): List<TodoPageRow>

    @Query("""
        SELECT COUNT(*) FROM todo_task
        WHERE completed_at IS NULL
          AND (:escapedQuery = '' OR title LIKE '%' || :escapedQuery || '%' ESCAPE '\')
    """)
    suspend fun countOpenSearchRows(escapedQuery: String): Int

    @Query("""
        SELECT task.id AS task_id, task.title, task.note, task.due_date, task.completed_at,
            task.created_at, task.updated_at, task.revision_token, task.due_time_minutes,
            task.importance_code, task.parent_id, task.sibling_position, task.family_revision_token,
            parent.title AS parent_title, progress.child_total AS child_total,
            progress.child_completed AS child_completed, 0 AS total_count
        FROM todo_task AS task
        LEFT JOIN todo_task AS parent ON parent.id = task.parent_id
        LEFT JOIN (
            SELECT parent_id, COUNT(*) AS child_total,
                SUM(CASE WHEN completed_at IS NOT NULL THEN 1 ELSE 0 END) AS child_completed
            FROM todo_task
            WHERE parent_id IS NOT NULL
            GROUP BY parent_id
        ) AS progress ON progress.parent_id = task.id
        WHERE task.completed_at IS NULL
          AND (:escapedQuery = '' OR task.title LIKE '%' || :escapedQuery || '%' ESCAPE '\')
        ORDER BY task.created_at DESC, task.id DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun searchOpenRowsPage(
        escapedQuery: String,
        offset: Int,
        limit: Int,
    ): List<TodoFamilyPageRow>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: TodoEntity)

    @Query("""
        SELECT task.id AS task_id, task.title, task.note, task.due_date, task.completed_at,
            task.due_time_minutes, task.created_at, task.updated_at, task.revision_token,
            counts.total_count, task.importance_code, task.parent_id, task.sibling_position,
            task.family_revision_token
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
            task.due_time_minutes, task.created_at, task.updated_at, task.revision_token,
            counts.total_count, task.importance_code, task.parent_id, task.sibling_position,
            task.family_revision_token
        FROM (SELECT COUNT(*) AS total_count FROM todo_task WHERE completed_at IS NOT NULL) AS counts
        LEFT JOIN todo_task AS task ON task.completed_at IS NOT NULL
        ORDER BY task.completed_at DESC, task.id ASC
        LIMIT :limit
    """)
    fun observeCompleted(limit: Int): Flow<List<TodoPageRow>>

    @Query("""
        SELECT task.id AS task_id, task.title, task.note, task.due_date, task.completed_at,
            task.due_time_minutes, task.created_at, task.updated_at, task.revision_token,
            counts.total_count, task.importance_code, task.parent_id, task.sibling_position,
            task.family_revision_token
        FROM (
            SELECT COUNT(*) AS total_count FROM todo_task
            WHERE completed_at IS NULL AND due_date <= :date
        ) AS counts
        LEFT JOIN todo_task AS task ON task.completed_at IS NULL AND task.due_date <= :date
        ORDER BY task.due_date ASC, CASE WHEN task.due_time_minutes IS NULL THEN 1 ELSE 0 END,
            task.due_time_minutes ASC, task.created_at ASC, task.id ASC
        LIMIT :limit
    """)
    fun observeToday(date: String, limit: Int): Flow<List<TodoPageRow>>

    @Query("""
        SELECT task.id AS task_id, task.title, task.note, task.due_date, task.completed_at,
            task.due_time_minutes, task.created_at, task.updated_at, task.revision_token,
            counts.total_count, task.importance_code, task.parent_id, task.sibling_position,
            task.family_revision_token
        FROM (
            SELECT COUNT(*) AS total_count FROM todo_task
            WHERE ((:completed = 1 AND completed_at IS NOT NULL)
                OR (:completed = 0 AND completed_at IS NULL))
              AND (:importanceCode IS NULL OR importance_code = :importanceCode)
        ) AS counts
        LEFT JOIN todo_task AS task
            ON ((:completed = 1 AND task.completed_at IS NOT NULL)
                OR (:completed = 0 AND task.completed_at IS NULL))
            AND (:importanceCode IS NULL OR task.importance_code = :importanceCode)
        ORDER BY
            CASE WHEN :sortByImportance = 1 THEN task.importance_code END DESC,
            CASE WHEN :sortByImportance = 1 AND task.due_date IS NULL THEN 1 ELSE 0 END ASC,
            CASE WHEN :sortByImportance = 1 THEN task.due_date END ASC,
            CASE WHEN :sortByImportance = 1 AND task.due_time_minutes IS NULL THEN 1 ELSE 0 END ASC,
            CASE WHEN :sortByImportance = 1 THEN task.due_time_minutes END ASC,
            CASE WHEN :completed = 1 AND :sortByImportance = 0 THEN task.completed_at END DESC,
            CASE WHEN :completed = 0 AND :sortByImportance = 0 AND task.due_date IS NULL THEN 1 ELSE 0 END ASC,
            CASE WHEN :completed = 0 AND :sortByImportance = 0 THEN task.due_date END ASC,
            CASE WHEN :completed = 0 AND :sortByImportance = 0 AND task.due_time_minutes IS NULL THEN 1 ELSE 0 END ASC,
            CASE WHEN :completed = 0 AND :sortByImportance = 0 THEN task.due_time_minutes END ASC,
            CASE WHEN (:completed = 0 AND :sortByImportance = 0) OR :sortByImportance = 1
                THEN task.created_at END ASC,
            task.id ASC
        LIMIT :limit
    """)
    fun observeFiltered(
        completed: Boolean,
        importanceCode: Int?,
        sortByImportance: Boolean,
        limit: Int,
    ): Flow<List<TodoPageRow>>

    @Query("""
        SELECT task.id AS task_id, task.title, task.note, task.due_date, task.completed_at,
            task.due_time_minutes, task.created_at, task.updated_at, task.revision_token,
            task.importance_code, task.parent_id, task.sibling_position, task.family_revision_token,
            parent.title AS parent_title, progress.child_total AS child_total,
            progress.child_completed AS child_completed, counts.total_count
        FROM (
            SELECT COUNT(*) AS total_count
            FROM todo_task
            WHERE ((:completed = 1 AND completed_at IS NOT NULL)
                OR (:completed = 0 AND completed_at IS NULL))
              AND (:importanceCode IS NULL OR importance_code = :importanceCode)
              AND (:importanceCode IS NOT NULL OR parent_id IS NULL)
        ) AS counts
        LEFT JOIN todo_task AS task
            ON ((:completed = 1 AND task.completed_at IS NOT NULL)
                OR (:completed = 0 AND task.completed_at IS NULL))
            AND (:importanceCode IS NULL OR task.importance_code = :importanceCode)
            AND (:importanceCode IS NOT NULL OR task.parent_id IS NULL)
        LEFT JOIN todo_task AS parent ON parent.id = task.parent_id
        LEFT JOIN (
            SELECT parent_id, COUNT(*) AS child_total,
                SUM(CASE WHEN completed_at IS NOT NULL THEN 1 ELSE 0 END) AS child_completed
            FROM todo_task
            WHERE parent_id IS NOT NULL
            GROUP BY parent_id
        ) AS progress ON progress.parent_id = task.id
        ORDER BY
            CASE WHEN :sortByImportance = 1 THEN task.importance_code END DESC,
            CASE WHEN :sortByImportance = 1 AND task.due_date IS NULL THEN 1 ELSE 0 END ASC,
            CASE WHEN :sortByImportance = 1 THEN task.due_date END ASC,
            CASE WHEN :sortByImportance = 1 AND task.due_time_minutes IS NULL THEN 1 ELSE 0 END ASC,
            CASE WHEN :sortByImportance = 1 THEN task.due_time_minutes END ASC,
            CASE WHEN :sortByImportance = 0 AND :completed = 1 THEN task.completed_at END DESC,
            CASE WHEN :sortByImportance = 0 AND :completed = 0 AND task.due_date IS NULL THEN 1 ELSE 0 END ASC,
            CASE WHEN :sortByImportance = 0 AND :completed = 0 THEN task.due_date END ASC,
            CASE WHEN :sortByImportance = 0 AND :completed = 0 AND task.due_time_minutes IS NULL THEN 1 ELSE 0 END ASC,
            CASE WHEN :sortByImportance = 0 AND :completed = 0 THEN task.due_time_minutes END ASC,
            CASE WHEN (:completed = 0 AND :sortByImportance = 0) OR :sortByImportance = 1
                THEN task.created_at END ASC,
            task.id ASC
        LIMIT :limit
    """)
    fun observeRows(
        completed: Boolean,
        importanceCode: Int?,
        sortByImportance: Boolean,
        limit: Int,
    ): Flow<List<TodoFamilyPageRow>>

    @Query("""
        SELECT task.id AS task_id, task.title, task.note, task.due_date, task.completed_at,
            task.created_at, task.updated_at, task.revision_token, task.due_time_minutes,
            task.importance_code, task.parent_id, task.sibling_position, task.family_revision_token,
            parent.title AS parent_title, counts.total_count AS child_total,
            counts.completed_count AS child_completed, counts.total_count
        FROM (
            SELECT COUNT(*) AS total_count,
                COALESCE(SUM(CASE WHEN completed_at IS NOT NULL THEN 1 ELSE 0 END), 0) AS completed_count
            FROM todo_task WHERE parent_id = :parentId
        ) AS counts
        LEFT JOIN todo_task AS task ON task.parent_id = :parentId
        LEFT JOIN todo_task AS parent ON parent.id = task.parent_id
        ORDER BY task.sibling_position ASC, task.id ASC
        LIMIT :limit
    """)
    fun observeChildren(parentId: String, limit: Int): Flow<List<TodoFamilyPageRow>>

    @Query("""
        SELECT task.id AS task_id, task.title, task.note, task.due_date, task.completed_at,
            task.created_at, task.updated_at, task.revision_token, task.due_time_minutes,
            task.importance_code, task.parent_id, task.sibling_position, task.family_revision_token,
            parent.title AS parent_title, progress.child_total AS child_total,
            progress.child_completed AS child_completed, counts.total_count
        FROM (
            SELECT COUNT(*) AS total_count FROM todo_task
            WHERE completed_at IS NULL AND due_date <= :date
        ) AS counts
        LEFT JOIN todo_task AS task
            ON task.completed_at IS NULL AND task.due_date <= :date
        LEFT JOIN todo_task AS parent ON parent.id = task.parent_id
        LEFT JOIN (
            SELECT parent_id, COUNT(*) AS child_total,
                SUM(CASE WHEN completed_at IS NOT NULL THEN 1 ELSE 0 END) AS child_completed
            FROM todo_task
            WHERE parent_id IS NOT NULL
            GROUP BY parent_id
        ) AS progress ON progress.parent_id = task.id
        ORDER BY task.due_date ASC, CASE WHEN task.due_time_minutes IS NULL THEN 1 ELSE 0 END,
            task.due_time_minutes ASC, task.created_at ASC, task.id ASC
        LIMIT :limit
    """)
    fun observeTodayRows(date: String, limit: Int): Flow<List<TodoFamilyPageRow>>

    @Query("""
        UPDATE todo_task SET title = :title, note = :note, due_date = :dueDate,
            due_time_minutes = :dueTimeMinutes, importance_code = :importanceCode,
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
        importanceCode: Int,
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

    @Query("""
        UPDATE todo_task SET family_revision_token = :nextRevision
        WHERE (id = :rootId OR parent_id = :rootId) AND family_revision_token = :expectedRevision
    """)
    suspend fun rotateFamilyRevision(rootId: String, expectedRevision: String, nextRevision: String): Int

    @Query("""
        UPDATE todo_task SET sibling_position = :position, revision_token = :nextRevision,
            updated_at = :updatedAt
        WHERE id = :id AND revision_token = :expectedRevision
    """)
    suspend fun updateSiblingPosition(
        id: String,
        expectedRevision: String,
        nextRevision: String,
        position: Long,
        updatedAt: Long,
    ): Int

    @Query("DELETE FROM todo_task WHERE id = :id AND revision_token = :expectedRevision")
    suspend fun delete(id: String, expectedRevision: String): Int
}
