package com.luma.lifeos.lock

import com.luma.lifeos.domain.lock.LockLoadStatus
import com.luma.lifeos.domain.lock.LockOperation
import com.luma.lifeos.domain.lock.LockPolicy
import kotlinx.coroutines.CancellationException
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/** Process-local handle for one user-initiated export/share/backup/restore/reset operation. */
class PendingSensitiveOperation internal constructor(
    internal val id: UUID,
    val purpose: AuthPurpose,
    internal val policyGeneration: Long,
    internal val operationGeneration: Long,
) {
    @Volatile internal var cancelled = false
}

/** Requires a fresh, purpose-bound proof and consumes it at the serialized operation boundary. */
class LockSensitiveOperationGuard internal constructor(
    private val coordinator: AppLockCoordinator,
    private val credentials: LockCredentialService,
    private val readPersistedPolicy: suspend () -> LockPolicy?,
) {
    private val operationGeneration = AtomicLong(0L)

    fun requiresAuthentication(): Boolean = coordinator.snapshot.value.let { snapshot ->
        snapshot.loadStatus != LockLoadStatus.READY || snapshot.policy?.enabled == true
    }

    /** Null means policy initialization failed; callers must keep sensitive work gated. */
    fun protectionEnabled(): Boolean? = coordinator.snapshot.value.let { snapshot ->
        if (snapshot.loadStatus != LockLoadStatus.READY) null else snapshot.policy?.enabled
    }

    fun begin(purpose: AuthPurpose): PendingSensitiveOperation? {
        require(purpose != AuthPurpose.BROWSE) { "Browse proofs are not sensitive-operation proofs" }
        val snapshot = coordinator.snapshot.value
        val policy = snapshot.policy
        if (snapshot.loadStatus != LockLoadStatus.READY || policy == null) return null
        if (!policy.enabled) return null
        return PendingSensitiveOperation(UUID.randomUUID(), purpose, policy.generation, operationGeneration.get())
    }

    suspend fun verify(
        pending: PendingSensitiveOperation,
        password: CharArray,
    ): CredentialResult {
        if (!isCurrent(pending)) {
            password.fill('\u0000')
            return CredentialResult.Unavailable
        }
        val result = credentials.verify(password, pending.purpose)
        if (result is CredentialResult.Success) {
            val proof = result.proof ?: return CredentialResult.Unavailable
            if (!proof.bindPendingOperation(pending.id, pending.policyGeneration)) {
                cancel(pending)
                return CredentialResult.Cancelled
            }
        }
        return result
    }

    suspend fun <T> execute(
        pending: PendingSensitiveOperation,
        proof: OperationProof,
        operation: suspend () -> T,
    ): T = executeSensitive(pending.purpose, pending, proof, operation)

    /** Rechecks policy at commit even when no prompt was needed when the UI flow began. */
    suspend fun <T> executeSensitive(
        purpose: AuthPurpose,
        pending: PendingSensitiveOperation?,
        proof: OperationProof?,
        operation: suspend () -> T,
    ): T = coordinator.serializeAction {
        val persisted = try {
            readPersistedPolicy() ?: LockPolicy()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        val current = coordinator.snapshot.value
        val policy = current.policy
        if (current.loadStatus != LockLoadStatus.READY || policy == null || persisted != policy) {
            pending?.let { it.cancelled = true }
            throw SecurityException("Sensitive operation authorization is stale")
        }
        if (policy.enabled) {
            val request = pending
            val operationProof = proof
            if (request == null || operationProof == null || request.cancelled || request.purpose != purpose ||
                policy.generation != request.policyGeneration ||
                operationGeneration.get() != request.operationGeneration ||
                !operationProof.consumePendingOperation(purpose, policy.generation, request.id)
            ) {
                request?.let { it.cancelled = true }
                throw SecurityException("Sensitive operation authorization is stale")
            }
            request.cancelled = true
        } else {
            pending?.let { it.cancelled = true }
        }
        operation()
    }

    fun cancel(pending: PendingSensitiveOperation) {
        pending.cancelled = true
    }

    /** Revokes pending proofs when a reset or archive replacement invalidates the old data set. */
    fun invalidateAll() {
        operationGeneration.updateAndGet { current -> if (current == Long.MAX_VALUE) 0L else current + 1L }
    }

    private fun isCurrent(pending: PendingSensitiveOperation): Boolean {
        val snapshot = coordinator.snapshot.value
        return !pending.cancelled && snapshot.loadStatus == LockLoadStatus.READY &&
            snapshot.policy?.enabled == true && snapshot.policy?.generation == pending.policyGeneration &&
            operationGeneration.get() == pending.operationGeneration
    }
}

internal fun AuthPurpose.operation(): LockOperation = when (this) {
    AuthPurpose.SHARE -> LockOperation.SHARE
    AuthPurpose.EXPORT -> LockOperation.EXPORT
    AuthPurpose.BACKUP -> LockOperation.BACKUP
    AuthPurpose.RESTORE -> LockOperation.RESTORE
    AuthPurpose.RESET -> LockOperation.RESET
    else -> throw IllegalArgumentException("Purpose is not a sensitive operation")
}
