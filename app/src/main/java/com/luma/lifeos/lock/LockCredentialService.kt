package com.luma.lifeos.lock

import com.luma.lifeos.domain.lock.LockPolicy
import com.luma.lifeos.domain.lock.LockSection
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

enum class AuthPurpose {
    BROWSE,
    OPEN_LOCK_SETTINGS,
    CHANGE_PASSWORD,
    CHANGE_POLICY,
    DISABLE_LOCK,
    REPLACE_RECOVERY_CODE,
    SHARE,
    EXPORT,
    BACKUP,
    RESTORE,
    RESET,
}

sealed interface CredentialResult {
    data class Success internal constructor(val proof: OperationProof? = null) : CredentialResult {
        override fun toString(): String = "Success(proof=${if (proof == null) "none" else "redacted"})"
    }

    data object InvalidInput : CredentialResult
    data object Incorrect : CredentialResult
    data class Cooldown(val remainingMs: Long) : CredentialResult {
        init { require(remainingMs > 0L) }
    }
    data object Cancelled : CredentialResult
    data object Unavailable : CredentialResult
}

/** Purpose- and generation-bound in-memory authority. It is intentionally not serializable. */
class OperationProof internal constructor(
    internal val purpose: AuthPurpose,
    internal val policyGeneration: Long,
) {
    private val consumed = AtomicBoolean(false)
    private var browseBinding: BrowseBinding? = null
    private var operationBinding: UUID? = null
    private var validatedOperation: UUID? = null

    /** A non-browse proof must be bound to the operation token captured before authentication. */
    @Synchronized
    internal fun bindPendingOperation(operationId: UUID, currentGeneration: Long): Boolean {
        if (purpose == AuthPurpose.BROWSE || policyGeneration != currentGeneration || consumed.get()) {
            consumed.set(true)
            return false
        }
        val existing = operationBinding
        if (existing == null) {
            operationBinding = operationId
            return true
        }
        if (existing == operationId) return true
        validatedOperation = null
        consumed.set(true)
        return false
    }

    @Synchronized
    internal fun validatePendingOperation(operationId: UUID, currentGeneration: Long): Boolean {
        val valid = purpose != AuthPurpose.BROWSE && policyGeneration == currentGeneration &&
            !consumed.get() && operationBinding == operationId
        if (valid) {
            validatedOperation = operationId
        } else {
            validatedOperation = null
            consumed.set(true)
        }
        return valid
    }

    @Synchronized
    internal fun consumePendingOperation(
        expectedPurpose: AuthPurpose,
        currentGeneration: Long,
        operationId: UUID,
    ): Boolean {
        if (!validatePendingOperation(operationId, currentGeneration)) return false
        return consume(expectedPurpose, currentGeneration)
    }

    /** Mutation methods consume only after the caller validates the unchanged pending UUID. */
    @Synchronized
    internal fun consume(expectedPurpose: AuthPurpose, currentGeneration: Long): Boolean {
        if (purpose == AuthPurpose.BROWSE || operationBinding == null ||
            validatedOperation != operationBinding
        ) return false
        if (!consumed.compareAndSet(false, true)) return false
        return purpose == expectedPurpose && policyGeneration == currentGeneration
    }

    /**
     * Binds browsing proof to the coordinator's request captured when the password prompt opened.
     * The coordinator must compare that captured request with its current pending request first.
     */
    @Synchronized
    internal fun bindBrowseRequest(
        sections: Set<LockSection>,
        wholeApp: Boolean,
        currentGeneration: Long,
    ): Boolean {
        if (purpose != AuthPurpose.BROWSE || policyGeneration != currentGeneration || consumed.get() ||
            (wholeApp && sections.isNotEmpty())
        ) {
            consumed.set(true)
            return false
        }
        val candidate = BrowseBinding(sections.toSet(), wholeApp)
        val existing = browseBinding
        if (existing == null) {
            browseBinding = candidate
            return true
        }
        if (existing == candidate) return true
        consumed.set(true)
        return false
    }

    @Synchronized
    internal fun consumeBrowseRequest(
        sections: Set<LockSection>,
        wholeApp: Boolean,
        currentGeneration: Long,
    ): Boolean {
        val expected = BrowseBinding(sections.toSet(), wholeApp)
        if (purpose != AuthPurpose.BROWSE || policyGeneration != currentGeneration ||
            browseBinding != expected
        ) {
            consumed.set(true)
            return false
        }
        return consumed.compareAndSet(false, true)
    }

    override fun toString(): String = "OperationProof(<redacted>)"

    private data class BrowseBinding(val sections: Set<LockSection>, val wholeApp: Boolean)
}

/** A recovery code can be retrieved for display once, then the local array is cleared. */
class RecoveryPreparation internal constructor(
    internal val id: UUID,
    code: CharArray,
) {
    private var displayCode: CharArray? = code.copyOf()

    @Synchronized
    fun takeCodeForDisplay(): CharArray {
        val value = displayCode ?: throw IllegalStateException("Recovery code is no longer available")
        displayCode = null
        return value
    }

    @Synchronized
    internal fun clear() {
        displayCode?.fill('\u0000')
        displayCode = null
    }

    override fun toString(): String = "RecoveryPreparation(<redacted>)"
}

/** Opaque acknowledgment that a display-once recovery code was saved by the user. */
class RecoveryConfirmation internal constructor(internal val id: UUID) {
    override fun toString(): String = "RecoveryConfirmation(<redacted>)"
}

class LockCredentialUnavailableException(cause: Throwable? = null) :
    Exception("App lock credentials are unavailable", cause)

/**
 * Takes ownership of passed secret arrays and clears them before returning. Proofs and recovery
 * handles are process-local and must never be put in saved state, intents, archives, or logs.
 */
interface LockCredentialService {
    suspend fun enable(
        password: CharArray,
        policy: LockPolicy,
        recoveryConfirmation: RecoveryConfirmation,
    ): CredentialResult

    suspend fun verify(password: CharArray, purpose: AuthPurpose): CredentialResult

    suspend fun changePassword(proof: OperationProof, replacement: CharArray): CredentialResult

    suspend fun recover(
        code: CharArray,
        replacement: CharArray,
        confirmation: RecoveryConfirmation,
    ): CredentialResult

    suspend fun disable(proof: OperationProof): CredentialResult

    suspend fun prepareRecovery(): RecoveryPreparation

    suspend fun confirmRecovery(preparation: RecoveryPreparation): RecoveryConfirmation

    suspend fun cancelRecovery(preparation: RecoveryPreparation)

    suspend fun replaceRecoveryCode(
        proof: OperationProof,
        confirmation: RecoveryConfirmation,
    ): CredentialResult

    suspend fun updatePolicy(proof: OperationProof, policy: LockPolicy): CredentialResult
}
