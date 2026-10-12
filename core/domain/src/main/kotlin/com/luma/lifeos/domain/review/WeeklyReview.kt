package com.luma.lifeos.domain.review

import com.luma.lifeos.domain.model.*
import com.luma.lifeos.domain.summary.*
import com.luma.lifeos.domain.usage.*
import java.math.BigDecimal
import java.time.*
import java.time.temporal.TemporalAdjusters
import java.util.UUID

/** Inclusive civil dates. Even skipped dates are represented as unavailable samples. */
data class ReviewDates(val start: LocalDate, val through: LocalDate) {
    init { require(through >= start && through <= start.plusDays(6)) }
    val dates: List<LocalDate> get() = generateSequence(start) { it.plusDays(1) }.takeWhile { it <= through }.toList()
    fun startInstant(zone: ZoneId): Instant = start.atStartOfDay(zone).toInstant()
    fun endInstant(zone: ZoneId): Instant = through.plusDays(1).atStartOfDay(zone).toInstant()
}
data class ReviewPeriod(val current: ReviewDates, val previous: ReviewDates, val zone: ZoneId, val partial: Boolean)
fun reviewPeriod(date: LocalDate, today: LocalDate, zone: ZoneId): ReviewPeriod {
    val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    require(monday <= today) { "Future weeks are unavailable" }
    val end = minOf(monday.plusDays(6), today)
    return ReviewPeriod(ReviewDates(monday, end), ReviewDates(monday.minusWeeks(1), end.minusWeeks(1)), zone,
        today <= monday.plusDays(6))
}
data class ReviewName(val id: UUID, val name: String)
/** Deliberately excludes bodies, notes and photos. */
data class ReviewRecord(val id: EventId, val occurredAt: Instant, val mood: MoodSample? = null,
    val emotions: List<ReviewName> = emptyList(), val journal: Boolean = false, val memory: Boolean = false,
    val people: List<ReviewName> = emptyList(), val place: ReviewName? = null, val activities: List<ActivityDetails> = emptyList())
data class NamedFrequency(val name: ReviewName, val records: Int)
data class ReviewActivity(val total: ActivityTotal, val dates: Int)
data class ReviewMoodDay(val date: LocalDate, val mood: MoodSummary?)
data class ReviewUsage(val totalMillis: Long?, val apps: List<AppUsageTotal>, val days: List<DailyUsageReport>) {
    val completeDates: Int get() = days.count { it.history == UsageHistory.COMPLETE }
}
data class ReviewStatistics(val records: Int, val journals: Int, val memories: Int, val mood: MoodSummary?,
    val moodDays: List<ReviewMoodDay>, val emotions: List<NamedFrequency>, val people: List<NamedFrequency>,
    val places: List<NamedFrequency>, val activities: List<ReviewActivity>, val usage: ReviewUsage?)
data class WeeklyReview(val period: ReviewPeriod, val current: ReviewStatistics, val previous: ReviewStatistics)

enum class ReviewMetricKind { ENTRIES, JOURNALS, MEMORIES, MOOD, ENERGY, STRESS, EMOTION, PERSON, PLACE, ACTIVITY }
data class ReviewActivityKey(val type: String, val unit: String, val source: EventSource)
data class ReviewMetric(val kind: ReviewMetricKind, val id: UUID? = null, val activity: ReviewActivityKey? = null) {
    init {
        require((kind in setOf(ReviewMetricKind.EMOTION, ReviewMetricKind.PERSON, ReviewMetricKind.PLACE)) == (id != null))
        require((kind == ReviewMetricKind.ACTIVITY) == (activity != null))
        require(activity == null || (activity.type.isNotBlank() && activity.unit.isNotBlank()))
    }
}

