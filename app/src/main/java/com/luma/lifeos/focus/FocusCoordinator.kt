package com.luma.lifeos.focus

import com.luma.lifeos.domain.focus.FocusAction
import com.luma.lifeos.domain.focus.FocusCommand
import com.luma.lifeos.domain.focus.FocusRecoveryChoice
import com.luma.lifeos.domain.focus.FocusRepository
import com.luma.lifeos.domain.focus.FocusSession
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
    private suspend fun context() = FocusWriteContext(
        databaseGeneration = repository.currentGeneration(),
        processInstanceId = processInstanceId,
    )

    suspend fun reconcile(): FocusWriteResult = repository.reconcile(clock.sample(), context())

    suspend fun start(commandId: UUID, sessionId: UUID): FocusWriteResult =
        repository.start(commandId, sessionId, clock.sample(), context())

    suspend fun act(commandId: UUID, session: FocusSession, action: FocusAction): FocusWriteResult =
        repository.apply(
            FocusCommand(commandId, session.id, session.revision, action, clock.sample(), context()),
        )

    suspend fun checkpoint(session: FocusSession): FocusWriteResult =
        repository.checkpoint(session.id, session.revision, clock.sample(), context())

    suspend fun resolveRecovery(session: FocusSession, choice: FocusRecoveryChoice): FocusWriteResult =
        repository.resolveRecovery(session.id, session.revision, choice, clock.sample(), context())
}
