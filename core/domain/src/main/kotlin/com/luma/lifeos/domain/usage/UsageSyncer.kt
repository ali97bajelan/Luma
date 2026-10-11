package com.luma.lifeos.domain.usage

import com.luma.lifeos.domain.time.TimeRange
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One instance shared by foreground imports and background workers. */
class UsageSyncer(private val source: UsageSource, private val repository: UsageRepository,
    private val scheduler: UsageScheduler, private val clock: Clock = Clock.systemUTC()) : UsageIntegration {
    private val mutex = Mutex()
    private val controls = Mutex()
    override fun observeState() = repository.observeState()
    override fun observeDaily(date: LocalDate, zone: ZoneId) = repository.observeDaily(date, zone)
    override suspend fun setEnabled(enabled: Boolean) {
        if (!enabled) {
            // Opt-out must not wait behind a platform query. The commit also checks this flag atomically.
            controls.withLock { repository.setEnabled(false); scheduler.cancel() }
        } else mutex.withLock {
            controls.withLock { repository.setEnabled(true) }
            refreshLocked()
        }
    }
    override suspend fun refreshAccess() = mutex.withLock { refreshLocked(); Unit }
    private suspend fun refreshLocked(): UsageAccess? {
        if (!repository.state().enabled) { scheduler.cancel(); return null }
        val access = try { source.access() } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            controls.withLock {
                if (repository.state().enabled) { repository.recordStatus(UsageSyncStatus.ERROR); scheduler.schedule() }
            }
            return null
        }
        return controls.withLock {
            if (!repository.state().enabled) return@withLock null
            if (access == UsageAccess.GRANTED) {
                scheduler.schedule()
                val status = repository.state().status
                if (status !in setOf(UsageSyncStatus.READY, UsageSyncStatus.NO_DATA, UsageSyncStatus.ERROR, UsageSyncStatus.INTERVAL_CHANGED)) repository.recordStatus(UsageSyncStatus.READY)
            } else { repository.recordStatus(access.status()); scheduler.cancel() }
            access
        }
    }
    override suspend fun importNow(): UsageSyncOutcome = mutex.withLock {
        if (!repository.state().enabled) return@withLock UsageSyncOutcome.DISABLED
        try {
            val access = source.access()
            if (access != UsageAccess.GRANTED) return@withLock unavailable(access)
            val now = Instant.ofEpochMilli(clock.millis())
            val state = repository.state()
            val beforeClock = source.clockAnchor()
            if (beforeClock != null && state.clockAnchor?.let { usageClockDiscontinuity(it, beforeClock) } == true)
                return@withLock quarantine(beforeClock)
            val previous = state.checkpoint?.coerceAtMost(now)
            val start = previous?.minus(Duration.ofDays(2))?.coerceAtLeast(now.minus(Duration.ofDays(7)))
                ?: now.minus(Duration.ofDays(7))
            val rows = distinctUsageBuckets(source.query(TimeRange(start, now)))
            if (!repository.state().enabled) return@withLock UsageSyncOutcome.DISABLED
            val afterClock = source.clockAnchor()
            if (afterClock != null && (beforeClock?.let { usageClockDiscontinuity(it, afterClock) } == true ||
                    state.clockAnchor?.let { usageClockDiscontinuity(it, afterClock) } == true))
                return@withLock quarantine(afterClock)
            val after = source.access()
            if (after != UsageAccess.GRANTED) return@withLock unavailable(after)
            val eligible = rows.filter { state.minimumBucketStart == null || it.start > state.minimumBucketStart }
            if (eligible.isEmpty()) {
                val quarantined = state.minimumBucketStart != null
                repository.recordStatus(if (quarantined) UsageSyncStatus.INTERVAL_CHANGED else UsageSyncStatus.NO_DATA)
                return@withLock if (quarantined) UsageSyncOutcome.INTERVAL_CHANGED else UsageSyncOutcome.NO_DATA
            }
            repository.commit(eligible, now, now, afterClock ?: beforeClock)
            UsageSyncOutcome.SUCCESS
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            if (!repository.state().enabled) return@withLock UsageSyncOutcome.DISABLED
            if (failure is UsageAccessException) return@withLock unavailable(failure.access)
            if (failure is UsageIntervalChangedException) {
                repository.recordStatus(UsageSyncStatus.INTERVAL_CHANGED)
                return@withLock UsageSyncOutcome.INTERVAL_CHANGED
            }
            repository.recordStatus(UsageSyncStatus.ERROR)
            UsageSyncOutcome.ERROR
        }
    }
    private suspend fun quarantine(anchor: UsageClockAnchor): UsageSyncOutcome {
        if (!repository.state().enabled) return UsageSyncOutcome.DISABLED
        val floor = maxOf(repository.state().minimumBucketStart ?: anchor.wallTime, anchor.wallTime)
        repository.recordClockSafety(anchor, floor)
        return UsageSyncOutcome.INTERVAL_CHANGED
    }
    private suspend fun unavailable(access: UsageAccess): UsageSyncOutcome {
        repository.recordStatus(access.status())
        if (access != UsageAccess.LOCKED) scheduler.cancel()
        return when (access) {
            UsageAccess.REQUIRED -> UsageSyncOutcome.ACCESS_REQUIRED
            UsageAccess.LOCKED -> UsageSyncOutcome.LOCKED
            else -> UsageSyncOutcome.UNAVAILABLE
        }
    }
    private fun UsageAccess.status() = when (this) {
        UsageAccess.GRANTED -> UsageSyncStatus.READY
        UsageAccess.REQUIRED -> UsageSyncStatus.ACCESS_REQUIRED
        UsageAccess.LOCKED -> UsageSyncStatus.LOCKED
        UsageAccess.UNAVAILABLE -> UsageSyncStatus.UNAVAILABLE
    }
}
