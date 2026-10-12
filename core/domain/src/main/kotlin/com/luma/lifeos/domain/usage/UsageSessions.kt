package com.luma.lifeos.domain.usage

import com.luma.lifeos.domain.time.TimeRange
import java.time.*

enum class UsageHistory { UNAVAILABLE, PARTIAL, COMPLETE }

/** An ongoing end is the observation boundary, not an observed app close. */
data class UsageSession(val packageName: String, val start: Instant, val end: Instant,
    val appLabel: String? = null, val ongoing: Boolean = false, val openedAt: Instant = start) {
    init {
        require(packageName.isNotBlank() && packageName.length <= 500)
        require(end > start && openedAt <= start)
        require(listOf(start, end, openedAt).all { it.nano % 1_000_000 == 0 })
        require(appLabel == null || (appLabel.isNotBlank() && appLabel.length <= 200))
    }
    val identity: String get() = "usage:session:$packageName:${openedAt.toEpochMilli()}"
    val durationMillis: Long get() = Duration.between(start, end).toMillis()
}

data class UsageSessionBatch(val range: TimeRange, val sessions: List<UsageSession>,
    val history: UsageHistory = UsageHistory.PARTIAL) {
    init {
        require(history != UsageHistory.UNAVAILABLE)
        require(sessions.all { it.start >= range.start && it.end <= range.endExclusive && it.openedAt == it.start })
        require(sessions.map { it.identity }.distinct().size == sessions.size)
    }
}

/** Union same-app overlaps before deriving both views, including multi-window use. */
fun sessionUsageReport(sessions: List<UsageSession>, date: LocalDate, zone: ZoneId,
    history: UsageHistory): DailyUsageReport {
    val dayStart = date.atStartOfDay(zone).toInstant()
    val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()
    if (dayStart == dayEnd) return DailyUsageReport(date, zone, emptyList(), 0, history = history)
    val visits = sessions.filter { it.start < dayEnd && it.end > dayStart }.groupBy { it.packageName }.values.flatMap { rows ->
        val union = mutableListOf<UsageSession>()
        rows.sortedWith(compareBy<UsageSession> { it.start }.thenBy { it.end }).forEach { row ->
            val last = union.lastOrNull()
            if (last != null && row.start < last.end) {
                union[union.lastIndex] = last.copy(end = maxOf(last.end, row.end),
                    ongoing = if (row.end >= last.end) row.ongoing else last.ongoing,
                    appLabel = row.appLabel ?: last.appLabel)
            } else union += row
        }
        union.map { it.copy(start = maxOf(it.start, dayStart), end = minOf(it.end, dayEnd),
            ongoing = it.ongoing && it.end <= dayEnd) }
    }.sortedWith(compareBy<UsageSession> { it.start }.thenBy { it.packageName }.thenBy { it.openedAt })
    val apps = visits.groupBy { it.packageName }.map { (pkg, rows) ->
        AppUsageTotal(pkg, rows.lastOrNull { it.appLabel != null }?.appLabel,
            rows.fold(0L) { total, row -> Math.addExact(total, row.durationMillis) })
    }.sortedWith(compareByDescending<AppUsageTotal> { it.durationMillis }.thenBy { it.packageName })
    return DailyUsageReport(date, zone, apps, apps.fold(0L) { total, row -> Math.addExact(total, row.durationMillis) }, visits, history)
}
