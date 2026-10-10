package com.luma.lifeos.domain.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Half-open [start, endExclusive): midnight belongs to exactly one local day. */
data class TimeRange(val start: Instant, val endExclusive: Instant) {
    init { require(start.isBefore(endExclusive)) { "Range must have positive duration" } }

    operator fun contains(instant: Instant): Boolean =
        !instant.isBefore(start) && instant.isBefore(endExclusive)

    companion object {
        fun forDay(date: LocalDate, zone: ZoneId): TimeRange = TimeRange(
            start = date.atStartOfDay(zone).toInstant(),
            endExclusive = date.plusDays(1).atStartOfDay(zone).toInstant(),
        )
    }
}
