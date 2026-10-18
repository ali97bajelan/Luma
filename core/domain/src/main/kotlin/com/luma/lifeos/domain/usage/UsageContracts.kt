package com.luma.lifeos.domain.usage

import com.luma.lifeos.domain.time.TimeRange
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

interface UsageSource {
    suspend fun access(): UsageAccess
    suspend fun query(range: TimeRange): List<UsageBucket>
    suspend fun querySessions(range: TimeRange): UsageSessionBatch? = null
    /** Native Android sources must supply wall/monotonic/boot identity or fail safely. */
    suspend fun clockAnchor(): UsageClockAnchor? = null
}
class UsageAccessException(val access: UsageAccess) : Exception("Usage source unavailable: $access")
interface UsageScheduler { fun schedule(); fun cancel() }
interface UsageRepository {
    fun observeState(): Flow<UsageImportState>
    suspend fun state(): UsageImportState
    suspend fun setEnabled(enabled: Boolean)
    suspend fun recordStatus(status: UsageSyncStatus)
    /** Retains successful checkpoint and history, and excludes native history before this boundary. */
    suspend fun recordClockSafety(anchor: UsageClockAnchor, minimumBucketStart: Instant)
    /** Atomic buckets/checkpoint commit; rejects writes while disabled. */
    suspend fun commit(buckets: List<UsageBucket>, syncedAt: Instant, checkpoint: Instant, anchor: UsageClockAnchor? = null)
    suspend fun commitImport(buckets: List<UsageBucket>, sessions: UsageSessionBatch?, syncedAt: Instant,
        checkpoint: Instant, anchor: UsageClockAnchor? = null) {
        check(sessions == null) { "Session persistence is unavailable" }
        commit(buckets, syncedAt, checkpoint, anchor)
    }
    fun observeDaily(date: LocalDate, zone: ZoneId): Flow<DailyUsageReport?>
}
interface UsageIntegration {
    fun observeState(): Flow<UsageImportState>
    fun observeDaily(date: LocalDate, zone: ZoneId): Flow<DailyUsageReport?>
    /** Read current platform access without changing import configuration or reading records. */
    suspend fun access(): UsageAccess
    suspend fun refreshAccess()
    suspend fun setEnabled(enabled: Boolean)
    suspend fun importNow(): UsageSyncOutcome
}
