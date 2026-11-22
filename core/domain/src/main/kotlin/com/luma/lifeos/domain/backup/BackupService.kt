package com.luma.lifeos.domain.backup

import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.util.UUID

enum class BackupArchiveFormat { PLAINTEXT_ZIP, ENCRYPTED }

object BackupFormatMarker {
    const val ENCRYPTED_SIGNATURE = "LUMAENC"
    const val ENCRYPTED_MIME_TYPE = "application/vnd.luma.backup"
    const val ENCRYPTED_FILE_EXTENSION = "lumabackup"
    const val MINIMUM_PASSWORD_CODE_POINTS = 12

    fun isEncryptedPrefix(prefix: ByteArray, length: Int = prefix.size): Boolean {
        if (length < ENCRYPTED_SIGNATURE.length || length > prefix.size) return false
        return ENCRYPTED_SIGNATURE.indices.all { prefix[it] == ENCRYPTED_SIGNATURE[it].code.toByte() }
    }
}

/** Caller owns this transient secret and must close it as soon as its operation finishes. */
class BackupEncryptionOptions(password: CharArray) : Closeable {
    @Volatile private var secret: CharArray? = password

    suspend fun <T> usePassword(block: suspend (CharArray) -> T): T =
        block(checkNotNull(secret) { "Backup password is no longer available" })

    override fun close() {
        secret?.fill('\u0000')
        secret = null
    }
}

class BackupPasswordRequiredException : IOException("Encrypted backup requires a password")

data class BackupPreview(
    val eventCount: Long,
    val photoCount: Int,
    val createdAt: Instant,
    val habitCount: Long = 0,
    val habitLogCount: Long = 0,
    val todoCount: Long = 0,
    val focusSessionCount: Long = 0,
    val format: BackupArchiveFormat = BackupArchiveFormat.PLAINTEXT_ZIP,
)

sealed class BackupFocusExportChoice {
    object STOPPED_AND_INCLUDE : BackupFocusExportChoice()
    data class EXCLUDE_PAUSED(val sessionId: UUID) : BackupFocusExportChoice()
}

interface BackupService {
    /** Caller owns document streams. Export completes only when every referenced photo exists. */
    suspend fun export(output: OutputStream)
    /** A paused timer must be explicitly excluded or stopped before it can be included. */
    suspend fun export(output: OutputStream, focusChoice: BackupFocusExportChoice?) = export(output)

    /** Existing implementations may keep supporting plaintext callers until they add encryption. */
    suspend fun export(
        output: OutputStream,
        focusChoice: BackupFocusExportChoice?,
        encryption: BackupEncryptionOptions?,
    ) {
        if (encryption != null) {
            encryption.close()
            throw UnsupportedOperationException("Encrypted backup export is unavailable")
        }
        export(output, focusChoice)
    }

    suspend fun prepare(input: InputStream): PreparedBackup

    /** Plaintext implementations remain compatible; encrypted inputs require an explicit password. */
    suspend fun prepare(input: InputStream, encryption: BackupEncryptionOptions?): PreparedBackup {
        if (encryption != null) {
            encryption.close()
            throw BackupPasswordRequiredException()
        }
        return prepare(input)
    }
}

interface PreparedBackup : Closeable {
    val preview: BackupPreview
    /** Explicit replacement. One-shot; validation and publication never overwrite old photo paths. */
    suspend fun restore()
}
