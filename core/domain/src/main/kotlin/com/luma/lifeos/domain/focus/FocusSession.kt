package com.luma.lifeos.domain.focus

import java.time.Instant
import java.util.UUID

enum class FocusState { RUNNING, PAUSED, FINISHED, INTERRUPTED }

enum class FocusAction { PAUSE, RESUME, STOP }

enum class FocusRecoveryChoice { CONTINUE_ELAPSED, END_AT_CHECKPOINT }

/** Why a session ended. Clock and process failures are kept distinct for history guidance. */
enum class FocusTerminalReason {
    USER_STOP,
    PROCESS_RESTART_GAP,
    EMERGENCY_EXIT,
    UNKNOWN_BOOT_ID,
    BOOT_CHANGED,
    ELAPSED_CLOCK_REGRESSED,
    WALL_CLOCK_DISCONTINUITY,
}

/** Canonical times are used for range queries; raw times preserve what the device clock observed. */
data class FocusRun(
    val id: UUID,
    val sessionId: UUID,
    val ordinal: Int,
    val startedAt: Instant,
    val endedAt: Instant?,
    val creditedMillis: Long,
    val rawStartedAt: Instant = startedAt,
    val rawEndedAt: Instant? = null,
) {
    init {
        require(ordinal > 0) { "Run ordinals start at one" }
        require(creditedMillis >= 0) { "Credited duration cannot be negative" }
        if (endedAt == null) {
            require(rawEndedAt == null) { "An open run cannot have an observed end" }
        } else {
            require(endedAt == canonicalFocusEnd(startedAt, creditedMillis)) {
                "Canonical run end must equal start plus credited duration"
            }
            require(rawEndedAt != null) { "A closed run must retain its observed end" }
        }
    }

    val isOpen: Boolean get() = endedAt == null
}

/** Immutable repository snapshot. The active run's credited duration is its last durable checkpoint. */
data class FocusSession(
    val id: UUID,
    val state: FocusState,
    val revision: Long,
    val startedAt: Instant,
    val endedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val terminalReason: FocusTerminalReason?,
    val recoveredOrUncertain: Boolean,
    val runs: List<FocusRun>,
) {
    init {
        require(revision >= 0) { "Revision cannot be negative" }
        require(runs.isNotEmpty()) { "A focus session must contain its first run" }
        require(runs.all { it.sessionId == id }) { "Every run must belong to this session" }
        val uniqueRuns = uniqueFocusRuns(runs)
        require(uniqueRuns.map { it.ordinal }.distinct().size == uniqueRuns.size) { "Run ordinals must be unique" }
        require(uniqueRuns.map { it.ordinal } == uniqueRuns.map { it.ordinal }.sorted()) {
            "Runs must be ordered by ordinal"
        }

        val openRuns = uniqueRuns.count(FocusRun::isOpen)
        when (state) {
            FocusState.RUNNING -> {
                require(endedAt == null && terminalReason == null && openRuns == 1) {
                    "A running session has exactly one open run and no terminal fields"
                }
            }
            FocusState.PAUSED -> {
                require(endedAt == null && terminalReason == null && openRuns == 0) {
                    "A paused session has no open run and no terminal fields"
                }
            }
            FocusState.FINISHED -> {
                require(endedAt != null && terminalReason == FocusTerminalReason.USER_STOP && openRuns == 0) {
                    "A finished session must be closed by a user stop"
                }
            }
            FocusState.INTERRUPTED -> {
                require(endedAt != null && terminalReason != null &&
                    terminalReason != FocusTerminalReason.USER_STOP && openRuns == 0) {
                    "An interrupted session must have a non-user terminal reason"
                }
            }
        }
    }

    /** Sum of unique run snapshots, with overflow rejected instead of wrapping. */
    val creditedMillis: Long
        get() = uniqueFocusRuns(runs).fold(0L) { total, run -> Math.addExact(total, run.creditedMillis) }

    val isTerminal: Boolean get() = state == FocusState.FINISHED || state == FocusState.INTERRUPTED
}

data class FocusWriteContext(
    val databaseGeneration: UUID,
    val processInstanceId: UUID,
)

data class FocusCommand(
    val commandId: UUID,
    val sessionId: UUID,
    val expectedRevision: Long,
    val action: FocusAction,
    val sample: FocusTimeSample,
    val context: FocusWriteContext,
) {
    init { require(expectedRevision >= 0) { "Expected revision cannot be negative" } }
}

enum class FocusInvalidReason {
    INVALID_TRANSITION,
    SESSION_MISMATCH,
    INVALID_SAMPLE,
    INVALID_SNAPSHOT,
    COMMAND_ID_REUSED,
    STALE_DATABASE_GENERATION,
    INVALID_PAGE,
}

enum class FocusUnavailableReason { STORAGE, UNKNOWN }

/** Result of a repository write. Exactly one of snapshot and deletedSessionId is present on success. */
sealed interface FocusWriteResult {
    data class Applied(
        val snapshot: FocusSession? = null,
        val deletedSessionId: UUID? = null,
    ) : FocusWriteResult {
        init { require((snapshot == null) xor (deletedSessionId == null)) }
    }

    data class AlreadyApplied(
        val snapshot: FocusSession? = null,
        val deletedSessionId: UUID? = null,
    ) : FocusWriteResult {
        init { require((snapshot == null) xor (deletedSessionId == null)) }
    }

    data class Conflict(val current: FocusSession?) : FocusWriteResult
    data class Invalid(val reason: FocusInvalidReason) : FocusWriteResult
    data class Unavailable(val reason: FocusUnavailableReason) : FocusWriteResult
    data class RecoveryRequired(val current: FocusSession) : FocusWriteResult
}

data class FocusHistoryRow(
    val session: FocusSession,
    val fullDurationMillis: Long,
    val rangeContributionMillis: Long,
) {
    init {
        require(session.isTerminal) { "History contains terminal sessions only" }
        require(fullDurationMillis == session.creditedMillis)
        require(rangeContributionMillis >= 0)
        require(rangeContributionMillis <= fullDurationMillis)
    }
}

data class FocusPage(
    val rows: List<FocusHistoryRow>,
    val offset: Int,
    val total: Int,
) {
    init {
        require(offset >= 0 && total >= 0)
        require(rows.size <= FOCUS_HISTORY_PAGE_SIZE)
        require(if (offset <= total) rows.size <= total - offset else rows.isEmpty())
    }
}

internal fun uniqueFocusRuns(runs: List<FocusRun>): List<FocusRun> =
    runs.groupBy(FocusRun::id).values.map { versions ->
        require(versions.distinct().size == 1) { "Conflicting snapshots for a focus run" }
        versions.first()
    }

internal fun canonicalFocusEnd(start: Instant, creditedMillis: Long): Instant =
    start.plusMillis(creditedMillis)
