package com.luma.lifeos.data.widget

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface WidgetDao {
    @Query("SELECT * FROM widget_instance WHERE widget_id = :widgetId")
    suspend fun instance(widgetId: Int): WidgetInstanceEntity?

    @Upsert
    suspend fun upsertInstance(instance: WidgetInstanceEntity)

    @Query("DELETE FROM widget_command WHERE widget_id = :widgetId")
    suspend fun deleteCommands(widgetId: Int)

    @Query("DELETE FROM widget_command")
    suspend fun deleteAllCommands()

    @Query("UPDATE widget_instance SET feedback_json = NULL WHERE widget_id = :widgetId")
    suspend fun clearFeedback(widgetId: Int): Int

    @Query("UPDATE widget_instance SET show_details = 0, feedback_json = NULL")
    suspend fun resetInstancesAfterRestore()

    @Query("DELETE FROM widget_instance WHERE widget_id = :widgetId")
    suspend fun deleteInstance(widgetId: Int): Int
}
