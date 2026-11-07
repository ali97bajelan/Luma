package com.luma.lifeos.domain.focus

import com.luma.lifeos.domain.time.TimeRange
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.LinkedHashMap
import java.util.Locale
import java.util.UUID

data class FocusTagSummary(
    val tag: FocusTag?,
    val totalMillis: Long,
    val sessions: Int,
    val percentage: BigDecimal,
) {
    init {
        require(totalMillis > 0)
        require(sessions > 0)
        require(percentage.scale() == 1 && percentage >= BigDecimal.ZERO && percentage <= HUNDRED)
    }
}

data class FocusLongest(val session: FocusSession, val contributionMillis: Long) {
    init {
        require(session.isTerminal)
        require(contributionMillis > 0 && contributionMillis <= session.creditedMillis)
    }
}

data class FocusInsights(
    val totalMillis: Long,
    val sessions: Int,
    val averageMillis: Long,
    val interruptedContributors: Int,
    val tags: List<FocusTagSummary>,
    val longest: FocusLongest?,
    /** True when this report includes saved time from task-origin sessions. */
    val requiresTaskAccess: Boolean = false,
) {
    init {
        require(totalMillis >= 0 && sessions >= 0 && averageMillis >= 0)
        require(interruptedContributors in 0..sessions)
        require((totalMillis == 0L) == (sessions == 0))
        require(averageMillis == if (sessions == 0) 0L else totalMillis / sessions)
        require(tags.sumOfExact(FocusTagSummary::totalMillis) == totalMillis)
        require(tags.sumOfExactInt(FocusTagSummary::sessions) == sessions)
        require(tags.map { it.tag?.id }.distinct().size == tags.size)
        require((sessions == 0) == (longest == null))
    }

    companion object {
        val EMPTY = FocusInsights(0, 0, 0, 0, emptyList(), null)
    }
}

/** Pure calculation for callers that do not need cooperative cancellation. */
fun focusInsights(sessions: List<FocusSession>, range: TimeRange?): FocusInsights =
    calculateFocusInsights(sessions, range) {}

/**
 * Same deterministic calculation with a caller-provided cancellation checkpoint. Repository
 * observers pass their coroutine Job's `ensureActive` callback so large snapshots stop promptly
 * when a newer period or database generation replaces them.
 */
fun focusInsightsCancellable(
    sessions: List<FocusSession>,
    range: TimeRange?,
    checkActive: () -> Unit,
): FocusInsights = calculateFocusInsights(sessions, range, checkActive)

