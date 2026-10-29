package com.luma.lifeos.domain.focus

import java.time.Duration
import java.time.Instant
import java.nio.charset.StandardCharsets
import java.util.UUID

data class FocusTimeSample(
    val wall: Instant,
    val elapsedMillis: Long,
    val bootId: String?,
) {
    init {
        require(elapsedMillis >= 0) { "Elapsed realtime cannot be negative" }
        require(bootId == null || bootId.isNotBlank()) { "A known boot identity cannot be blank" }
    }
}

data class FocusTimingCheckpoint(
    val runId: UUID,
    val sample: FocusTimeSample,
)

enum class FocusContinuityFailure {
    UNKNOWN_BOOT_ID,
    BOOT_CHANGED,
    ELAPSED_CLOCK_REGRESSED,
    WALL_CLOCK_DISCONTINUITY,
}

sealed interface FocusSampleContinuity {
    data class Continuous(val elapsedDeltaMillis: Long) : FocusSampleContinuity
    data class Discontinuous(val reason: FocusContinuityFailure) : FocusSampleContinuity
}

enum class FocusTimingError { RUN_NOT_OPEN, CHECKPOINT_RUN_MISMATCH, ARITHMETIC_OVERFLOW, TIMESTAMP_OVERFLOW }

/** The projection is derived from samples; UI ticks never add time to the saved total. */
sealed interface FocusTimingResult {
    data class Advanced(
        val creditedMillis: Long,
        val canonicalEnd: Instant,
        val checkpoint: FocusTimingCheckpoint,
        val elapsedDeltaMillis: Long,
    ) : FocusTimingResult

    /** Keep the prior durable value and end at that checkpoint when continuity is not trustworthy. */
    data class InterruptedAtCheckpoint(
        val creditedMillis: Long,
        val canonicalEnd: Instant,
        val checkpoint: FocusTimingCheckpoint,
        val failedSample: FocusTimeSample,
        val reason: FocusContinuityFailure,
    ) : FocusTimingResult

    data class Invalid(val reason: FocusTimingError) : FocusTimingResult
}

sealed interface FocusRecoveryAssessment {
    data object NoChange : FocusRecoveryAssessment
    data class Continue(val projection: FocusTimingResult.Advanced) : FocusRecoveryAssessment
    data class ChoiceRequired(val projection: FocusTimingResult.Advanced) : FocusRecoveryAssessment
    data class Interrupt(val projection: FocusTimingResult.InterruptedAtCheckpoint) : FocusRecoveryAssessment
    data class Invalid(val reason: FocusTimingError) : FocusRecoveryAssessment
}

sealed interface FocusTransitionResult {
    data class Changed(
        val snapshot: FocusSession,
        val timing: FocusTimingResult? = null,
    ) : FocusTransitionResult
    data class AlreadyApplied(val snapshot: FocusSession) : FocusTransitionResult
    data class Conflict(val current: FocusSession) : FocusTransitionResult
    data class Invalid(val reason: FocusInvalidReason) : FocusTransitionResult
}

/**
 * Compares two raw samples. A missing identity is unknown even when both samples are missing it;
 * wall drift is measured precisely and a difference of exactly 2 seconds remains continuous.
 */
fun focusSampleContinuity(
    previous: FocusTimeSample,
    current: FocusTimeSample,
): FocusSampleContinuity {
    val previousBoot = previous.bootId ?: return FocusSampleContinuity.Discontinuous(
        FocusContinuityFailure.UNKNOWN_BOOT_ID,
    )
    val currentBoot = current.bootId ?: return FocusSampleContinuity.Discontinuous(
        FocusContinuityFailure.UNKNOWN_BOOT_ID,
    )
    if (previousBoot != currentBoot) {
        return FocusSampleContinuity.Discontinuous(FocusContinuityFailure.BOOT_CHANGED)
    }
    if (current.elapsedMillis < previous.elapsedMillis) {
        return FocusSampleContinuity.Discontinuous(FocusContinuityFailure.ELAPSED_CLOCK_REGRESSED)
    }

    val elapsedDelta = current.elapsedMillis - previous.elapsedMillis
    val wallDelta = Duration.between(previous.wall, current.wall)
    val wallMinusElapsed = wallDelta.minusMillis(elapsedDelta)
    if (wallMinusElapsed > MAX_WALL_DRIFT || wallMinusElapsed < MIN_WALL_DRIFT) {
        return FocusSampleContinuity.Discontinuous(FocusContinuityFailure.WALL_CLOCK_DISCONTINUITY)
    }
    return FocusSampleContinuity.Continuous(elapsedDelta)
}

