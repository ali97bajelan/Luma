package com.luma.lifeos.domain.summary

import com.luma.lifeos.domain.model.*
import com.luma.lifeos.domain.usage.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

const val DAILY_SUMMARY_VERSION = 1

/** Only metric inputs: no private body text, attachments or unrelated catalog relations. */
data class SummaryRecord(val id: EventId, val occurredAt: Instant, val mood: MoodSample? = null,
    val journal: Boolean = false, val memory: Boolean = false, val activities: List<ActivityDetails> = emptyList(),
    val usage: AppUsageDetails? = null)
data class MoodSample(val score: Int, val energy: Int? = null, val stress: Int? = null) {
    init { require(score in 1..5 && (energy == null || energy in 1..5) && (stress == null || stress in 1..5)) }
}
data class ScaleTotal(val samples: Int, val sum: Long) {
    init { require(samples > 0 && sum in samples.toLong()..(5L * samples)) }
    val average: Double get() = sum.toDouble() / samples
}
data class MoodSummary(val score: ScaleTotal, val energy: ScaleTotal? = null, val stress: ScaleTotal? = null) {
    init { require((energy?.samples ?: 0) <= score.samples && (stress?.samples ?: 0) <= score.samples) }
}
data class ActivityTotal(val type: String, val unit: String, val source: EventSource, val samples: Int, val total: BigDecimal) {
    init { require(type.isNotBlank() && unit.isNotBlank() && samples > 0 && total.signum() >= 0) }
}
data class DailySummary(val date: LocalDate, val zone: ZoneId, val records: Int, val journals: Int, val memories: Int,
    val mood: MoodSummary? = null, val usage: DailyUsageReport? = null, val activities: List<ActivityTotal>? = null) {
    init {
        require(records >= 0 && journals in 0..records && memories in 0..records && (mood?.score?.samples ?: 0) <= records)
        require(usage == null || (usage.date == date && usage.zone == zone && usage.apps.isNotEmpty() &&
            usage.apps.map { it.packageName }.distinct().size == usage.apps.size && usage.apps.all {
                it.packageName.isNotBlank() && it.durationMillis >= 0 && (it.appLabel == null || it.appLabel.isNotBlank())
            } && usage.totalMillis == usage.apps.fold(0L) { sum, app -> Math.addExact(sum, app.durationMillis) }))
        require(activities == null || (activities.isNotEmpty() &&
            activities.map { Triple(it.type, it.unit, it.source) }.distinct().size == activities.size))
    }
}

/** Reproducible saved-only metrics. Usage is dated by native starts; all other inputs by occurrence. */
fun calculateDailySummary(date: LocalDate, zone: ZoneId, records: List<SummaryRecord>): DailySummary {
    val rows = records.map { it.copy(activities = it.activities.sortedBy { activity -> activity.id.toString() }) }
        .groupBy { it.id }.map { (_, versions) ->
            require(versions.distinct().size == 1) { "Conflicting summary snapshots" }; versions.first()
        }
    val start = date.atStartOfDay(zone).toInstant(); val end = date.plusDays(1).atStartOfDay(zone).toInstant()
    val day = rows.filter { it.occurredAt >= start && it.occurredAt < end }
    val moods = day.mapNotNull { it.mood }
    fun scale(values: List<Int>) = values.takeIf { it.isNotEmpty() }?.let { ScaleTotal(it.size, it.sumOf(Int::toLong)) }
    val mood = scale(moods.map { it.score })?.let { MoodSummary(it, scale(moods.mapNotNull { it.energy }), scale(moods.mapNotNull { it.stress })) }
    val usageRows = rows.mapNotNull { it.usage }.filter { it.bucketStart >= start && it.bucketStart < end }.map {
        UsageBucket(it.packageName, it.bucketStart, it.bucketEnd, it.durationMillis, it.appLabel)
    }
    assertUnambiguousUsage(usageRows, emptyList())
    val usage = dailyUsageReport(usageRows, date, zone)
    val activities = day.flatMap { it.activities }
    require(activities.map { it.id }.distinct().size == activities.size) { "Duplicate activity identity" }
    val totals = activities.groupBy { Triple(it.type, it.unit, it.source) }.map { (key, observations) ->
        val total = observations.fold(BigDecimal.ZERO) { sum, observation -> sum + BigDecimal.valueOf(observation.value) }
        ActivityTotal(key.first, key.second, key.third, observations.size, total.stripTrailingZeros().toPlainString().toBigDecimal())
    }.sortedWith(compareBy<ActivityTotal> { it.type }.thenBy { it.unit }.thenBy { it.source.name }).takeIf { it.isNotEmpty() }
    return DailySummary(date, zone, day.size, day.count { it.journal }, day.count { it.memory }, mood, usage, totals)
}
