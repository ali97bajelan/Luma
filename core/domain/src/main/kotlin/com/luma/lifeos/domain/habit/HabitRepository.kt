package com.luma.lifeos.domain.habit

import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow

interface HabitRepository {
    fun observeDay(date: LocalDate): Flow<List<HabitDay>>
    fun observeChanges(): Flow<Unit>
    suspend fun trackingZone(): ZoneId?
    suspend fun get(id: UUID): HabitDefinition?
    suspend fun schedules(id: UUID): List<HabitSchedule>
    suspend fun getLog(id: UUID): HabitLog?
    suspend fun create(id: UUID, input: HabitSaveInput): HabitDefinition
    suspend fun rename(id: UUID, name: String, unitLabel: String?)
    suspend fun schedule(id: UUID, input: HabitScheduleInput): HabitSchedule
    suspend fun cancelPendingSchedule(id: UUID)
    suspend fun log(id: UUID, input: HabitLogInput): HabitLog
    suspend fun updateLog(id: UUID, input: HabitLogInput): HabitLog
    suspend fun deleteLog(id: UUID)
    suspend fun setSkipRemaining(id: UUID, date: LocalDate, skip: Boolean)
    suspend fun history(id: UUID, date: LocalDate?, offset: Int, limit: Int = 50): HabitHistoryPage
    suspend fun week(start: LocalDate, through: LocalDate): HabitWeek
    suspend fun deleteHabit(id: UUID)
}
