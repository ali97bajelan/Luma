package com.luma.lifeos.lock

import android.content.Context
import android.util.AtomicFile
import androidx.annotation.WorkerThread
import com.luma.lifeos.domain.lock.LockCoverage
import com.luma.lifeos.domain.lock.LockPolicy
import com.luma.lifeos.domain.lock.LockSection
import com.luma.lifeos.domain.lock.RelockMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest

internal data class PersistedCredential(
    val passwordSalt: ByteArray,
    val passwordVerifier: ByteArray,
    val recoverySalt: ByteArray,
    val recoveryVerifier: ByteArray,
)

internal data class PersistedLockState(
    val policy: LockPolicy,
    val credential: PersistedCredential?,
    val failedAttempts: Long = 0L,
    val cooldownUntilElapsedMs: Long = 0L,
    val cooldownActive: Boolean = false,
    val retryBootMarkerMs: Long = 0L,
) {
    init {
        require(failedAttempts >= 0L)
        require(cooldownUntilElapsedMs >= 0L)
        require(retryBootMarkerMs >= 0L)
        require(policy.enabled == (credential != null)) {
            "An enabled lock must have credentials, and a disabled lock must not"
        }
        require(!cooldownActive || failedAttempts >= LockRetryPolicy.FIRST_COOLDOWN_FAILURE)
    }
}

internal interface LockStoreAuthenticator {
    /** Returns a keyed authenticator. Implementations must fail if the key is unavailable. */
    fun authenticate(payload: ByteArray): ByteArray
    fun hasKey(): Boolean
    fun removeKeyIfPresent()
}

/** Synchronous operations must run on a worker dispatcher. Writes replace one complete record. */
internal interface LocalLockStore {
    @WorkerThread
    fun read(): PersistedLockState?

    @WorkerThread
    fun writeAtomically(state: PersistedLockState)
}

/**
 * Stores only policy, one-way verifiers and retry metadata in Android's no-backup app directory.
 * The record MAC detects modified/corrupt payloads. It does not detect restoration of an older
 * payload with a previously valid tag; this app-private store is not an anti-rollback anchor.
 */