private fun calculateFocusInsights(
    sessions: List<FocusSession>,
    range: TimeRange?,
    checkActive: () -> Unit,
): FocusInsights {
    val canonicalById = LinkedHashMap<UUID, FocusSession>()
    for (snapshot in sessions) {
        checkActive()
        require(snapshot.isTerminal) { "Only terminal focus sessions contribute to insights" }
        val canonical = snapshot.copy(runs = uniqueRuns(snapshot.runs, checkActive))
        val previous = canonicalById[snapshot.id]
        require(previous == null || previous == canonical) { "Conflicting snapshots for a focus session" }
        if (previous == null) canonicalById[snapshot.id] = canonical
    }

    // Match focusStatistics: repeated identical session/run rows count once, while run IDs
    // reused with different snapshots are invalid even if they came from different sessions.
    val uniqueSessions = ArrayList<FocusSession>(canonicalById.size)
    val allRuns = ArrayList<FocusRun>()
    for (session in canonicalById.values) {
        checkActive()
        uniqueSessions += session
        for (run in session.runs) {
            checkActive()
            allRuns += run
        }
    }
    val uniqueRuns = uniqueRuns(allRuns, checkActive)
    val runsBySession = LinkedHashMap<UUID, MutableList<FocusRun>>()
    for (run in uniqueRuns) {
        checkActive()
        runsBySession.getOrPut(run.sessionId) { mutableListOf() }.add(run)
    }

    val tagTotals = LinkedHashMap<UUID?, MutableTagTotal>()
    var totalMillis = 0L
    var contributingSessions = 0
    var interruptedContributors = 0
    var longestSession: FocusSession? = null
    var longestContribution = 0L
    var requiresTaskAccess = false

    for (session in uniqueSessions) {
        checkActive()
        var contribution = 0L
        for (run in runsBySession[session.id].orEmpty()) {
            checkActive()
            contribution = Math.addExact(
                contribution,
                if (range == null) run.creditedMillis else overlapMillis(run, range),
            )
        }
        if (contribution <= 0) continue

        totalMillis = Math.addExact(totalMillis, contribution)
        contributingSessions = Math.addExact(contributingSessions, 1)
        if (session.state == FocusState.INTERRUPTED) {
            interruptedContributors = Math.addExact(interruptedContributors, 1)
        }
        if (session.taskOrigin) requiresTaskAccess = true

        val priorLongest = longestSession
        if (priorLongest == null || isLongerWinner(session, contribution, priorLongest, longestContribution)) {
            longestSession = session
            longestContribution = contribution
        }

        val tag = session.metadata.tag
        val group = tagTotals.getOrPut(tag?.id) { MutableTagTotal(tag) }
        require(group.tag == tag) { "Conflicting names for a focus tag" }
        group.totalMillis = Math.addExact(group.totalMillis, contribution)
        group.sessions = Math.addExact(group.sessions, 1)
    }

    if (contributingSessions == 0) return FocusInsights.EMPTY

    val tagRows = ArrayList<FocusTagSummary>(tagTotals.size)
    for (total in tagTotals.values) {
        checkActive()
        tagRows += FocusTagSummary(
            tag = total.tag,
            totalMillis = total.totalMillis,
            sessions = total.sessions,
            percentage = BigDecimal.valueOf(total.totalMillis)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(totalMillis), 1, RoundingMode.HALF_UP),
        )
    }
    tagRows.sortWith { left, right ->
        checkActive()
        val byTotal = right.totalMillis.compareTo(left.totalMillis)
        if (byTotal != 0) return@sortWith byTotal
        val byName = left.tag?.name?.lowercase(Locale.ROOT).orEmpty()
            .compareTo(right.tag?.name?.lowercase(Locale.ROOT).orEmpty())
        if (byName != 0) return@sortWith byName
        left.tag?.id?.toString().orEmpty().compareTo(right.tag?.id?.toString().orEmpty())
    }

    val result = FocusInsights(
        totalMillis = totalMillis,
        sessions = contributingSessions,
        averageMillis = totalMillis / contributingSessions,
        interruptedContributors = interruptedContributors,
        tags = tagRows,
        longest = longestSession?.let { FocusLongest(it, longestContribution) },
        requiresTaskAccess = requiresTaskAccess,
    )
    checkActive()
    return result
}

private fun uniqueRuns(runs: List<FocusRun>, checkActive: () -> Unit): List<FocusRun> {
    val versionsById = LinkedHashMap<UUID, MutableList<FocusRun>>()
    for (run in runs) {
        checkActive()
        versionsById.getOrPut(run.id) { mutableListOf() }.add(run)
    }
    val unique = ArrayList<FocusRun>(versionsById.size)
    for (versions in versionsById.values) {
        checkActive()
        val first = versions.first()
        for (version in versions) {
            checkActive()
            require(version == first) { "Conflicting snapshots for a focus run" }
        }
        unique += first
    }
    return unique
}

private fun isLongerWinner(
    candidate: FocusSession,
    contribution: Long,
    current: FocusSession,
    currentContribution: Long,
): Boolean = when {
    contribution != currentContribution -> contribution > currentContribution
    candidate.startedAt != current.startedAt -> candidate.startedAt > current.startedAt
    else -> candidate.id.toString() > current.id.toString()
}

private data class MutableTagTotal(
    val tag: FocusTag?,
    var totalMillis: Long = 0,
    var sessions: Int = 0,
)

private fun <T> List<T>.sumOfExact(selector: (T) -> Long): Long = fold(0L) { total, value ->
    Math.addExact(total, selector(value))
}

private fun <T> List<T>.sumOfExactInt(selector: (T) -> Int): Int = fold(0) { total, value ->
    Math.addExact(total, selector(value))
}

private val HUNDRED: BigDecimal = BigDecimal.valueOf(100)
