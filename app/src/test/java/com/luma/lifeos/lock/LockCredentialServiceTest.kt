package com.luma.lifeos.lock

import com.luma.lifeos.domain.lock.LockSection
import java.util.UUID
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockCredentialServiceTest {
    @Test
    fun operationProofNeedsItsOriginalPendingRequestAndCanBeConsumedOnce() {
        val proof = OperationProof(AuthPurpose.CHANGE_POLICY, policyGeneration = 7)
        val originalOperation = UUID.randomUUID()

        assertTrue(proof.bindPendingOperation(originalOperation, currentGeneration = 7))
        assertTrue(proof.validatePendingOperation(originalOperation, currentGeneration = 7))
        assertTrue(proof.consume(AuthPurpose.CHANGE_POLICY, currentGeneration = 7))
        assertFalse(proof.consume(AuthPurpose.CHANGE_POLICY, currentGeneration = 7))
    }

    @Test
    fun changedOperationPurposeOrGenerationCannotReuseAnOldProof() {
        val changedRequestProof = OperationProof(AuthPurpose.CHANGE_PASSWORD, policyGeneration = 4)
        assertTrue(changedRequestProof.bindPendingOperation(UUID.randomUUID(), currentGeneration = 4))
        assertFalse(changedRequestProof.bindPendingOperation(UUID.randomUUID(), currentGeneration = 4))

        val changedPurposeProof = OperationProof(AuthPurpose.DISABLE_LOCK, policyGeneration = 4)
        val operation = UUID.randomUUID()
        assertTrue(changedPurposeProof.bindPendingOperation(operation, currentGeneration = 4))
        assertTrue(changedPurposeProof.validatePendingOperation(operation, currentGeneration = 4))
        assertFalse(changedPurposeProof.consume(AuthPurpose.CHANGE_POLICY, currentGeneration = 4))
        assertFalse(changedPurposeProof.consume(AuthPurpose.DISABLE_LOCK, currentGeneration = 4))

        val changedGenerationProof = OperationProof(AuthPurpose.DISABLE_LOCK, policyGeneration = 4)
        assertFalse(changedGenerationProof.bindPendingOperation(UUID.randomUUID(), currentGeneration = 5))
    }

    @Test
    fun browseProofIsBoundToTheExactSectionRequest() {
        val proof = OperationProof(AuthPurpose.BROWSE, policyGeneration = 2)
        val requested = setOf(LockSection.MEMORIES, LockSection.JOURNAL)

        assertTrue(proof.bindBrowseRequest(requested, wholeApp = false, currentGeneration = 2))
        assertFalse(proof.consumeBrowseRequest(setOf(LockSection.MEMORIES), wholeApp = false, currentGeneration = 2))

        val freshProof = OperationProof(AuthPurpose.BROWSE, policyGeneration = 2)
        assertTrue(freshProof.bindBrowseRequest(requested, wholeApp = false, currentGeneration = 2))
        assertTrue(freshProof.consumeBrowseRequest(requested, wholeApp = false, currentGeneration = 2))
        assertFalse(freshProof.consumeBrowseRequest(requested, wholeApp = false, currentGeneration = 2))
    }

    @Test
    fun recoveryHandlesAndProofsNeverRevealCredentialMaterialInTheirStringForm() {
        assertTrue(OperationProof(AuthPurpose.BACKUP, 1).toString().contains("redacted"))
        assertTrue(RecoveryPreparation(UUID.randomUUID(), "SYNTHETIC-SECRET".toCharArray()).toString().contains("redacted"))
        assertTrue(RecoveryConfirmation(UUID.randomUUID()).toString().contains("redacted"))
    }
}
