package com.luma.lifeos.data.focus

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/** Persisted focus state is device-local except for terminal history handled by FM06. */
@Entity(
    tableName = "focus_session",
    indices = [
        Index(value = ["open_slot"], unique = true),
        Index(value = ["state", "started_at_seconds", "started_at_nanos"]),
    ],
)
data class FocusSessionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "state")
    val state: String,
    @ColumnInfo(name = "revision")
    val revision: Long,
    @ColumnInfo(name = "started_at_seconds")
    val startedAtSeconds: Long,
    @ColumnInfo(name = "started_at_nanos")
    val startedAtNanos: Int,
    @ColumnInfo(name = "ended_at_seconds")
    val endedAtSeconds: Long?,
    @ColumnInfo(name = "ended_at_nanos")
    val endedAtNanos: Int?,
    @ColumnInfo(name = "created_at_seconds")
    val createdAtSeconds: Long,
    @ColumnInfo(name = "created_at_nanos")
    val createdAtNanos: Int,
    @ColumnInfo(name = "updated_at_seconds")
    val updatedAtSeconds: Long,
    @ColumnInfo(name = "updated_at_nanos")
    val updatedAtNanos: Int,
    @ColumnInfo(name = "terminal_reason")
    val terminalReason: String?,
    @ColumnInfo(name = "recovered_or_uncertain")
    val recoveredOrUncertain: Boolean,
    /** Nullable unique slot: one active or paused session for the whole installation. */
    @ColumnInfo(name = "open_slot")
    val openSlot: Int?,
)

@Entity(
    tableName = "focus_run",
    foreignKeys = [
        ForeignKey(
            entity = FocusSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["session_id", "ordinal"], unique = true),
        Index(value = ["id", "session_id"], unique = true),
        Index(value = ["open_session_id"], unique = true),
        Index(value = ["started_at_seconds", "started_at_nanos"]),
        Index(value = ["ended_at_seconds", "ended_at_nanos"]),
    ],
)
data class FocusRunEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "session_id")
    val sessionId: String,
    @ColumnInfo(name = "ordinal")
    val ordinal: Int,
    @ColumnInfo(name = "started_at_seconds")
    val startedAtSeconds: Long,
    @ColumnInfo(name = "started_at_nanos")
    val startedAtNanos: Int,
    @ColumnInfo(name = "ended_at_seconds")
    val endedAtSeconds: Long?,
    @ColumnInfo(name = "ended_at_nanos")
    val endedAtNanos: Int?,
    @ColumnInfo(name = "credited_millis")
    val creditedMillis: Long,
    @ColumnInfo(name = "raw_started_at_seconds")
    val rawStartedAtSeconds: Long,
    @ColumnInfo(name = "raw_started_at_nanos")
    val rawStartedAtNanos: Int,
    @ColumnInfo(name = "raw_ended_at_seconds")
    val rawEndedAtSeconds: Long?,
    @ColumnInfo(name = "raw_ended_at_nanos")
    val rawEndedAtNanos: Int?,
    /** Non-null only while this run is open; unique to prevent duplicate open runs per session. */
    @ColumnInfo(name = "open_session_id")
    val openSessionId: String?,
)

/** There is only one runtime row (slot 1); it is deleted when a session pauses or terminates. */
@Entity(
    tableName = "focus_runtime",
    foreignKeys = [
        ForeignKey(
            entity = FocusSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = FocusRunEntity::class,
            parentColumns = ["id", "session_id"],
            childColumns = ["run_id", "session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["session_id"], unique = true),
        Index(value = ["run_id"], unique = true),
    ],
)
data class FocusRuntimeEntity(
    @PrimaryKey
    @ColumnInfo(name = "slot_id")
    val slotId: Int,
    @ColumnInfo(name = "session_id")
    val sessionId: String,
    @ColumnInfo(name = "run_id")
    val runId: String,
    @ColumnInfo(name = "run_started_elapsed_millis")
    val runStartedElapsedMillis: Long,
    @ColumnInfo(name = "checkpoint_elapsed_millis")
    val checkpointElapsedMillis: Long,
    @ColumnInfo(name = "checkpoint_wall_seconds")
    val checkpointWallSeconds: Long,
    @ColumnInfo(name = "checkpoint_wall_nanos")
    val checkpointWallNanos: Int,
    @ColumnInfo(name = "boot_id")
    val bootId: String?,
    @ColumnInfo(name = "process_instance_id")
    val processInstanceId: String,
    @ColumnInfo(name = "recovery_required")
    val recoveryRequired: Boolean,
    @ColumnInfo(name = "revision")
    val revision: Long,
)

/** Command receipts outlive their session and are retained in sequence order, newest first. */
@Entity(
    tableName = "focus_command_receipt",
    indices = [Index(value = ["database_generation", "command_id"], unique = true)],
)
data class FocusCommandReceiptEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "sequence")
    val sequence: Long = 0,
    @ColumnInfo(name = "command_id")
    val commandId: String,
    @ColumnInfo(name = "database_generation")
    val databaseGeneration: String,
    @ColumnInfo(name = "payload")
    val payload: String,
    @ColumnInfo(name = "result_type")
    val resultType: String,
    @ColumnInfo(name = "result_session_id")
    val resultSessionId: String?,
    @ColumnInfo(name = "result_invalid_reason")
    val resultInvalidReason: String?,
    @ColumnInfo(name = "created_at_seconds")
    val createdAtSeconds: Long,
    @ColumnInfo(name = "created_at_nanos")
    val createdAtNanos: Int,
)

/** Prevents an evicted Start receipt from recreating a deleted session in this generation. */
@Entity(
    tableName = "focus_deleted_session",
    indices = [Index(value = ["database_generation"])],
)
data class FocusDeletedSessionEntity(
    @PrimaryKey
    @ColumnInfo(name = "session_id")
    val sessionId: String,
    @ColumnInfo(name = "database_generation")
    val databaseGeneration: String,
)

/** Durable local authority for rejecting mutations queued against a replaced database. */
@Entity(tableName = "focus_storage_state")
data class FocusStorageStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "singleton_id")
    val singletonId: Int,
    @ColumnInfo(name = "generation_id")
    val generationId: String,
)

data class FocusSessionWithRuns(
    @Embedded
    val session: FocusSessionEntity,
    @Relation(parentColumn = "id", entityColumn = "session_id", entity = FocusRunEntity::class)
    val runs: List<FocusRunEntity>,
)

/** Narrow joined projection: statistics never materialize unrelated or non-overlapping history. */
data class FocusStatisticsJoinRow(
    @Embedded(prefix = "session_")
    val session: FocusSessionEntity,
    @Embedded(prefix = "run_")
    val run: FocusRunEntity,
)
