package com.luma.lifeos.data.focus

import android.database.sqlite.SQLiteException
import androidx.room.withTransaction
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.domain.focus.FocusAction
import com.luma.lifeos.domain.focus.FocusCommand
import com.luma.lifeos.domain.focus.FocusHistoryRow
import com.luma.lifeos.domain.focus.FocusInvalidReason
import com.luma.lifeos.domain.focus.FocusPage
import com.luma.lifeos.domain.focus.FocusRecoveryAssessment
import com.luma.lifeos.domain.focus.FocusRecoveryChoice
import com.luma.lifeos.domain.focus.FocusRepository
import com.luma.lifeos.domain.focus.FocusRun
import com.luma.lifeos.domain.focus.FocusSession
import com.luma.lifeos.domain.focus.FocusState
import com.luma.lifeos.domain.focus.FocusStatistics
import com.luma.lifeos.domain.focus.FocusTerminalReason
import com.luma.lifeos.domain.focus.FocusTimeSample
import com.luma.lifeos.domain.focus.FocusTimingCheckpoint
import com.luma.lifeos.domain.focus.FocusTimingResult
import com.luma.lifeos.domain.focus.FocusTransitionResult
import com.luma.lifeos.domain.focus.FocusUnavailableReason
import com.luma.lifeos.domain.focus.FocusWriteContext
import com.luma.lifeos.domain.focus.FocusWriteResult
import com.luma.lifeos.domain.focus.FOCUS_HISTORY_PAGE_SIZE
import com.luma.lifeos.domain.focus.FOCUS_WALL_DRIFT_TOLERANCE_MILLIS
import com.luma.lifeos.domain.focus.applyFocusCommand
import com.luma.lifeos.domain.focus.assessFocusRecovery
import com.luma.lifeos.domain.focus.calculateFocusTiming
import com.luma.lifeos.domain.focus.createFocusSession
import com.luma.lifeos.domain.focus.focusRangeContributionMillis
import com.luma.lifeos.domain.focus.focusStatistics
import com.luma.lifeos.domain.focus.resolveFocusRecovery
import com.luma.lifeos.domain.time.TimeRange
import java.time.Instant
import java.time.Duration
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Room implementation whose mutations are serialized with runtime, generation and receipt state. */
class RoomFocusRepository(
    private val database: LumaDatabase,
) : FocusRepository {
    private val dao: FocusDao get() = database.focus()

    override suspend fun currentGeneration(): UUID = database.withTransaction {
        ensureGeneration().let { UUID.fromString(it.generationId) }
    }

    override fun observeOpen(): Flow<FocusSession?> = dao.observeOpenSession().map { it?.toDomain() }

    override suspend fun start(
        commandId: UUID,
        sessionId: UUID,
        sample: FocusTimeSample,
        context: FocusWriteContext,
    ): FocusWriteResult = runCommand(
        commandId = commandId,
        context = context,
        requestedSessionId = sessionId,
        payload = commandPayload(
            "START", sessionId.toString(), sample.payload(), context.payload(),
        ),
        createdAt = sample.wall,
    ) { generationId ->
        val id = sessionId.toString()
        if (dao.tombstone(id, generationId) != null) {
            return@runCommand FocusWriteResult.Invalid(FocusInvalidReason.INVALID_SNAPSHOT)
        }

        val expected = createFocusSession(sessionId, commandId, sample)
        val existing = dao.sessionWithRuns(id)?.toDomain()
        if (existing != null) {
            val sameStart = existing.startedAt == sample.wall &&
                existing.runs.any { it.id == expected.runs.single().id }
            return@runCommand if (sameStart) {
                FocusWriteResult.AlreadyApplied(existing)
            } else {
                FocusWriteResult.Conflict(existing)
            }
        }

        val active = dao.openSession()?.toDomain()
        if (active != null) return@runCommand FocusWriteResult.Conflict(active)

        dao.insertSession(expected.toEntity())
        val run = expected.runs.single()
        dao.insertRun(run.toEntity())
        dao.insertRuntime(
            runtimeFor(
                sessionId = sessionId,
                run = run,
                runStartedElapsedMillis = sample.elapsedMillis,
                sample = sample,
                processInstanceId = context.processInstanceId,
                recoveryRequired = false,
                revision = expected.revision,
            ),
        )
        FocusWriteResult.Applied(expected)
    }

    override suspend fun apply(command: FocusCommand): FocusWriteResult = runCommand(
        commandId = command.commandId,
        context = command.context,
        requestedSessionId = command.sessionId,
        payload = commandPayload(
            "ACTION",
            command.sessionId.toString(),
            command.expectedRevision.toString(),
            command.action.name,
            command.sample.payload(),
            command.context.payload(),
        ),
        createdAt = command.sample.wall,
    ) {
        applyWithinTransaction(command)
    }

    override suspend fun reconcile(
        sample: FocusTimeSample,
        context: FocusWriteContext,
    ): FocusWriteResult = storageWrite {
        database.withTransaction {
            val generation = ensureGeneration().generationId
            if (generation != context.databaseGeneration.toString()) {
                return@withTransaction FocusWriteResult.Invalid(FocusInvalidReason.STALE_DATABASE_GENERATION)
            }

            val current = dao.openSession()?.toDomain() ?: return@withTransaction FocusWriteResult.Conflict(null)
            if (current.state == FocusState.PAUSED) {
                return@withTransaction FocusWriteResult.AlreadyApplied(current)
            }

            val runtime = dao.runtime() ?: return@withTransaction FocusWriteResult.Unavailable(FocusUnavailableReason.STORAGE)
            if (runtime.sessionId != current.id.toString() || runtime.revision != current.revision) {
                return@withTransaction FocusWriteResult.Unavailable(FocusUnavailableReason.STORAGE)
            }
            if (runtime.recoveryRequired) {
                if (runtime.processInstanceId != context.processInstanceId.toString()) {
                    dao.updateRuntime(runtime.copy(processInstanceId = context.processInstanceId.toString()))
                }
                return@withTransaction FocusWriteResult.RecoveryRequired(current)
            }
            if (sampleIsStale(runtime, sample)) return@withTransaction FocusWriteResult.Conflict(current)

            val checkpoint = runtime.toCheckpoint()
            when (
                val assessment = assessFocusRecovery(
                    session = current,
                    checkpoint = checkpoint,
                    sample = sample,
                    processOwnerChanged = runtime.processInstanceId != context.processInstanceId.toString(),
                )
            ) {
                FocusRecoveryAssessment.NoChange -> FocusWriteResult.AlreadyApplied(current)
                is FocusRecoveryAssessment.Continue -> FocusWriteResult.AlreadyApplied(current)
                is FocusRecoveryAssessment.ChoiceRequired -> {
                    dao.updateRuntime(
                        runtime.copy(
                            processInstanceId = context.processInstanceId.toString(),
                            recoveryRequired = true,
                        ),
                    )
                    FocusWriteResult.RecoveryRequired(current)
                }
                is FocusRecoveryAssessment.Interrupt -> {
                    val command = FocusCommand(
                        commandId = UUID.randomUUID(),
                        sessionId = current.id,
                        expectedRevision = current.revision,
                        action = FocusAction.STOP,
                        sample = sample,
                        context = context,
                    )
                    when (val transition = applyFocusCommand(current, command, checkpoint)) {
                        is FocusTransitionResult.Changed -> persistTransition(
                            current = current,
                            next = transition.snapshot,
                            timing = transition.timing,
                            runtime = runtime,
                            sample = sample,
                            context = context,
                        )
                        is FocusTransitionResult.AlreadyApplied -> FocusWriteResult.AlreadyApplied(transition.snapshot)
                        is FocusTransitionResult.Conflict -> FocusWriteResult.Conflict(transition.current)
                        is FocusTransitionResult.Invalid -> FocusWriteResult.Invalid(transition.reason)
                    }
                }
                is FocusRecoveryAssessment.Invalid -> FocusWriteResult.Invalid(FocusInvalidReason.INVALID_SNAPSHOT)
            }
        }
    }

    override suspend fun resolveRecovery(
        sessionId: UUID,
        expectedRevision: Long,
        choice: FocusRecoveryChoice,
        sample: FocusTimeSample,
        context: FocusWriteContext,
    ): FocusWriteResult = storageWrite {
        database.withTransaction {
            val generation = ensureGeneration().generationId
            if (generation != context.databaseGeneration.toString()) {
                return@withTransaction FocusWriteResult.Invalid(FocusInvalidReason.STALE_DATABASE_GENERATION)
            }
            val current = dao.sessionWithRuns(sessionId.toString())?.toDomain()
                ?: return@withTransaction FocusWriteResult.Conflict(null)
            val runtime = dao.runtime()
                ?: return@withTransaction FocusWriteResult.Conflict(current)
            if (runtime.sessionId != sessionId.toString()) return@withTransaction FocusWriteResult.Conflict(current)
            if (runtime.processInstanceId != context.processInstanceId.toString()) {
                dao.updateRuntime(
                    runtime.copy(
                        processInstanceId = context.processInstanceId.toString(),
                        recoveryRequired = true,
                    ),
                )
                return@withTransaction FocusWriteResult.RecoveryRequired(current)
            }
            if (!runtime.recoveryRequired || runtime.revision != current.revision) {
                return@withTransaction FocusWriteResult.Conflict(current)
            }
            if (expectedRevision != current.revision || sampleIsStale(runtime, sample)) {
                return@withTransaction FocusWriteResult.Conflict(current)
            }

            val checkpoint = runtime.toCheckpoint()
            when (
                val transition = resolveFocusRecovery(
                    session = current,
                    expectedRevision = expectedRevision,
                    checkpoint = checkpoint,
                    sample = sample,
                    choice = choice,
                )
            ) {
                is FocusTransitionResult.Changed -> persistTransition(
                    current = current,
                    next = transition.snapshot,
                    timing = transition.timing,
                    runtime = runtime,
                    sample = sample,
                    context = context,
                )
                is FocusTransitionResult.AlreadyApplied -> FocusWriteResult.AlreadyApplied(transition.snapshot)
                is FocusTransitionResult.Conflict -> FocusWriteResult.Conflict(transition.current)
                is FocusTransitionResult.Invalid -> FocusWriteResult.Invalid(transition.reason)
            }
        }
    }

    override suspend fun checkpoint(
        sessionId: UUID,
        expectedRevision: Long,
        sample: FocusTimeSample,
        context: FocusWriteContext,
    ): FocusWriteResult = storageWrite {
        database.withTransaction {
            val generation = ensureGeneration().generationId
            if (generation != context.databaseGeneration.toString()) {
                return@withTransaction FocusWriteResult.Invalid(FocusInvalidReason.STALE_DATABASE_GENERATION)
            }
            val current = dao.sessionWithRuns(sessionId.toString())?.toDomain()
                ?: return@withTransaction FocusWriteResult.Conflict(null)
            val runtime = dao.runtime()
                ?: return@withTransaction FocusWriteResult.Conflict(current)
            if (runtime.sessionId != sessionId.toString()) return@withTransaction FocusWriteResult.Conflict(current)
            if (runtime.recoveryRequired) return@withTransaction FocusWriteResult.RecoveryRequired(current)
            if (runtime.processInstanceId != context.processInstanceId.toString()) {
                dao.updateRuntime(
                    runtime.copy(
                        processInstanceId = context.processInstanceId.toString(),
                        recoveryRequired = true,
                    ),
                )
                return@withTransaction FocusWriteResult.RecoveryRequired(current)
            }
            if (current.state != FocusState.RUNNING || runtime.revision != current.revision ||
                expectedRevision != current.revision || sampleIsStale(runtime, sample)
            ) {
                return@withTransaction FocusWriteResult.Conflict(current)
            }

            val run = current.runs.singleOrNull(FocusRun::isOpen)
                ?: return@withTransaction FocusWriteResult.Invalid(FocusInvalidReason.INVALID_SNAPSHOT)
            when (
                val timing = calculateFocusTiming(run, runtime.toCheckpoint(), sample)
            ) {
                is FocusTimingResult.Advanced -> {
                    if (timing.elapsedDeltaMillis == 0L) {
                        return@withTransaction FocusWriteResult.AlreadyApplied(current)
                    }
                    val updatedRun = run.copy(creditedMillis = timing.creditedMillis)
                    val updated = current.copy(
                        updatedAt = sample.wall,
                        runs = current.runs.map { if (it.id == run.id) updatedRun else it },
                    )
                    if (dao.updateRun(updatedRun.toEntity()) != 1 || dao.updateSession(updated.toEntity()) != 1) {
                        throw FocusPersistenceException()
                    }
                    val newRuntime = runtime.copy(
                        checkpointElapsedMillis = timing.checkpoint.sample.elapsedMillis,
                        checkpointWallSeconds = timing.checkpoint.sample.wall.epochSecond,
                        checkpointWallNanos = timing.checkpoint.sample.wall.nano,
                        bootId = timing.checkpoint.sample.bootId,
                    )
                    if (dao.updateRuntime(newRuntime) != 1) throw FocusPersistenceException()
                    FocusWriteResult.Applied(updated)
                }
                is FocusTimingResult.InterruptedAtCheckpoint -> {
                    val command = FocusCommand(
                        commandId = UUID.randomUUID(),
                        sessionId = current.id,
                        expectedRevision = current.revision,
                        action = FocusAction.STOP,
                        sample = sample,
                        context = context,
                    )
                    when (val transition = applyFocusCommand(current, command, runtime.toCheckpoint())) {
                        is FocusTransitionResult.Changed -> persistTransition(
                            current = current,
                            next = transition.snapshot,
                            timing = transition.timing,
                            runtime = runtime,
                            sample = sample,
                            context = context,
                        )
                        is FocusTransitionResult.AlreadyApplied -> FocusWriteResult.AlreadyApplied(transition.snapshot)
                        is FocusTransitionResult.Conflict -> FocusWriteResult.Conflict(transition.current)
                        is FocusTransitionResult.Invalid -> FocusWriteResult.Invalid(transition.reason)
                    }
                }
                is FocusTimingResult.Invalid -> FocusWriteResult.Invalid(FocusInvalidReason.INVALID_SNAPSHOT)
            }
        }
    }

    override fun observeStatistics(range: TimeRange): Flow<FocusStatistics> =
        dao.observeStatisticsRows(
            startSeconds = range.start.epochSecond,
            startNanos = range.start.nano,
            endSeconds = range.endExclusive.epochSecond,
            endNanos = range.endExclusive.nano,
        ).map { rows ->
            val sessions = rows.groupBy { it.session.id }.values.map { joined ->
                val first = joined.first()
                first.session.toDomain(joined.map { it.run.toDomain() })
            }
            focusStatistics(sessions, range)
        }

    override suspend fun history(range: TimeRange?, offset: Int, limit: Int): FocusPage {
        require(offset >= 0 && limit in 1..FOCUS_HISTORY_PAGE_SIZE) {
            "History pages require a nonnegative offset and a limit from 1 to $FOCUS_HISTORY_PAGE_SIZE"
        }
        return database.withTransaction {
            val start = range?.start
            val end = range?.endExclusive
            val total = Math.toIntExact(
                dao.historyCount(
                    startSeconds = start?.epochSecond,
                    startNanos = start?.nano,
                    endSeconds = end?.epochSecond,
                    endNanos = end?.nano,
                ),
            )
            val page = dao.historyPage(
                startSeconds = start?.epochSecond,
                startNanos = start?.nano,
                endSeconds = end?.epochSecond,
                endNanos = end?.nano,
                offset = offset,
                limit = limit,
            )
            if (page.isEmpty()) return@withTransaction FocusPage(emptyList(), offset, total)

            val runsBySession = dao.runsForSessions(page.map(FocusSessionEntity::id))
                .groupBy(FocusRunEntity::sessionId)
            val rows = page.map { entity ->
                val session = entity.toDomain(runsBySession[entity.id].orEmpty().map(FocusRunEntity::toDomain))
                FocusHistoryRow(
                    session = session,
                    fullDurationMillis = session.creditedMillis,
                    rangeContributionMillis = range?.let { focusRangeContributionMillis(session, it) }
                        ?: session.creditedMillis,
                )
            }
            FocusPage(rows, offset, total)
        }
    }

    override suspend fun delete(
        sessionId: UUID,
        expectedRevision: Long,
        context: FocusWriteContext,
    ): FocusWriteResult = storageWrite {
        database.withTransaction {
            val generation = ensureGeneration().generationId
            if (generation != context.databaseGeneration.toString()) {
                return@withTransaction FocusWriteResult.Invalid(FocusInvalidReason.STALE_DATABASE_GENERATION)
            }
            val id = sessionId.toString()
            if (dao.tombstone(id, generation) != null) {
                return@withTransaction FocusWriteResult.AlreadyApplied(deletedSessionId = sessionId)
            }
            val current = dao.sessionWithRuns(id)?.toDomain()
                ?: return@withTransaction FocusWriteResult.Conflict(null)
            if (!current.isTerminal) return@withTransaction FocusWriteResult.Invalid(FocusInvalidReason.INVALID_TRANSITION)
            if (current.revision != expectedRevision) return@withTransaction FocusWriteResult.Conflict(current)
            if (dao.runtime()?.sessionId == id) return@withTransaction FocusWriteResult.Unavailable(FocusUnavailableReason.STORAGE)

            dao.insertTombstone(FocusDeletedSessionEntity(sessionId = id, databaseGeneration = generation))
            if (dao.deleteSession(id) != 1) throw FocusPersistenceException()
            FocusWriteResult.Applied(deletedSessionId = sessionId)
        }
    }

    private suspend fun applyWithinTransaction(command: FocusCommand): FocusWriteResult {
        val current = dao.sessionWithRuns(command.sessionId.toString())?.toDomain()
            ?: return FocusWriteResult.Conflict(null)
        val runtime = dao.runtime()

        if (current.isTerminal) {
            return when (val transition = applyFocusCommand(current, command)) {
                is FocusTransitionResult.AlreadyApplied -> FocusWriteResult.AlreadyApplied(transition.snapshot)
                is FocusTransitionResult.Conflict -> FocusWriteResult.Conflict(transition.current)
                is FocusTransitionResult.Invalid -> FocusWriteResult.Invalid(transition.reason)
                is FocusTransitionResult.Changed -> error("A terminal focus session cannot transition")
            }
        }

        if (current.state == FocusState.PAUSED) {
            if (runtime != null) return FocusWriteResult.Unavailable(FocusUnavailableReason.STORAGE)
            return when (val transition = applyFocusCommand(current, command)) {
                is FocusTransitionResult.Changed -> persistTransition(
                    current = current,
                    next = transition.snapshot,
                    timing = transition.timing,
                    runtime = null,
                    sample = command.sample,
                    context = command.context,
                )
                is FocusTransitionResult.AlreadyApplied -> FocusWriteResult.AlreadyApplied(transition.snapshot)
                is FocusTransitionResult.Conflict -> FocusWriteResult.Conflict(transition.current)
                is FocusTransitionResult.Invalid -> FocusWriteResult.Invalid(transition.reason)
            }
        }

        val activeRuntime = runtime
            ?: return FocusWriteResult.Unavailable(FocusUnavailableReason.STORAGE)
        if (activeRuntime.sessionId != current.id.toString() || activeRuntime.revision != current.revision) {
            return FocusWriteResult.Unavailable(FocusUnavailableReason.STORAGE)
        }
        if (activeRuntime.recoveryRequired) return FocusWriteResult.RecoveryRequired(current)
        if (activeRuntime.processInstanceId != command.context.processInstanceId.toString()) {
            dao.updateRuntime(
                activeRuntime.copy(
                    processInstanceId = command.context.processInstanceId.toString(),
                    recoveryRequired = true,
                ),
            )
            return FocusWriteResult.RecoveryRequired(current)
        }
        if (command.expectedRevision != current.revision || sampleIsStale(activeRuntime, command.sample)) {
            return FocusWriteResult.Conflict(current)
        }

        return when (
            val transition = applyFocusCommand(current, command, activeRuntime.toCheckpoint())
        ) {
            is FocusTransitionResult.Changed -> persistTransition(
                current = current,
                next = transition.snapshot,
                timing = transition.timing,
                runtime = activeRuntime,
                sample = command.sample,
                context = command.context,
            )
            is FocusTransitionResult.AlreadyApplied -> FocusWriteResult.AlreadyApplied(transition.snapshot)
            is FocusTransitionResult.Conflict -> FocusWriteResult.Conflict(transition.current)
            is FocusTransitionResult.Invalid -> FocusWriteResult.Invalid(transition.reason)
        }
    }

    private suspend fun persistTransition(
        current: FocusSession,
        next: FocusSession,
        timing: FocusTimingResult?,
        runtime: FocusRuntimeEntity?,
        sample: FocusTimeSample,
        context: FocusWriteContext,
    ): FocusWriteResult {
        if (next.id != current.id) return FocusWriteResult.Invalid(FocusInvalidReason.SESSION_MISMATCH)
        val openRun = if (next.state == FocusState.RUNNING) {
            next.runs.singleOrNull(FocusRun::isOpen)
                ?: return FocusWriteResult.Invalid(FocusInvalidReason.INVALID_SNAPSHOT)
        } else {
            null
        }
        val advancedTiming = if (current.state == FocusState.RUNNING && next.state == FocusState.RUNNING) {
            timing as? FocusTimingResult.Advanced
                ?: return FocusWriteResult.Invalid(FocusInvalidReason.INVALID_SNAPSHOT)
        } else {
            null
        }
        if (current.state == FocusState.RUNNING && next.state == FocusState.RUNNING &&
            (runtime == null || runtime.runId != openRun?.id.toString())
        ) {
            return FocusWriteResult.Unavailable(FocusUnavailableReason.STORAGE)
        }
        if (next.state != FocusState.RUNNING && runtime != null) dao.deleteRuntime()

        val previousRuns = current.runs.associateBy(FocusRun::id)
        for (run in next.runs) {
            val previous = previousRuns[run.id]
            if (previous == null) {
                dao.insertRun(run.toEntity())
            } else if (previous != run && dao.updateRun(run.toEntity()) != 1) {
                throw FocusPersistenceException()
            }
        }
        if (dao.updateSession(next.toEntity()) != 1) throw FocusPersistenceException()

        if (next.state == FocusState.RUNNING) {
            val nextOpenRun = checkNotNull(openRun)
            if (current.state == FocusState.RUNNING) {
                val existingRuntime = checkNotNull(runtime)
                val advanced = checkNotNull(advancedTiming)
                val updatedRuntime = existingRuntime.copy(
                    checkpointElapsedMillis = advanced.checkpoint.sample.elapsedMillis,
                    checkpointWallSeconds = advanced.checkpoint.sample.wall.epochSecond,
                    checkpointWallNanos = advanced.checkpoint.sample.wall.nano,
                    bootId = advanced.checkpoint.sample.bootId,
                    processInstanceId = context.processInstanceId.toString(),
                    recoveryRequired = false,
                    revision = next.revision,
                )
                if (updatedRuntime.runId != nextOpenRun.id.toString() || dao.updateRuntime(updatedRuntime) != 1) {
                    throw FocusPersistenceException()
                }
            } else {
                dao.insertRuntime(
                    runtimeFor(
                        sessionId = next.id,
                        run = nextOpenRun,
                        runStartedElapsedMillis = sample.elapsedMillis,
                        sample = sample,
                        processInstanceId = context.processInstanceId,
                        recoveryRequired = false,
                        revision = next.revision,
                    ),
                )
            }
        }
        return FocusWriteResult.Applied(next)
    }

    private suspend fun runCommand(
        commandId: UUID,
        context: FocusWriteContext,
        requestedSessionId: UUID,
        payload: String,
        createdAt: Instant,
        operation: suspend (generationId: String) -> FocusWriteResult,
    ): FocusWriteResult = storageWrite {
        database.withTransaction {
            val generationId = ensureGeneration().generationId
            if (generationId != context.databaseGeneration.toString()) {
                return@withTransaction FocusWriteResult.Invalid(FocusInvalidReason.STALE_DATABASE_GENERATION)
            }

            val existingReceipt = dao.receipt(commandId.toString(), generationId)
            if (existingReceipt != null) {
                return@withTransaction replay(existingReceipt, payload, generationId)
            }

            val result = operation(generationId)
            if (result !is FocusWriteResult.Unavailable) {
                val resultSessionId = when (result) {
                    is FocusWriteResult.Applied -> result.snapshot?.id ?: result.deletedSessionId ?: requestedSessionId
                    is FocusWriteResult.AlreadyApplied -> result.snapshot?.id ?: result.deletedSessionId ?: requestedSessionId
                    is FocusWriteResult.Conflict -> result.current?.id ?: requestedSessionId
                    is FocusWriteResult.Invalid -> requestedSessionId
                    is FocusWriteResult.RecoveryRequired -> result.current.id
                    is FocusWriteResult.Unavailable -> requestedSessionId
                }
                val invalidReason = (result as? FocusWriteResult.Invalid)?.reason?.name
                dao.insertReceipt(
                    FocusCommandReceiptEntity(
                        commandId = commandId.toString(),
                        databaseGeneration = generationId,
                        payload = payload,
                        resultType = result.receiptType(),
                        resultSessionId = resultSessionId.toString(),
                        resultInvalidReason = invalidReason,
                        createdAtSeconds = createdAt.epochSecond,
                        createdAtNanos = createdAt.nano,
                    ),
                )
                dao.trimReceipts(MAX_COMMAND_RECEIPTS)
            }
            result
        }
    }

    private suspend fun replay(
        receipt: FocusCommandReceiptEntity,
        payload: String,
        generationId: String,
    ): FocusWriteResult {
        if (receipt.payload != payload) {
            return FocusWriteResult.Invalid(FocusInvalidReason.COMMAND_ID_REUSED)
        }
        val id = receipt.resultSessionId
        val snapshot = id?.let { dao.sessionWithRuns(it)?.toDomain() }
        return when (receipt.resultType) {
            RECEIPT_APPLIED,
            RECEIPT_ALREADY_APPLIED,
            -> {
                val tombstone = id?.let { dao.tombstone(it, generationId) }
                when {
                    tombstone != null -> FocusWriteResult.AlreadyApplied(
                        deletedSessionId = UUID.fromString(tombstone.sessionId),
                    )
                    snapshot != null -> FocusWriteResult.AlreadyApplied(snapshot)
                    else -> FocusWriteResult.Conflict(null)
                }
            }
            RECEIPT_CONFLICT -> FocusWriteResult.Conflict(snapshot)
            RECEIPT_INVALID -> FocusWriteResult.Invalid(
                receipt.resultInvalidReason?.let(FocusInvalidReason::valueOf)
                    ?: FocusInvalidReason.INVALID_SNAPSHOT,
            )
            RECEIPT_RECOVERY_REQUIRED -> snapshot?.let { FocusWriteResult.RecoveryRequired(it) }
                ?: FocusWriteResult.Conflict(null)
            else -> FocusWriteResult.Invalid(FocusInvalidReason.INVALID_SNAPSHOT)
        }
    }

    private suspend fun ensureGeneration(): FocusStorageStateEntity {
        val current = dao.storageState()
        if (current != null) return current
        dao.insertStorageStateIfAbsent(
            FocusStorageStateEntity(singletonId = STORAGE_SINGLETON_ID, generationId = UUID.randomUUID().toString()),
        )
        return checkNotNull(dao.storageState()) { "Focus database generation was not initialized" }
    }
}

