package com.luma.lifeos.data.todo

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface TaskReminderDao {
    @Query("SELECT * FROM task_reminder WHERE task_id = :taskId")
    suspend fun reminder(taskId: String): TaskReminderEntity?

    @Query("SELECT * FROM task_reminder ORDER BY task_id")
    suspend fun reminders(): List<TaskReminderEntity>

    @Upsert
    suspend fun upsert(reminder: TaskReminderEntity)

    @Query("DELETE FROM task_reminder WHERE task_id = :taskId")
    suspend fun delete(taskId: String): Int

    @Query("""
        UPDATE task_reminder SET task_revision = :taskRevision, revision_token = :revision,
            consumed_action_token = NULL, last_delivered_slot = NULL, snooze_slot = NULL,
            snooze_until = NULL, needs_reconcile = 1
        WHERE task_id = :taskId
    """)
    suspend fun onTaskRevisionChanged(taskId: String, taskRevision: String, revision: String): Int

    @Query("""
        UPDATE task_reminder SET consumed_action_token = :actionToken, needs_reconcile = 1
        WHERE task_id = :taskId AND revision_token = :revision
    """)
    suspend fun consumeAction(taskId: String, revision: String, actionToken: String): Int

    @Query("""
        UPDATE task_reminder SET last_delivered_slot = :slotKey, consumed_action_token = NULL,
            needs_reconcile = 0
        WHERE task_id = :taskId AND revision_token = :revision AND enabled = 1
    """)
    suspend fun markDelivered(taskId: String, revision: String, slotKey: String): Int

    @Query("""
        UPDATE task_reminder SET snooze_slot = :slotKey, snooze_until = :until,
            consumed_action_token = :actionToken, needs_reconcile = 1
        WHERE task_id = :taskId AND revision_token = :revision AND enabled = 1
            AND consumed_action_token IS NULL
    """)
    suspend fun snooze(taskId: String, revision: String, slotKey: String, until: Long, actionToken: String): Int

    @Query("""
        UPDATE task_reminder SET needs_reconcile = 0
        WHERE task_id = :taskId AND revision_token = :revision
    """)
    suspend fun markReconciled(taskId: String, revision: String): Int
}
