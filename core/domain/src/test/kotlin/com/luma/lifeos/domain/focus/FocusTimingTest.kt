package com.luma.lifeos.domain.focus

import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusTimingTest {
    private val start = Instant.parse("2026-10-28T09:00:00Z")
    private val boot = "boot-A"

    @Test fun pauseAndResumeCreditOnlyRunningIntervals() {
        val initial = sample(start, 1_000)
        val session = createFocusSession(uuid(1), uuid(2), initial)
        val pauseSample = sample(start.plusSeconds(20 * 60L), 1_000 + 20 * 60_000L)
        val paused = changed(applyFocusCommand(
            session,
            command(session, 3, FocusAction.PAUSE, 0, pauseSample),
            FocusTimingCheckpoint(session.runs.single().id, initial),
        )).snapshot

        assertEquals(FocusState.PAUSED, paused.state)
        assertEquals(1_200_000L, paused.creditedMillis)
        assertEquals(1L, paused.revision)
        assertEquals(start.plusSeconds(20 * 60L), paused.runs.single().endedAt)

        val resumeSample = sample(start.plusSeconds(30 * 60L), 1_000 + 30 * 60_000L)
        val runningAgain = changed(applyFocusCommand(
            paused,
            command(paused, 4, FocusAction.RESUME, 1, resumeSample),
        )).snapshot
        assertEquals(2, runningAgain.runs.size)
        assertEquals(2, runningAgain.runs.last().ordinal)
        assertTrue(runningAgain.runs.last().isOpen)

        val stopSample = sample(start.plusSeconds(35 * 60L), 1_000 + 35 * 60_000L)
        val finished = changed(applyFocusCommand(
            runningAgain,
            command(runningAgain, 5, FocusAction.STOP, 2, stopSample),
            FocusTimingCheckpoint(runningAgain.runs.last().id, resumeSample),
        )).snapshot

        assertEquals(FocusState.FINISHED, finished.state)
        assertEquals(FocusTerminalReason.USER_STOP, finished.terminalReason)
        assertEquals(1_500_000L, finished.creditedMillis)
        assertEquals(2, finished.runs.size)
        assertEquals(300_000L, finished.runs.last().creditedMillis)
        assertEquals(start.plusSeconds(35 * 60L), finished.runs.last().endedAt)
    }

    @Test fun actionTransitionsRejectInvalidAndStaleCommands() {
        val initial = sample(start, 10)
        val running = createFocusSession(uuid(10), uuid(11), initial)
        val invalidResume = applyFocusCommand(running, command(running, 12, FocusAction.RESUME, 0, initial))
        assertEquals(FocusTransitionResult.Invalid(FocusInvalidReason.INVALID_TRANSITION), invalidResume)

        val stalePause = applyFocusCommand(running, command(running, 13, FocusAction.PAUSE, 1, initial))
        assertTrue(stalePause is FocusTransitionResult.Conflict)

        val missingCheckpoint = applyFocusCommand(running, command(running, 14, FocusAction.STOP, 0, initial))
        assertEquals(FocusTransitionResult.Invalid(FocusInvalidReason.INVALID_SNAPSHOT), missingCheckpoint)

        val wrongSession = running.copy(id = uuid(99), runs = running.runs.map { it.copy(sessionId = uuid(99)) })
        val mismatch = applyFocusCommand(wrongSession, command(running, 15, FocusAction.PAUSE, 0, initial))
        assertEquals(FocusTransitionResult.Invalid(FocusInvalidReason.SESSION_MISMATCH), mismatch)
    }

    @Test fun continuityUsesElapsedTimeAndAcceptsExactlyTwoSecondsOfWallDrift() {
        val checkpoint = sample(start, 100_000)
        for (drift in listOf(-2_000L, -1_999L, 0L, 1_999L, 2_000L)) {
            val next = sample(start.plusMillis(60_000 + drift), 160_000)
            assertEquals(
                "drift=$drift",
                FocusSampleContinuity.Continuous(60_000),
                focusSampleContinuity(checkpoint, next),
            )
        }

        for (drift in listOf(-2_001L, 2_001L)) {
            val next = sample(start.plusMillis(60_000 + drift), 160_000)
            assertEquals(
                "drift=$drift",
                FocusSampleContinuity.Discontinuous(FocusContinuityFailure.WALL_CLOCK_DISCONTINUITY),
                focusSampleContinuity(checkpoint, next),
            )
        }

        assertEquals(
            FocusSampleContinuity.Discontinuous(FocusContinuityFailure.UNKNOWN_BOOT_ID),
            focusSampleContinuity(checkpoint, checkpoint.copy(bootId = null)),
        )
        assertEquals(
            FocusSampleContinuity.Discontinuous(FocusContinuityFailure.BOOT_CHANGED),
            focusSampleContinuity(checkpoint, checkpoint.copy(bootId = "boot-B")),
        )
        assertEquals(
            FocusSampleContinuity.Discontinuous(FocusContinuityFailure.ELAPSED_CLOCK_REGRESSED),
            focusSampleContinuity(checkpoint, checkpoint.copy(elapsedMillis = 99_999)),
        )
    }

    @Test fun timingProjectionUsesMonotonicDeltaAndKeepsCheckpointOnDiscontinuity() {
        val initial = sample(start, 10_000)
        val session = createFocusSession(uuid(20), uuid(21), initial)
        val run = session.runs.single()
        val checkpointSample = sample(start.plusSeconds(10), 20_000)
        val checkpoint = FocusTimingCheckpoint(run.id, checkpointSample)

        val projection = calculateFocusTiming(
            run.copy(creditedMillis = 10_000),
            checkpoint,
            sample(start.plusSeconds(70), 80_000),
        )
        assertEquals(
            FocusTimingResult.Advanced(
                creditedMillis = 70_000,
                canonicalEnd = start.plusMillis(70_000),
                checkpoint = FocusTimingCheckpoint(run.id, sample(start.plusSeconds(70), 80_000)),
                elapsedDeltaMillis = 60_000,
            ),
            projection,
        )

        val failed = sample(start.plusSeconds(95), 90_000, bootId = "boot-B")
        val interrupted = calculateFocusTiming(run.copy(creditedMillis = 10_000), checkpoint, failed)
        assertTrue(interrupted is FocusTimingResult.InterruptedAtCheckpoint)
        interrupted as FocusTimingResult.InterruptedAtCheckpoint
        assertEquals(10_000L, interrupted.creditedMillis)
        assertEquals(checkpoint, interrupted.checkpoint)
        assertEquals(FocusContinuityFailure.BOOT_CHANGED, interrupted.reason)
        assertEquals(failed, interrupted.failedSample)
    }

    @Test fun recoveryDistinguishesSameProcessFromNewProcessAndPausedState() {
        val initial = sample(start, 1_000)
        val session = createFocusSession(uuid(30), uuid(31), initial)
        val checkpoint = FocusTimingCheckpoint(session.runs.single().id, initial)
        val now = sample(start.plusSeconds(600), 601_000)

        assertTrue(assessFocusRecovery(session, checkpoint, now, processOwnerChanged = false) is FocusRecoveryAssessment.Continue)
        val required = assessFocusRecovery(session, checkpoint, now, processOwnerChanged = true)
        assertTrue(required is FocusRecoveryAssessment.ChoiceRequired)
        required as FocusRecoveryAssessment.ChoiceRequired
        assertEquals(600_000L, required.projection.creditedMillis)

        val paused = changed(applyFocusCommand(
            session,
            command(session, 32, FocusAction.PAUSE, 0, sample(start.plusSeconds(60), 61_000)),
            checkpoint,
        )).snapshot
        assertEquals(FocusRecoveryAssessment.NoChange, assessFocusRecovery(paused, null, now, processOwnerChanged = true))
    }

    @Test fun explicitRecoveryChoiceEitherAcceptsElapsedGapOrEndsAtCheckpoint() {
        val initial = sample(start, 5_000)
        val open = createFocusSession(uuid(40), uuid(41), initial)
        val checkpointSample = sample(start.plusSeconds(120), 125_000)
        val checkpoint = FocusTimingCheckpoint(open.runs.single().id, checkpointSample)
        val session = open.copy(runs = listOf(open.runs.single().copy(creditedMillis = 120_000)))
        val recoverySample = sample(start.plusSeconds(600), 605_000)

        val continued = changed(resolveFocusRecovery(
            session, 0, checkpoint, recoverySample, FocusRecoveryChoice.CONTINUE_ELAPSED,
        )).snapshot
        assertEquals(FocusState.RUNNING, continued.state)
        assertEquals(600_000L, continued.creditedMillis)
        assertTrue(continued.recoveredOrUncertain)

        val ended = changed(resolveFocusRecovery(
            session, 0, checkpoint, recoverySample, FocusRecoveryChoice.END_AT_CHECKPOINT,
        )).snapshot
        assertEquals(FocusState.INTERRUPTED, ended.state)
        assertEquals(FocusTerminalReason.PROCESS_RESTART_GAP, ended.terminalReason)
        assertEquals(120_000L, ended.creditedMillis)
        assertEquals(checkpointSample.wall, ended.endedAt)
        assertTrue(ended.recoveredOrUncertain)
    }

    @Test fun recoveryDiscontinuityAndPausedRebootNeverCreditUnknownTime() {
        val initial = sample(start, 0)
        val open = createFocusSession(uuid(50), uuid(51), initial)
        val checkpointSample = sample(start.plusSeconds(120), 120_000)
        val checkpoint = FocusTimingCheckpoint(open.runs.single().id, checkpointSample)
        val session = open.copy(runs = listOf(open.runs.single().copy(creditedMillis = 120_000)))
        val rebooted = sample(start.plusSeconds(900), 0, bootId = "boot-B")

        val interrupted = changed(resolveFocusRecovery(
            session, 0, checkpoint, rebooted, FocusRecoveryChoice.CONTINUE_ELAPSED,
        )).snapshot
        assertEquals(FocusState.INTERRUPTED, interrupted.state)
        assertEquals(FocusTerminalReason.BOOT_CHANGED, interrupted.terminalReason)
        assertEquals(120_000L, interrupted.creditedMillis)

        val paused = changed(applyFocusCommand(
            open,
            command(session, 52, FocusAction.PAUSE, 0, checkpointSample),
            FocusTimingCheckpoint(open.runs.single().id, initial),
        )).snapshot
        assertEquals(FocusState.PAUSED, paused.state)
        assertEquals(FocusRecoveryAssessment.NoChange, assessFocusRecovery(paused, null, rebooted, processOwnerChanged = true))
        assertFalse(paused.runs.any(FocusRun::isOpen))
    }

    @Test fun checkedArithmeticReportsDurationAndTimestampOverflow() {
        val checkpointSample = sample(start, 0)
        val session = createFocusSession(uuid(60), uuid(61), checkpointSample)
        val run = session.runs.single().copy(creditedMillis = Long.MAX_VALUE)
        val elapsedOverflow = calculateFocusTiming(
            run,
            FocusTimingCheckpoint(run.id, checkpointSample),
            sample(start.plusMillis(1), 1),
        )
        assertEquals(FocusTimingResult.Invalid(FocusTimingError.ARITHMETIC_OVERFLOW), elapsedOverflow)

        val nearMax = Instant.MAX.minusMillis(1)
        val edgeSample = sample(nearMax, 0)
        val edgeRun = FocusRun(uuid(62), uuid(63), 1, nearMax, null, 0)
        val timestampOverflow = calculateFocusTiming(
            edgeRun,
            FocusTimingCheckpoint(edgeRun.id, edgeSample),
            sample(Instant.MAX, 2),
        )
        assertEquals(FocusTimingResult.Invalid(FocusTimingError.TIMESTAMP_OVERFLOW), timestampOverflow)
    }

    @Test fun terminalStopIsIdempotentAndIdenticalRunRowsAreNormalized() {
        val initial = sample(start, 0)
        val session = createFocusSession(uuid(70), uuid(71), initial)
        val duplicated = session.copy(runs = listOf(session.runs.single(), session.runs.single()))
        val stopSample = sample(start.plusSeconds(30), 30_000)
        val result = changed(applyFocusCommand(
            duplicated,
            command(duplicated, 72, FocusAction.STOP, 0, stopSample),
            FocusTimingCheckpoint(session.runs.single().id, initial),
        ))
        assertEquals(1, result.snapshot.runs.size)
        assertEquals(30_000L, result.snapshot.creditedMillis)

        val repeatedStop = applyFocusCommand(
            result.snapshot,
            command(result.snapshot, 73, FocusAction.STOP, 1, sample(start.plusSeconds(31), 31_000)),
        )
        assertTrue(repeatedStop is FocusTransitionResult.AlreadyApplied)
        repeatedStop as FocusTransitionResult.AlreadyApplied
        assertEquals(1, repeatedStop.snapshot.runs.size)
    }

    private fun sample(wall: Instant, elapsed: Long, bootId: String = boot) =
        FocusTimeSample(wall, elapsed, bootId)

    private fun command(
        session: FocusSession,
        command: Int,
        action: FocusAction,
        revision: Long,
        sample: FocusTimeSample,
    ) = FocusCommand(
        commandId = uuid(command),
        sessionId = session.id,
        expectedRevision = revision,
        action = action,
        sample = sample,
        context = FocusWriteContext(uuid(100), uuid(101)),
    )

    private fun changed(result: FocusTransitionResult): FocusTransitionResult.Changed {
        assertTrue("Expected Changed, was $result", result is FocusTransitionResult.Changed)
        return result as FocusTransitionResult.Changed
    }

    private fun uuid(value: Int): UUID = UUID(0, value.toLong())
}