private const val STORAGE_SINGLETON_ID = 1
private const val RUNTIME_SLOT_ID = 1
private const val MAX_COMMAND_RECEIPTS = 100

private const val RECEIPT_APPLIED = "APPLIED"
private const val RECEIPT_ALREADY_APPLIED = "ALREADY_APPLIED"
private const val RECEIPT_CONFLICT = "CONFLICT"
private const val RECEIPT_INVALID = "INVALID"
private const val RECEIPT_RECOVERY_REQUIRED = "RECOVERY_REQUIRED"

private fun FocusWriteResult.receiptType(): String = when (this) {
    is FocusWriteResult.Applied -> RECEIPT_APPLIED
    is FocusWriteResult.AlreadyApplied -> RECEIPT_ALREADY_APPLIED
    is FocusWriteResult.Conflict -> RECEIPT_CONFLICT
    is FocusWriteResult.Invalid -> RECEIPT_INVALID
    is FocusWriteResult.RecoveryRequired -> RECEIPT_RECOVERY_REQUIRED
    is FocusWriteResult.Unavailable -> error("Unavailable results are not persisted as receipts")
}

private fun runtimeFor(
    sessionId: UUID,
    run: FocusRun,
    runStartedElapsedMillis: Long,
    sample: FocusTimeSample,
    processInstanceId: UUID,
    recoveryRequired: Boolean,
    revision: Long,
) = FocusRuntimeEntity(
    slotId = RUNTIME_SLOT_ID,
    sessionId = sessionId.toString(),
    runId = run.id.toString(),
    runStartedElapsedMillis = runStartedElapsedMillis,
    checkpointElapsedMillis = sample.elapsedMillis,
    checkpointWallSeconds = sample.wall.epochSecond,
    checkpointWallNanos = sample.wall.nano,
    bootId = sample.bootId,
    processInstanceId = processInstanceId.toString(),
    recoveryRequired = recoveryRequired,
    revision = revision,
)

