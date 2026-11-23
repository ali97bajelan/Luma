package com.luma.lifeos.data.backup

import com.google.crypto.tink.CleartextKeysetHandle
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.BinaryKeysetReader
import com.google.crypto.tink.BinaryKeysetWriter
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import com.luma.lifeos.domain.backup.BackupFormatMarker
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * LUMAENC envelope v1. The inner ZIP remains the existing portable archive.
 * No decrypted byte is sent to ZIP validation until Tink has read/authenticated EOF.
 */
internal object EncryptedBackupEnvelope {
    private const val VERSION = 1
    private const val KDF_PBKDF2_HMAC_SHA256 = 1
    private const val STREAM_AES_256_GCM_HKDF_1MB = 1
    private const val PBKDF2_ITERATIONS = 600_000
    private const val KEY_SIZE_BYTES = 32
    private const val SALT_SIZE_BYTES = 16
    private const val WRAP_NONCE_SIZE_BYTES = 12
    private const val WRAP_TAG_SIZE_BYTES = 16
    private const val MAX_WRAPPED_KEYSET_BYTES = 64 * 1024
    private const val MAX_ENVELOPE_OVERHEAD_BYTES = 128L * 1024 * 1024
    private const val MAX_ARCHIVE_BYTES = BackupArchive.MAX_TOTAL_BYTES + MAX_ENVELOPE_OVERHEAD_BYTES
    private const val STREAMING_TEMPLATE = "AES256_GCM_HKDF_1MB"

    private val signature = BackupFormatMarker.ENCRYPTED_SIGNATURE.toByteArray(StandardCharsets.US_ASCII)
    private val random = SecureRandom()

    suspend fun encrypt(
        output: OutputStream,
        password: CharArray,
        writeArchive: (OutputStream) -> Unit,
    ) {
        val job = currentCoroutineContext()[Job]
        val salt = ByteArray(SALT_SIZE_BYTES).also(random::nextBytes)
        var derivedKey: ByteArray? = null
        var serializedKeyset: ByteArray? = null
        var encryptedKeyset: ByteArray? = null
        try {
            requirePassword(password)
            derivedKey = derivePasswordKey(password, salt, job)
            val keysetHandle = newKeyset()
            serializedKeyset = serializeKeyset(keysetHandle)
            val wrappedLength = WRAP_NONCE_SIZE_BYTES + serializedKeyset.size + WRAP_TAG_SIZE_BYTES
            require(wrappedLength <= MAX_WRAPPED_KEYSET_BYTES) { "Backup header is too large" }
            val headerPrefix = headerPrefix(wrappedLength, salt)
            encryptedKeyset = wrapKeyset(serializedKeyset, derivedKey, headerPrefix)
            val header = headerPrefix + encryptedKeyset

            val boundedOutput = BoundedOutputStream(output, MAX_ARCHIVE_BYTES, job)
            boundedOutput.write(header)
            val primitive = keysetHandle.getPrimitive(StreamingAead::class.java)
            val encryptedStream = primitive.newEncryptingStream(NonClosingOutputStream(boundedOutput), header)
            var writeFailure: Throwable? = null
            try {
                writeArchive(CancellableOutputStream(encryptedStream, job))
            } catch (failure: Throwable) {
                writeFailure = failure
                throw failure
            } finally {
                try {
                    encryptedStream.close()
                } catch (closeFailure: Throwable) {
                    if (writeFailure != null) writeFailure.addSuppressed(closeFailure) else throw closeFailure
                }
            }
        } finally {
            password.fill('\u0000')
            derivedKey?.fill(0)
            serializedKeyset?.fill(0)
            encryptedKeyset?.fill(0)
            salt.fill(0)
        }
    }

