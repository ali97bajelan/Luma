package com.luma.lifeos.ui.mood

import androidx.lifecycle.SavedStateHandle
import com.luma.lifeos.domain.model.*
import com.luma.lifeos.domain.repository.MoodRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MoodEditorTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = Clock.fixed(Instant.parse("2026-10-11T08:00:00Z"), ZoneOffset.UTC)
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun oneScoreIsEnoughToCaptureAndRapidRepeatedSaveCreatesOneRecord() = runTest(dispatcher) {
        val repo = TestMoodRepository()
        val editor = MoodEditorViewModel(repo, null, SavedStateHandle(), clock)
        advanceUntilIdle()
        editor.setScore(4)
        editor.save(); editor.save()
        advanceUntilIdle()
        assertEquals(1, repo.records.size)
        assertEquals(1, repo.creates)
        val mood = repo.records.values.single().mood!!
        assertEquals(4, mood.score)
        assertNull(mood.energy)
        assertNull(mood.stress)
        assertTrue(editor.state.value.completed)
    }

    @Test fun errorRetainsOptionalValuesAndRetryKeepsTheSameCaptureId() = runTest(dispatcher) {
        val repo = TestMoodRepository().apply { failAfterCreate = true }
        val editor = MoodEditorViewModel(repo, null, SavedStateHandle(), clock)
        advanceUntilIdle()
        editor.setScore(2); editor.setEnergy(5); editor.setStress(1); editor.setNote("A note")
        repo.emotions.forEach { editor.toggleEmotion(it.id) }
        editor.save(); advanceUntilIdle()
        assertEquals(MoodEditorError.SAVE, editor.state.value.error)
        assertEquals("A note", editor.state.value.draft.note)
        repo.failAfterCreate = false
        editor.save(); advanceUntilIdle()
        assertEquals(1, repo.records.size)
        val saved = repo.records.values.single()
        assertEquals(2, saved.mood!!.emotions.size)
        assertEquals(5, saved.mood!!.energy)
        assertEquals(1, saved.mood!!.stress)
        assertEquals(clock.instant(), saved.event.occurredAt)
    }

    @Test fun restoredDraftKeepsChangesWhenTheStoredRecordIsLoadedAgain() = runTest(dispatcher) {
        val repo = TestMoodRepository()
        val id = EventId.new()
        repo.create(id, MoodInput(3, clock.instant()))
        val handle = SavedStateHandle()
        val editor = MoodEditorViewModel(repo, id, handle, clock)
        advanceUntilIdle()
        editor.setScore(5); editor.setNote("Unsaved")
        val restored = MoodEditorViewModel(repo, id, handle, clock)
        advanceUntilIdle()
        assertEquals(5, restored.state.value.draft.score)
        assertEquals("Unsaved", restored.state.value.draft.note)
        assertTrue(restored.state.value.hasChanges)
        restored.save(); advanceUntilIdle()
        assertEquals("Unsaved", repo.records[id]!!.mood!!.note)
        assertEquals(1, repo.records.size)
    }

    @Test fun missingRecordCannotBeSavedOrRecreatedByEditing() = runTest(dispatcher) {
        val repo = TestMoodRepository()
        val editor = MoodEditorViewModel(repo, EventId.new(), SavedStateHandle(), clock)
        advanceUntilIdle()
        assertEquals(MoodEditorError.LOAD, editor.state.value.error)
        editor.setScore(4); editor.save(); advanceUntilIdle()
        assertTrue(repo.records.isEmpty())
    }

    @Test fun deletingAnExistingMoodCompletesOnlyAfterTheWrite() = runTest(dispatcher) {
        val repo = TestMoodRepository()
        val id = EventId.new()
        repo.create(id, MoodInput(1, clock.instant()))
        val editor = MoodEditorViewModel(repo, id, SavedStateHandle(), clock)
        advanceUntilIdle()
        editor.delete()
        assertFalse(editor.state.value.completed)
        advanceUntilIdle()
        assertTrue(repo.records.isEmpty())
        assertTrue(editor.state.value.completed)
    }

    @Test fun retryingALoadErrorEnablesCaptureWithoutLosingADraft() = runTest(dispatcher) {
        val repo = TestMoodRepository().apply { failLoading = true }
        val handle = SavedStateHandle()
        val editor = MoodEditorViewModel(repo, null, handle, clock)
        advanceUntilIdle()
        assertFalse(editor.state.value.ready)
        assertEquals(MoodEditorError.LOAD, editor.state.value.error)
        repo.failLoading = false
        editor.load(); advanceUntilIdle()
        assertTrue(editor.state.value.ready)
        editor.setScore(1)
        assertTrue(editor.state.value.hasChanges)
    }

    @Test fun nonexistentTimeKeepsThePreviousTimestampAndShowsAnError() = runTest(dispatcher) {
        val editor = MoodEditorViewModel(TestMoodRepository(), null, SavedStateHandle(), clock)
        advanceUntilIdle()
        assertFalse(editor.setLocalTime(LocalDateTime.parse("2026-03-08T02:30:00"), ZoneId.of("America/New_York")))
        assertEquals(clock.millis(), editor.state.value.draft.occurredAtMillis)
        assertEquals(MoodEditorError.TIME, editor.state.value.error)
    }

    @Test fun importedMoodCannotBeModifiedByTheEditor() = runTest(dispatcher) {
        val repo = TestMoodRepository()
        val id = EventId.new()
        repo.records[id] = LifeEventRecord(LifeEvent(id, EventType.MOOD, clock.instant(), clock.instant(),
            source = EventSource.HEALTH_CONNECT, sourceReference = "upstream"), MoodDetails(3))
        val editor = MoodEditorViewModel(repo, id, SavedStateHandle(), clock)
        advanceUntilIdle()
        assertFalse(editor.state.value.ready)
        editor.save(); editor.delete(); advanceUntilIdle()
        assertEquals(EventSource.HEALTH_CONNECT, repo.records[id]!!.event.source)
    }

    @Test fun restoredUnsavedChangesRemainDirtyBeforeLoadingAndAfterALoadFailure() = runTest(dispatcher) {
        val repo = TestMoodRepository()
        val handle = SavedStateHandle()
        val editor = MoodEditorViewModel(repo, null, handle, clock)
        advanceUntilIdle()
        editor.setScore(4); editor.setNote("Keep this draft")
        repo.failLoading = true
        val restored = MoodEditorViewModel(repo, null, handle, clock)
        assertTrue("Discard must confirm even before loading finishes", restored.state.value.hasChanges)
        advanceUntilIdle()
        assertTrue(restored.state.value.hasChanges)
        assertEquals("Keep this draft", restored.state.value.draft.note)
    }

    @Test fun restoredCommittedCaptureBecomesAnEditAndKeepsTheNewDraftValues() = runTest(dispatcher) {
        val repo = TestMoodRepository().apply { failAfterCreate = true }
        val handle = SavedStateHandle()
        val editor = MoodEditorViewModel(repo, null, handle, clock)
        advanceUntilIdle()
        editor.setScore(4); editor.save(); advanceUntilIdle()
        editor.setNote("New draft value")
        repo.failAfterCreate = false
        val restored = MoodEditorViewModel(repo, null, handle, clock)
        advanceUntilIdle()
        assertTrue(restored.state.value.editing)
        restored.save(); advanceUntilIdle()
        assertTrue(restored.state.value.completed)
        assertEquals(1, repo.records.size)
        assertEquals("New draft value", repo.records.values.single().mood!!.note)
    }

    @Test fun peopleDraftRestoresAndSubmitsExplicitSelectionIncludingClear() = runTest(dispatcher) {
        val repo = TestMoodRepository()
        val handle = SavedStateHandle()
        val person = java.util.UUID.randomUUID()
        val editor = MoodEditorViewModel(repo, null, handle, clock)
        advanceUntilIdle(); editor.setScore(4); editor.togglePerson(person)
        val restored = MoodEditorViewModel(repo, null, handle, clock)
        advanceUntilIdle()
        assertEquals(setOf(person), restored.state.value.draft.personIds)
        restored.save(); advanceUntilIdle()
        assertEquals(setOf(person), repo.submittedPeople)
        val editing = MoodEditorViewModel(repo, repo.records.keys.single(), SavedStateHandle(), clock)
        advanceUntilIdle()
        assertEquals(setOf(person), editing.state.value.draft.personIds)
        editing.togglePerson(person); editing.save(); advanceUntilIdle()
        assertEquals(emptySet<java.util.UUID>(), repo.submittedPeople)
    }

    private class TestMoodRepository : MoodRepository {
        val emotions = listOf(Emotion(UUID.randomUUID(), "Calm"), Emotion(UUID.randomUUID(), "Happy"))
        val records = mutableMapOf<EventId, LifeEventRecord>()
        var failAfterCreate = false
        var failLoading = false
        var creates = 0
        var submittedPeople: Set<java.util.UUID>? = null
        override suspend fun prepareEmotions() { if (failLoading) error("Unavailable") }
        override fun observeEmotions() = flowOf(emotions)
        override suspend fun get(id: EventId) = records[id]
        override suspend fun create(id: EventId, input: MoodInput): EventId {
            submittedPeople = input.personIds
            creates++
            val incoming = record(id, input)
            val previous = records[id]
            require(previous == null || previous == incoming) { "Occupied capture ID" }
            records.putIfAbsent(id, incoming)
            if (failAfterCreate) error("write result unavailable")
            return id
        }
        override suspend fun update(id: EventId, input: MoodInput) {
            submittedPeople = input.personIds
            require(records.containsKey(id))
            records[id] = record(id, input)
        }
        override suspend fun delete(id: EventId) { records.remove(id) }
        private fun record(id: EventId, input: MoodInput) = LifeEventRecord(
            LifeEvent(id, EventType.MOOD, input.occurredAt, input.occurredAt),
            MoodDetails(input.score, emotions.filter { it.id in input.emotionIds }, input.energy, input.stress, input.note),
            people = input.personIds?.map { Person(it, "Friend") } ?: records[id]?.people.orEmpty())
    }
}
