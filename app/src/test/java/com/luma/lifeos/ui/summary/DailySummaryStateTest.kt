package com.luma.lifeos.ui.summary

import com.luma.lifeos.domain.repository.DailySummaryRepository
import com.luma.lifeos.domain.summary.*
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class DailySummaryStateTest {
    private val day = LocalDate.parse("2026-10-11")
    private val zone = ZoneOffset.UTC
    private val empty = DailySummary(day, zone, 0, 0, 0)
    @Test fun loadingAndRealDataRemainDistinctFromUnavailableSignals() = runBlocking {
        val data = empty.copy(records = 1, mood = MoodSummary(ScaleTotal(1, 4)))
        val states = observeDailySummary(Repository { flowOf(empty, data) }, day, zone).toList()
        assertEquals(DailySummaryState.Loading, states[0])
        assertEquals(empty, (states[1] as DailySummaryState.Data).summary)
        assertNull((states[1] as DailySummaryState.Data).summary.usage)
        assertEquals(4L, (states[2] as DailySummaryState.Data).summary.mood!!.score.sum)
    }
    @Test fun failedLoadsShowErrorAndNewCollectionsCanRetry() = runBlocking {
        var failed = true
        val repository = Repository { if (failed) error("private database details") else flowOf(empty) }
        assertEquals(listOf(DailySummaryState.Loading, DailySummaryState.Error), observeDailySummary(repository, day, zone).toList())
        failed = false
        assertEquals(DailySummaryState.Data(empty), observeDailySummary(repository, day, zone).toList().last())
    }
    @Test fun wrongDateOrZoneCannotAppearUnderTheSelectedDay() = runBlocking {
        listOf(empty.copy(date = day.minusDays(1)), empty.copy(zone = ZoneId.of("Asia/Tehran"))).forEach { wrong ->
            assertEquals(DailySummaryState.Error, observeDailySummary(Repository { flowOf(wrong) }, day, zone).toList().last())
        }
    }
    @Test fun lifecycleCancellationPropagates() = runBlocking {
        try {
            observeDailySummary(Repository { flow { throw CancellationException("Stopped") } }, day, zone).toList()
            fail("Cancellation swallowed")
        } catch (_: CancellationException) { }
    }
    private class Repository(val read: () -> Flow<DailySummary>) : DailySummaryRepository {
        override fun observe(date: LocalDate, zone: ZoneId) = read()
        override suspend fun get(date: LocalDate, zone: ZoneId): DailySummary = error("Not used")
        override suspend fun recalculate(date: LocalDate, zone: ZoneId): DailySummary = error("Not used")
    }
}