/** Projects one open run from its last durable sample, never from wall-clock subtraction. */
fun calculateFocusTiming(
    run: FocusRun,
    checkpoint: FocusTimingCheckpoint,
    sample: FocusTimeSample,
): FocusTimingResult {
    if (!run.isOpen) return FocusTimingResult.Invalid(FocusTimingError.RUN_NOT_OPEN)
    if (checkpoint.runId != run.id) return FocusTimingResult.Invalid(FocusTimingError.CHECKPOINT_RUN_MISMATCH)

    return when (val continuity = focusSampleContinuity(checkpoint.sample, sample)) {
        is FocusSampleContinuity.Continuous -> {
            val total = try {
                Math.addExact(run.creditedMillis, continuity.elapsedDeltaMillis)
            } catch (_: ArithmeticException) {
                return FocusTimingResult.Invalid(FocusTimingError.ARITHMETIC_OVERFLOW)
            }
            val end = try {
                canonicalFocusEnd(run.startedAt, total)
            } catch (_: ArithmeticException) {
                return FocusTimingResult.Invalid(FocusTimingError.TIMESTAMP_OVERFLOW)
            } catch (_: java.time.DateTimeException) {
                return FocusTimingResult.Invalid(FocusTimingError.TIMESTAMP_OVERFLOW)
            }
            FocusTimingResult.Advanced(
                creditedMillis = total,
                canonicalEnd = end,
                checkpoint = FocusTimingCheckpoint(run.id, sample),
                elapsedDeltaMillis = continuity.elapsedDeltaMillis,
            )
        }
        is FocusSampleContinuity.Discontinuous -> {
            val end = try {
                canonicalFocusEnd(run.startedAt, run.creditedMillis)
            } catch (_: ArithmeticException) {
                return FocusTimingResult.Invalid(FocusTimingError.TIMESTAMP_OVERFLOW)
            } catch (_: java.time.DateTimeException) {
                return FocusTimingResult.Invalid(FocusTimingError.TIMESTAMP_OVERFLOW)
            }
            FocusTimingResult.InterruptedAtCheckpoint(
                creditedMillis = run.creditedMillis,
                canonicalEnd = end,
                checkpoint = checkpoint,
                failedSample = sample,
                reason = continuity.reason,
            )
        }
    }
}

/** Creates the initial RUNNING snapshot with a run identity derived from its stable start command. */
fun createFocusSession(
    sessionId: UUID,
    commandId: UUID,
    sample: FocusTimeSample,
): FocusSession {
    val run = FocusRun(
        id = runIdFor(commandId),
        sessionId = sessionId,
        ordinal = 1,
        startedAt = sample.wall,
        endedAt = null,
        creditedMillis = 0,
        rawStartedAt = sample.wall,
    )
    return FocusSession(
        id = sessionId,
        state = FocusState.RUNNING,
        revision = 0,
        startedAt = sample.wall,
        endedAt = null,
        createdAt = sample.wall,
        updatedAt = sample.wall,
        terminalReason = null,
        recoveredOrUncertain = false,
        runs = listOf(run),
    )
}

/** Pure action transition. Durable command receipts and cross-window serialization belong to FM02. */
fun applyFocusCommand(
    session: FocusSession,
    command: FocusCommand,
    checkpoint: FocusTimingCheckpoint? = null,
): FocusTransitionResult {
    if (command.sessionId != session.id) {
        return FocusTransitionResult.Invalid(FocusInvalidReason.SESSION_MISMATCH)
    }
    if (session.isTerminal) {
        return if (command.action == FocusAction.STOP) {
            FocusTransitionResult.AlreadyApplied(session.copy(runs = uniqueFocusRuns(session.runs)))
        } else {
            FocusTransitionResult.Invalid(FocusInvalidReason.INVALID_TRANSITION)
        }
    }
    if (command.expectedRevision != session.revision) {
        return FocusTransitionResult.Conflict(session.copy(runs = uniqueFocusRuns(session.runs)))
    }

    return when (command.action) {
        FocusAction.PAUSE -> {
            if (session.state != FocusState.RUNNING) return invalidTransition()
            applyRunningEnd(session, command, checkpoint, FocusState.PAUSED)
        }
        FocusAction.RESUME -> {
            if (session.state != FocusState.PAUSED) return invalidTransition()
            val revision = nextRevision(session) ?: return invalidSnapshot()
            val currentRuns = uniqueFocusRuns(session.runs)
            val ordinal = try {
                Math.addExact(currentRuns.maxOf(FocusRun::ordinal), 1)
            } catch (_: ArithmeticException) {
                return invalidSnapshot()
            }
            val run = FocusRun(
                id = runIdFor(command.commandId),
                sessionId = session.id,
                ordinal = ordinal,
                startedAt = command.sample.wall,
                endedAt = null,
                creditedMillis = 0,
                rawStartedAt = command.sample.wall,
            )
            FocusTransitionResult.Changed(
                session.copy(
                    state = FocusState.RUNNING,
                    revision = revision,
                    updatedAt = command.sample.wall,
                    runs = currentRuns + run,
                ),
            )
        }
        FocusAction.STOP -> if (session.state == FocusState.PAUSED) {
            val revision = nextRevision(session) ?: return invalidSnapshot()
            FocusTransitionResult.Changed(
                session.copy(
                    state = FocusState.FINISHED,
                    revision = revision,
                    endedAt = command.sample.wall,
                    updatedAt = command.sample.wall,
                    terminalReason = FocusTerminalReason.USER_STOP,
                    runs = uniqueFocusRuns(session.runs),
                ),
            )
        } else {
            applyRunningEnd(session, command, checkpoint, FocusState.FINISHED)
        }
    }
}

