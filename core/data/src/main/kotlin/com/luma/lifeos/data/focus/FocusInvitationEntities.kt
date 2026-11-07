package com.luma.lifeos.data.focus

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.util.UUID

/** Private device configuration. This table is intentionally absent from portable archives. */
@Entity(tableName = "focus_invitation_settings")
data class FocusInvitationSettingsEntity(
    @PrimaryKey
    @ColumnInfo(name = "singleton_id")
    val singletonId: Int = 1,
    @ColumnInfo(name = "enabled", defaultValue = "0")
    val enabled: Boolean = false,
    @ColumnInfo(name = "revision", defaultValue = "0")
    val revision: Long = 0,
    @ColumnInfo(name = "weekday_mask", defaultValue = "0")
    val weekdayMask: Int = 0,
    @ColumnInfo(name = "minute_of_day", defaultValue = "540")
    val minuteOfDay: Int = 540,
) {
    init {
        require(singletonId == 1)
        require(revision >= 0)
        require(weekdayMask in 0..127)
        require(minuteOfDay in 0..1439)
        require(!enabled || weekdayMask != 0)
    }
}

/** Per-date delivery claim. Occurrences and action tokens stay local to one installation. */
@Entity(
    tableName = "focus_invitation_delivery",
    primaryKeys = ["database_generation", "schedule_revision", "local_date"],
    indices = [
        Index(value = ["due_at"]),
        Index(value = ["action_token"], unique = true),
    ],
)
data class FocusInvitationDeliveryEntity(
    @ColumnInfo(name = "database_generation")
    val databaseGeneration: String,
    @ColumnInfo(name = "schedule_revision")
    val scheduleRevision: Long,
    @ColumnInfo(name = "local_date")
    val localDate: String,
    @ColumnInfo(name = "due_at")
    val dueAt: Long,
    @ColumnInfo(name = "state", defaultValue = "'PENDING'")
    val state: String = "PENDING",
    @ColumnInfo(name = "snoozed_due_at")
    val snoozedDueAt: Long? = null,
    @ColumnInfo(name = "action_token")
    val actionToken: String? = null,
) {
    init {
        require(UUID.fromString(databaseGeneration).toString() == databaseGeneration)
        require(scheduleRevision >= 0)
        require(LocalDate.parse(localDate).toString() == localDate)
        require(state.isNotBlank())
        require(actionToken == null || actionToken.isNotBlank())
    }
}
