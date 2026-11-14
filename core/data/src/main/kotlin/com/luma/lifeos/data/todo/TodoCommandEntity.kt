package com.luma.lifeos.data.todo

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import java.util.UUID

/** Device-local immutable preview and idempotent result journal. Never included in portable backup. */
@Entity(
    tableName = "todo_family_command",
    indices = [
        Index(value = ["command_id"], unique = true),
        Index(value = ["state", "pinned", "created_at"]),
        Index(value = ["source_command_id"]),
    ],
)
data class TodoCommandEntity(
    @androidx.room.PrimaryKey val id: String,
    val kind: String,
    val state: String,
    @ColumnInfo(name = "target_id") val targetId: String,
    @ColumnInfo(name = "root_id") val rootId: String,
    val completed: Boolean?,
    @ColumnInfo(name = "family_revision_before") val familyRevisionBefore: String,
    @ColumnInfo(name = "family_revision_after") val familyRevisionAfter: String?,
    @ColumnInfo(name = "process_token") val processToken: String?,
    @ColumnInfo(name = "payload_version", defaultValue = "1") val payloadVersion: Int = 1,
    @ColumnInfo(name = "command_id") val commandId: String?,
    @ColumnInfo(name = "source_command_id") val sourceCommandId: String?,
    @ColumnInfo(name = "child_count", defaultValue = "0") val childCount: Int = 0,
    @ColumnInfo(name = "changed_task_count", defaultValue = "0") val changedTaskCount: Int = 0,
    @ColumnInfo(name = "open_child_count", defaultValue = "0") val openChildCount: Int = 0,
    @ColumnInfo(name = "reopens_parent", defaultValue = "0") val reopensParent: Boolean = false,
    @ColumnInfo(name = "confirmation_required", defaultValue = "0") val confirmationRequired: Boolean = false,
    @ColumnInfo(name = "undo_available", defaultValue = "0") val undoAvailable: Boolean = false,
    val pinned: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "terminal_at") val terminalAt: Long?,
) {
    init {
        require(id == canonicalUuid(id))
        require(targetId == canonicalUuid(targetId))
        require(rootId == canonicalUuid(rootId))
        require(familyRevisionBefore == canonicalUuid(familyRevisionBefore))
        require(familyRevisionAfter == null || familyRevisionAfter == canonicalUuid(familyRevisionAfter))
        require(processToken == null || processToken == canonicalUuid(processToken))
        require(commandId == null || commandId == canonicalUuid(commandId))
        require(sourceCommandId == null || sourceCommandId == canonicalUuid(sourceCommandId))
        require(payloadVersion > 0)
        require(childCount >= 0 && changedTaskCount >= 0 && openChildCount >= 0)
    }
}

@Entity(
    tableName = "todo_family_command_member",
    primaryKeys = ["command_id", "task_id"],
    foreignKeys = [
        ForeignKey(
            entity = TodoCommandEntity::class,
            parentColumns = ["id"],
            childColumns = ["command_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["task_id"])],
)
data class TodoCommandMemberEntity(
    @ColumnInfo(name = "command_id") val commandId: String,
    @ColumnInfo(name = "task_id") val taskId: String,
    @ColumnInfo(name = "expected_revision") val expectedRevision: String,
    @ColumnInfo(name = "previous_completed_at") val previousCompletedAt: Long?,
    @ColumnInfo(name = "will_change") val willChange: Boolean,
    @ColumnInfo(name = "resulting_revision") val resultingRevision: String? = null,
    @ColumnInfo(name = "resulting_completed_at") val resultingCompletedAt: Long? = null,
) {
    init {
        require(commandId == canonicalUuid(commandId))
        require(taskId == canonicalUuid(taskId))
        require(expectedRevision == canonicalUuid(expectedRevision))
        require(resultingRevision == null || resultingRevision == canonicalUuid(resultingRevision))
    }
}

internal object TodoCommandKind {
    const val COMPLETION = "COMPLETION"
    const val DELETE = "DELETE"
    const val UNDO = "UNDO"
}

internal object TodoCommandState {
    const val PREPARED = "PREPARED"
    const val APPLIED = "APPLIED"
    const val UNDONE = "UNDONE"
    const val STALE = "STALE"
}

private fun canonicalUuid(value: String): String = UUID.fromString(value).toString()
