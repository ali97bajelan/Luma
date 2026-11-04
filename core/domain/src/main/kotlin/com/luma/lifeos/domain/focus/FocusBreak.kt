package com.luma.lifeos.domain.focus

import java.util.UUID

/** Local countdown authority; it never creates a focus session or contributes saved time. */
data class FocusBreakRecord(
    val durationMillis: Long,
    val startedElapsedMillis: Long,
    val bootId: String?,
    val databaseGeneration: UUID,
    val revision: Long,
) {
    init {
        require(durationMillis in 60_000L..3_600_000L)
        require(startedElapsedMillis >= 0L && revision >= 0L)
        require(bootId == null || bootId.isNotBlank())
    }
}

sealed interface FocusBreakWriteResult {
    data class Applied(val record: FocusBreakRecord?) : FocusBreakWriteResult
    data class AlreadyApplied(val record: FocusBreakRecord?) : FocusBreakWriteResult
    data class Conflict(val current: FocusBreakRecord?) : FocusBreakWriteResult
    data object Invalid : FocusBreakWriteResult
    data object Unavailable : FocusBreakWriteResult
}
