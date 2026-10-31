package com.luma.lifeos.lock

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.luma.lifeos.domain.model.PhotoAttachment
import com.luma.lifeos.domain.model.PhotoMetadata
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface PrivateCaptureDraftStore {
    suspend fun write(draft: CaptureDraft)
    suspend fun read(id: UUID): CaptureDraft?
    suspend fun listIds(): List<UUID>
    suspend fun delete(id: UUID)
}

/** Encrypted, atomic, no-backup storage for independent restricted-capture drafts. */
class AndroidPrivateCaptureDraftStore(context: Context) : PrivateCaptureDraftStore {
    private val directory = File(context.applicationContext.noBackupFilesDir, DIRECTORY)
    private val locks = mutableMapOf<UUID, Mutex>()
    private val lockMutex = Mutex()

    override suspend fun write(draft: CaptureDraft) = withContext(Dispatchers.IO) {
        lockFor(draft.id).withLock {
            check(directory.isDirectory || directory.mkdirs()) { "Capture draft storage is unavailable" }
            val current = readUnlocked(draft.id)
            if (current != null && current.revision >= draft.revision) {
                if (current == draft) return@withLock
                if (current.revision > draft.revision) return@withLock
                error("Capture draft revision was reused with different content")
            }
            val plaintext = encode(draft)
            val encryptor = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, key())
                updateAAD(aad(draft.id))
            }
            val iv = encryptor.iv.also { require(it.size == GCM_IV_BYTES) }
            val ciphertext = try {
                encryptor.doFinal(plaintext)
            } finally {
                plaintext.fill(0)
            }
            val file = atomicFile(draft.id)
            val stream = file.startWrite()
            try {
                val output = DataOutputStream(stream)
                output.writeInt(MAGIC)
                output.writeInt(VERSION)
                output.writeInt(iv.size)
                output.write(iv)
                output.writeInt(ciphertext.size)
                output.write(ciphertext)
                output.flush()
                stream.fd.sync()
                file.finishWrite(stream)
            } catch (failure: Throwable) {
                file.failWrite(stream)
                throw failure
            } finally {
                ciphertext.fill(0)
            }
        }
    }

    override suspend fun read(id: UUID): CaptureDraft? = withContext(Dispatchers.IO) {
        lockFor(id).withLock { readUnlocked(id) }
    }

    override suspend fun listIds(): List<UUID> = withContext(Dispatchers.IO) {
        directory.listFiles().orEmpty().mapNotNull { file ->
            if (!file.name.endsWith(EXTENSION) || file.name.endsWith(".bak")) return@mapNotNull null
            val raw = file.name.removeSuffix(EXTENSION)
            runCatching { UUID.fromString(raw).takeIf { it.toString() == raw } }.getOrNull()
        }.sortedBy(UUID::toString)
    }

    override suspend fun delete(id: UUID) = withContext(Dispatchers.IO) {
        lockFor(id).withLock { atomicFile(id).delete() }
    }

    private suspend fun lockFor(id: UUID): Mutex = lockMutex.withLock { locks.getOrPut(id) { Mutex() } }

    private fun atomicFile(id: UUID) = AtomicFile(File(directory, "$id$EXTENSION"))

    private fun readUnlocked(id: UUID): CaptureDraft? {
        val base = File(directory, "$id$EXTENSION")
        if (!base.exists() && !File(base.path + ".bak").exists()) return null
        return atomicFile(id).openRead().use { raw ->
            DataInputStream(raw).use { input ->
                require(input.readInt() == MAGIC) { "Invalid capture draft header" }
                require(input.readInt() == VERSION) { "Unsupported capture draft version" }
                val ivSize = input.readInt()
                require(ivSize == GCM_IV_BYTES) { "Invalid capture draft nonce" }
                val iv = ByteArray(ivSize).also(input::readFully)
                val encryptedSize = input.readInt()
                require(encryptedSize in 1..MAX_ENCRYPTED_BYTES) { "Invalid capture draft size" }
                val ciphertext = ByteArray(encryptedSize).also(input::readFully)
                val plaintext = try {
                    require(input.read() == -1) { "Trailing capture draft bytes" }
                    decryptor(key(), iv, aad(id)).doFinal(ciphertext)
                } finally {
                    ciphertext.fill(0)
                }
                try {
                    decode(plaintext).also { require(it.id == id) { "Capture draft identity mismatch" } }
                } finally {
                    plaintext.fill(0)
                }
            }
        }
    }

    private fun encode(draft: CaptureDraft): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeUTF(draft.id.toString())
            output.writeUTF(draft.kind.name)
            output.writeLong(draft.revision)
            output.writeUTF(draft.idempotencyRevision.toString())
            output.writeInt(draft.fields.size)
            draft.fields.toSortedMap().forEach { (name, value) ->
                output.writeBoundedString(name)
                output.writeBoundedString(value)
            }
            output.writeInt(draft.photos.size)
            draft.photos.forEach { photo ->
                output.writeUTF(photo.id.toString())
                output.writeBoundedString(photo.relativePath)
                output.writeBoolean(photo.metadata != null)
                photo.metadata?.let { metadata ->
                    output.writeUTF(metadata.mimeType)
                    output.writeLong(metadata.byteCount)
                    output.writeInt(metadata.width)
                    output.writeInt(metadata.height)
                }
            }
        }
        bytes.toByteArray().also { require(it.size <= MAX_PLAINTEXT_BYTES) { "Capture draft is too large" } }
    }

    private fun decode(bytes: ByteArray): CaptureDraft = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
        val id = UUID.fromString(input.readUTF())
        val kind = CaptureKind.valueOf(input.readUTF())
        val revision = input.readLong().also { require(it >= 0) }
        val idempotencyRevision = UUID.fromString(input.readUTF())
        val fieldCount = input.readInt().also { require(it in 0..MAX_FIELD_COUNT) }
        val fields = buildMap(fieldCount) {
            repeat(fieldCount) { put(input.readBoundedString(MAX_FIELD_NAME_BYTES), input.readBoundedString(MAX_FIELD_VALUE_BYTES)) }
        }
        require(fields.size == fieldCount) { "Duplicate capture draft field" }
        val photoCount = input.readInt().also { require(it in 0..MAX_PHOTOS) }
        val photos = List(photoCount) {
            val photoId = UUID.fromString(input.readUTF())
            val path = input.readBoundedString(MAX_PHOTO_PATH_BYTES)
            val metadata = if (input.readBoolean()) PhotoMetadata(
                mimeType = input.readUTF(), byteCount = input.readLong(), width = input.readInt(), height = input.readInt(),
            ) else null
            PhotoAttachment(photoId, path, metadata)
        }
        require(input.read() == -1) { "Trailing decrypted capture draft bytes" }
        CaptureDraft(id, kind, revision, idempotencyRevision, fields, photos)
    }

    private fun DataOutputStream.writeBoundedString(value: String) {
        val encoded = value.toByteArray(Charsets.UTF_8)
        require(encoded.size <= MAX_FIELD_VALUE_BYTES)
        writeInt(encoded.size)
        write(encoded)
    }

    private fun DataInputStream.readBoundedString(maxBytes: Int): String {
        val size = readInt().also { require(it in 0..maxBytes && it <= available()) }
        val encoded = ByteArray(size).also(::readFully)
        return String(encoded, Charsets.UTF_8)
    }

    private fun key(): SecretKey = synchronized(KEY_LOCK) {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey) ?: run {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            generator.init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .setUserAuthenticationRequired(false)
                    .build(),
            )
            generator.generateKey()
        }
    }

    private fun decryptor(key: SecretKey, iv: ByteArray, aad: ByteArray): Cipher =
        Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            updateAAD(aad)
        }

    private fun aad(id: UUID): ByteArray = "$MAGIC:$VERSION:$id".toByteArray(Charsets.UTF_8)

    private companion object {
        const val DIRECTORY = "restricted-capture-drafts"
        const val EXTENSION = ".bin"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "com.luma.lifeos.restricted_capture.aes.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val MAGIC = 0x4C434450
        const val VERSION = 1
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
        const val MAX_ENCRYPTED_BYTES = 1_100_000
        const val MAX_PLAINTEXT_BYTES = 1_000_000
        const val MAX_FIELD_COUNT = 64
        const val MAX_FIELD_NAME_BYTES = 64
        const val MAX_FIELD_VALUE_BYTES = 400_000
        const val MAX_PHOTO_PATH_BYTES = 512
        const val MAX_PHOTOS = 20
        val KEY_LOCK = Any()
    }
}
