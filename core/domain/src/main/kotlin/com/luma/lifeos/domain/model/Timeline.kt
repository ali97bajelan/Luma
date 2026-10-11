package com.luma.lifeos.domain.model

import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Actual stored signal values. Acquisition and aggregation remain integration concerns. */
data class ActivityDetails(val id: UUID, val type: String, val value: Double, val unit: String, val source: EventSource) {
    init { require(type.isNotBlank() && unit.isNotBlank()); require(value.isFinite() && value >= 0) }
}
data class AppUsageDetails(val packageName: String, val bucketStart: Instant, val bucketEnd: Instant, val durationMillis: Long) {
    init {
        require(packageName.isNotBlank() && bucketEnd.isAfter(bucketStart))
        require(durationMillis >= 0 && durationMillis <= Duration.between(bucketStart, bucketEnd).toMillis())
    }
}
/** Count and records come from one committed snapshot; an offset beyond the end returns no records. */
data class TimelinePage(val records: List<LifeEventRecord>, val offset: Int, val totalCount: Int) {
    init { require(offset >= 0 && totalCount >= 0); require(records.size.toLong() <= maxOf(0L, totalCount.toLong() - offset)) }
}
