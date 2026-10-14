package com.luma.lifeos.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.luma.lifeos.data.entity.UsageSessionEntity
import com.luma.lifeos.data.entity.UsageSessionWindowEntity

@Dao
interface UsageSessionDao {
    @Upsert suspend fun upsert(sessions: List<UsageSessionEntity>)
    @Upsert suspend fun upsertWindow(window: UsageSessionWindowEntity)
    @Query("SELECT * FROM usage_session WHERE started_at < :end AND ended_at > :start ORDER BY started_at, package_name")
    suspend fun forDay(start: Long, end: Long): List<UsageSessionEntity>
    @Query("SELECT * FROM usage_session WHERE started_at < :end AND ended_at > :start ORDER BY started_at, package_name")
    suspend fun forRange(start: Long, end: Long): List<UsageSessionEntity>
    @Query("SELECT * FROM usage_session_window WHERE query_start < :end AND query_end > :start ORDER BY query_start, query_end")
    suspend fun windows(start: Long, end: Long): List<UsageSessionWindowEntity>
}
