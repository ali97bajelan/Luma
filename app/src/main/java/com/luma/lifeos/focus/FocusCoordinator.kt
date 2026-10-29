package com.luma.lifeos.focus

import com.luma.lifeos.domain.focus.FocusAction
import com.luma.lifeos.domain.focus.FocusCommand
import com.luma.lifeos.domain.focus.FocusRecoveryChoice
import com.luma.lifeos.domain.focus.FocusRepository
import com.luma.lifeos.domain.focus.FocusSession
import com.luma.lifeos.domain.focus.FocusTimeSample
import com.luma.lifeos.domain.focus.FocusTerminalReason
import kotlinx.coroutines.flow.Flow
import com.luma.lifeos.domain.focus.FocusWriteContext
import com.luma.lifeos.domain.focus.FocusWriteResult
import java.util.UUID

/** Owns the process identity used for durable focus operations.
 *
 * Callers must retain this instance for the lifetime of the application and reconcile before
 * exposing navigation. Results are authoritative; a failed write must not release a guard.
 */
class FocusCoordinator(
    private val repository: FocusRepository,
    private val clock: FocusClock,
    private val processInstanceId: UUID = UUID.randomUUID(),
) {
    fun sample(): FocusTimeSample = clock.sample()

    fun observeOpen(): Flow<FocusSession?> = repository.observeOpen()

    private suspend fun context() = FocusWriteContext(
        databaseGeneration = repository.currentGeneration(),
        processInstanceId = processInstanceId,
    )

    suspend fun reconcile(): FocusWriteResult = repository.reconcile(clock.sample(), context())

    suspend fun start(
        commandId: UUID,
        sessionId: UUID,
        sample: FocusTimeSample = clock.sample(),
    ): FocusWriteResult = repository.start(commandId, sessionId, sample, context())

    suspend fun act(
        commandId: UUID,
        session: FocusSession,
        action: FocusAction,
        sample: FocusTimeSample = clock.sample(),
    ): FocusWriteResult =
        repository.apply(
            FocusCommand(commandId, session.id, session.revision, action, sample, context()),
        )

    suspend fun checkpoint(
        session: FocusSession,
        sample: FocusTimeSample = clock.sample(),
    ): FocusWriteResult = repository.checkpoint(session.id, session.revision, sample, context())

    suspend fun resolveRecovery(
        session: FocusSession,
        choice: FocusRecoveryChoice,
        terminalReason: FocusTerminalReason = FocusTerminalReason.PROCESS_RESTART_GAP,
        sample: FocusTimeSample = clock.sample(),
    ): FocusWriteResult = repository.resolveRecovery(
        session.id,
        session.revision,
        choice,
        sample,
        context(),
        terminalReason,
    )
}