/** Resolve a new-process RUNNING prompt. END_AT_CHECKPOINT never credits the unseen gap. */
fun resolveFocusRecovery(
    session: FocusSession,
    expectedRevision: Long,
    checkpoint: FocusTimingCheckpoint,
    sample: FocusTimeSample,
    choice: FocusRecoveryChoice,
): FocusTransitionResult {
    if (session.state != FocusState.RUNNING) return invalidTransition()
    if (expectedRevision != session.revision) {
        return FocusTransitionResult.Conflict(session.copy(runs = uniqueFocusRuns(session.runs)))
    }
    val run = uniqueFocusRuns(session.runs).singleOrNull(FocusRun::isOpen) ?: return invalidSnapshot()
    if (checkpoint.runId != run.id) {
        return FocusTransitionResult.Invalid(FocusInvalidReason.INVALID_SNAPSHOT)
    }
    val revision = nextRevision(session) ?: return invalidSnapshot()

    return when (choice) {
        FocusRecoveryChoice.CONTINUE_ELAPSED -> when (
            val timing = calculateFocusTiming(run, checkpoint, sample)
        ) {
            is FocusTimingResult.Advanced -> FocusTransitionResult.Changed(
                snapshot = session.copy(
                    revision = revision,
                    updatedAt = sample.wall,
                    recoveredOrUncertain = true,
                    runs = replaceRun(session, run.copy(creditedMillis = timing.creditedMillis)),
                ),
                timing = timing,
            )
            is FocusTimingResult.InterruptedAtCheckpoint -> interruptAtCheckpoint(session, run, revision, timing)
            is FocusTimingResult.Invalid -> FocusTransitionResult.Invalid(FocusInvalidReason.INVALID_SNAPSHOT)
        }
        FocusRecoveryChoice.END_AT_CHECKPOINT -> {
            val canonicalEnd = try {
                canonicalFocusEnd(run.startedAt, run.creditedMillis)
            } catch (_: ArithmeticException) {
                return invalidSnapshot()
            } catch (_: java.time.DateTimeException) {
                return invalidSnapshot()
            }
            val closed = run.copy(
                endedAt = canonicalEnd,
                rawEndedAt = checkpoint.sample.wall,
            )
            FocusTransitionResult.Changed(
                snapshot = session.copy(
                    state = FocusState.INTERRUPTED,
                    revision = revision,
                    endedAt = checkpoint.sample.wall,
                    updatedAt = checkpoint.sample.wall,
                    terminalReason = FocusTerminalReason.PROCESS_RESTART_GAP,
                    recoveredOrUncertain = true,
                    runs = replaceRun(session, closed),
                ),
            )
        }
    }
}

/** Paused sessions stay paused across process death or reboot and have no interval to reconcile. */
fun assessFocusRecovery(
    session: FocusSession,
    checkpoint: FocusTimingCheckpoint?,
    sample: FocusTimeSample,
    processOwnerChanged: Boolean,
): FocusRecoveryAssessment {
    if (session.state != FocusState.RUNNING) return FocusRecoveryAssessment.NoChange
    val run = uniqueFocusRuns(session.runs).singleOrNull(FocusRun::isOpen)
        ?: return FocusRecoveryAssessment.Invalid(FocusTimingError.RUN_NOT_OPEN)
    val anchor = checkpoint ?: return FocusRecoveryAssessment.Invalid(FocusTimingError.CHECKPOINT_RUN_MISMATCH)
    return when (val projection = calculateFocusTiming(run, anchor, sample)) {
        is FocusTimingResult.Advanced -> if (processOwnerChanged) {
            FocusRecoveryAssessment.ChoiceRequired(projection)
        } else {
            FocusRecoveryAssessment.Continue(projection)
        }
        is FocusTimingResult.InterruptedAtCheckpoint -> FocusRecoveryAssessment.Interrupt(projection)
        is FocusTimingResult.Invalid -> FocusRecoveryAssessment.Invalid(projection.reason)
    }
}

