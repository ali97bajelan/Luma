package com.luma.lifeos.domain.repository

import com.luma.lifeos.domain.summary.DailySummary
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

interface DailySummaryRepository {
    fun observe(date: LocalDate, zone: ZoneId): Flow<DailySummary>
    suspend fun get(date: LocalDate, zone: ZoneId): DailySummary
    suspend fun recalculate(date: LocalDate, zone: ZoneId): DailySummary
}
