package com.luma.lifeos.domain.usage

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

const val APP_USAGE_DISPLAY_THRESHOLD_MILLIS = 60_000L

/** Bounds and foreground total reported by Android, not a prorated query range. */
data class UsageBucket(val packageName: String, val start: Instant, val end: Instant,
    val durationMillis: Long, val appLabel: String? = null) {
    init {
        require(packageName.isNotBlank() && packageName.length <= 500)
        require(end > start && start.nano % 1_000_000 == 0 && end.nano % 1_000_000 == 0)
        require(durationMillis >= 0 && durationMillis <= Duration.between(start, end).toMillis())
        require(appLabel == null || (appLabel.isNotBlank() && appLabel.length <= 200))
    }
    // The native end grows during the current interval. It must not be part of retry identity.
    val sourceReference: String get() = "usage:daily:$packageName:${start.toEpochMilli()}"
}
enum class UsageAccess { GRANTED, REQUIRED, LOCKED, UNAVAILABLE }
enum class UsageSyncStatus { DISABLED, ACCESS_REQUIRED, LOCKED, UNAVAILABLE, READY, NO_DATA, ERROR, INTERVAL_CHANGED }
enum class UsageSyncOutcome { SUCCESS, DISABLED, ACCESS_REQUIRED, LOCKED, UNAVAILABLE, NO_DATA, ERROR, INTERVAL_CHANGED }
data class UsageImportState(val enabled: Boolean = false, val status: UsageSyncStatus = UsageSyncStatus.DISABLED,
    val lastSync: Instant? = null, val checkpoint: Instant? = null, val clockAnchor: UsageClockAnchor? = null,
    val minimumBucketStart: Instant? = null)
data class UsageClockAnchor(val wallTime: Instant, val elapsedMillis: Long, val bootCount: Int) {
    init { require(wallTime.nano % 1_000_000 == 0 && elapsedMillis >= 0 && bootCount >= 0) }
}

/** Across reboots elapsed time cannot prove clock continuity; both cases require fresh native intervals. */
fun usageClockDiscontinuity(previous: UsageClockAnchor, current: UsageClockAnchor): Boolean {
    if (previous.bootCount != current.bootCount || current.elapsedMillis < previous.elapsedMillis) return true
    val wallDelta = Duration.between(previous.wallTime, current.wallTime).toMillis()
    val elapsedDelta = current.elapsedMillis - previous.elapsedMillis
    return kotlin.math.abs(wallDelta.toDouble() - elapsedDelta.toDouble()) > 1_000
}
data class AppUsageTotal(val packageName: String, val appLabel: String?, val durationMillis: Long)
data class DailyUsageReport(val date: LocalDate, val zone: ZoneId, val apps: List<AppUsageTotal>, val totalMillis: Long,
    val sessions: List<UsageSession> = emptyList(), val history: UsageHistory = UsageHistory.UNAVAILABLE)

class UsageIntervalChangedException : Exception("Native usage intervals overlap with different starts")

/** Clock corrections can shift native bucket starts. Refuse ambiguous imports rather than count both. */
fun assertUnambiguousUsage(incoming: List<UsageBucket>, stored: List<UsageBucket>) {
    val byPackage = (incoming + stored).groupBy { it.packageName }
    incoming.forEach { row ->
        if (byPackage.getValue(row.packageName).any {
                it.start != row.start && it.start < row.end && it.end > row.start
            }) throw UsageIntervalChangedException()
    }
}

fun distinctUsageBuckets(buckets: List<UsageBucket>): List<UsageBucket> = buckets.groupBy { it.sourceReference }.map { (_, rows) ->
    require(rows.distinct().size == 1) { "Conflicting native usage buckets" }
    rows.first()
}.sortedWith(compareBy<UsageBucket> { it.start }.thenBy { it.packageName })

/** Dates label native bucket starts. App foreground totals can overlap and are not device screen-on time. */
fun dailyUsageReport(buckets: List<UsageBucket>, date: LocalDate, zone: ZoneId): DailyUsageReport? {
    val rows = distinctUsageBuckets(buckets).filter { it.start.atZone(zone).toLocalDate() == date }
    if (rows.isEmpty()) return null
    val apps = rows.filter { it.durationMillis > 0 }.groupBy { it.packageName }.map { (pkg, entries) ->
        AppUsageTotal(pkg, entries.last().appLabel, entries.fold(0L) { total, row -> Math.addExact(total, row.durationMillis) })
    }.sortedWith(compareByDescending<AppUsageTotal> { it.durationMillis }.thenBy { it.packageName })
    return DailyUsageReport(date, zone, apps, apps.fold(0L) { total, app -> Math.addExact(total, app.durationMillis) })
}
