package com.luma.lifeos.lock

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luma.lifeos.domain.lock.LockCoverage
import com.luma.lifeos.domain.lock.LockPolicy
import com.luma.lifeos.domain.lock.RelockMode
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalLockStoreTest {
    private lateinit var context: Context
    private lateinit var lockDirectory: File
    private lateinit var authenticator: TestAuthenticator
    private lateinit var store: AndroidLocalLockStore

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        lockDirectory = File(context.noBackupFilesDir, "app_lock")
        lockDirectory.deleteRecursively()
        authenticator = TestAuthenticator()
        store = AndroidLocalLockStore(context, authenticator)
    }

    @After
    fun tearDown() {
        lockDirectory.deleteRecursively()
    }

    @Test
    fun atomicStoreRoundTripsPolicyCredentialAndRetryStateUnderNoBackupFiles() {
        val state = enabledState()

        store.writeAtomically(state)
        val restored = store.read()!!

        assertEquals(state.policy, restored.policy)
        assertArrayEquals(state.credential!!.passwordSalt, restored.credential!!.passwordSalt)
        assertArrayEquals(state.credential.passwordVerifier, restored.credential.passwordVerifier)
        assertArrayEquals(state.credential.recoverySalt, restored.credential.recoverySalt)
        assertArrayEquals(state.credential.recoveryVerifier, restored.credential.recoveryVerifier)
        assertEquals(5L, restored.failedAttempts)
        assertEquals(1_030_000L, restored.cooldownUntilElapsedMs)
        assertTrue(File(context.noBackupFilesDir, "app_lock/credential_state.bin").isFile)
    }

    @Test
    fun corruptAndMissingStateWithAnExistingKeyFailClosed() {
        lockDirectory.mkdirs()
        val stateFile = File(lockDirectory, "credential_state.bin")
        stateFile.writeBytes(byteArrayOf(0, 1, 2, 3))
        assertTrue(authenticator.hasKey())

        assertTrue(runCatching { store.read() }.exceptionOrNull() is LockStoreCorruptException)
        stateFile.delete()
        assertTrue(runCatching { store.read() }.exceptionOrNull() is LockStoreCorruptException)
    }

    @Test
    fun legacyV1AndV2CoverageMigratesSafelyAndNoticeSurvivesCanonicalWrite() {
        writeLegacyStore(version = 1, sections = listOf("MOOD"))
        val moodMigration = store.read()!!
        assertEquals(LockCoverage.WHOLE_APP, moodMigration.policy.coverage)
        assertTrue(moodMigration.policy.migrationNoticePending)
        assertEquals(LockCredentialType.PASSWORD, moodMigration.credential!!.type)
        store.writeAtomically(moodMigration)
        assertTrue(store.read()!!.policy.migrationNoticePending)

        writeLegacyStore(version = 2, sections = listOf("MEMORIES", "JOURNAL"))
        val memoryMigration = store.read()!!
        assertEquals(LockCoverage.MEMORY_SECTION, memoryMigration.policy.coverage)
        assertFalse(memoryMigration.policy.migrationNoticePending)
        assertEquals(LockCredentialType.PASSWORD, memoryMigration.credential!!.type)

        writeLegacyStore(version = 2, sections = listOf("MEMORIES", "MOOD"))
        val mixedMigration = store.read()!!
        assertEquals(LockCoverage.WHOLE_APP, mixedMigration.policy.coverage)
        assertTrue(mixedMigration.policy.migrationNoticePending)
    }

    @Test
    fun failedFirstAtomicWriteRemovesTheUncommittedAuthenticatorKey() {
        lockDirectory.writeBytes(byteArrayOf(1))

        assertTrue(runCatching { store.writeAtomically(enabledState()) }.exceptionOrNull() is LockStoreWriteException)
        assertFalse(authenticator.hasKey())
        assertFalse(File(lockDirectory, "credential_state.bin").exists())
    }

    private fun writeLegacyStore(version: Int, sections: List<String>) {
        require(version in 1..2)
        val payloadBytes = ByteArrayOutputStream()
        DataOutputStream(payloadBytes).use { output ->
            output.writeInt(version)
            output.writeBoolean(true)
            output.writeUTF("SELECTED")
            output.writeInt(sections.size)
            sections.forEach(output::writeUTF)
            output.writeUTF(RelockMode.AFTER_5_MINUTES.name)
            output.writeBoolean(false)
            output.writeLong(12L)
            output.writeBoolean(true)
            if (version >= 2) output.writeUTF(LockCredentialType.PASSWORD.name)
            output.writeBoundedBytesForTest(ByteArray(16) { it.toByte() })
            output.writeBoundedBytesForTest(ByteArray(32) { (it + 1).toByte() })
            output.writeBoundedBytesForTest(ByteArray(16) { (it + 2).toByte() })
            output.writeBoundedBytesForTest(ByteArray(32) { (it + 3).toByte() })
            output.writeLong(0L)
            output.writeBoolean(false)
            output.writeLong(0L)
            output.writeLong(1_700_000_000_000L)
        }
        val payload = payloadBytes.toByteArray()
        val tag = authenticator.authenticate(payload)
        lockDirectory.mkdirs()
        DataOutputStream(File(lockDirectory, "credential_state.bin").outputStream()).use { output ->
            output.writeInt(0x4c554d41)
            output.writeInt(payload.size)
            output.write(payload)
            output.writeInt(tag.size)
            output.write(tag)
        }
    }

    private fun DataOutputStream.writeBoundedBytesForTest(value: ByteArray) {
        writeInt(value.size)
        write(value)
    }

    private fun enabledState() = PersistedLockState(
        policy = LockPolicy(
            enabled = true,
            coverage = LockCoverage.MEMORY_SECTION,
            relockMode = RelockMode.AFTER_5_MINUTES,
            screenshotsProtected = false,
            generation = 12,
            migrationNoticePending = true,
        ),
        credential = PersistedCredential(
            type = LockCredentialType.PASSWORD,
            passwordSalt = ByteArray(16) { it.toByte() },
            passwordVerifier = ByteArray(32) { (it + 1).toByte() },
            recoverySalt = ByteArray(16) { (it + 2).toByte() },
            recoveryVerifier = ByteArray(32) { (it + 3).toByte() },
        ),
        failedAttempts = 5,
        cooldownUntilElapsedMs = 1_030_000,
        cooldownActive = true,
        retryBootMarkerMs = 1_700_000_000_000,
    )

    private class TestAuthenticator : LockStoreAuthenticator {
        private val key = ByteArray(32) { (it * 3).toByte() }
        private var available = true

        override fun authenticate(payload: ByteArray): ByteArray {
            check(available)
            return Mac.getInstance("HmacSHA256").run {
                init(SecretKeySpec(key, "HmacSHA256"))
                doFinal(payload)
            }
        }

        override fun hasKey(): Boolean = available

        override fun removeKeyIfPresent() {
            available = false
        }
    }
}
