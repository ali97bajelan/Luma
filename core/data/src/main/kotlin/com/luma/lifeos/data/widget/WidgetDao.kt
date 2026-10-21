package com.luma.lifeos.data.widget

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface WidgetDao {
    @Query("SELECT * FROM widget_instance WHERE widget_id = :widgetId")
    suspend fun instance(widgetId: Int): WidgetInstanceEntity?

    @Query("SELECT * FROM widget_command WHERE token = :token")
    suspend fun command(token: String): WidgetCommandEntity?

    @Query("SELECT * FROM widget_command WHERE widget_id = :widgetId AND status = 'READY'")
    suspend fun readyCommands(widgetId: Int): List<WidgetCommandEntity>

    @Insert
    suspend fun insertCommand(command: WidgetCommandEntity)

    @Query("UPDATE widget_command SET status = :status, outcome_json = :outcomeJson WHERE token = :token AND status = 'READY'")
    suspend fun finishReadyCommand(token: String, status: String, outcomeJson: String): Int

    @Query("DELETE FROM widget_command WHERE created_at < :cutoff")
    suspend fun deleteCommandsBefore(cutoff: Long): Int

    @Upsert
    suspend fun upsertInstance(instance: WidgetInstanceEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertInstanceIfAbsent(instance: WidgetInstanceEntity): Long

    @Query("DELETE FROM widget_command WHERE widget_id = :widgetId")
    suspend fun deleteCommands(widgetId: Int)

    @Query("DELETE FROM widget_command")
    suspend fun deleteAllCommands()

    @Query("UPDATE widget_instance SET feedback_json = NULL WHERE widget_id = :widgetId")
    suspend fun clearFeedback(widgetId: Int): Int

    @Query("UPDATE widget_instance SET feedback_json = :feedbackJson WHERE widget_id = :widgetId")
    suspend fun updateFeedback(widgetId: Int, feedbackJson: String?): Int

    @Query("UPDATE widget_instance SET show_details = 1, feedback_json = NULL")
    suspend fun resetInstancesAfterRestore()

    @Query("DELETE FROM widget_instance WHERE widget_id = :widgetId")
    suspend fun deleteInstance(widgetId: Int): Int
}