    suspend fun decryptToFile(input: InputStream, password: CharArray, target: java.io.File) {
        val job = currentCoroutineContext()[Job]
        var derivedKey: ByteArray? = null
        var serializedKeyset: ByteArray? = null
        var salt: ByteArray? = null
        var wrappedKeyset: ByteArray? = null
        var header: ByteArray? = null
        try {
            requirePassword(password)
            val boundedInput = BoundedInputStream(input, MAX_ARCHIVE_BYTES, job)
            val data = DataInputStream(boundedInput)
            val readSignature = ByteArray(signature.size)
            data.readFully(readSignature)
            require(readSignature.contentEquals(signature)) { "Unsupported backup format" }
            val version = data.readUnsignedByte()
            require(version == VERSION) { "Unsupported encrypted backup version" }
            val kdf = data.readUnsignedByte()
            require(kdf == KDF_PBKDF2_HMAC_SHA256) { "Unsupported encrypted backup KDF" }
            val streamAlgorithm = data.readUnsignedByte()
            require(streamAlgorithm == STREAM_AES_256_GCM_HKDF_1MB) { "Unsupported encrypted backup algorithm" }
            val iterations = data.readInt()
            require(iterations == PBKDF2_ITERATIONS) { "Unsupported encrypted backup KDF parameters" }
            val saltBytes = ByteArray(SALT_SIZE_BYTES)
            salt = saltBytes
            data.readFully(saltBytes)
            val wrappedLength = data.readInt()
            require(wrappedLength in (WRAP_NONCE_SIZE_BYTES + WRAP_TAG_SIZE_BYTES + 1)..MAX_WRAPPED_KEYSET_BYTES) {
                "Invalid encrypted backup header"
            }
            val prefix = headerPrefix(wrappedLength, saltBytes)
            val wrappedKeysetBytes = ByteArray(wrappedLength)
            wrappedKeyset = wrappedKeysetBytes
            data.readFully(wrappedKeysetBytes)
            val headerBytes = prefix + wrappedKeysetBytes
            header = headerBytes

            derivedKey = derivePasswordKey(password, saltBytes, job)
            serializedKeyset = unwrapKeyset(wrappedKeysetBytes, derivedKey, prefix)
            val keysetHandle = CleartextKeysetHandle.read(BinaryKeysetReader.withBytes(serializedKeyset))
            val primitive = keysetHandle.getPrimitive(StreamingAead::class.java)

            check(target.parentFile?.isDirectory == true)
            target.delete()
            java.io.FileOutputStream(target).use { fileOutput ->
                val authenticated = primitive.newDecryptingStream(NonClosingInputStream(data), headerBytes)
                authenticated.use { plaintext ->
                    val boundedOutput = BoundedOutputStream(fileOutput, MAX_ARCHIVE_BYTES, job)
                    val buffer = ByteArray(32 * 1024)
                    try {
                        while (true) {
                            job?.ensureActive()
                            val count = plaintext.read(buffer)
                            if (count < 0) break
                            boundedOutput.write(buffer, 0, count)
                        }
                    } finally {
                        buffer.fill(0)
                    }
                }
                job?.ensureActive()
                require(boundedInput.read() == -1) { "Trailing data after encrypted backup" }
            }
        } catch (failure: Throwable) {
            target.delete()
            throw failure
        } finally {
            password.fill(Char.MIN_VALUE)
            derivedKey?.fill(0)
            serializedKeyset?.fill(0)
            salt?.fill(0)
            wrappedKeyset?.fill(0)
            header?.fill(0)
        }
    }

    private fun newKeyset(): KeysetHandle {
        StreamingAeadConfig.register()
        return KeysetHandle.generateNew(KeyTemplates.get(STREAMING_TEMPLATE))
    }

    private fun serializeKeyset(handle: KeysetHandle): ByteArray {
        val output = WipingByteArrayOutputStream()
        try {
            CleartextKeysetHandle.write(handle, BinaryKeysetWriter.withOutputStream(output))
            return output.toByteArray()
        } finally {
            output.clear()
        }
    }

