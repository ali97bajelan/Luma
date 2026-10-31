package com.luma.lifeos.lock

import com.luma.lifeos.domain.lock.AccessDecision
import com.luma.lifeos.domain.lock.LockContentClassification
import com.luma.lifeos.domain.lock.LockCoverage
import com.luma.lifeos.domain.lock.LockOperation
import com.luma.lifeos.domain.lock.LockRequest
import com.luma.lifeos.domain.lock.LockSection
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** One in-memory confirmation ticket for an existing-record widget or reminder action. */
class ProtectedActionConfirmation internal constructor(
    internal val id: UUID,
    internal val section: LockSection,
    internal val policyGeneration: Long,
    internal val sessionGeneration: Long,
) {
    private val confirmed = AtomicBoolean(false)
    private val dispatched = AtomicBoolean(false)

    internal fun confirmOnce(): Boolean = confirmed.compareAndSet(false, true)
    internal fun consumeForDispatch(): Boolean = confirmed.get() && dispatched.compareAndSet(false, true)
}

/** Keeps external action confirmation tied to the current grant and policy generation. */
class ProtectedActionDispatcher(private val coordinator: AppLockCoordinator) {
    fun requiresUnlock(section: LockSection): Boolean {
        val snapshot = coordinator.snapshot.value
        return snapshot.decide(request(section, LockOperation.READ)) != AccessDecision.Allow
    }

    /** Returns null when this section is not protected by the current policy. */
    fun begin(section: LockSection): ProtectedActionConfirmation? {
        val snapshot = coordinator.snapshot.value
        val policy = snapshot.policy ?: return null
        if (snapshot.loadStatus != com.luma.lifeos.domain.lock.LockLoadStatus.READY || !policy.enabled) return null
        val protected = policy.coverage == LockCoverage.WHOLE_APP || section in policy.sections
        if (!protected) return null
        return ProtectedActionConfirmation(
            id = UUID.randomUUID(),
            section = section,
            policyGeneration = policy.generation,
            sessionGeneration = snapshot.sessionGeneration,
        )
    }

    /** Called only by the explicit confirmation button after any required unlock has succeeded. */
    fun confirm(ticket: ProtectedActionConfirmation): Boolean {
        val snapshot = coordinator.snapshot.value
        if (snapshot.loadStatus != com.luma.lifeos.domain.lock.LockLoadStatus.READY ||
            snapshot.policy?.enabled != true || snapshot.policy?.generation != ticket.policyGeneration ||
            snapshot.sessionGeneration != ticket.sessionGeneration ||
            snapshot.decide(request(ticket.section, LockOperation.UPDATE)) != AccessDecision.Allow
        ) return false
        return ticket.confirmOnce()
    }

    /** Consume the confirmation before queuing work; workers independently revalidate at commit. */
    fun consumeForDispatch(ticket: ProtectedActionConfirmation): Boolean {
        val snapshot = coordinator.snapshot.value
        if (snapshot.loadStatus != com.luma.lifeos.domain.lock.LockLoadStatus.READY ||
            snapshot.policy?.enabled != true || snapshot.policy?.generation != ticket.policyGeneration ||
            snapshot.sessionGeneration != ticket.sessionGeneration ||
            snapshot.decide(request(ticket.section, LockOperation.UPDATE)) != AccessDecision.Allow
        ) return false
        return ticket.consumeForDispatch()
    }

    private fun request(section: LockSection, operation: LockOperation) = LockRequest(
        operation = operation,
        requiredSections = setOf(section),
        contentClassification = LockContentClassification.CLASSIFIED,
    )
}