internal class AndroidLocalLockStore internal constructor(
    context: Context,
    private val authenticator: LockStoreAuthenticator,
) : LocalLockStore {
    private val baseFile: File = File(File(context.applicationContext.noBackupFilesDir, DIRECTORY), FILE_NAME)
    private val atomicFile = AtomicFile(baseFile)

    @WorkerThread
    override fun read(): PersistedLockState? {
        val backupFile = File(baseFile.path + ".bak")
        if (!baseFile.exists() && !backupFile.exists()) {
            if (authenticator.hasKey()) throw LockStoreCorruptException("Lock state is missing")
            return null
        }

        try {
            atomicFile.openRead().use { raw ->
                val input = DataInputStream(raw)
                if (input.readInt() != MAGIC) throw LockStoreCorruptException("Invalid lock state header")
                val payloadLength = input.readInt()
                if (payloadLength !in 1..MAX_PAYLOAD_BYTES) {
                    throw LockStoreCorruptException("Invalid lock state size")
                }
                val payload = ByteArray(payloadLength)
                input.readFully(payload)
                val tagLength = input.readInt()
                if (tagLength != AUTH_TAG_BYTES) throw LockStoreCorruptException("Invalid lock state tag")
                val tag = ByteArray(tagLength)
                input.readFully(tag)
                if (input.read() != -1) throw LockStoreCorruptException("Trailing lock state data")
                val expectedTag = authenticator.authenticate(payload)
                if (!MessageDigest.isEqual(expectedTag, tag)) {
                    throw LockStoreCorruptException("Lock state authentication failed")
                }
                return decode(payload)
            }
        } catch (e: LockStoreCorruptException) {
            throw e
        } catch (e: Exception) {
            throw LockStoreCorruptException("Lock state could not be read", e)
        }
    }

    @WorkerThread
    override fun writeAtomically(state: PersistedLockState) {
        val priorStateExists = baseFile.exists() || File(baseFile.path + ".bak").exists()
        try {
            baseFile.parentFile?.let { parent ->
                if (!parent.exists() && !parent.mkdirs()) throw IOException("Lock directory unavailable")
            }
            val payload = encode(state)
            val tag = authenticator.authenticate(payload)
            if (tag.size != AUTH_TAG_BYTES) throw IOException("Unexpected lock state authenticator size")

            var stream: java.io.FileOutputStream? = null
            try {
                val outputStream = atomicFile.startWrite()
                stream = outputStream
                val output = DataOutputStream(outputStream)
                output.writeInt(MAGIC)
                output.writeInt(payload.size)
                output.write(payload)
                output.writeInt(tag.size)
                output.write(tag)
                output.flush()
                outputStream.fd.sync()
                atomicFile.finishWrite(outputStream)
                stream = null
            } catch (e: Exception) {
                if (stream != null) atomicFile.failWrite(stream)
                throw e
            }
        } catch (e: Exception) {
            if (!priorStateExists && !baseFile.exists() && !File(baseFile.path + ".bak").exists()) {
                runCatching { authenticator.removeKeyIfPresent() }
            }
            throw LockStoreWriteException(e)
        }
    }

    private fun encode(state: PersistedLockState): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeInt(VERSION)
            output.writeBoolean(state.policy.enabled)
            output.writeUTF(state.policy.coverage.name)
            output.writeInt(state.policy.sections.size)
            state.policy.sections.sortedBy { it.name }.forEach { output.writeUTF(it.name) }
            output.writeUTF(state.policy.relockMode.name)
            output.writeBoolean(state.policy.screenshotsProtected)
            output.writeLong(state.policy.generation)

            val credential = state.credential
            output.writeBoolean(credential != null)
            if (credential != null) {
                output.writeBoundedBytes(credential.passwordSalt)
                output.writeBoundedBytes(credential.passwordVerifier)
                output.writeBoundedBytes(credential.recoverySalt)
                output.writeBoundedBytes(credential.recoveryVerifier)
            }
            output.writeLong(state.failedAttempts)
            output.writeBoolean(state.cooldownActive)
            output.writeLong(state.cooldownUntilElapsedMs)
            output.writeLong(state.retryBootMarkerMs)
        }
        return bytes.toByteArray().also { if (it.size > MAX_PAYLOAD_BYTES) throw IOException("Lock state is too large") }
    }

    private fun decode(payload: ByteArray): PersistedLockState {
        try {
            DataInputStream(ByteArrayInputStream(payload)).use { input ->
                if (input.readInt() != VERSION) throw LockStoreCorruptException("Unsupported lock state version")
                val enabled = input.readBoolean()
                val coverage = enumValue<LockCoverage>(input.readUTF())
                val sectionCount = input.readInt()
                if (sectionCount !in 0..LockSection.values().size) {
                    throw LockStoreCorruptException("Invalid section count")
                }
                val sections = buildSet {
                    repeat(sectionCount) {
                        if (!add(enumValue<LockSection>(input.readUTF()))) {
                            throw LockStoreCorruptException("Duplicate lock section")
                        }
                    }
                }
                val relockMode = enumValue<RelockMode>(input.readUTF())
                val screenshotsProtected = input.readBoolean()
                val generation = input.readLong()
                val policy = try {
                    LockPolicy(enabled, coverage, sections, relockMode, screenshotsProtected, generation)
                } catch (e: IllegalArgumentException) {
                    throw LockStoreCorruptException("Invalid stored lock policy", e)
                }

                val credential = if (input.readBoolean()) {
                    PersistedCredential(
                        passwordSalt = input.readBoundedBytes(SALT_BYTES),
                        passwordVerifier = input.readBoundedBytes(VERIFIER_BYTES),
                        recoverySalt = input.readBoundedBytes(SALT_BYTES),
                        recoveryVerifier = input.readBoundedBytes(VERIFIER_BYTES),
                    ).also {
                        if (it.passwordSalt.size != SALT_BYTES || it.recoverySalt.size != SALT_BYTES ||
                            it.passwordVerifier.size != VERIFIER_BYTES || it.recoveryVerifier.size != VERIFIER_BYTES
                        ) throw LockStoreCorruptException("Invalid credential verifier size")
                    }
                } else null
                val state = try {
                    PersistedLockState(
                        policy = policy,
                        credential = credential,
                        failedAttempts = input.readLong(),
                        cooldownActive = input.readBoolean(),
                        cooldownUntilElapsedMs = input.readLong(),
                        retryBootMarkerMs = input.readLong(),
                    )
                } catch (e: IllegalArgumentException) {
                    throw LockStoreCorruptException("Invalid stored retry state", e)
                }
                if (input.read() != -1) throw LockStoreCorruptException("Trailing payload data")
                if (policy.enabled != (credential != null)) {
                    throw LockStoreCorruptException("Credential/policy state mismatch")
                }
                return state
            }
        } catch (e: LockStoreCorruptException) {
            throw e
        } catch (e: Exception) {
            throw LockStoreCorruptException("Lock state is malformed", e)
        }
    }

    private inline fun <reified T : Enum<T>> enumValue(name: String): T =
        enumValues<T>().firstOrNull { it.name == name }
            ?: throw LockStoreCorruptException("Unknown lock state value")

    private fun DataOutputStream.writeBoundedBytes(value: ByteArray) {
        if (value.size !in 1..VERIFIER_BYTES) throw IOException("Invalid lock state byte array")
        writeInt(value.size)
        write(value)
    }

    private fun DataInputStream.readBoundedBytes(maximum: Int): ByteArray {
        val size = readInt()
        if (size !in 1..maximum) throw LockStoreCorruptException("Invalid lock state byte array")
        val bytes = ByteArray(size)
        readFully(bytes)
        return bytes
    }

    private companion object {
        const val DIRECTORY = "app_lock"
        const val FILE_NAME = "credential_state.bin"
        const val MAGIC = 0x4c554d41 // "LUMA"
        const val VERSION = 1
        const val AUTH_TAG_BYTES = 32
        const val SALT_BYTES = 16
        const val VERIFIER_BYTES = 32
        const val MAX_PAYLOAD_BYTES = 16 * 1024
    }
}

class LockStoreCorruptException(message: String, cause: Throwable? = null) : IOException(message, cause)

class LockStoreWriteException(cause: Throwable) : IOException("Lock state could not be saved", cause)
