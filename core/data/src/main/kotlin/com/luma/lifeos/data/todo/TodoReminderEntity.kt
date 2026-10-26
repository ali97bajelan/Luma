package com.luma.lifeos.data.todo

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "task_reminder",
    foreignKeys = [ForeignKey(entity = TodoEntity::class, parentColumns = ["id"], childColumns = ["task_id"], onDelete = ForeignKey.CASCADE)],
)
data class TaskReminderEntity(
    @PrimaryKey @ColumnInfo(name = "task_id") val taskId: String,
    val enabled: Boolean,
    @ColumnInfo(name = "task_revision") val taskRevision: String,
    @ColumnInfo(name = "revision_token") val revisionToken: String,
    @ColumnInfo(name = "consumed_action_token") val consumedActionToken: String?,
    @ColumnInfo(name = "last_delivered_slot") val lastDeliveredSlot: String?,
    @ColumnInfo(name = "snooze_slot") val snoozeSlot: String?,
    @ColumnInfo(name = "snooze_until") val snoozeUntil: Long?,
    @ColumnInfo(name = "needs_reconcile") val needsReconcile: Boolean,
) {
    init {
        require(taskId == UUID.fromString(taskId).toString())
        require(taskRevision == UUID.fromString(taskRevision).toString())
        require(revisionToken == UUID.fromString(revisionToken).toString())
        require(consumedActionToken == null || consumedActionToken == UUID.fromString(consumedActionToken).toString())
        require((snoozeSlot == null) == (snoozeUntil == null))
        require(snoozeUntil == null || snoozeUntil > 0)
    }
}
