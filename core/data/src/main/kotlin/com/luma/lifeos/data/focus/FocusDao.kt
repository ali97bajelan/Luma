package com.luma.lifeos.data.focus

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusDao {
    @Query("SELECT * FROM focus_storage_state WHERE singleton_id = 1")
    suspend fun storageState(): FocusStorageStateEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertStorageStateIfAbsent(state: FocusStorageStateEntity): Long

    @Query("UPDATE focus_storage_state SET generation_id = :generationId WHERE singleton_id = 1")
    suspend fun updateGeneration(generationId: String): Int

    @Query("SELECT * FROM focus_session WHERE open_slot = 1 LIMIT 1")
    @Transaction
    fun observeOpenSession(): Flow<FocusSessionWithRuns?>

    @Query("SELECT * FROM focus_session WHERE open_slot = 1 LIMIT 1")
    @Transaction
    suspend fun openSession(): FocusSessionWithRuns?

    @Query("SELECT * FROM focus_session WHERE id = :sessionId LIMIT 1")
    @Transaction
    suspend fun sessionWithRuns(sessionId: String): FocusSessionWithRuns?

    @Query("SELECT * FROM focus_session WHERE id = :sessionId LIMIT 1")
    suspend fun session(sessionId: String): FocusSessionEntity?

    @Query("SELECT * FROM focus_runtime WHERE slot_id = 1 LIMIT 1")
    suspend fun runtime(): FocusRuntimeEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(session: FocusSessionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRun(run: FocusRunEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRuntime(runtime: FocusRuntimeEntity)

    @Update
    suspend fun updateSession(session: FocusSessionEntity): Int

    @Update
    suspend fun updateRun(run: FocusRunEntity): Int

    @Update
    suspend fun updateRuntime(runtime: FocusRuntimeEntity): Int

    @Query("DELETE FROM focus_runtime WHERE slot_id = 1")
    suspend fun deleteRuntime(): Int

    @Query("DELETE FROM focus_session WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String): Int

    @Query("SELECT * FROM focus_command_receipt WHERE command_id = :commandId AND database_generation = :generationId LIMIT 1")
    suspend fun receipt(commandId: String, generationId: String): FocusCommandReceiptEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReceipt(receipt: FocusCommandReceiptEntity): Long

    @Query(
        """DELETE FROM focus_command_receipt
            WHERE sequence NOT IN (
                SELECT sequence FROM focus_command_receipt ORDER BY sequence DESC LIMIT :maximum
            )""",
    )
    suspend fun trimReceipts(maximum: Int)

    @Query("SELECT * FROM focus_deleted_session WHERE session_id = :sessionId AND database_generation = :generationId LIMIT 1")
    suspend fun tombstone(sessionId: String, generationId: String): FocusDeletedSessionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTombstone(tombstone: FocusDeletedSessionEntity)

    @Query("DELETE FROM focus_deleted_session")
    suspend fun clearTombstones()

    @Query(
        """SELECT
            s.id AS session_id,
            s.state AS session_state,
            s.revision AS session_revision,
            s.started_at_seconds AS session_started_at_seconds,
            s.started_at_nanos AS session_started_at_nanos,
            s.ended_at_seconds AS session_ended_at_seconds,
            s.ended_at_nanos AS session_ended_at_nanos,
            s.created_at_seconds AS session_created_at_seconds,
            s.created_at_nanos AS session_created_at_nanos,
            s.updated_at_seconds AS session_updated_at_seconds,
            s.updated_at_nanos AS session_updated_at_nanos,
            s.terminal_reason AS session_terminal_reason,
            s.recovered_or_uncertain AS session_recovered_or_uncertain,
            s.open_slot AS session_open_slot,
            r.id AS run_id,
            r.session_id AS run_session_id,
            r.ordinal AS run_ordinal,
            r.started_at_seconds AS run_started_at_seconds,
            r.started_at_nanos AS run_started_at_nanos,
            r.ended_at_seconds AS run_ended_at_seconds,
            r.ended_at_nanos AS run_ended_at_nanos,
            r.credited_millis AS run_credited_millis,
            r.raw_started_at_seconds AS run_raw_started_at_seconds,
            r.raw_started_at_nanos AS run_raw_started_at_nanos,
            r.raw_ended_at_seconds AS run_raw_ended_at_seconds,
            r.raw_ended_at_nanos AS run_raw_ended_at_nanos,
            r.open_session_id AS run_open_session_id
        FROM focus_session AS s
        INNER JOIN focus_run AS r ON r.session_id = s.id
        WHERE s.state IN ('FINISHED', 'INTERRUPTED')
          AND r.credited_millis > 0
          AND (r.started_at_seconds < :endSeconds
               OR (r.started_at_seconds = :endSeconds AND r.started_at_nanos < :endNanos))
          AND (r.ended_at_seconds > :startSeconds
               OR (r.ended_at_seconds = :startSeconds AND r.ended_at_nanos > :startNanos))""",
    )
    fun observeStatisticsRows(
        startSeconds: Long,
        startNanos: Int,
        endSeconds: Long,
        endNanos: Int,
    ): Flow<List<FocusStatisticsJoinRow>>

    @Query(
        """SELECT * FROM focus_session AS s
        WHERE s.state IN ('FINISHED', 'INTERRUPTED')
          AND (:startSeconds IS NULL OR EXISTS (
              SELECT 1 FROM focus_run AS r
              WHERE r.session_id = s.id AND r.credited_millis > 0
                AND (r.started_at_seconds < :endSeconds
                     OR (r.started_at_seconds = :endSeconds AND r.started_at_nanos < :endNanos))
                AND (r.ended_at_seconds > :startSeconds
                     OR (r.ended_at_seconds = :startSeconds AND r.ended_at_nanos > :startNanos))
          ))
        ORDER BY s.started_at_seconds DESC, s.started_at_nanos DESC, s.id DESC
        LIMIT :limit OFFSET :offset""",
    )
    suspend fun historyPage(
        startSeconds: Long?,
        startNanos: Int?,
        endSeconds: Long?,
        endNanos: Int?,
        offset: Int,
        limit: Int,
    ): List<FocusSessionEntity>

    @Query(
        """SELECT COUNT(*) FROM focus_session AS s
        WHERE s.state IN ('FINISHED', 'INTERRUPTED')
          AND (:startSeconds IS NULL OR EXISTS (
              SELECT 1 FROM focus_run AS r
              WHERE r.session_id = s.id AND r.credited_millis > 0
                AND (r.started_at_seconds < :endSeconds
                     OR (r.started_at_seconds = :endSeconds AND r.started_at_nanos < :endNanos))
                AND (r.ended_at_seconds > :startSeconds
                     OR (r.ended_at_seconds = :startSeconds AND r.ended_at_nanos > :startNanos))
          ))""",
    )
    suspend fun historyCount(
        startSeconds: Long?,
        startNanos: Int?,
        endSeconds: Long?,
        endNanos: Int?,
    ): Long

    @Query("SELECT * FROM focus_run WHERE session_id IN (:sessionIds) ORDER BY session_id, ordinal")
    suspend fun runsForSessions(sessionIds: List<String>): List<FocusRunEntity>
}
