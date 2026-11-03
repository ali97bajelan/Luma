package com.luma.lifeos.lock

import android.os.SystemClock
import com.luma.lifeos.domain.lock.LockPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

internal data class LockClockSample(val elapsedMs: Long, val wallClockMs: Long)

internal enum class LockSessionDisposition { REVOKE, PRESERVE_FOREGROUND }

internal fun interface LockClock {
    fun sample(): LockClockSample
}

internal class AndroidLockClock : LockClock {
    override fun sample(): LockClockSample = LockClockSample(
        elapsedMs = SystemClock.elapsedRealtime(),
        wallClockMs = System.currentTimeMillis(),
    )
}

/** The app-facing service; all mutations are serialized and revalidated against stored generation. */
class DefaultLockCredentialService internal constructor(
    private val store: LocalLockStore,
    private val crypto: AndroidLockCrypto,
    private val clock: LockClock,
    private val onSecurityStateChanged: (LockPolicy, LockSessionDisposition) -> Unit,
) : LockCredentialService {
    private val mutex = PROCESS_MUTEX
    private val pendingRecoveries = mutableMapOf<UUID, PendingRecovery>()

    override suspend fun credentialDescriptor(): CredentialDescriptorResult = try {
        mutex.withLock {
            val state = readState() ?: return@withLock CredentialDescriptorResult.Disabled
            if (!state.policy.enabled) return@withLock CredentialDescriptorResult.Disabled
            val credential = state.credential ?: return@withLock CredentialDescriptorResult.Unavailable
            CredentialDescriptorResult.Ready(
                LockCredentialDescriptor(credential.type, state.policy.generation),
            )
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        CredentialDescriptorResult.Unavailable
    }

    override suspend fun enable(
        secret: CharArray,
        type: LockCredentialType,
        policy: LockPolicy,
        recoveryConfirmation: RecoveryConfirmation,
    ): CredentialResult = withCleared(secret) {
        val canonical = LockCredentialInputPolicy.canonicalize(type, secret)
            ?: return@withCleared CredentialResult.InvalidInput
        try {
            safely {
                mutex.withLock {
                    if (!policy.enabled) return@withLock CredentialResult.InvalidInput
                    val state = readState()
                    if (state?.policy?.enabled == true) return@withLock CredentialResult.InvalidInput
                    val pending = confirmedRecovery(recoveryConfirmation, state?.policy?.generation ?: 0L)
                        ?: return@withLock CredentialResult.InvalidInput
                    val generation = nextGeneration(state?.policy?.generation ?: 0L)
                        ?: return@withLock CredentialResult.Unavailable
                    val newPolicy = policy.copy(generation = generation)
                    val sample = validSample()
                    val newCredential = try {
                        newPasswordCredential(canonical, type, pending.recoverySalt, pending.recoveryVerifier)
                    } catch (cancelled: CancellationException) {
                        if (state == null) discardUncommittedInitialKey()
                        throw cancelled
                    } catch (failure: Exception) {
                        if (state == null) discardUncommittedInitialKey()
                        throw failure
                    }
                    val newState = PersistedLockState(
                        policy = newPolicy,
                        credential = newCredential,
                        retryBootMarkerMs = bootMarker(sample),
                    )
                    commitSecurityState(
                        newState,
                        newPolicy,
                        afterCommit = { consumeRecovery(pending) },
                        sessionDisposition = LockSessionDisposition.PRESERVE_FOREGROUND,
                    )
                    CredentialResult.Success()
                }
            }
        } finally {
            canonical.fill('\u0000')
        }
    }

    override suspend fun verify(
        secret: CharArray,
        purpose: AuthPurpose,
        expectedGeneration: Long?,
    ): CredentialResult = withCleared(secret) {
            safely {
                mutex.withLock {
                    val state = readState() ?: return@withLock CredentialResult.Unavailable
                    val credential = state.credential ?: return@withLock CredentialResult.Unavailable
                    if (!state.policy.enabled) return@withLock CredentialResult.Unavailable
                    if (expectedGeneration != null && expectedGeneration != state.policy.generation) {
                        return@withLock CredentialResult.Cancelled
                    }
                    val sample = validSample()
                    val reconciled = reconcileBoot(state, sample)
                    val cooldown = activeCooldown(reconciled, sample.elapsedMs)
                    if (cooldown != null) {
                        if (reconciled != state) writeState(reconciled)
                        return@withLock CredentialResult.Cooldown(cooldown)
                    }

                    var ready = reconciled
                    if (ready.cooldownActive) {
                        ready = ready.copy(cooldownActive = false, cooldownUntilElapsedMs = 0L)
                        // Persist expiry before verification so a reboot cannot restart this cooldown.
                        writeState(ready)
                    }
                    val canonical = LockCredentialInputPolicy.canonicalize(credential.type, secret)
                        ?: return@withLock CredentialResult.InvalidInput
                    try {
                        val matches = withContext(Dispatchers.Default) {
                            crypto.passwordMatches(canonical, credential.passwordSalt, credential.passwordVerifier)
                        }
                        if (!matches) return@withLock recordFailure(ready, sample)

                        val reset = resetFailures(ready, sample)
                        if (reset != ready) writeState(reset)
                        CredentialResult.Success(OperationProof(purpose, reset.policy.generation))
                    } finally {
                        canonical.fill('\u0000')
                    }
                }
            }
        }

    override suspend fun changePassword(
        proof: OperationProof,
        replacement: CharArray,
        replacementType: LockCredentialType,
    ): CredentialResult =
        withCleared(replacement) {
            safely {
                mutex.withLock {
                    val canonical = LockCredentialInputPolicy.canonicalize(replacementType, replacement)
                        ?: return@withLock CredentialResult.InvalidInput
                    try {
                        val state = readState() ?: return@withLock CredentialResult.Unavailable
                        val oldCredential = state.credential ?: return@withLock CredentialResult.Unavailable
                        if (!state.policy.enabled) return@withLock CredentialResult.Unavailable
                        if (!proof.consume(AuthPurpose.CHANGE_PASSWORD, state.policy.generation)) {
                            return@withLock CredentialResult.Cancelled
                        }
                        val generation = nextGeneration(state.policy.generation)
                            ?: return@withLock CredentialResult.Unavailable
                        val changedCredential = withContext(Dispatchers.Default) {
                            val salt = crypto.randomSalt()
                            oldCredential.copy(
                                type = replacementType,
                                passwordSalt = salt,
                                passwordVerifier = crypto.createPasswordVerifier(canonical, salt),
                            )
                        }
                        val changedPolicy = state.policy.copy(generation = generation)
                        commitSecurityState(
                            resetFailures(state.copy(policy = changedPolicy, credential = changedCredential), validSample()),
                            changedPolicy,
                            sessionDisposition = LockSessionDisposition.PRESERVE_FOREGROUND,
                        )
                        CredentialResult.Success()
                    } finally {
                        canonical.fill('\u0000')
                    }
                }
            }
        }

    override suspend fun recover(
        code: CharArray,
        replacement: CharArray,
        replacementType: LockCredentialType,
        confirmation: RecoveryConfirmation,
    ): CredentialResult = withCleared(code, replacement) {
        safely {
            mutex.withLock {
                val state = readState() ?: return@withLock CredentialResult.Unavailable
                val oldCredential = state.credential ?: return@withLock CredentialResult.Unavailable
                if (!state.policy.enabled) return@withLock CredentialResult.Unavailable
                val sample = validSample()
                val reconciled = reconcileBoot(state, sample)
                val cooldown = activeCooldown(reconciled, sample.elapsedMs)
                if (cooldown != null) {
                    if (reconciled != state) writeState(reconciled)
                    return@withLock CredentialResult.Cooldown(cooldown)
                }

                var ready = reconciled
                if (ready.cooldownActive) {
                    ready = ready.copy(cooldownActive = false, cooldownUntilElapsedMs = 0L)
                    writeState(ready)
                }
                val canonical = LockCredentialInputPolicy.canonicalize(replacementType, replacement)
                    ?: return@withLock CredentialResult.InvalidInput
                try {
                    val pending = confirmedRecovery(confirmation, state.policy.generation)
                        ?: return@withLock CredentialResult.InvalidInput
                    val codeMatches = withContext(Dispatchers.Default) {
                        crypto.recoveryCodeMatches(code, oldCredential.recoverySalt, oldCredential.recoveryVerifier)
                    }
                    if (!codeMatches) return@withLock recordFailure(ready, sample)

                    val generation = nextGeneration(state.policy.generation)
                        ?: return@withLock CredentialResult.Unavailable
                    val replacementCredential = newPasswordCredential(
                        canonical,
                        replacementType,
                        pending.recoverySalt,
                        pending.recoveryVerifier,
                    )
                    val changedPolicy = state.policy.copy(generation = generation)
                    val changedState = resetFailures(
                        ready.copy(policy = changedPolicy, credential = replacementCredential),
                        sample,
                    )
                    commitSecurityState(changedState, changedPolicy) { consumeRecovery(pending) }
                    CredentialResult.Success()
                } finally {
                    canonical.fill('\u0000')
                }
            }
        }
    }

    override suspend fun disable(proof: OperationProof): CredentialResult = safely {
        mutex.withLock {
            val state = readState() ?: return@withLock CredentialResult.Unavailable
            if (!state.policy.enabled || state.credential == null) return@withLock CredentialResult.Unavailable
            if (!proof.consume(AuthPurpose.DISABLE_LOCK, state.policy.generation)) {
                return@withLock CredentialResult.Cancelled
            }
            val generation = nextGeneration(state.policy.generation)
                ?: return@withLock CredentialResult.Unavailable
            val disabledPolicy = state.policy.copy(enabled = false, generation = generation)
            val disabledState = PersistedLockState(policy = disabledPolicy, credential = null)
            commitSecurityState(disabledState, disabledPolicy) { clearPendingRecoveries() }
            CredentialResult.Success()
        }
    }

    override suspend fun prepareRecovery(): RecoveryPreparation = try {
        mutex.withLock {
            val state = readState()
            clearPendingRecoveries()
            val code = withContext(Dispatchers.Default) { crypto.generateRecoveryCode() }
            try {
                val salt = withContext(Dispatchers.Default) { crypto.randomSalt() }
                val verifier = withContext(Dispatchers.Default) { crypto.recoveryVerifier(code, salt) }
                val id = UUID.randomUUID()
                pendingRecoveries[id] = PendingRecovery(
                    id = id,
                    baseGeneration = state?.policy?.generation ?: 0L,
                    recoverySalt = salt,
                    recoveryVerifier = verifier,
                )
                RecoveryPreparation(id, code)
            } finally {
                code.fill('\u0000')
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        throw LockCredentialUnavailableException(failure)
    }

    override suspend fun confirmRecovery(preparation: RecoveryPreparation): RecoveryConfirmation = try {
        mutex.withLock {
            val pending = pendingRecoveries[preparation.id]
                ?: throw LockCredentialUnavailableException()
            if (pending.confirmed) throw LockCredentialUnavailableException()
            pending.confirmed = true
            preparation.clear()
            RecoveryConfirmation(pending.id)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        if (failure is LockCredentialUnavailableException) throw failure
        throw LockCredentialUnavailableException(failure)
    }

    override suspend fun cancelRecovery(preparation: RecoveryPreparation) {
        mutex.withLock {
            preparation.clear()
            pendingRecoveries.remove(preparation.id)?.clear()
        }
    }

    override suspend fun replaceRecoveryCode(
        proof: OperationProof,
        confirmation: RecoveryConfirmation,
    ): CredentialResult = safely {
        mutex.withLock {
            val state = readState() ?: return@withLock CredentialResult.Unavailable
            val credential = state.credential ?: return@withLock CredentialResult.Unavailable
            if (!state.policy.enabled) return@withLock CredentialResult.Unavailable
            val pending = confirmedRecovery(confirmation, state.policy.generation)
                ?: return@withLock CredentialResult.InvalidInput
            if (!proof.consume(AuthPurpose.REPLACE_RECOVERY_CODE, state.policy.generation)) {
                return@withLock CredentialResult.Cancelled
            }
            val generation = nextGeneration(state.policy.generation)
                ?: return@withLock CredentialResult.Unavailable
            val changedPolicy = state.policy.copy(generation = generation)
            val changedCredential = credential.copy(
                recoverySalt = pending.recoverySalt.copyOf(),
                recoveryVerifier = pending.recoveryVerifier.copyOf(),
            )
            commitSecurityState(
                resetFailures(state.copy(policy = changedPolicy, credential = changedCredential), validSample()),
                changedPolicy,
            ) { consumeRecovery(pending) }
            CredentialResult.Success()
        }
    }

    override suspend fun updatePolicy(proof: OperationProof, policy: LockPolicy): CredentialResult = safely {
        mutex.withLock {
            if (!policy.enabled) return@withLock CredentialResult.InvalidInput
            val state = readState() ?: return@withLock CredentialResult.Unavailable
            val credential = state.credential ?: return@withLock CredentialResult.Unavailable
            if (!state.policy.enabled) return@withLock CredentialResult.Unavailable
            if (!proof.consume(AuthPurpose.CHANGE_POLICY, state.policy.generation)) {
                return@withLock CredentialResult.Cancelled
            }
            val generation = nextGeneration(state.policy.generation)
                ?: return@withLock CredentialResult.Unavailable
            val changedPolicy = policy.copy(generation = generation)
            commitSecurityState(
                resetFailures(state.copy(policy = changedPolicy, credential = credential), validSample()),
                changedPolicy,
            )
            CredentialResult.Success()
        }
    }

    private suspend fun newPasswordCredential(
        secret: CharArray,
        type: LockCredentialType,
        recoverySalt: ByteArray,
        recoveryVerifier: ByteArray,
    ): PersistedCredential = withContext(Dispatchers.Default) {
        val salt = crypto.randomSalt()
        PersistedCredential(
            type = type,
            passwordSalt = salt,
            passwordVerifier = crypto.createPasswordVerifier(secret, salt),
            recoverySalt = recoverySalt.copyOf(),
            recoveryVerifier = recoveryVerifier.copyOf(),
        )
    }

    private suspend fun recordFailure(state: PersistedLockState, sample: LockClockSample): CredentialResult {
        val failedAttempts = if (state.failedAttempts == Long.MAX_VALUE) Long.MAX_VALUE else state.failedAttempts + 1L
        val delayMs = LockRetryPolicy.cooldownAfterFailure(failedAttempts)
        val until = if (delayMs == 0L) 0L else saturatedAdd(sample.elapsedMs, delayMs)
        val updated = state.copy(
            failedAttempts = failedAttempts,
            cooldownActive = delayMs > 0L,
            cooldownUntilElapsedMs = until,
            retryBootMarkerMs = bootMarker(sample),
        )
        writeState(updated)
        return if (delayMs == 0L) CredentialResult.Incorrect else CredentialResult.Cooldown(delayMs)
    }

    private fun reconcileBoot(state: PersistedLockState, sample: LockClockSample): PersistedLockState {
        val marker = bootMarker(sample)
        if (withinSameBoot(state.retryBootMarkerMs, marker)) return state
        return if (state.cooldownActive) {
            state.copy(
                cooldownUntilElapsedMs = saturatedAdd(sample.elapsedMs, LockRetryPolicy.MAX_COOLDOWN_MS),
                retryBootMarkerMs = marker,
            )
        } else {
            state.copy(retryBootMarkerMs = marker)
        }
    }

    private fun activeCooldown(state: PersistedLockState, nowElapsedMs: Long): Long? {
        if (!state.cooldownActive) return null
        return LockRetryPolicy.remainingMs(state.cooldownUntilElapsedMs, nowElapsedMs).takeIf { it > 0L }
    }

    private fun resetFailures(state: PersistedLockState, sample: LockClockSample): PersistedLockState {
        // A clean successful unlock does not need a new boot marker or a durable file write.
        if (state.failedAttempts == 0L && !state.cooldownActive && state.cooldownUntilElapsedMs == 0L) {
            return state
        }
        return state.copy(
            failedAttempts = 0L,
            cooldownActive = false,
            cooldownUntilElapsedMs = 0L,
            retryBootMarkerMs = bootMarker(sample),
        )
    }

    private fun confirmedRecovery(confirmation: RecoveryConfirmation, generation: Long): PendingRecovery? =
        pendingRecoveries[confirmation.id]?.takeIf {
            it.id == confirmation.id && it.confirmed && it.baseGeneration == generation
        }

    private fun consumeRecovery(pending: PendingRecovery) {
        pendingRecoveries.remove(pending.id)?.clear()
    }

    private fun clearPendingRecoveries() {
        pendingRecoveries.values.forEach(PendingRecovery::clear)
        pendingRecoveries.clear()
    }

    private suspend fun readState(): PersistedLockState? = withContext(Dispatchers.IO) { store.read() }

    private suspend fun writeState(state: PersistedLockState) {
        withContext(Dispatchers.IO) { store.writeAtomically(state) }
    }

    /** Commit and generation invalidation cannot be split by coroutine cancellation. */
    private suspend fun commitSecurityState(
        state: PersistedLockState,
        policy: LockPolicy,
        sessionDisposition: LockSessionDisposition = LockSessionDisposition.REVOKE,
        afterCommit: () -> Unit = {},
    ) {
        withContext(NonCancellable) {
            withContext(Dispatchers.IO) { store.writeAtomically(state) }
            afterCommit()
            onSecurityStateChanged(policy, sessionDisposition)
        }
    }

    private suspend fun discardUncommittedInitialKey() {
        withContext(NonCancellable + Dispatchers.IO) {
            runCatching { crypto.removeKeyIfPresent() }
        }
    }

    private fun validSample(): LockClockSample = clock.sample().also {
        if (it.elapsedMs < 0L || it.wallClockMs < 0L) throw IllegalStateException("Invalid lock clock sample")
    }

    private fun bootMarker(sample: LockClockSample): Long =
        (sample.wallClockMs - sample.elapsedMs).coerceAtLeast(0L)

    private fun withinSameBoot(previous: Long, current: Long): Boolean =
        if (previous >= current) previous - current <= BOOT_MARKER_TOLERANCE_MS
        else current - previous <= BOOT_MARKER_TOLERANCE_MS

    private fun nextGeneration(current: Long): Long? =
        if (current == Long.MAX_VALUE) null else current + 1L

    private fun saturatedAdd(left: Long, right: Long): Long =
        if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right

    private suspend fun safely(block: suspend () -> CredentialResult): CredentialResult = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        CredentialResult.Unavailable
    }

    private suspend fun <T> withCleared(secret: CharArray, block: suspend () -> T): T = try {
        block()
    } finally {
        secret.fill('\u0000')
    }

    private suspend fun <T> withCleared(first: CharArray, second: CharArray, block: suspend () -> T): T = try {
        block()
    } finally {
        first.fill('\u0000')
        second.fill('\u0000')
    }

    private inner class PendingRecovery(
        val id: UUID,
        val baseGeneration: Long,
        val recoverySalt: ByteArray,
        val recoveryVerifier: ByteArray,
        var confirmed: Boolean = false,
    ) {
        fun clear() {
            recoverySalt.fill(0)
            recoveryVerifier.fill(0)
        }
    }

    private companion object {
        val PROCESS_MUTEX = Mutex()
        const val BOOT_MARKER_TOLERANCE_MS = 1_000L
    }
}
