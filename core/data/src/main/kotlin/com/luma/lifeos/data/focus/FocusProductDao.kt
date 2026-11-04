package com.luma.lifeos.data.focus

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusProductDao {
    @Query("SELECT * FROM focus_goal_settings WHERE singleton_id = 1 LIMIT 1")
    suspend fun settings(): FocusGoalSettingsEntity?

    @Query("SELECT * FROM focus_goal_settings WHERE singleton_id = 1 LIMIT 1")
    fun observeSettings(): Flow<FocusGoalSettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSettingsIfAbsent(settings: FocusGoalSettingsEntity): Long

    @Update
    suspend fun updateSettings(settings: FocusGoalSettingsEntity): Int

    @Query("SELECT * FROM focus_goal_version WHERE settings_id = 1 ORDER BY effective_monday, zone_id, id")
    suspend fun versions(): List<FocusGoalVersionEntity>

    @Query("SELECT * FROM focus_goal_version WHERE settings_id = 1 ORDER BY effective_monday, zone_id, id")
    fun observeVersions(): Flow<List<FocusGoalVersionEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertVersion(version: FocusGoalVersionEntity)

    @Query("DELETE FROM focus_goal_version WHERE id = :id")
    suspend fun deleteVersion(id: String): Int

    @Query("SELECT * FROM focus_product_command_receipt WHERE database_generation = :generation AND command_id = :commandId LIMIT 1")
    suspend fun receipt(generation: String, commandId: String): FocusProductCommandReceiptEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReceipt(receipt: FocusProductCommandReceiptEntity)
}
