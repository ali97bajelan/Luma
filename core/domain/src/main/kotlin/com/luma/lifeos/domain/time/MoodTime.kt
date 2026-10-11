package com.luma.lifeos.domain.time

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Reject DST gaps. Preserve an existing offset in an overlap; otherwise use the earlier offset. */
fun moodInstant(local: LocalDateTime, zone: ZoneId, previous: Instant? = null): Instant {
    val offsets = zone.rules.getValidOffsets(local)
    require(offsets.isNotEmpty()) { "This local time does not exist in the selected timezone" }
    val preferred = previous?.atZone(zone)?.offset
    return local.toInstant(preferred?.takeIf(offsets::contains) ?: offsets.first())
}