private fun FocusRuntimeEntity.toCheckpoint() = FocusTimingCheckpoint(
    runId = UUID.fromString(runId),
    sample = FocusTimeSample(
        wall = Instant.ofEpochSecond(checkpointWallSeconds, checkpointWallNanos.toLong()),
        elapsedMillis = checkpointElapsedMillis,
        bootId = bootId,
    ),
)

/** Detects an out-of-order sample without hiding a real elapsed-clock regression. */
private fun sampleIsStale(runtime: FocusRuntimeEntity, sample: FocusTimeSample): Boolean {
    if (runtime.bootId == null || runtime.bootId != sample.bootId ||
        sample.elapsedMillis >= runtime.checkpointElapsedMillis
    ) {
        return false
    }

    val elapsedBehind = runtime.checkpointElapsedMillis - sample.elapsedMillis
    val checkpointWall = Instant.ofEpochSecond(runtime.checkpointWallSeconds, runtime.checkpointWallNanos.toLong())
    val wallBehind = try {
        Duration.between(sample.wall, checkpointWall).toMillis()
    } catch (_: ArithmeticException) {
        return false
    }
    val drift = try {
        Math.subtractExact(wallBehind, elapsedBehind)
    } catch (_: ArithmeticException) {
        return false
    }
    return drift in -FOCUS_WALL_DRIFT_TOLERANCE_MILLIS..FOCUS_WALL_DRIFT_TOLERANCE_MILLIS
}

