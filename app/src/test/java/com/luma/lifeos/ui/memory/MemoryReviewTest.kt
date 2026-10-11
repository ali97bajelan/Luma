package com.luma.lifeos.ui.memory

import com.luma.lifeos.domain.model.*
import com.luma.lifeos.domain.repository.MemoryRepository
import com.luma.lifeos.domain.time.TimeRange
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MemoryReviewTest {
    private val date = LocalDate.parse("2026-03-08")
    private val zone = ZoneId.of("America/New_York")
    @Test fun weekRangeUsesLocalMondayAndHandlesDstWithoutAddingFixedHours() {
        val range = memoryWeekRange(date, zone)
        assertEquals(Instant.parse("2026-03-02T05:00:00Z"), range.start)
        assertEquals(Instant.parse("2026-03-09T04:00:00Z"), range.endExclusive)
    }
    @Test fun reviewLoadsStoredMarkersAndOriginalContentInStableOrder() = runBlocking {
        val first = record(1)
        val second = record(2)
        val repo = ReadRepository { flowOf(listOf(second, first), emptyList()) }
        val states = observeMemoryReview(repo, date, zone).toList()
        assertEquals(MemoryReviewState.Loading, states[0])
        assertEquals(listOf(first, second), (states[1] as MemoryReviewState.Data).records)
        assertEquals("Original journal", (states[1] as MemoryReviewState.Data).records.first().journal!!.body)
        assertEquals(MemoryReviewState.Empty, states[2])
        assertEquals(Instant.parse("2026-03-02T05:00:00Z"), repo.range!!.start)
    }
    @Test fun failuresAreRetryableAndCancellationPropagates() = runBlocking {
        var fails = true
        val repo = ReadRepository { if (fails) error("Unavailable") else flowOf(emptyList()) }
        assertEquals(MemoryReviewState.Error, observeMemoryReview(repo, date, zone).toList().last())
        fails = false
        assertEquals(MemoryReviewState.Empty, observeMemoryReview(repo, date, zone).toList().last())
        assertTrue(runCatching {
            observeMemoryReview(ReadRepository { flow { throw CancellationException() } }, date, zone).toList()
        }.exceptionOrNull() is CancellationException)
    }
    private fun record(number: Int) = LifeEventRecord(LifeEvent(
        EventId.parse("00000000-0000-0000-0000-${number.toString().padStart(12, '0')}"), EventType.JOURNAL,
        Instant.parse("2026-03-08T08:00:00Z"), Instant.parse("2026-03-08T08:00:00Z")),
        journal = JournalDetails(null, "Original journal"), memory = MemoryDetails(5, "Marker note"))
    private class ReadRepository(private val read: () -> kotlinx.coroutines.flow.Flow<List<LifeEventRecord>>) : MemoryRepository {
        var range: TimeRange? = null
        override fun observe(range: TimeRange): kotlinx.coroutines.flow.Flow<List<LifeEventRecord>> { this.range = range; return read() }
        override suspend fun get(id: EventId): LifeEventRecord? = error("Unused")
        override suspend fun create(id: EventId, input: MemoryInput): EventId = error("Unused")
        override suspend fun update(id: EventId, input: MemoryInput): Unit = error("Unused")
        override suspend fun delete(id: EventId): Unit = error("Unused")
        override suspend fun saveMarker(id: EventId, input: MemoryMarkerInput): Unit = error("Unused")
        override suspend fun removeMarker(id: EventId): Unit = error("Unused")
    }
}
