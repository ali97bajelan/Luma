package com.luma.lifeos.domain.focus

import com.luma.lifeos.domain.time.TimeRange
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant

data class FocusStatistics(
    val totalMillis: Long,
    val contributingSessions: Int,
    val interruptedContributors: Int,
) {
    init {
        require(totalMillis >= 0)
        require(contributingSessions >= 0 && interruptedContributors in 0..contributingSessions)
        require((totalMillis == 0L) == (contributingSessions == 0))
    }
}

enum class FocusComparisonStatus { CHANGE, NO_CHANGE, PREVIOUS_PERIOD_EMPTY }

/** `percentageChange` is null when the previous period has no baseline. */
data class FocusComparison(
    val currentMillis: Long,
    val previousMillis: Long,
    val deltaMillis: Long,
    val percentageChange: BigDecimal?,
    val status: FocusComparisonStatus,
) {
    init {
        require(currentMillis >= 0 && previousMillis >= 0)
        require(deltaMillis == currentMillis - previousMillis)
        when {
            previousMillis == 0L && currentMillis > 0L -> {
                require(status == FocusComparisonStatus.PREVIOUS_PERIOD_EMPTY && percentageChange == null)
            }
            currentMillis == 0L && previousMillis == 0L -> {
                require(status == FocusComparisonStatus.NO_CHANGE && percentageChange == null)
            }
            else -> {
                require(status == if (deltaMillis == 0L) FocusComparisonStatus.NO_CHANGE else FocusComparisonStatus.CHANGE)
                require(percentageChange != null)
                require(percentageChange.scale() == 1)
            }
        }
    }
}

/** Aggregate terminal runs by half-open overlap; open and paused sessions never enter saved totals. */
fun focusStatistics(sessions: List<FocusSession>, range: TimeRange): FocusStatistics {
    require(sessions.all(FocusSession::isTerminal)) { "Only terminal focus sessions contribute to reports" }

    val uniqueSessions = sessions.groupBy(FocusSession::id).values.map { versions ->
        val canonicalVersions = versions.map { snapshot ->
            snapshot.copy(runs = uniqueFocusRuns(snapshot.runs))
        }.distinct()
        require(canonicalVersions.size == 1) { "Conflicting snapshots for a focus session" }
        canonicalVersions.first()
    }
    val uniqueRuns = uniqueFocusRuns(uniqueSessions.flatMap(FocusSession::runs))
    val runsBySession = uniqueRuns.groupBy(FocusRun::sessionId)

    var totalMillis = 0L
    var contributingSessions = 0
    var interruptedContributors = 0
    for (session in uniqueSessions) {
        val contribution = (runsBySession[session.id].orEmpty()).fold(0L) { subtotal, run ->
            Math.addExact(subtotal, overlapMillis(run, range))
        }
        if (contribution > 0) {
            totalMillis = Math.addExact(totalMillis, contribution)
            contributingSessions = Math.addExact(contributingSessions, 1)
            if (session.state == FocusState.INTERRUPTED) {
                interruptedContributors = Math.addExact(interruptedContributors, 1)
            }
        }
    }
    return FocusStatistics(totalMillis, contributingSessions, interruptedContributors)
}

/** Same overlap policy used for history rows, including a zero result for a noncontributing run. */
fun focusRangeContributionMillis(session: FocusSession, range: TimeRange): Long {
    require(session.isTerminal) { "Only terminal focus sessions have saved range contributions" }
    return uniqueFocusRuns(session.runs).fold(0L) { total, run ->
        Math.addExact(total, overlapMillis(run, range))
    }
}

fun compareFocusStatistics(current: FocusStatistics, previous: FocusStatistics): FocusComparison {
    val delta = Math.subtractExact(current.totalMillis, previous.totalMillis)
    return when {
        current.totalMillis == 0L && previous.totalMillis == 0L -> FocusComparison(
            currentMillis = 0,
            previousMillis = 0,
            deltaMillis = 0,
            percentageChange = null,
            status = FocusComparisonStatus.NO_CHANGE,
        )
        previous.totalMillis == 0L -> FocusComparison(
            currentMillis = current.totalMillis,
            previousMillis = 0,
            deltaMillis = delta,
            percentageChange = null,
            status = FocusComparisonStatus.PREVIOUS_PERIOD_EMPTY,
        )
        delta == 0L -> FocusComparison(
            currentMillis = current.totalMillis,
            previousMillis = previous.totalMillis,
            deltaMillis = 0,
            percentageChange = BigDecimal.ZERO.setScale(1),
            status = FocusComparisonStatus.NO_CHANGE,
        )
        else -> {
            val percentage = BigDecimal.valueOf(delta)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(previous.totalMillis), 1, RoundingMode.HALF_UP)
            FocusComparison(
                currentMillis = current.totalMillis,
                previousMillis = previous.totalMillis,
                deltaMillis = delta,
                percentageChange = percentage,
                status = FocusComparisonStatus.CHANGE,
            )
        }
    }
}

/**
 * Compute bucket overlap by differences from the run's start. This keeps fractional-millisecond
 * boundary offsets additive, so splitting a run at midnight cannot lose credited whole millis.
 */
private fun overlapMillis(run: FocusRun, range: TimeRange): Long {
    val end = run.endedAt ?: error("Terminal session contains an open run")
    if (run.creditedMillis == 0L) return 0

    val overlapStart = maxOf(run.startedAt, range.start)
    val overlapEnd = minOf(end, range.endExclusive)
    if (!overlapEnd.isAfter(overlapStart)) return 0

    val startOffset = Duration.between(run.startedAt, overlapStart).toMillis()
    val endOffset = Duration.between(run.startedAt, overlapEnd).toMillis()
    return Math.subtractExact(endOffset, startOffset)
}

private val HUNDRED = BigDecimal.valueOf(100)

private fun minOf(left: Instant, right: Instant): Instant = if (left <= right) left else right
private fun maxOf(left: Instant, right: Instant): Instant = if (left >= right) left else right
