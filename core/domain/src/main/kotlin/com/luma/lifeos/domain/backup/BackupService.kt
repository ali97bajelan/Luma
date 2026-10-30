package com.luma.lifeos.domain.backup

import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.util.UUID

data class BackupPreview(
    val eventCount: Long,
    val photoCount: Int,
    val createdAt: Instant,
    val habitCount: Long = 0,
    val habitLogCount: Long = 0,
    val todoCount: Long = 0,
    val focusSessionCount: Long = 0,
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
    suspend fun prepare(input: InputStream): PreparedBackup
}
interface PreparedBackup : Closeable {
    val preview: BackupPreview
    /** Explicit replacement. One-shot; validation and publication never overwrite old photo paths. */
    suspend fun restore()
}
