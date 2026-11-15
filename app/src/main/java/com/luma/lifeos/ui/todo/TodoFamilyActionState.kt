package com.luma.lifeos.ui.todo

import java.util.UUID

enum class TodoFamilyActionType { COMPLETION, DELETE }

enum class TodoFamilyActionPhase {
    IDLE,
    PREVIEWING,
    AWAITING_CONFIRMATION,
    APPLYING,
    APPLIED,
    RETRYABLE_FAILURE,
    CONFLICT,
    MISSING,
}

/** UI state for the shared, revision-checked family action flow. */
data class TodoFamilyActionState(
    val phase: TodoFamilyActionPhase = TodoFamilyActionPhase.IDLE,
    val type: TodoFamilyActionType? = null,
    val targetId: UUID? = null,
    val targetTitle: String? = null,
    val expectedRevision: UUID? = null,
    val planId: UUID? = null,
    val commandId: UUID? = null,
    val completed: Boolean? = null,
    val changedTaskCount: Int = 0,
    val openChildCount: Int = 0,
    val childCount: Int = 0,
    val reopensParent: Boolean = false,
) {
    val busy: Boolean
        get() = (phase == TodoFamilyActionPhase.PREVIEWING) || (phase == TodoFamilyActionPhase.APPLYING)

    val isActive: Boolean
        get() = phase != TodoFamilyActionPhase.IDLE && phase != TodoFamilyActionPhase.APPLIED
}
