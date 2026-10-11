package com.luma.lifeos.ui.today

import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.EventSource
import com.luma.lifeos.domain.model.EventType
import com.luma.lifeos.domain.model.LifeEvent
import com.luma.lifeos.domain.model.LifeEventRecord
import com.luma.lifeos.domain.model.MoodDetails
import com.luma.lifeos.domain.model.MemoryDetails
import com.luma.lifeos.domain.model.JournalDetails
import com.luma.lifeos.domain.model.Tag
import com.luma.lifeos.domain.model.Emotion
import java.util.UUID
import com.luma.lifeos.domain.model.SourceIdentity
import com.luma.lifeos.domain.repository.LifeEventRepository
import com.luma.lifeos.domain.time.TimeRange
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TodayStateTest {
    private val date = LocalDate.parse("2026-03-08")
    private val zone = ZoneId.of("America/New_York")

    @Test fun startsLoadingThenShowsEmptyForA23HourLocalDay() = runBlocking {
        val repository = ReadRepository { flowOf(emptyList()) }
        assertEquals(listOf(TodayUiState.Loading, TodayUiState.Empty), observeToday(repository, date, zone).toList())
        assertEquals(Instant.parse("2026-03-08T05:00:00Z"), repository.range!!.start)
        assertEquals(Instant.parse("2026-03-09T04:00:00Z"), repository.range!!.endExclusive)
    }

    @Test fun ordersStoredEventsAndCountsOnlyAvailableTypes() = runBlocking {
        val early = event(1, EventType.MOOD, "2026-03-08T08:00:00Z")
        val tied = event(2, EventType.JOURNAL, "2026-03-08T08:00:00Z")
        val late = event(3, EventType.MOOD, "2026-03-08T10:00:00Z")
        val states = observeToday(ReadRepository { flowOf(listOf(late, tied, early)) }, date, zone).toList()
        val data = states.last() as TodayUiState.Data
        assertEquals(listOf(early, tied, late), data.events)
        assertEquals(mapOf(EventType.MOOD to 2, EventType.JOURNAL to 1), data.counts)
    }

    @Test fun committedUpdatesReplaceTheSummaryAndRows() = runBlocking {
        val journal = event(1, EventType.JOURNAL, "2026-03-08T08:00:00Z")
        val repository = ReadRepository { flow { emit(emptyList()); emit(listOf(journal)); emit(emptyList()) } }
        val states = observeToday(repository, date, zone).toList()
        assertEquals(TodayUiState.Empty, states[1])
        assertEquals(listOf(journal), (states[2] as TodayUiState.Data).events)
        assertEquals(TodayUiState.Empty, states[3])
    }

    @Test fun queryFailureShowsErrorAndAFreshCollectionCanRetry() = runBlocking {
        var fails = true
        val repository = ReadRepository {
            if (fails) error("private database details") else flowOf(emptyList())
        }
        assertEquals(listOf(TodayUiState.Loading, TodayUiState.Error), observeToday(repository, date, zone).toList())
        fails = false
        assertEquals(TodayUiState.Empty, observeToday(repository, date, zone).toList().last())
    }

    @Test fun lifecycleCancellationIsNotShownAsAnError() = runBlocking {
        try {
            observeToday(ReadRepository { flow { throw CancellationException("stopped") } }, date, zone).toList()
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }

    @Test fun pickerCalendarDateIsIndependentOfTheDeviceZone() {
        val milliseconds = 1772928000000L // 2026-03-08 at UTC midnight
        assertEquals(date, pickerDate(milliseconds))
        assertEquals(milliseconds, pickerMillis(date))
    }

    @Test fun typedMoodValuesArriveWithTheEventInTheSameFeedState() = runBlocking {
        val moodEvent = event(1, EventType.MOOD, "2026-03-08T08:00:00Z")
        val details = MoodDetails(4, listOf(Emotion(UUID.randomUUID(), "Calm")), energy = 2, note = "Quiet")
        val repository = ReadRepository { flowOf(listOf(moodEvent)) }.apply {
            records = flowOf(listOf(LifeEventRecord(moodEvent, details)))
        }
        val state = observeToday(repository, date, zone).toList().last() as TodayUiState.Data
        assertEquals(listOf(moodEvent), state.events)
        assertEquals(4, state.moods[moodEvent.id]!!.score)
        assertEquals("Calm", state.moods[moodEvent.id]!!.emotions.single().name)
        assertEquals("Quiet", state.moods[moodEvent.id]!!.note)
    }

    @Test fun typedJournalBodyAndTagsUpdateTogetherWithTheEnvelope() = runBlocking {
        val journal = event(1, EventType.JOURNAL, "2026-03-08T08:00:00Z")
        val tag = Tag(UUID.fromString("00000000-0000-0000-0000-000000000011"), "Work")
        val repository = ReadRepository { flowOf(listOf(journal)) }.apply {
            records = flowOf(listOf(LifeEventRecord(journal, journal = JournalDetails("Title", "Body", listOf(tag)))),
                listOf(LifeEventRecord(journal.copy(title = "Edited"), journal = JournalDetails("Edited", "New text"))))
        }
        val states = observeToday(repository, date, zone).toList()
        val first = states[1] as TodayUiState.Data
        assertEquals("Body", first.journals[journal.id]!!.body)
        assertEquals(listOf(tag), first.journals[journal.id]!!.tags)
        val updated = states[2] as TodayUiState.Data
        assertEquals("Edited", updated.events.single().title)
        assertEquals("New text", updated.journals[journal.id]!!.body)
        assertTrue(updated.journals[journal.id]!!.tags.isEmpty())
    }

    @Test fun promotionChangesMemoryStatusWithoutDuplicatingTheOriginalEvent() = runBlocking {
        val journal = event(1, EventType.JOURNAL, "2026-03-08T08:00:00Z")
        val original = LifeEventRecord(journal, journal = JournalDetails(null, "Keep this body"))
        val repository = ReadRepository { flowOf(listOf(journal)) }.apply {
            records = flowOf(listOf(original), listOf(original.copy(memory = MemoryDetails(5, "Notable"))), listOf(original))
        }
        val states = observeToday(repository, date, zone).toList()
        val promoted = states[2] as TodayUiState.Data
        assertEquals(listOf(journal), promoted.events)
        assertEquals(mapOf(EventType.JOURNAL to 1), promoted.counts)
        assertEquals(5, promoted.memories[journal.id]!!.importance)
        assertEquals("Keep this body", promoted.journals[journal.id]!!.body)
        val unmarked = states[3] as TodayUiState.Data
        assertTrue(unmarked.memories.isEmpty())
        assertEquals(listOf(journal), unmarked.events)
    }

    private fun event(number: Int, type: EventType, instant: String) = LifeEvent(
        id = EventId.parse("00000000-0000-0000-0000-${number.toString().padStart(12, '0')}"),
        type = type,
        occurredAt = Instant.parse(instant),
        createdAt = Instant.parse(instant),
        source = EventSource.MANUAL,
    )

    private class ReadRepository(val read: () -> Flow<List<LifeEvent>>) : LifeEventRepository {
        var range: TimeRange? = null
        var records: Flow<List<LifeEventRecord>>? = null
        override fun observeRecords(range: TimeRange): Flow<List<LifeEventRecord>> =
            records ?: super.observeRecords(range)
        override fun observe(range: TimeRange): Flow<List<LifeEvent>> {
            this.range = range
            return read()
        }
        override suspend fun get(id: EventId): LifeEvent? = error("Not used")
        override suspend fun findBySource(identity: SourceIdentity): LifeEvent? = error("Not used")
        override suspend fun upsert(event: LifeEvent): EventId = error("Not used")
        override suspend fun delete(id: EventId) = error("Not used")
    }
}
