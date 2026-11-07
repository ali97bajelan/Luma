package com.luma.lifeos.data.focus

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusProductDao {
    @Query("SELECT * FROM focus_tag_catalog_state WHERE singleton_id = 1 LIMIT 1")
    suspend fun tagCatalogState(): FocusTagCatalogStateEntity?

    @Query("SELECT * FROM focus_tag_catalog_state WHERE singleton_id = 1 LIMIT 1")
    fun observeTagCatalogState(): Flow<FocusTagCatalogStateEntity?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTagCatalogStateIfAbsent(state: FocusTagCatalogStateEntity): Long

    @Update
    suspend fun updateTagCatalogState(state: FocusTagCatalogStateEntity): Int

    @Query("SELECT * FROM focus_tag_alias ORDER BY normalized_name")
    fun observeTagAliases(): Flow<List<FocusTagAliasEntity>>

    @Query("SELECT * FROM focus_tag_alias ORDER BY normalized_name")
    suspend fun tagAliases(): List<FocusTagAliasEntity>

    @Query("SELECT * FROM focus_tag_alias WHERE normalized_name = :normalizedName LIMIT 1")
    suspend fun tagAlias(normalizedName: String): FocusTagAliasEntity?

    @Query("UPDATE focus_tag_alias SET target_tag_id = :targetTagId WHERE target_tag_id = :sourceTagId")
    suspend fun retargetAliases(sourceTagId: String, targetTagId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceTagAlias(alias: FocusTagAliasEntity)

    @Query("DELETE FROM focus_tag_alias WHERE normalized_name = :normalizedName")
    suspend fun deleteTagAlias(normalizedName: String): Int

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

    @Query("SELECT * FROM focus_invitation_settings WHERE singleton_id = 1 LIMIT 1")
    suspend fun invitationSettings(): FocusInvitationSettingsEntity?

    @Query("SELECT * FROM focus_invitation_settings WHERE singleton_id = 1 LIMIT 1")
    fun observeInvitationSettings(): Flow<FocusInvitationSettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertInvitationSettingsIfAbsent(settings: FocusInvitationSettingsEntity): Long

    @Update
    suspend fun updateInvitationSettings(settings: FocusInvitationSettingsEntity): Int

    @Query("""SELECT * FROM focus_invitation_delivery
        WHERE database_generation = :generation AND schedule_revision = :revision AND local_date = :date
        LIMIT 1""")
    suspend fun invitationDelivery(generation: String, revision: Long, date: String): FocusInvitationDeliveryEntity?

    @Query("""SELECT * FROM focus_invitation_delivery
        WHERE database_generation = :generation AND schedule_revision = :revision
          AND state IN ('PENDING', 'SNOOZED')
        ORDER BY due_at, local_date""")
    suspend fun pendingInvitations(generation: String, revision: Long): List<FocusInvitationDeliveryEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertInvitationDelivery(delivery: FocusInvitationDeliveryEntity): Long

    @Update
    suspend fun updateInvitationDelivery(delivery: FocusInvitationDeliveryEntity): Int

    @Query("DELETE FROM focus_invitation_delivery WHERE database_generation != :generation")
    suspend fun deleteInvitationsFromOtherGenerations(generation: String): Int
}
