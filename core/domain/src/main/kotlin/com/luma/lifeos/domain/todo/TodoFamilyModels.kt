package com.luma.lifeos.domain.todo

import java.util.UUID

/** Lightweight parent context for a child row; titles stay out of retained command payloads. */
data class TodoParentContext(
    val id: UUID,
    val title: String,
)

/** Completed/total child count across the whole family, not only the currently loaded page. */
data class TodoChildProgress(
    val completed: Int,
    val total: Int,
) {
    init {
        require(total > 0) { "Progress is omitted when a parent has no children" }
        require(completed in 0..total) { "Completed child count must be within the family total" }
    }
}

/** A task plus the immediate parent breadcrumb and root-level progress, when applicable. */
data class TodoTaskRow(
    val task: TodoTask,
    val parent: TodoParentContext?,
    val progress: TodoChildProgress?,
) {
    init {
        require(task.parentId == parent?.id) { "Row parent context must match the task relationship" }
        require(progress == null || task.parentId == null) { "Only roots can carry child progress" }
    }
}

/** Bounded row prefix and full matching count from the same query snapshot. */
data class TodoRowPage(
    val items: List<TodoTaskRow>,
    val total: Int,
) {
    init {
        require(total >= 0) { "Task total cannot be negative" }
        require(items.size <= total) { "Task page cannot exceed its total" }
        require(items.map { it.task.id }.distinct().size == items.size) {
            "Task page contains duplicate tasks"
        }
    }
}

/** Direction for an adjacent sibling swap. */
enum class TodoMoveDirection {
    UP,
    DOWN,
}

/** Legacy one-row actions use this typed outcome when family confirmation is required. */
enum class TodoFamilyAction {
    COMPLETE_FAMILY,
    REOPEN_PARENT_WITH_CHILD,
    DELETE_FAMILY,
}

/** Result classification used to reconcile retained handles after process recreation. */
enum class TodoCommandOutcome {
    APPLIED,
    STALE,
    MISSING,
}

/** User-visible preview; exact revision snapshots remain in the local command journal. */
data class TodoCompletionPreview(
    val planId: UUID,
    val targetId: UUID,
    val completed: Boolean,
    val changedTaskCount: Int,
    val openChildCount: Int,
    val reopensParent: Boolean,
    val confirmationRequired: Boolean,
) {
    init {
        require(changedTaskCount >= 0) { "Changed task count cannot be negative" }
        require(openChildCount >= 0) { "Open child count cannot be negative" }
        require(!reopensParent || !completed) { "Reopening a child cannot complete its parent" }
    }
}

/** Committed family action receipt; it contains identifiers and counts, not copied task rows. */
data class TodoCompletionReceipt(
    val commandId: UUID,
    val targetId: UUID,
    val completed: Boolean,
    val changedTaskCount: Int,
    val undoAvailable: Boolean,
) {
    init {
        require(changedTaskCount >= 0) { "Changed task count cannot be negative" }
        require(!undoAvailable || changedTaskCount > 0) { "A no-op completion has no Undo" }
    }
}

/** User-visible delete preview for a child or its complete root family. */
data class TodoDeletePreview(
    val planId: UUID,
    val targetId: UUID,
    val childCount: Int,
) {
    init {
        require(childCount >= 0) { "Child count cannot be negative" }
    }
}
