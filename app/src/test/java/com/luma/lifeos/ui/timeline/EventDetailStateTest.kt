package com.luma.lifeos.ui.timeline

import com.luma.lifeos.domain.model.*
import com.luma.lifeos.domain.repository.TimelineRepository
import com.luma.lifeos.domain.time.TimeRange
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class EventDetailStateTest {
    private val time = Instant.parse("2026-10-11T08:00:00Z")
    private val record = LifeEventRecord(LifeEvent(EventId.new(), EventType.ACTIVITY, time, time))
    @Test fun storedEventIsLoadedAndADeletedEventIsDistinctFromAnError() = runTest {
        assertEquals(listOf(EventDetailState.Loading, EventDetailState.Data(record)), observeEventDetail(Reader(record), record.event.id).toList())
        assertEquals(listOf(EventDetailState.Loading, EventDetailState.Missing), observeEventDetail(Reader(null), record.event.id).toList())
    }
    @Test fun readFailureHasAnErrorStateAndFreshRetryCanLoadData() = runTest {
        val reader = Reader(record).apply { failure = IllegalStateException("Unavailable") }
        assertEquals(listOf(EventDetailState.Loading, EventDetailState.Error), observeEventDetail(reader, record.event.id).toList())
        reader.failure = null
        assertEquals(EventDetailState.Data(record), observeEventDetail(reader, record.event.id).toList().last())
    }
    @Test fun cancellationNeverMasqueradesAsAMissingOrFailedRecord() = runTest {
        val reader = Reader(record).apply { failure = CancellationException() }
        try { observeEventDetail(reader, record.event.id).toList(); fail("Expected cancellation") } catch (_: CancellationException) { }
    }
    private class Reader(private val record: LifeEventRecord?) : TimelineRepository {
        var failure: Exception? = null
        override fun observeChanges() = flowOf(Unit)
        override suspend fun page(range: TimeRange, offset: Int, limit: Int): TimelinePage = error("Not used by detail")
        override suspend fun get(id: EventId): LifeEventRecord? { failure?.let { throw it }; return record }
    }
}
