package com.luma.lifeos.domain.backup

import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant

data class BackupPreview(val eventCount: Long, val photoCount: Int, val createdAt: Instant)
interface BackupService {
    /** Caller owns document streams. Export completes only when every referenced photo exists. */
    suspend fun export(output: OutputStream)
    suspend fun prepare(input: InputStream): PreparedBackup
}
interface PreparedBackup : Closeable {
    val preview: BackupPreview
    /** Explicit replacement. One-shot; validation and publication never overwrite old photo paths. */
    suspend fun restore()
}