private fun FocusSessionWithRuns.toDomain(): FocusSession = session.toDomain(runs.map(FocusRunEntity::toDomain))

private fun FocusSessionEntity.toDomain(runs: List<FocusRun>): FocusSession = FocusSession(
    id = UUID.fromString(id),
    state = FocusState.valueOf(state),
    revision = revision,
    startedAt = Instant.ofEpochSecond(startedAtSeconds, startedAtNanos.toLong()),
    endedAt = pairedInstant(endedAtSeconds, endedAtNanos),
    createdAt = Instant.ofEpochSecond(createdAtSeconds, createdAtNanos.toLong()),
    updatedAt = Instant.ofEpochSecond(updatedAtSeconds, updatedAtNanos.toLong()),
    terminalReason = terminalReason?.let(FocusTerminalReason::valueOf),
    recoveredOrUncertain = recoveredOrUncertain,
    runs = runs.sortedBy(FocusRun::ordinal),
)

private fun FocusRunEntity.toDomain(): FocusRun = FocusRun(
    id = UUID.fromString(id),
    sessionId = UUID.fromString(sessionId),
    ordinal = ordinal,
    startedAt = Instant.ofEpochSecond(startedAtSeconds, startedAtNanos.toLong()),
    endedAt = pairedInstant(endedAtSeconds, endedAtNanos),
    creditedMillis = creditedMillis,
    rawStartedAt = Instant.ofEpochSecond(rawStartedAtSeconds, rawStartedAtNanos.toLong()),
    rawEndedAt = pairedInstant(rawEndedAtSeconds, rawEndedAtNanos),
)

