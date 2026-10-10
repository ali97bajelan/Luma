package com.luma.lifeos.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.luma.lifeos.data.entity.EventWithDetails
import com.luma.lifeos.data.entity.LifeEventEntity
import com.luma.lifeos.domain.model.EventSource
import kotlinx.coroutines.flow.Flow

/** Low-level operations: application writes go through the transactional repositories. */
@Dao
interface LifeEventDao {
    @Query("SELECT * FROM life_event WHERE occurred_at >= :start AND occurred_at < :end ORDER BY occurred_at, id")
    fun observe(start: Long, end: Long): Flow<List<LifeEventEntity>>

    @Transaction
    @Query("SELECT * FROM life_event WHERE occurred_at >= :start AND occurred_at < :end ORDER BY occurred_at, id")
    fun observeDetails(start: Long, end: Long): Flow<List<EventWithDetails>>

    @Transaction
    @Query("SELECT * FROM life_event WHERE occurred_at >= :start AND occurred_at < :end ORDER BY occurred_at, id")
    suspend fun details(start: Long, end: Long): List<EventWithDetails>

    @Query("SELECT * FROM life_event WHERE id = :id")
    suspend fun get(id: String): LifeEventEntity?

    @Transaction
    @Query("SELECT * FROM life_event WHERE id = :id")
    suspend fun getDetails(id: String): EventWithDetails?

    @Query("SELECT * FROM life_event WHERE source = :source AND source_ref = :reference LIMIT 1")
    suspend fun findBySource(source: EventSource, reference: String): LifeEventEntity?

    @Insert suspend fun insert(event: LifeEventEntity)
    @Update suspend fun update(event: LifeEventEntity)
    @Query("DELETE FROM life_event WHERE id = :id") suspend fun delete(id: String)
}
