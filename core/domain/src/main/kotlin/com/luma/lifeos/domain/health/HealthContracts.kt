package com.luma.lifeos.domain.health

import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

interface HealthSource {
    suspend fun access(): HealthAccess
    /** One observation per requested window; null counts are successful missing-data responses. */
    suspend fun query(windows: List<StepWindow>): List<StepObservation>
}
class HealthAccessException(val access: HealthAccess) : Exception("Health access unavailable: $access")
class HealthImportChangedException : Exception("Health import configuration changed")
interface HealthRepository {
    fun observeState(): Flow<HealthState>
    suspend fun state(): HealthState
    suspend fun setEnabled(enabled: Boolean, zone: ZoneId)
    /** Compare revision so a delayed result cannot overwrite a newer configuration. */
    suspend fun recordStatus(status: HealthStatus, revision: Long)
    /** Atomic snapshot/checkpoint write. Check enabled, zone and revision inside the transaction. */
    suspend fun commit(rows: List<StepObservation>, syncedAt: Instant, expected: HealthState)
}
interface HealthIntegration {
    fun observeState(): Flow<HealthState>
    suspend fun setEnabled(enabled: Boolean)
    suspend fun refreshAccess()
    suspend fun importNow(): HealthStatus
}