private fun FocusSession.toEntity(): FocusSessionEntity = FocusSessionEntity(
    id = id.toString(),
    state = state.name,
    revision = revision,
    startedAtSeconds = startedAt.epochSecond,
    startedAtNanos = startedAt.nano,
    endedAtSeconds = endedAt?.epochSecond,
    endedAtNanos = endedAt?.nano,
    createdAtSeconds = createdAt.epochSecond,
    createdAtNanos = createdAt.nano,
    updatedAtSeconds = updatedAt.epochSecond,
    updatedAtNanos = updatedAt.nano,
    terminalReason = terminalReason?.name,
    recoveredOrUncertain = recoveredOrUncertain,
    openSlot = if (isTerminal) null else STORAGE_SINGLETON_ID,
)

private fun FocusRun.toEntity(): FocusRunEntity = FocusRunEntity(
    id = id.toString(),
    sessionId = sessionId.toString(),
    ordinal = ordinal,
    startedAtSeconds = startedAt.epochSecond,
    startedAtNanos = startedAt.nano,
    endedAtSeconds = endedAt?.epochSecond,
    endedAtNanos = endedAt?.nano,
    creditedMillis = creditedMillis,
    rawStartedAtSeconds = rawStartedAt.epochSecond,
    rawStartedAtNanos = rawStartedAt.nano,
    rawEndedAtSeconds = rawEndedAt?.epochSecond,
    rawEndedAtNanos = rawEndedAt?.nano,
    openSessionId = if (isOpen) sessionId.toString() else null,
)

private fun pairedInstant(seconds: Long?, nanos: Int?): Instant? {
    require((seconds == null) == (nanos == null)) { "Stored instant seconds and nanos must be paired" }
    return seconds?.let { Instant.ofEpochSecond(it, checkNotNull(nanos).toLong()) }
}

private fun FocusTimeSample.payload(): String = commandPayload(
    wall.epochSecond.toString(),
    wall.nano.toString(),
    elapsedMillis.toString(),
    bootId.orEmpty(),
)

private fun FocusWriteContext.payload(): String = commandPayload(databaseGeneration.toString())

/** Length-prefix every field so null/empty values and delimiters cannot alias another payload. */
private fun commandPayload(vararg fields: String): String = buildString {
    fields.forEach { field ->
        append(field.length)
        append(':')
        append(field)
        append(';')
    }
}

private suspend fun storageWrite(block: suspend () -> FocusWriteResult): FocusWriteResult = try {
    block()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: SQLiteException) {
    FocusWriteResult.Unavailable(FocusUnavailableReason.STORAGE)
} catch (_: FocusPersistenceException) {
    FocusWriteResult.Unavailable(FocusUnavailableReason.STORAGE)
}

private class FocusPersistenceException : RuntimeException()
