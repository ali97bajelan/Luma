package com.luma.lifeos.domain.health

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class HealthAccess { GRANTED, REQUIRED, UPDATE_REQUIRED, UNAVAILABLE }
enum class HealthStatus { DISABLED, ACCESS_REQUIRED, UPDATE_REQUIRED, UNAVAILABLE, READY, NO_DATA, ERROR }
data class HealthState(val enabled: Boolean = false, val status: HealthStatus = HealthStatus.DISABLED,
    val lastSync: Instant? = null, val importZone: ZoneId? = null, val revision: Long = 0) {
    init { require(revision >= 0 && (!enabled || importZone != null)) }
}

/** A calendar day in the durable import zone, capped at the query time for today. */
data class StepWindow(val date: LocalDate, val zone: ZoneId, val start: Instant, val end: Instant) {
    init {
        require(start == date.atStartOfDay(zone).toInstant())
        require(end > start && end <= date.plusDays(1).atStartOfDay(zone).toInstant())
        require(start.nano % 1_000_000 == 0 && end.nano % 1_000_000 == 0)
    }
    val sourceReference: String get() = "health:steps:daily:${zone.id}:$date"
}
data class StepObservation(val window: StepWindow, val count: Long?) {
    // ActivityRecord stores Double: only accept exactly representable integral values.
    init { require(count == null || count in 0..9_007_199_254_740_991L) }
}
fun stepWindows(now: Instant, zone: ZoneId): List<StepWindow> {
    val today = now.atZone(zone).toLocalDate()
    return (6L downTo 0L).mapNotNull { offset ->
        val date = today.minusDays(offset)
        val start = date.atStartOfDay(zone).toInstant()
        val end = minOf(now, date.plusDays(1).atStartOfDay(zone).toInstant())
        if (end > start) StepWindow(date, zone, start, end) else null
    }
}
fun HealthAccess.status(): HealthStatus = when (this) {
    HealthAccess.GRANTED -> HealthStatus.READY
    HealthAccess.REQUIRED -> HealthStatus.ACCESS_REQUIRED
    HealthAccess.UPDATE_REQUIRED -> HealthStatus.UPDATE_REQUIRED
    HealthAccess.UNAVAILABLE -> HealthStatus.UNAVAILABLE
}