private fun applyRunningEnd(
    session: FocusSession,
    command: FocusCommand,
    checkpoint: FocusTimingCheckpoint?,
    requestedState: FocusState,
): FocusTransitionResult {
    val run = uniqueFocusRuns(session.runs).singleOrNull(FocusRun::isOpen) ?: return invalidSnapshot()
    val anchor = checkpoint ?: return invalidSnapshot()
    val revision = nextRevision(session) ?: return invalidSnapshot()
    return when (val timing = calculateFocusTiming(run, anchor, command.sample)) {
        is FocusTimingResult.Advanced -> {
            val closed = run.copy(
                creditedMillis = timing.creditedMillis,
                endedAt = timing.canonicalEnd,
                rawEndedAt = command.sample.wall,
            )
            if (requestedState == FocusState.PAUSED) {
                FocusTransitionResult.Changed(
                    session.copy(
                        state = FocusState.PAUSED,
                        revision = revision,
                        updatedAt = command.sample.wall,
                        runs = replaceRun(session, closed),
                    ),
                    timing,
                )
            } else {
                FocusTransitionResult.Changed(
                    session.copy(
                        state = FocusState.FINISHED,
                        revision = revision,
                        endedAt = command.sample.wall,
                        updatedAt = command.sample.wall,
                        terminalReason = FocusTerminalReason.USER_STOP,
                        runs = replaceRun(session, closed),
                    ),
                    timing,
                )
            }
        }
        is FocusTimingResult.InterruptedAtCheckpoint -> interruptAtCheckpoint(session, run, revision, timing)
        is FocusTimingResult.Invalid -> invalidSnapshot()
    }
}

private fun interruptAtCheckpoint(
    session: FocusSession,
    run: FocusRun,
    revision: Long,
    timing: FocusTimingResult.InterruptedAtCheckpoint,
): FocusTransitionResult {
    val closed = run.copy(
        creditedMillis = timing.creditedMillis,
        endedAt = timing.canonicalEnd,
        rawEndedAt = timing.checkpoint.sample.wall,
    )
    val interrupted = session.copy(
        state = FocusState.INTERRUPTED,
        revision = revision,
        endedAt = timing.checkpoint.sample.wall,
        updatedAt = timing.checkpoint.sample.wall,
        terminalReason = timing.reason.toTerminalReason(),
        recoveredOrUncertain = true,
        runs = replaceRun(session, closed),
    )
    return FocusTransitionResult.Changed(interrupted, timing)
}

private fun FocusContinuityFailure.toTerminalReason(): FocusTerminalReason = when (this) {
    FocusContinuityFailure.UNKNOWN_BOOT_ID -> FocusTerminalReason.UNKNOWN_BOOT_ID
    FocusContinuityFailure.BOOT_CHANGED -> FocusTerminalReason.BOOT_CHANGED
    FocusContinuityFailure.ELAPSED_CLOCK_REGRESSED -> FocusTerminalReason.ELAPSED_CLOCK_REGRESSED
    FocusContinuityFailure.WALL_CLOCK_DISCONTINUITY -> FocusTerminalReason.WALL_CLOCK_DISCONTINUITY
}

private fun replaceRun(session: FocusSession, replacement: FocusRun): List<FocusRun> =
    uniqueFocusRuns(session.runs).map { if (it.id == replacement.id) replacement else it }

private fun nextRevision(session: FocusSession): Long? = try {
    Math.addExact(session.revision, 1)
} catch (_: ArithmeticException) {
    null
}

private fun runIdFor(commandId: UUID): UUID = UUID.nameUUIDFromBytes(
    "luma-focus-run:$commandId".toByteArray(StandardCharsets.UTF_8),
)

private fun invalidTransition() = FocusTransitionResult.Invalid(FocusInvalidReason.INVALID_TRANSITION)
private fun invalidSnapshot() = FocusTransitionResult.Invalid(FocusInvalidReason.INVALID_SNAPSHOT)

const val FOCUS_WALL_DRIFT_TOLERANCE_MILLIS: Long = 2_000

private val MAX_WALL_DRIFT: Duration = Duration.ofMillis(FOCUS_WALL_DRIFT_TOLERANCE_MILLIS)
private val MIN_WALL_DRIFT: Duration = MAX_WALL_DRIFT.negated()
