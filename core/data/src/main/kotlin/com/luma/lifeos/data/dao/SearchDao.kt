package com.luma.lifeos.data.dao

import androidx.room.Dao
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.sqlite.db.SupportSQLiteQuery
import com.luma.lifeos.data.entity.EventWithDetails

@Dao
interface SearchDao {
    @Transaction @RawQuery
    suspend fun page(query: SupportSQLiteQuery): List<EventWithDetails>
    @RawQuery suspend fun count(query: SupportSQLiteQuery): Int
}
