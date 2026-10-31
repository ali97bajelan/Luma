package com.luma.lifeos.ui.privacy

import androidx.lifecycle.ViewModel
import com.luma.lifeos.domain.lock.LockPolicy
import com.luma.lifeos.lock.AppLockCoordinator
import com.luma.lifeos.lock.AuthPurpose
import com.luma.lifeos.lock.CredentialResult
import com.luma.lifeos.lock.LockCredentialService
import com.luma.lifeos.lock.OperationProof
import com.luma.lifeos.lock.RecoveryConfirmation
import com.luma.lifeos.lock.RecoveryPreparation
import java.util.UUID

/** Thin UI adapter; credential policy and proof consumption remain in the lock services. */
class AppLockViewModel(
    private val credentials: LockCredentialService,
    private val coordinator: AppLockCoordinator,
) : ViewModel() {
    suspend fun verify(password: CharArray, purpose: AuthPurpose): CredentialResult =
        credentials.verify(password, purpose)

    fun consumeOpenSettings(proof: OperationProof, operationId: UUID): Boolean {
        val generation = coordinator.snapshot.value.policy?.generation ?: return false
        return proof.bindPendingOperation(operationId, generation) &&
            proof.consumePendingOperation(AuthPurpose.OPEN_LOCK_SETTINGS, generation, operationId)
    }

    fun bindPendingOperation(proof: OperationProof, operationId: UUID): Boolean {
        val generation = coordinator.snapshot.value.policy?.generation ?: return false
        return proof.bindPendingOperation(operationId, generation)
    }

    fun validatePendingOperation(proof: OperationProof, operationId: UUID): Boolean {
        val generation = coordinator.snapshot.value.policy?.generation ?: return false
        return proof.validatePendingOperation(operationId, generation)
    }

    suspend fun enable(password: CharArray, policy: LockPolicy, confirmation: RecoveryConfirmation) =
        credentials.enable(password, policy, confirmation)

    suspend fun changePassword(proof: OperationProof, password: CharArray) =
        credentials.changePassword(proof, password)

    suspend fun updatePolicy(proof: OperationProof, policy: LockPolicy) =
        credentials.updatePolicy(proof, policy)

    suspend fun disable(proof: OperationProof) = credentials.disable(proof)

    suspend fun prepareRecovery(): RecoveryPreparation = credentials.prepareRecovery()
    suspend fun confirmRecovery(preparation: RecoveryPreparation) = credentials.confirmRecovery(preparation)
    suspend fun cancelRecovery(preparation: RecoveryPreparation) = credentials.cancelRecovery(preparation)
    suspend fun replaceRecoveryCode(proof: OperationProof, confirmation: RecoveryConfirmation) =
        credentials.replaceRecoveryCode(proof, confirmation)
}
