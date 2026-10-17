package com.luma.lifeos.data.habit

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.ZoneId

@Entity(tableName = "habit_settings")
data class HabitSettingsEntity(
    @PrimaryKey @ColumnInfo(name = "setting_id") val settingId: Int = 1,
    @ColumnInfo(name = "tracking_zone_id") val trackingZoneId: String,
) {
    init {
        require(settingId == 1)
        require(ZoneId.of(trackingZoneId).id == trackingZoneId)
    }
}

@Entity(tableName = "habit", indices = [Index(value = ["start_date"])])
data class HabitEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "unit_label") val unitLabel: String?,
    @ColumnInfo(name = "start_date") val startDate: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
) {
    init {
        require(id == canonicalUuid(id))
        require(name == name.trim() && name.isNotEmpty() && name.codePointCount(0, name.length) <= 100)
        require(unitLabel == null || (unitLabel == unitLabel.trim() && unitLabel.isNotEmpty() && unitLabel.codePointCount(0, unitLabel.length) <= 30))
        require(LocalDate.parse(startDate).toString() == startDate)
        require(updatedAt >= createdAt)
    }
}

@Entity(
    tableName = "habit_schedule",
    foreignKeys = [ForeignKey(entity = HabitEntity::class, parentColumns = ["id"], childColumns = ["habit_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["habit_id", "effective_from"], unique = true)],
)
data class HabitScheduleEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "habit_id") val habitId: String,
    @ColumnInfo(name = "effective_from") val effectiveFrom: String,
    val target: Int,
    @ColumnInfo(name = "weekday_mask") val weekdayMask: Int,
    val state: String,
) {
    init {
        require(id == canonicalUuid(id) && habitId == canonicalUuid(habitId))
        require(LocalDate.parse(effectiveFrom).toString() == effectiveFrom)
        require(target in 1..999 && weekdayMask in 1..127)
        require(state in setOf("ACTIVE", "PAUSED", "ARCHIVED"))
    }
}

@Entity(
    tableName = "habit_log",
    foreignKeys = [ForeignKey(entity = HabitEntity::class, parentColumns = ["id"], childColumns = ["habit_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["habit_date", "occurred_at", "id"]), Index(value = ["habit_id", "habit_date", "id"])],
)
data class HabitLogEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "habit_id") val habitId: String,
    @ColumnInfo(name = "habit_date") val habitDate: String,
    @ColumnInfo(name = "zone_id") val zoneId: String,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long?,
    @ColumnInfo(name = "recorded_at") val recordedAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
) {
    init {
        require(id == canonicalUuid(id) && habitId == canonicalUuid(habitId))
        require(LocalDate.parse(habitDate).toString() == habitDate)
        val zone = ZoneId.of(zoneId)
        require(zone.id == zoneId)
        require(occurredAt == null || java.time.Instant.ofEpochMilli(occurredAt).atZone(zone).toLocalDate().toString() == habitDate)
        require(updatedAt >= recordedAt)
    }
}

@Entity(
    tableName = "habit_day_choice",
    primaryKeys = ["habit_id", "habit_date"],
    foreignKeys = [ForeignKey(entity = HabitEntity::class, parentColumns = ["id"], childColumns = ["habit_id"], onDelete = ForeignKey.CASCADE)],
)
data class HabitDayChoiceEntity(
    @ColumnInfo(name = "habit_id") val habitId: String,
    @ColumnInfo(name = "habit_date") val habitDate: String,
    @ColumnInfo(name = "skip_remaining") val skipRemaining: Boolean,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
) {
    init {
        require(habitId == canonicalUuid(habitId))
        require(LocalDate.parse(habitDate).toString() == habitDate)
    }
}

@Entity(
    tableName = "habit_reminder",
    foreignKeys = [ForeignKey(entity = HabitEntity::class, parentColumns = ["id"], childColumns = ["habit_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["habit_id", "revision_token"], unique = true)],
)
data class HabitReminderEntity(
    @PrimaryKey @ColumnInfo(name = "habit_id") val habitId: String,
    val enabled: Boolean,
    val mode: String,
    @ColumnInfo(name = "times_json") val timesJson: String,
    @ColumnInfo(name = "interval_minutes") val intervalMinutes: Int?,
    @ColumnInfo(name = "start_minute") val startMinute: Int?,
    @ColumnInfo(name = "end_minute") val endMinute: Int?,
    @ColumnInfo(name = "revision_token") val revisionToken: String,
) {
    init {
        require(habitId == canonicalUuid(habitId) && revisionToken == canonicalUuid(revisionToken))
        require(mode == "TIMES" || mode == "INTERVAL")
    }
}

@Entity(
    tableName = "habit_reminder_runtime",
    primaryKeys = ["habit_id"],
    foreignKeys = [ForeignKey(
        entity = HabitReminderEntity::class,
        parentColumns = ["habit_id", "revision_token"],
        childColumns = ["habit_id", "revision_token"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["habit_id", "revision_token"])],
)
data class HabitReminderRuntimeEntity(
    @ColumnInfo(name = "habit_id") val habitId: String,
    @ColumnInfo(name = "revision_token") val revisionToken: String,
    @ColumnInfo(name = "last_delivered_slot") val lastDeliveredSlot: String?,
    @ColumnInfo(name = "consumed_action_token") val consumedActionToken: String?,
    @ColumnInfo(name = "snooze_slot") val snoozeSlot: String?,
    @ColumnInfo(name = "snooze_until") val snoozeUntil: Long?,
    @ColumnInfo(name = "needs_reconcile") val needsReconcile: Boolean,
) {
    init {
        require(habitId == canonicalUuid(habitId) && revisionToken == canonicalUuid(revisionToken))
        require(consumedActionToken == null || consumedActionToken == canonicalUuid(consumedActionToken))
        require((snoozeSlot == null) == (snoozeUntil == null))
        require(snoozeUntil == null || snoozeUntil > 0)
    }
}

internal fun canonicalUuid(value: String): String = java.util.UUID.fromString(value).toString().also { require(it == value) }
