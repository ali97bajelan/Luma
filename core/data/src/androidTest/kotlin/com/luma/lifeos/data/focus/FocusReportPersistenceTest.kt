package com.luma.lifeos.data.focus

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.domain.focus.FocusAction
import com.luma.lifeos.domain.focus.FocusCommand
import com.luma.lifeos.domain.focus.FocusState
import com.luma.lifeos.domain.focus.FocusTimeSample
import com.luma.lifeos.domain.focus.FocusWriteContext
import com.luma.lifeos.domain.focus.FocusWriteResult
import com.luma.lifeos.domain.time.TimeRange
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FocusReportPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: LumaDatabase
    private lateinit var repository: RoomFocusRepository
    private lateinit var writeContext: FocusWriteContext

    @Before fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, LumaDatabase::class.java).build()
        repository = RoomFocusRepository(database)
        writeContext = FocusWriteContext(repository.currentGeneration(), PROCESS_ID)
    }

    @After fun tearDown() {
        database.close()
    }

    @Test fun reportClipsCrossMidnightAndCrossWeekRunsAndCountsDistinctSessions() = runBlocking {
        val crossingMidnight = finish(
            sessionId = id(1),
            commandId = id(101),
            start = Instant.parse("2026-10-28T23:50:00Z"),
            durationMillis = 30 * MINUTE,
        )
        val oct28 = range("2026-10-28T00:00:00Z", "2026-10-29T00:00:00Z")
        val oct29 = range("2026-10-29T00:00:00Z", "2026-10-30T00:00:00Z")
        assertEquals(10 * MINUTE, repository.observeStatistics(oct28).first().totalMillis)
        assertEquals(1, repository.observeStatistics(oct28).first().contributingSessions)
        assertEquals(20 * MINUTE, repository.observeStatistics(oct29).first().totalMillis)
        assertEquals(1, repository.observeStatistics(oct29).first().contributingSessions)

        finish(
            sessionId = id(2),
            commandId = id(102),
            start = Instant.parse("2026-10-25T23:50:00Z"),
            durationMillis = 30 * MINUTE,
        )
        val previousWeek = range("2026-10-19T00:00:00Z", "2026-10-26T00:00:00Z")
        val currentWeek = range("2026-10-26T00:00:00Z", "2026-11-02T00:00:00Z")
        val currentHistory = repository.history(currentWeek, offset = 0)
        assertEquals(2, currentHistory.total)
        assertEquals(listOf(id(1), id(2)), currentHistory.rows.map { it.session.id })
        assertEquals(listOf(30 * MINUTE, 20 * MINUTE), currentHistory.rows.map { it.rangeContributionMillis })
        val previous = repository.observeStatistics(previousWeek).first()
        val current = repository.observeStatistics(currentWeek).first()
        assertEquals(10 * MINUTE, previous.totalMillis)
        assertEquals(1, previous.contributingSessions)
        assertEquals(50 * MINUTE, current.totalMillis)
        assertEquals(2, current.contributingSessions)
        assertEquals(FocusState.FINISHED, crossingMidnight.state)
    }

    @Test fun rangedHistoryRetainsFullDurationAndDeletionRefreshesTheReport() = runBlocking {
        val session = finish(
            sessionId = id(3),
            commandId = id(103),
            start = Instant.parse("2026-10-28T23:55:00Z"),
            durationMillis = 20 * MINUTE,
        )
        val selectedDay = range("2026-10-29T00:00:00Z", "2026-10-30T00:00:00Z")
        val page = repository.history(selectedDay, offset = 0)

        assertEquals(1, page.total)
        assertEquals(session.creditedMillis, page.rows.single().fullDurationMillis)
        assertEquals(15 * MINUTE, page.rows.single().rangeContributionMillis)
        assertTrue(repository.delete(session.id, session.revision, writeContext) is FocusWriteResult.Applied)
        assertEquals(0, repository.observeStatistics(selectedDay).first().totalMillis)
        assertEquals(0, repository.history(selectedDay, offset = 0).total)
    }

    @Test fun historyUsesStableIdTieBreakAndBoundsPagesToFiftyRows() = runBlocking {
        val start = Instant.parse("2026-10-28T12:00:00Z")
        repeat(51) { index ->
            val number = index + 1L
            finish(
                sessionId = id(number),
                commandId = id(1_000 + number),
                start = start,
                durationMillis = 1_000,
                actionCommandId = id(2_000 + number),
            )
        }

        val first = repository.history(null, offset = 0)
        val second = repository.history(null, offset = 50)
        val orderedIds = first.rows.map { it.session.id } + second.rows.map { it.session.id }

        assertEquals(51, first.total)
        assertEquals(50, first.rows.size)
        assertEquals(1, second.rows.size)
        assertEquals(orderedIds.sortedByDescending(UUID::toString), orderedIds)
        assertEquals(51, orderedIds.distinct().size)
    }

    private suspend fun finish(
        sessionId: UUID,
        commandId: UUID,
        start: Instant,
        durationMillis: Long,
        actionCommandId: UUID = UUID(commandId.mostSignificantBits xor 1L, commandId.leastSignificantBits),
    ): com.luma.lifeos.domain.focus.FocusSession {
        val running = (repository.start(
            commandId,
            sessionId,
            FocusTimeSample(start, START_ELAPSED, "report-boot"),
            writeContext,
        ) as FocusWriteResult.Applied).snapshot!!
        val sample = FocusTimeSample(
            wall = start.plusMillis(durationMillis),
            elapsedMillis = START_ELAPSED + durationMillis,
            bootId = "report-boot",
        )
        val result = repository.apply(
            FocusCommand(
                commandId = actionCommandId,
                sessionId = sessionId,
                expectedRevision = running.revision,
                action = FocusAction.STOP,
                sample = sample,
                context = writeContext,
            ),
        )
        assertTrue(result is FocusWriteResult.Applied)
        return (result as FocusWriteResult.Applied).snapshot!!
    }

    private fun id(number: Long): UUID = UUID(0L, number)

    private fun range(start: String, end: String) = TimeRange(Instant.parse(start), Instant.parse(end))

    private companion object {
        const val MINUTE = 60_000L
        const val START_ELAPSED = 500_000L
        val PROCESS_ID = UUID.fromString("10000000-0000-0000-0000-000000000001")
    }
}
