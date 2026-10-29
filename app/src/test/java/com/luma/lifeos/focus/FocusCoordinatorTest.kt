package com.luma.lifeos.focus

import com.luma.lifeos.domain.focus.FocusAction
import com.luma.lifeos.domain.focus.FocusCommand
import com.luma.lifeos.domain.focus.FocusHistoryRow
import com.luma.lifeos.domain.focus.FocusPage
import com.luma.lifeos.domain.focus.FocusRecoveryChoice
import com.luma.lifeos.domain.focus.FocusRepository
import com.luma.lifeos.domain.focus.FocusSession
import com.luma.lifeos.domain.focus.FocusStatistics
import com.luma.lifeos.domain.focus.FocusTerminalReason
import com.luma.lifeos.domain.focus.FocusTimeSample
import com.luma.lifeos.domain.focus.FocusTimingCheckpoint
import com.luma.lifeos.domain.focus.FocusWriteContext
import com.luma.lifeos.domain.focus.FocusWriteResult
import com.luma.lifeos.domain.time.TimeRange
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class FocusCoordinatorTest {
    @Test fun startAndActionsUseTheProvidedClockSampleAndStableProcessIdentity() = runTest {
        val repository = RecordingRepository()
        val first = sample("2026-10-30T12:00:00Z", 10_000)
        val second = sample("2026-10-30T12:00:05Z", 15_000)
        val coordinator = FocusCoordinator(repository, FocusClock { first }, processId)

        coordinator.start(commandId, sessionId, first)
        val session = repository.startedSession!!
        coordinator.act(actionId, session, FocusAction.PAUSE, second)

        assertEquals(first, repository.startSample)
        assertEquals(second, repository.action!!.sample)
        assertEquals(generationId, repository.startContext!!.databaseGeneration)
        assertEquals(processId, repository.startContext!!.processInstanceId)
        assertEquals(repository.startContext, repository.action!!.context)
    }

    @Test fun recoveryCarriesEmergencyTerminalReasonToTheRepository() = runTest {
        val repository = RecordingRepository()
        val coordinator = FocusCoordinator(repository, FocusClock { sample("2026-10-30T12:00:05Z", 15_000) }, processId)
        val session = FocusCoordinatorTest.runningSession()

        coordinator.resolveRecovery(
            session,
            FocusRecoveryChoice.END_AT_CHECKPOINT,
            terminalReason = FocusTerminalReason.EMERGENCY_EXIT,
            sample = sample("2026-10-30T12:00:05Z", 15_000),
        )

        assertEquals(FocusTerminalReason.EMERGENCY_EXIT, repository.recoveryReason)
        assertEquals(FocusRecoveryChoice.END_AT_CHECKPOINT, repository.recoveryChoice)
        assertEquals(processId, repository.recoveryContext!!.processInstanceId)
    }

    private class RecordingRepository : FocusRepository {
        var startSample: FocusTimeSample? = null
        var startContext: FocusWriteContext? = null
        var startedSession: FocusSession? = null
        var action: FocusCommand? = null
        var recoveryReason: FocusTerminalReason? = null
        var recoveryChoice: FocusRecoveryChoice? = null
        var recoveryContext: FocusWriteContext? = null

        override suspend fun currentGeneration() = generationId
        override fun observeOpen(): Flow<FocusSession?> = flowOf(null)
        override suspend fun start(
            commandId: UUID,
            sessionId: UUID,
            sample: FocusTimeSample,
            context: FocusWriteContext,
        ): FocusWriteResult {
            startSample = sample
            startContext = context
            val snapshot = com.luma.lifeos.domain.focus.createFocusSession(sessionId, commandId, sample)
            startedSession = snapshot
            return FocusWriteResult.Applied(snapshot = snapshot)
        }

        override suspend fun apply(command: FocusCommand): FocusWriteResult {
            action = command
            return FocusWriteResult.Conflict(startedSession)
        }

        override suspend fun reconcile(sample: FocusTimeSample, context: FocusWriteContext) =
            FocusWriteResult.Conflict(null)

        override suspend fun resolveRecovery(
            sessionId: UUID,
            expectedRevision: Long,
            choice: FocusRecoveryChoice,
            sample: FocusTimeSample,
            context: FocusWriteContext,
            terminalReason: FocusTerminalReason,
        ): FocusWriteResult {
            recoveryReason = terminalReason
            recoveryChoice = choice
            recoveryContext = context
            return FocusWriteResult.Conflict(null)
        }

        override suspend fun checkpoint(
            sessionId: UUID,
            expectedRevision: Long,
            sample: FocusTimeSample,
            context: FocusWriteContext,
        ) = FocusWriteResult.Conflict(null)

        override fun observeStatistics(range: TimeRange): Flow<FocusStatistics> = emptyFlow()
        override suspend fun history(range: TimeRange?, offset: Int, limit: Int): FocusPage =
            FocusPage(emptyList<FocusHistoryRow>(), offset = 0, total = 0)

        override suspend fun delete(
            sessionId: UUID,
            expectedRevision: Long,
            context: FocusWriteContext,
        ) = FocusWriteResult.Conflict(null)
    }

    companion object {
        private val generationId = UUID.fromString("10000000-0000-0000-0000-000000000001")
        private val processId = UUID.fromString("10000000-0000-0000-0000-000000000002")
        private val sessionId = UUID.fromString("10000000-0000-0000-0000-000000000003")
        private val commandId = UUID.fromString("10000000-0000-0000-0000-000000000004")
        private val actionId = UUID.fromString("10000000-0000-0000-0000-000000000005")

        private fun sample(wall: String, elapsed: Long) = FocusTimeSample(Instant.parse(wall), elapsed, "boot-a")

        private fun runningSession() = com.luma.lifeos.domain.focus.createFocusSession(
            sessionId,
            commandId,
            sample("2026-10-30T12:00:00Z", 10_000),
        )
    }
}
