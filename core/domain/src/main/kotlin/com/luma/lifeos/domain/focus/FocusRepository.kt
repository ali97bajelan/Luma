package com.luma.lifeos.domain.focus

import com.luma.lifeos.domain.time.TimeRange
import kotlinx.coroutines.flow.Flow
import java.util.UUID

const val FOCUS_HISTORY_PAGE_SIZE: Int = 50

/** Persistence boundary for local focus sessions; implementations serialize mutations atomically. */
interface FocusRepository {
    suspend fun currentGeneration(): UUID

    /** Emits the authoritative RUNNING or PAUSED session, or null after state has loaded. */
    fun observeOpen(): Flow<FocusSession?>

    suspend fun start(
        commandId: UUID,
        sessionId: UUID,
        sample: FocusTimeSample,
        context: FocusWriteContext,
    ): FocusWriteResult

    suspend fun apply(command: FocusCommand): FocusWriteResult

    suspend fun reconcile(
        sample: FocusTimeSample,
        context: FocusWriteContext,
    ): FocusWriteResult

    suspend fun resolveRecovery(
        sessionId: UUID,
        expectedRevision: Long,
        choice: FocusRecoveryChoice,
        sample: FocusTimeSample,
        context: FocusWriteContext,
    ): FocusWriteResult

    /** A checkpoint updates durable elapsed progress without advancing the user-action revision. */
    suspend fun checkpoint(
        sessionId: UUID,
        expectedRevision: Long,
        sample: FocusTimeSample,
        context: FocusWriteContext,
    ): FocusWriteResult

    fun observeStatistics(range: TimeRange): Flow<FocusStatistics>

    /** Returns at most [FOCUS_HISTORY_PAGE_SIZE] rows in stable started-descending, id-descending order. */
    suspend fun history(range: TimeRange?, offset: Int, limit: Int = FOCUS_HISTORY_PAGE_SIZE): FocusPage

    suspend fun delete(
        sessionId: UUID,
        expectedRevision: Long,
        context: FocusWriteContext,
    ): FocusWriteResult
}