    private fun headerPrefix(wrappedLength: Int, salt: ByteArray): ByteArray =
        ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { header ->
                header.write(signature)
                header.writeByte(VERSION)
                header.writeByte(KDF_PBKDF2_HMAC_SHA256)
                header.writeByte(STREAM_AES_256_GCM_HKDF_1MB)
                header.writeInt(PBKDF2_ITERATIONS)
                header.write(salt)
                header.writeInt(wrappedLength)
            }
        }.toByteArray()

    private fun wrapKeyset(keyset: ByteArray, key: ByteArray, associatedData: ByteArray): ByteArray {
        val nonce = ByteArray(WRAP_NONCE_SIZE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(associatedData)
        return nonce + cipher.doFinal(keyset)
    }

    private fun unwrapKeyset(wrapped: ByteArray, key: ByteArray, associatedData: ByteArray): ByteArray {
        require(wrapped.size >= WRAP_NONCE_SIZE_BYTES + WRAP_TAG_SIZE_BYTES + 1) { "Invalid encrypted backup header" }
        val nonce = wrapped.copyOfRange(0, WRAP_NONCE_SIZE_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(associatedData)
        return cipher.doFinal(wrapped, WRAP_NONCE_SIZE_BYTES, wrapped.size - WRAP_NONCE_SIZE_BYTES)
    }

    private fun requirePassword(password: CharArray) {
        require(Character.codePointCount(password, 0, password.size) >= BackupFormatMarker.MINIMUM_PASSWORD_CODE_POINTS) {
            "Backup password is too short"
        }
    }

    private fun derivePasswordKey(password: CharArray, salt: ByteArray, job: Job?): ByteArray {
        val encoded = StandardCharsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .encode(CharBuffer.wrap(password))
        val passwordBytes = ByteArray(encoded.remaining()).also(encoded::get)
        val mac = Mac.getInstance("HmacSHA256")
        val previous = ByteArray(KEY_SIZE_BYTES)
        val next = ByteArray(KEY_SIZE_BYTES)
        val result = ByteArray(KEY_SIZE_BYTES)
        var completed = false
        return try {
            mac.init(SecretKeySpec(passwordBytes, "HmacSHA256"))
            mac.update(salt)
            mac.update(byteArrayOf(0, 0, 0, 1))
            mac.doFinal(previous, 0)
            previous.copyInto(result)
            var current = previous
            var destination = next
            for (iteration in 2..PBKDF2_ITERATIONS) {
                if ((iteration and 0x0fff) == 0) job?.ensureActive()
                mac.update(current)
                mac.doFinal(destination, 0)
                for (index in result.indices) result[index] = (result[index].toInt() xor destination[index].toInt()).toByte()
                val oldCurrent = current
                current = destination
                destination = oldCurrent
            }
            completed = true
            result
        } finally {
            previous.fill(0)
            next.fill(0)
            if (!completed) result.fill(0)
            passwordBytes.fill(0)
            if (encoded.hasArray()) encoded.array().fill(0)
            encoded.clear()
        }
    }

    private class WipingByteArrayOutputStream : ByteArrayOutputStream() {
        fun clear() {
            buf.fill(0)
            reset()
        }
    }

    private class NonClosingOutputStream(output: OutputStream) : FilterOutputStream(output) {
        override fun close() = flush()
    }

    private class NonClosingInputStream(input: InputStream) : FilterInputStream(input) {
        override fun close() = Unit
    }

    private class CancellableOutputStream(output: OutputStream, private val job: Job?) : FilterOutputStream(output) {
        override fun write(value: Int) {
            job?.ensureActive()
            out.write(value)
        }

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            job?.ensureActive()
            out.write(bytes, offset, length)
        }

        override fun close() = flush()
    }

    private class BoundedOutputStream(
        output: OutputStream,
        private val maximum: Long,
        private val job: Job?,
    ) : FilterOutputStream(output) {
        private var count = 0L

        override fun write(value: Int) {
            job?.ensureActive()
            checkCount(1)
            out.write(value)
            count++
        }

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            job?.ensureActive()
            checkCount(length)
            out.write(bytes, offset, length)
            count += length
        }

        private fun checkCount(additional: Int) {
            if (additional < 0 || count > maximum - additional) throw IOException("Backup exceeds the supported size")
        }
    }

    private class BoundedInputStream(
        input: InputStream,
        private val maximum: Long,
        private val job: Job?,
    ) : FilterInputStream(input) {
        private var count = 0L

        override fun read(): Int {
            job?.ensureActive()
            if (count == maximum) {
                val extra = `in`.read()
                if (extra < 0) return -1
                throw IOException("Backup exceeds the supported size")
            }
            val value = `in`.read()
            if (value >= 0) count++
            return value
        }

        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
            job?.ensureActive()
            if (length == 0) return 0
            val remainingWithProbe = (maximum - count + 1).coerceAtMost(length.toLong()).toInt()
            val read = `in`.read(bytes, offset, remainingWithProbe)
            if (read > maximum - count) throw IOException("Backup exceeds the supported size")
            if (read > 0) count += read
            return read
        }
    }
}
