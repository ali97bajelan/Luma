package com.luma.lifeos.domain.model

import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Actual stored signal values. Acquisition and aggregation remain integration concerns. */
data class ActivityDetails(val id: UUID, val type: String, val value: Double, val unit: String, val source: EventSource) {
    init { require(type.isNotBlank() && unit.isNotBlank()); require(value.isFinite() && value >= 0) }
}
data class AppUsageDetails(val packageName: String, val bucketStart: Instant, val bucketEnd: Instant, val durationMillis: Long, val appLabel: String? = null) {
    init {
        require(packageName.isNotBlank() && bucketEnd.isAfter(bucketStart))
        require(durationMillis >= 0 && durationMillis <= Duration.between(bucketStart, bucketEnd).toMillis())
        require(appLabel == null || (appLabel.isNotBlank() && appLabel.length <= 200))
    }
}
/** Counts and rows share one snapshot. totalCount pages visible rows; eventCount excludes app usage. */
data class TimelinePage(val records: List<LifeEventRecord>, val offset: Int, val totalCount: Int,
    val eventCount: Int = totalCount) {
    init {
        require(offset >= 0 && totalCount >= 0 && eventCount in 0..totalCount)
        require(records.size.toLong() <= maxOf(0L, totalCount.toLong() - offset))
    }
}
