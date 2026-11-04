package com.luma.lifeos.domain.focus

import com.luma.lifeos.domain.time.TimeRange
import kotlinx.coroutines.flow.Flow
import java.util.UUID

const val FOCUS_HISTORY_PAGE_SIZE: Int = 50

/** Persistence boundary for local focus sessions; implementations serialize mutations atomically. */
interface FocusRepository {
    suspend fun currentGeneration(): UUID

    /** Reloads a terminal record by identity before using it as a repeat source. */
    suspend fun terminalSession(sessionId: UUID): FocusSession? {
        var offset = 0
        while (true) {
            val page = history(FocusHistoryQuery(range = null), offset, FOCUS_HISTORY_PAGE_SIZE)
            page.rows.firstOrNull { it.session.id == sessionId }?.let { return it.session }
            if (page.rows.isEmpty() || offset + page.rows.size >= page.total) return null
            offset += page.rows.size
        }
    }

    /** Emits the authoritative RUNNING or PAUSED session, or null after state has loaded. */
    fun observeOpen(): Flow<FocusSession?>

    suspend fun start(
        commandId: UUID,
        sessionId: UUID,
        input: FocusStartInput,
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
        terminalReason: FocusTerminalReason = FocusTerminalReason.PROCESS_RESTART_GAP,
    ): FocusWriteResult

    /** A checkpoint updates durable elapsed progress without advancing the user-action revision. */
    suspend fun checkpoint(
        sessionId: UUID,
        expectedRevision: Long,
        sample: FocusTimeSample,
        context: FocusWriteContext,
    ): FocusWriteResult

    fun observeStatistics(range: TimeRange): Flow<FocusStatistics>

    /** Observes a complete terminal snapshot; a null range means all saved history. */
    fun observeInsights(range: TimeRange?): Flow<FocusInsights>

    /** Referenced focus tags ordered by most recent session start, limited to ten suggestions. */
    fun observeTagSuggestions(): Flow<List<FocusTag>>

    /** Full catalog for filtering saved history. */
    fun observeTags(): Flow<List<FocusTag>>

    /** Filters are applied before counts and page slicing; ordering is started-descending, id-descending. */
    suspend fun history(
        query: FocusHistoryQuery,
        offset: Int,
        limit: Int = FOCUS_HISTORY_PAGE_SIZE,
    ): FocusPage

    /** Compatibility overload for the original period-only history view. */
    suspend fun history(range: TimeRange?, offset: Int, limit: Int = FOCUS_HISTORY_PAGE_SIZE): FocusPage =
        history(FocusHistoryQuery(range), offset, limit)

    suspend fun delete(
        sessionId: UUID,
        expectedRevision: Long,
        context: FocusWriteContext,
    ): FocusWriteResult

    /** Explicitly discards an open session as part of a confirmed database replacement. */
    suspend fun discardOpen(
        sessionId: UUID,
        expectedRevision: Long,
        context: FocusWriteContext,
    ): FocusWriteResult = FocusWriteResult.Unavailable(FocusUnavailableReason.UNKNOWN)
}
