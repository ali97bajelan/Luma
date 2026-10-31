package com.luma.lifeos.lock

import com.luma.lifeos.domain.lock.AccessDecision
import com.luma.lifeos.domain.lock.LockPolicy
import com.luma.lifeos.domain.lock.LockRequest
import kotlinx.coroutines.CancellationException

class LockActionDeniedException(val decision: AccessDecision) : SecurityException("App lock denied this action")

/** Serializes UI commits and re-reads the authenticated local policy at admission time. */
class LockActionGuard(
    private val coordinator: AppLockCoordinator,
    private val readPersistedPolicy: suspend () -> com.luma.lifeos.domain.lock.LockPolicy?,
) {
    suspend fun <T> execute(request: LockRequest, commit: suspend () -> T): T =
        coordinator.serializeAction {
            val persisted = try {
                readPersistedPolicy() ?: LockPolicy()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            val decision = coordinator.admissionDecision(request, persisted)
            if (decision != AccessDecision.Allow) throw LockActionDeniedException(decision)
            // Admission is the commit boundary: revocation blocks queued operations, while a
            // commit already admitted here may finish without a mid-transaction partial write.
            commit()
        }
}
