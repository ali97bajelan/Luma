package com.luma.lifeos.data.focus

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.domain.focus.FocusAction
import com.luma.lifeos.domain.focus.FocusCommand
import com.luma.lifeos.domain.focus.FocusInvalidReason
import com.luma.lifeos.domain.focus.FocusRecoveryChoice
import com.luma.lifeos.domain.focus.FocusState
import com.luma.lifeos.domain.focus.FocusTerminalReason
import com.luma.lifeos.domain.focus.FocusTimeSample
import com.luma.lifeos.domain.focus.FocusWriteContext
import com.luma.lifeos.domain.focus.FocusWriteResult
import com.luma.lifeos.domain.time.TimeRange
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FocusPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databases = mutableListOf<LumaDatabase>()
    private val databaseFiles = mutableListOf<String>()
    private val processId = UUID.fromString("10000000-0000-0000-0000-000000000001")
    private lateinit var database: LumaDatabase
    private lateinit var repository: RoomFocusRepository

    @Before fun setUp() {
        database = inMemoryDatabase()
        repository = RoomFocusRepository(database)
    }

    @After fun tearDown() {
        databases.forEach(LumaDatabase::close)
        databaseFiles.forEach(context::deleteDatabase)
    }

    @Test fun pauseResumeStopPersistsExactActiveTimeAndCommandReceipts() = runBlocking {
        val file = "focus-persistence-${UUID.randomUUID()}.db"
        close(database)
        database = persistentDatabase(file)
        repository = RoomFocusRepository(database)
        val generation = repository.currentGeneration()
        val writeContext = FocusWriteContext(generation, processId)
        val sessionId = id(1)
        val startCommandId = id(101)
        val origin = sample(0)

        val started = repository.start(startCommandId, sessionId, origin, writeContext).snapshot()
        assertEquals(
            FocusWriteResult.AlreadyApplied(started),
            repository.start(startCommandId, sessionId, origin, writeContext),
        )
        assertEquals(
            FocusInvalidReason.COMMAND_ID_REUSED,
            (repository.start(startCommandId, sessionId, sample(1), writeContext) as FocusWriteResult.Invalid).reason,
        )

        val paused = repository.apply(
            command(sessionId, 0, FocusAction.PAUSE, sample(20 * MINUTE), writeContext, id(102)),
        ).snapshot()
        assertEquals(FocusState.PAUSED, paused.state)
        assertEquals(20 * MINUTE, paused.creditedMillis)

        val resumed = repository.apply(
            command(sessionId, 1, FocusAction.RESUME, sample(30 * MINUTE), writeContext, id(103)),
        ).snapshot()
        assertEquals(FocusState.RUNNING, resumed.state)
        assertEquals(2, resumed.runs.size)

        val stopped = repository.apply(
            command(sessionId, 2, FocusAction.STOP, sample(35 * MINUTE), writeContext, id(104)),
        ).snapshot()
        assertEquals(FocusState.FINISHED, stopped.state)
        assertEquals(25 * MINUTE, stopped.creditedMillis)
        assertEquals(35 * MINUTE, stopped.endedAt!!.toEpochMilli() - ORIGIN.toEpochMilli())
        assertEquals(listOf(20 * MINUTE, 5 * MINUTE), stopped.runs.map { it.creditedMillis })
        assertNull(database.focus().runtime())
        assertEquals(4, scalar("SELECT COUNT(*) FROM focus_command_receipt"))

        close(database)
        database = persistentDatabase(file)
        repository = RoomFocusRepository(database)
        assertEquals(generation, repository.currentGeneration())
        assertNull(repository.observeOpen().first())
        val saved = repository.history(null, 0).rows.single().session
        assertEquals(stopped, saved)
        assertEquals(2, scalar("SELECT COUNT(*) FROM focus_run"))
    }

    @Test fun twoDatabaseClientsSerializeSingletonStartAndDuplicateActions() = runBlocking {
        close(database)
        val file = "focus-concurrency-${UUID.randomUUID()}.db"
        val firstDb = persistentDatabase(file)
        val secondDb = persistentDatabase(file)
        val first = RoomFocusRepository(firstDb)
        val second = RoomFocusRepository(secondDb)
        val generation = first.currentGeneration()
        assertEquals(generation, second.currentGeneration())
        val writeContext = FocusWriteContext(generation, processId)
        val sessionId = id(2)
        val startId = id(201)

        val starts = coroutineScope {
            listOf(
                async(Dispatchers.IO) { first.start(startId, sessionId, sample(0), writeContext) },
                async(Dispatchers.IO) { second.start(startId, sessionId, sample(0), writeContext) },
            ).map { it.await() }
        }
        assertEquals(1, starts.count { it is FocusWriteResult.Applied })
        assertEquals(1, starts.count { it is FocusWriteResult.AlreadyApplied })
        assertEquals(1, scalar(firstDb, "SELECT COUNT(*) FROM focus_session WHERE open_slot = 1"))
        assertEquals(1, scalar(secondDb, "SELECT COUNT(*) FROM focus_runtime"))

        val competingStart = second.start(id(202), id(3), sample(0), writeContext)
        assertTrue(competingStart is FocusWriteResult.Conflict)
        val pause = command(sessionId, 0, FocusAction.PAUSE, sample(1_000), writeContext, id(203))
        val pauses = coroutineScope {
            listOf(
                async(Dispatchers.IO) { first.apply(pause) },
                async(Dispatchers.IO) { second.apply(pause) },
            ).map { it.await() }
        }
        assertEquals(1, pauses.count { it is FocusWriteResult.Applied })
        assertEquals(1, pauses.count { it is FocusWriteResult.AlreadyApplied })
        val current = first.observeOpen().first()!!
        assertEquals(FocusState.PAUSED, current.state)
        assertEquals(1, current.revision)
        assertEquals(1, current.runs.size)
        assertNull(firstDb.focus().runtime())
    }

    @Test fun checkpointsKeepActionRevisionAndRejectOlderSamplesAndOldGenerationWrites() = runBlocking {
        val generation = repository.currentGeneration()
        val writeContext = FocusWriteContext(generation, processId)
        val sessionId = id(4)
        val started = repository.start(id(401), sessionId, sample(0), writeContext).snapshot()

        val checkpointed = repository.checkpoint(sessionId, 0, sample(20 * MINUTE), writeContext).snapshot()
        assertEquals(0, checkpointed.revision)
        assertEquals(20 * MINUTE, checkpointed.creditedMillis)
        assertEquals(
            FocusWriteResult.AlreadyApplied(checkpointed),
            repository.checkpoint(sessionId, 0, sample(20 * MINUTE), writeContext),
        )
        assertTrue(repository.checkpoint(sessionId, 0, sample(15 * MINUTE), writeContext) is FocusWriteResult.Conflict)

        val newGeneration = UUID.fromString("20000000-0000-0000-0000-000000000001")
        assertEquals(1, database.focus().updateGeneration(newGeneration.toString()))
        val staleWrite = repository.checkpoint(sessionId, 0, sample(21 * MINUTE), writeContext)
        assertEquals(FocusInvalidReason.STALE_DATABASE_GENERATION, (staleWrite as FocusWriteResult.Invalid).reason)
        assertEquals(started.id, database.focus().openSession()!!.session.id.let(UUID::fromString))
        assertEquals(20 * MINUTE, database.focus().openSession()!!.runs.single().creditedMillis)
    }

    @Test fun processRecoveryContinueCreditsGapOnceAndEndChoiceUsesCheckpoint() = runBlocking {
        val file = "focus-recovery-${UUID.randomUUID()}.db"
        close(database)
        database = persistentDatabase(file)
        repository = RoomFocusRepository(database)
        val generation = repository.currentGeneration()
        val oldContext = FocusWriteContext(generation, processId)
        val sessionId = id(5)
        repository.start(id(501), sessionId, sample(0), oldContext)
        repository.checkpoint(sessionId, 0, sample(20 * MINUTE), oldContext)

        close(database)
        database = persistentDatabase(file)
        repository = RoomFocusRepository(database)
        val reopenedGeneration = repository.currentGeneration()
        val newProcessId = id(502)
        val newContext = FocusWriteContext(reopenedGeneration, newProcessId)
        val recovery = repository.reconcile(sample(25 * MINUTE), newContext)
        assertTrue(recovery is FocusWriteResult.RecoveryRequired)
        val continued = repository.resolveRecovery(
            sessionId, 0, FocusRecoveryChoice.CONTINUE_ELAPSED, sample(30 * MINUTE), newContext,
        ).snapshot()
        assertEquals(30 * MINUTE, continued.creditedMillis)
        assertEquals(1, continued.revision)
        assertTrue(continued.recoveredOrUncertain)
        assertEquals(
            FocusWriteResult.Conflict(continued),
            repository.resolveRecovery(
                sessionId, 0, FocusRecoveryChoice.CONTINUE_ELAPSED, sample(30 * MINUTE), newContext,
            ),
        )
        val stopped = repository.apply(
            command(sessionId, 1, FocusAction.STOP, sample(35 * MINUTE), newContext, id(503)),
        ).snapshot()
        assertEquals(35 * MINUTE, stopped.creditedMillis)

        val secondId = id(6)
        val secondCommandId = id(601)
        repository.start(secondCommandId, secondId, sample(40 * MINUTE), newContext)
        repository.checkpoint(secondId, 0, sample(60 * MINUTE), newContext)
        val endContext = FocusWriteContext(reopenedGeneration, id(602))
        assertTrue(repository.reconcile(sample(65 * MINUTE), endContext) is FocusWriteResult.RecoveryRequired)
        val ended = repository.resolveRecovery(
            secondId, 0, FocusRecoveryChoice.END_AT_CHECKPOINT, sample(65 * MINUTE), endContext,
        ).snapshot()
        assertEquals(FocusState.INTERRUPTED, ended.state)
        assertEquals(FocusTerminalReason.PROCESS_RESTART_GAP, ended.terminalReason)
        assertEquals(20 * MINUTE, ended.creditedMillis)
        assertEquals(60 * MINUTE, ended.endedAt!!.toEpochMilli() - ORIGIN.toEpochMilli())
        assertNull(database.focus().runtime())
    }

    @Test fun changedBootInterruptsAtLastCheckpointAndRetainsExactSubMinuteCredit() = runBlocking {
        val generation = repository.currentGeneration()
        val writeContext = FocusWriteContext(generation, processId)
        val sessionId = id(7)
        repository.start(id(701), sessionId, sample(0), writeContext)
        repository.checkpoint(sessionId, 0, sample(59_999), writeContext)

        val afterBoot = FocusTimeSample(
            wall = ORIGIN.plusMillis(120_000),
            elapsedMillis = 500,
            bootId = "B2",
        )
        val interrupted = repository.reconcile(afterBoot, writeContext).snapshot()
        assertEquals(FocusState.INTERRUPTED, interrupted.state)
        assertEquals(FocusTerminalReason.BOOT_CHANGED, interrupted.terminalReason)
        assertEquals(59_999, interrupted.creditedMillis)
        assertEquals(59_999, interrupted.runs.single().endedAt!!.toEpochMilli() - ORIGIN.toEpochMilli())
        assertNull(database.focus().runtime())

        val stats = repository.observeStatistics(TimeRange(ORIGIN, ORIGIN.plusSeconds(60))).first()
        assertEquals(59_999, stats.totalMillis)
        assertEquals(1, stats.contributingSessions)
        assertEquals(1, stats.interruptedContributors)
    }

    @Test fun historyPagesAreStableAndDeletionTombstoneOutlivesEvictedStartReceipt() = runBlocking {
        val generation = repository.currentGeneration()
        val writeContext = FocusWriteContext(generation, processId)
        for (index in 1L..51L) {
            val sessionId = id(1_000 + index)
            val startId = id(2_000 + index)
            repository.start(startId, sessionId, sample(0), writeContext)
            val duration: Long = if (index == 1L) 0 else 1_000
            repository.apply(
                command(sessionId, 0, FocusAction.STOP, sample(duration), writeContext, id(3_000 + index)),
            )
        }
        val deletedId = id(8_001)
        val deletedStartCommand = id(8_002)
        repository.start(deletedStartCommand, deletedId, sample(2_000), writeContext)
        repository.apply(command(deletedId, 0, FocusAction.STOP, sample(2_000), writeContext, id(8_003)))

        val firstPage = repository.history(null, 0)
        val secondPage = repository.history(null, 50)
        assertEquals(52, firstPage.total)
        assertEquals(50, firstPage.rows.size)
        assertEquals(2, secondPage.rows.size)
        val ids = firstPage.rows.map { it.session.id } + secondPage.rows.map { it.session.id }
        assertEquals(ids.sortedByDescending { it.toString() }, ids)
        assertEquals(50, repository.history(null, 0).rows.size)

        val range = TimeRange(ORIGIN, ORIGIN.plusMillis(500))
        val statistics = repository.observeStatistics(range).first()
        assertEquals(25_000, statistics.totalMillis)
        assertEquals(50, statistics.contributingSessions)
        val rangedHistory = repository.history(range, 0)
        assertEquals(50, rangedHistory.total)
        assertTrue(rangedHistory.rows.all { it.fullDurationMillis == 1_000L && it.rangeContributionMillis == 500L })

        val deleted = firstPage.rows.single { it.session.id == deletedId }.session
        assertTrue(repository.delete(deletedId, deleted.revision, writeContext) is FocusWriteResult.Applied)
        assertEquals(0, scalar("SELECT COUNT(*) FROM focus_run WHERE session_id = '${deletedId}'"))
        assertEquals(1, scalar("SELECT COUNT(*) FROM focus_deleted_session WHERE session_id = '${deletedId}'"))
        val startReceipt = database.focus().receipt(deletedStartCommand.toString(), generation.toString())
        assertTrue(startReceipt != null)

        repeat(101) { index ->
            repository.apply(
                command(
                    id(50_000 + index.toLong()), 0, FocusAction.STOP, sample(2_000), writeContext,
                    id(60_000 + index.toLong()),
                ),
            )
        }
        assertEquals(100, scalar("SELECT COUNT(*) FROM focus_command_receipt"))
        assertNull(database.focus().receipt(deletedStartCommand.toString(), generation.toString()))
        assertTrue(
            repository.start(id(70_001), deletedId, sample(0), writeContext) is FocusWriteResult.Invalid,
        )
        assertEquals(50, repository.history(range, 0).total)
    }

    @Test fun pausedSessionCannotBeDeletedAndStaysTheSingleOpenSession() = runBlocking {
        val generation = repository.currentGeneration()
        val writeContext = FocusWriteContext(generation, processId)
        val sessionId = id(8)
        val paused = repository.start(id(801), sessionId, sample(0), writeContext).snapshot()
            .let {
                repository.apply(command(sessionId, it.revision, FocusAction.PAUSE, sample(1_000), writeContext, id(802)))
                    .snapshot()
            }
        val deletion = repository.delete(sessionId, paused.revision, writeContext) as FocusWriteResult.Invalid
        assertEquals(FocusInvalidReason.INVALID_TRANSITION, deletion.reason)
        assertEquals(sessionId, repository.observeOpen().first()!!.id)
        assertEquals(0, scalar("SELECT COUNT(*) FROM focus_runtime"))
        assertEquals(1, scalar("SELECT COUNT(*) FROM focus_session WHERE open_slot = 1"))
    }

    private fun inMemoryDatabase(): LumaDatabase = Room.inMemoryDatabaseBuilder(context, LumaDatabase::class.java)
        .build()
        .also(databases::add)

    private fun persistentDatabase(fileName: String): LumaDatabase {
        databaseFiles += fileName
        return LumaDatabase.open(context, fileName).also(databases::add)
    }

    private fun close(value: LumaDatabase) {
        value.close()
        databases.remove(value)
    }

    private suspend fun scalar(sql: String): Int = scalar(database, sql)

    private suspend fun scalar(value: LumaDatabase, sql: String): Int =
        value.openHelper.readableDatabase.query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun sample(elapsedFromOriginMillis: Long) = FocusTimeSample(
        wall = ORIGIN.plusMillis(elapsedFromOriginMillis),
        elapsedMillis = ELAPSED_ORIGIN + elapsedFromOriginMillis,
        bootId = "B1",
    )

    private fun command(
        sessionId: UUID,
        revision: Long,
        action: FocusAction,
        sample: FocusTimeSample,
        context: FocusWriteContext,
        commandId: UUID,
    ) = FocusCommand(commandId, sessionId, revision, action, sample, context)

    private fun id(number: Long): UUID = UUID.fromString(
        "00000000-0000-0000-0000-${number.toString().padStart(12, '0')}",
    )

    private fun FocusWriteResult.snapshot() = when (this) {
        is FocusWriteResult.Applied -> requireNotNull(snapshot)
        is FocusWriteResult.AlreadyApplied -> requireNotNull(snapshot)
        else -> error("Expected a saved focus snapshot; got $this")
    }

    private companion object {
        const val MINUTE = 60_000L
        const val ELAPSED_ORIGIN = 100_000L
        val ORIGIN: Instant = Instant.parse("2026-10-28T09:00:00Z")
    }
}
