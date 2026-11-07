package com.luma.lifeos.ui.focus

import java.util.UUID

data class PendingFocusTaskCompletion(
    val taskId: UUID,
    val expectedRevision: UUID,
    val nextRevision: UUID,
    val generation: UUID,
)

data class PendingFocusTaskUnlink(
    val commandId: UUID,
    val sessionId: UUID,
    val taskId: UUID,
    val expectedRevision: Long,
    val generation: UUID,
)

enum class FocusTaskCompletionPhase {
    IDLE,
    LOADING,
    SAVING,
    COMPLETED,
    MISSING,
    CONFLICT,
    ERROR,
}

enum class FocusTaskUnlinkPhase {
    IDLE,
    SAVING,
    UNLINKED,
    CONFLICT,
    ERROR,
}

data class FocusTaskCompletionState(
    val sessionId: UUID? = null,
    val sessionRevision: Long? = null,
    val taskId: UUID? = null,
    val generation: UUID? = null,
    val completionPhase: FocusTaskCompletionPhase = FocusTaskCompletionPhase.IDLE,
    val pendingCompletion: PendingFocusTaskCompletion? = null,
    val unlinkPhase: FocusTaskUnlinkPhase = FocusTaskUnlinkPhase.IDLE,
    val pendingUnlink: PendingFocusTaskUnlink? = null,
) {
    fun matches(sessionId: UUID, sessionRevision: Long, taskId: UUID?, generation: UUID?): Boolean =
        (
            this.sessionId == sessionId &&
                this.sessionRevision == sessionRevision &&
                this.taskId == taskId &&
                this.generation == generation
        )
}
