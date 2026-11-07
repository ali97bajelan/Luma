package com.luma.lifeos.data.focus

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusInvitationDao {
    @Query("SELECT * FROM focus_invitation_settings WHERE singleton_id = 1 LIMIT 1")
    suspend fun settings(): FocusInvitationSettingsEntity?

    @Query("SELECT * FROM focus_invitation_settings WHERE singleton_id = 1 LIMIT 1")
    fun observeSettings(): Flow<FocusInvitationSettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSettingsIfAbsent(settings: FocusInvitationSettingsEntity): Long

    @Update
    suspend fun updateSettings(settings: FocusInvitationSettingsEntity): Int

    @Query("""SELECT * FROM focus_invitation_delivery
        WHERE database_generation = :generation AND schedule_revision = :revision AND local_date = :date
        LIMIT 1""")
    suspend fun delivery(generation: String, revision: Long, date: String): FocusInvitationDeliveryEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDelivery(delivery: FocusInvitationDeliveryEntity): Long

    @Update
    suspend fun updateDelivery(delivery: FocusInvitationDeliveryEntity): Int

    @Query("DELETE FROM focus_invitation_delivery WHERE database_generation = :generation")
    suspend fun deleteDeliveries(generation: String): Int

    @Query("DELETE FROM focus_invitation_delivery WHERE database_generation != :generation")
    suspend fun deleteOtherGenerations(generation: String): Int

    @Query("""SELECT * FROM focus_product_command_receipt
        WHERE database_generation = :generation AND command_id = :commandId LIMIT 1""")
    suspend fun receipt(generation: String, commandId: String): FocusProductCommandReceiptEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReceipt(receipt: FocusProductCommandReceiptEntity)
}
