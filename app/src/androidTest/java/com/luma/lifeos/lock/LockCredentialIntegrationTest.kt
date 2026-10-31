package com.luma.lifeos.lock

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.domain.lock.LockCoverage
import com.luma.lifeos.domain.lock.LockPolicy
import com.luma.lifeos.domain.lock.LockSection
import com.luma.lifeos.domain.lock.RelockMode
import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LockCredentialIntegrationTest {
    private lateinit var context: Context
    private lateinit var lockDirectory: File
    private lateinit var crypto: AndroidLockCrypto
    private lateinit var backingStore: AndroidLocalLockStore
    private lateinit var clock: TestLockClock
    private lateinit var store: LocalLockStore

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        lockDirectory = File(context.noBackupFilesDir, "app_lock")
        lockDirectory.deleteRecursively()
        crypto = AndroidLockCrypto()
        crypto.removeKeyIfPresent()
        backingStore = AndroidLocalLockStore(context, crypto)
        clock = TestLockClock()
        store = backingStore
    }

    @After
    fun tearDown() {
        lockDirectory.deleteRecursively()
        crypto.removeKeyIfPresent()
    }

    @Test
    fun setupRejectsLengthVectorsPreservesExactInputAndUnlocksOffline() = runBlocking {
        val service = newService()
        val (confirmation, recoveryCode) = confirmedRecovery(service)
        val policy = selectedPolicy()
        val tooShort = "aaaaaaa".toCharArray()
        val tooLong = "a".repeat(129).toCharArray()

        assertEquals(CredentialResult.InvalidInput, service.enable(tooShort, policy, confirmation))
        assertArrayEquals(CharArray(tooShort.size), tooShort)
        assertEquals(null, backingStore.read())
        assertEquals(CredentialResult.InvalidInput, service.enable(tooLong, policy, confirmation))
        assertEquals(null, backingStore.read())

        val password = "a".repeat(128).toCharArray()
        assertEquals(CredentialResult.Success(), service.enable(password, policy, confirmation))
        assertArrayEquals(CharArray(password.size), password)
        assertTrue(service.verify("a".repeat(128).toCharArray(), AuthPurpose.BROWSE) is CredentialResult.Success)

        recoveryCode.fill('\u0000')
    }

    @Test
    fun eightEmojiAreEightPasswordCharacters() = runBlocking {
        val service = newService()
        val (confirmation, recoveryCode) = confirmedRecovery(service)
        val emojiPassword = "🔒🔒🔒🔒🔒🔒🔒🔒".toCharArray()

        assertEquals(CredentialResult.Success(), service.enable(emojiPassword, selectedPolicy(), confirmation))
        assertTrue(service.verify("🔒🔒🔒🔒🔒🔒🔒🔒".toCharArray(), AuthPurpose.BROWSE) is CredentialResult.Success)
        assertEquals(CredentialResult.InvalidInput, service.verify("🔒🔒🔒🔒🔒🔒🔒".toCharArray(), AuthPurpose.BROWSE))

        recoveryCode.fill('\u0000')
    }

    @Test
    fun passwordPreservesSpacesAndDoesNotNormalizeUnicode() = runBlocking {
        val service = newService()
        val (confirmation, recoveryCode) = confirmedRecovery(service)
        val password = "  éééééééé  ".toCharArray()

        assertEquals(CredentialResult.Success(), service.enable(password, selectedPolicy(), confirmation))
        assertTrue(service.verify("  éééééééé  ".toCharArray(), AuthPurpose.BROWSE) is CredentialResult.Success)
        assertEquals(CredentialResult.Incorrect, service.verify("éééééééé".toCharArray(), AuthPurpose.BROWSE))
        assertEquals(CredentialResult.Incorrect, service.verify("  éééééééé  ".toCharArray(), AuthPurpose.BROWSE))

        recoveryCode.fill('\u0000')
    }

    @Test
    fun cancellationAndAtomicEnableFailureLeaveNoCredentialState() = runBlocking {
        val service = newService()
        val cancelled = service.prepareRecovery()
        val displayedCode = cancelled.takeCodeForDisplay()
        service.cancelRecovery(cancelled)
        displayedCode.fill('\u0000')
        assertTrue(runCatching { service.confirmRecovery(cancelled) }.exceptionOrNull() is LockCredentialUnavailableException)
        assertEquals(null, backingStore.read())

        val (confirmation, recoveryCode) = confirmedRecovery(service)
        lockDirectory.writeBytes(byteArrayOf(1)) // A regular file makes the app_lock directory unavailable.
        val password = "Luma QA pass 31".toCharArray()

        assertEquals(CredentialResult.Unavailable, service.enable(password, selectedPolicy(), confirmation))
        assertArrayEquals(CharArray(password.size), password)
        assertFalse(crypto.hasKey())
        assertEquals(null, backingStore.read())

        recoveryCode.fill('\u0000')
    }

    @Test
    fun sharedCooldownPersistsAcrossServiceRecreationAndBootChange() = runBlocking {
        val service = newService()
        val (confirmation, recoveryCode) = confirmedRecovery(service)
        assertEquals(CredentialResult.Success(), service.enable("Luma QA pass 31".toCharArray(), selectedPolicy(), confirmation))
        val (retryConfirmation, retryCode) = confirmedRecovery(service)

        repeat(4) {
            assertEquals(CredentialResult.Incorrect, service.verify("Luma QA pass 32".toCharArray(), AuthPurpose.BROWSE))
        }
        assertEquals(
            CredentialResult.Cooldown(30_000L),
            service.recover("AAAAA-AAAAA-AAAAA-AAAAA-AAAAAA".toCharArray(), "New Luma QA pass 31".toCharArray(), retryConfirmation),
        )
        assertEquals(5L, backingStore.read()!!.failedAttempts)

        val restartedService = newService()
        val (restartedRetryConfirmation, restartedRetryCode) = confirmedRecovery(restartedService)
        clock.advanceWithinBoot(29_999L)
        assertEquals(CredentialResult.Cooldown(1L), restartedService.verify("Luma QA pass 32".toCharArray(), AuthPurpose.BROWSE))
        assertEquals(5L, backingStore.read()!!.failedAttempts)

        clock.advanceWithinBoot(1L)
        assertEquals(CredentialResult.Cooldown(60_000L), restartedService.verify("Luma QA pass 32".toCharArray(), AuthPurpose.BROWSE))
        assertEquals(6L, backingStore.read()!!.failedAttempts)

        val expectedDelayByFailure = mapOf(7L to 120_000L, 8L to 240_000L, 9L to 480_000L, 10L to 900_000L, 11L to 900_000L)
        expectedDelayByFailure.forEach { (failure, delay) ->
            clock.advanceWithinBoot(LockRetryPolicy.cooldownAfterFailure(failure - 1L))
            val result = if (failure % 2L == 1L) {
                restartedService.recover(
                    "AAAAA-AAAAA-AAAAA-AAAAA-AAAAAA".toCharArray(),
                    "New Luma QA pass 31".toCharArray(),
                    restartedRetryConfirmation,
                )
            } else {
                restartedService.verify("Luma QA pass 32".toCharArray(), AuthPurpose.BROWSE)
            }
            assertEquals("failure $failure", CredentialResult.Cooldown(delay), result)
            assertEquals(failure, backingStore.read()!!.failedAttempts)
        }

        clock.elapsedMs = 3_000L
        clock.wallClockMs += 86_400_000L
        assertEquals(CredentialResult.Cooldown(900_000L), newService().verify("Luma QA pass 32".toCharArray(), AuthPurpose.BROWSE))
        assertEquals(11L, backingStore.read()!!.failedAttempts)

        recoveryCode.fill('\u0000')
        retryCode.fill('\u0000')
        restartedRetryCode.fill('\u0000')
    }

    @Test
    fun recoveryFailureInterruptedWriteAndSuccessPreserveOldThenReplaceCredentials() = runBlocking {
        val faultingStore = FaultingStore(backingStore).also { store = it }
        val service = newService()
        val (setupConfirmation, oldRecoveryCode) = confirmedRecovery(service)
        assertEquals(CredentialResult.Success(), service.enable("Luma QA pass 31".toCharArray(), selectedPolicy(), setupConfirmation))
        val (replacementConfirmation, replacementRecoveryCode) = confirmedRecovery(service)

        assertEquals(
            CredentialResult.Incorrect,
            service.recover("AAAAA-AAAAA-AAAAA-AAAAA-AAAAAA".toCharArray(), "New Luma QA pass 31".toCharArray(), replacementConfirmation),
        )
        assertTrue(service.verify("Luma QA pass 31".toCharArray(), AuthPurpose.BROWSE) is CredentialResult.Success)

        faultingStore.failNextWrite = true
        assertEquals(
            CredentialResult.Unavailable,
            service.recover(oldRecoveryCode.copyOf(), "New Luma QA pass 31".toCharArray(), replacementConfirmation),
        )
        assertTrue(service.verify("Luma QA pass 31".toCharArray(), AuthPurpose.BROWSE) is CredentialResult.Success)
        assertEquals(CredentialResult.Incorrect, service.verify("New Luma QA pass 31".toCharArray(), AuthPurpose.BROWSE))

        assertEquals(
            CredentialResult.Success(),
            service.recover(oldRecoveryCode.copyOf(), "New Luma QA pass 31".toCharArray(), replacementConfirmation),
        )
        assertEquals(CredentialResult.Incorrect, service.verify("Luma QA pass 31".toCharArray(), AuthPurpose.BROWSE))
        assertTrue(service.verify("New Luma QA pass 31".toCharArray(), AuthPurpose.BROWSE) is CredentialResult.Success)

        val (nextConfirmation, nextRecoveryCode) = confirmedRecovery(service)
        assertEquals(
            CredentialResult.Incorrect,
            service.recover(oldRecoveryCode.copyOf(), "Another Luma QA pass".toCharArray(), nextConfirmation),
        )
        assertTrue(service.verify("New Luma QA pass 31".toCharArray(), AuthPurpose.BROWSE) is CredentialResult.Success)
        assertEquals(
            CredentialResult.Success(),
            service.recover(replacementRecoveryCode.copyOf(), "Another Luma QA pass".toCharArray(), nextConfirmation),
        )
        assertEquals(CredentialResult.Incorrect, service.verify("New Luma QA pass 31".toCharArray(), AuthPurpose.BROWSE))
        assertTrue(service.verify("Another Luma QA pass".toCharArray(), AuthPurpose.BROWSE) is CredentialResult.Success)

        oldRecoveryCode.fill('\u0000')
        replacementRecoveryCode.fill('\u0000')
        nextRecoveryCode.fill('\u0000')
    }

    @Test
    fun validatedSettingsProofChangesPolicyAndInvalidatesItsOldGeneration() = runBlocking {
        val service = newService()
        val (confirmation, recoveryCode) = confirmedRecovery(service)
        assertEquals(CredentialResult.Success(), service.enable("Luma QA pass 31".toCharArray(), selectedPolicy(), confirmation))
        val oldPolicy = backingStore.read()!!.policy
        val verification = service.verify("Luma QA pass 31".toCharArray(), AuthPurpose.CHANGE_POLICY) as CredentialResult.Success
        val operationId = java.util.UUID.randomUUID()
        assertTrue(verification.proof!!.bindPendingOperation(operationId, oldPolicy.generation))
        assertTrue(verification.proof.validatePendingOperation(operationId, oldPolicy.generation))

        assertEquals(
            CredentialResult.Success(),
            service.updatePolicy(
                verification.proof,
                oldPolicy.copy(sections = setOf(LockSection.MEMORIES, LockSection.JOURNAL)),
            ),
        )
        assertEquals(oldPolicy.generation + 1L, backingStore.read()!!.policy.generation)
        assertEquals(setOf(LockSection.MEMORIES, LockSection.JOURNAL), backingStore.read()!!.policy.sections)
        assertEquals(
            CredentialResult.Cancelled,
            service.updatePolicy(
                verification.proof,
                oldPolicy.copy(sections = setOf(LockSection.MEMORIES)),
            ),
        )

        recoveryCode.fill('\u0000')
    }

    private fun newService() = DefaultLockCredentialService(
        store = store,
        crypto = crypto,
        clock = clock,
        onSecurityStateChanged = {},
    )

    private suspend fun confirmedRecovery(service: LockCredentialService): Pair<RecoveryConfirmation, CharArray> {
        val preparation = service.prepareRecovery()
        val code = preparation.takeCodeForDisplay()
        val confirmation = service.confirmRecovery(preparation)
        return confirmation to code
    }

    private fun selectedPolicy() = LockPolicy(
        enabled = true,
        coverage = LockCoverage.SELECTED,
        sections = setOf(LockSection.MEMORIES),
        relockMode = RelockMode.AFTER_5_MINUTES,
    )

    private class TestLockClock(
        var elapsedMs: Long = 1_000_000L,
        var wallClockMs: Long = 1_800_000_000_000L,
    ) : LockClock {
        override fun sample() = LockClockSample(elapsedMs, wallClockMs)

        fun advanceWithinBoot(deltaMs: Long) {
            elapsedMs += deltaMs
            wallClockMs += deltaMs
        }
    }

    private class FaultingStore(private val delegate: LocalLockStore) : LocalLockStore {
        var failNextWrite = false

        override fun read(): PersistedLockState? = delegate.read()

        override fun writeAtomically(state: PersistedLockState) {
            if (failNextWrite) {
                failNextWrite = false
                throw IOException("Injected pre-commit failure")
            }
            delegate.writeAtomically(state)
        }
    }
}