fun calculateWeeklyReview(period: ReviewPeriod, records: List<ReviewRecord>, usageDays: List<DailyUsageReport>): WeeklyReview {
    val rows = records.map { it.copy(emotions = it.emotions.distinct().sortedBy { name -> name.id },
        people = it.people.distinct().sortedBy { name -> name.id }, activities = it.activities.sortedBy { a -> a.id }) }
        .groupBy { it.id }.map { (_, versions) ->
            require(versions.distinct().size == 1) { "Conflicting review snapshots" }; versions.first()
        }.sortedWith(compareBy<ReviewRecord> { it.occurredAt }.thenBy { it.id.toString() })
    require(usageDays.all { it.zone == period.zone } && usageDays.map { it.date }.distinct().size == usageDays.size)
    fun stats(dates: ReviewDates): ReviewStatistics {
        val start = dates.startInstant(period.zone); val end = dates.endInstant(period.zone)
        val selected = rows.filter { it.occurredAt >= start && it.occurredAt < end }
        fun moods(samples: List<MoodSample>): MoodSummary? {
            fun scale(values: List<Int>) = values.takeIf { it.isNotEmpty() }?.let { ScaleTotal(it.size, it.sumOf(Int::toLong)) }
            return scale(samples.map { it.score })?.let { MoodSummary(it, scale(samples.mapNotNull { m -> m.energy }), scale(samples.mapNotNull { m -> m.stress })) }
        }
        fun frequencies(values: List<ReviewName>) = values.groupBy { it.id }.map { (_, names) ->
            require(names.map { it.name }.distinct().size == 1) { "Conflicting catalog names" }
            NamedFrequency(names.first(), names.size)
        }.sortedWith(compareByDescending<NamedFrequency> { it.records }.thenBy { it.name.name }.thenBy { it.name.id })
        val activities = selected.flatMap { row -> row.activities.map { row.occurredAt.atZone(period.zone).toLocalDate() to it } }
        require(activities.map { it.second.id }.distinct().size == activities.size) { "Duplicate activity identity" }
        val totals = activities.groupBy { (_, a) -> ReviewActivityKey(a.type, a.unit, a.source) }.map { (key, observations) ->
            val total = observations.fold(BigDecimal.ZERO) { sum, sample -> sum + BigDecimal.valueOf(sample.second.value) }
            ReviewActivity(ActivityTotal(key.type, key.unit, key.source, observations.size,
                total.stripTrailingZeros().toPlainString().toBigDecimal()), observations.map { it.first }.distinct().size)
        }.sortedWith(compareBy<ReviewActivity> { it.total.type }.thenBy { it.total.unit }.thenBy { it.total.source.name })
        val days = usageDays.filter { it.date in dates.start..dates.through }.sortedBy { it.date }
        val usage = days.takeIf { it.isNotEmpty() }?.let {
            val apps = days.flatMap { it.apps }.filter { it.durationMillis > 0 }.groupBy { it.packageName }.map { (pkg, values) ->
                AppUsageTotal(pkg, values.lastOrNull { it.appLabel != null }?.appLabel,
                    values.fold(0L) { total, app -> Math.addExact(total, app.durationMillis) })
            }.sortedWith(compareByDescending<AppUsageTotal> { it.durationMillis }.thenBy { it.packageName })
            // An empty partial window establishes no measurement, unlike complete/legacy zero.
            val known = days.any { it.apps.isNotEmpty() || it.history != UsageHistory.PARTIAL }
            ReviewUsage(if (known) days.fold(0L) { total, day -> Math.addExact(total, day.totalMillis) } else null, apps, days)
        }
        return ReviewStatistics(selected.size, selected.count { it.journal }, selected.count { it.memory },
            moods(selected.mapNotNull { it.mood }), dates.dates.map { date ->
                ReviewMoodDay(date, moods(selected.filter { it.occurredAt.atZone(period.zone).toLocalDate() == date }.mapNotNull { it.mood }))
            }, frequencies(selected.filter { it.mood != null }.flatMap { it.emotions }),
            frequencies(selected.flatMap { it.people }), frequencies(selected.mapNotNull { it.place }), totals, usage)
    }
    return WeeklyReview(period, stats(period.current), stats(period.previous))
}
