package com.luma.lifeos.ui.places

import androidx.lifecycle.SavedStateHandle
import com.luma.lifeos.domain.model.*
import com.luma.lifeos.domain.repository.PlaceRepository
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaceEditorTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun requiredNameAndRepeatedSubmitCommitOnce() = runTest(dispatcher) {
        val repo = TestPlaces()
        val editor = PlaceEditorViewModel(repo, null, SavedStateHandle())
        advanceUntilIdle(); editor.setName(" "); editor.save(); advanceUntilIdle()
        assertTrue(repo.records.isEmpty())
        editor.setName("Ali"); editor.setNote("Friend"); editor.save(); editor.save()
        assertTrue(editor.state.value.busy)
        assertFalse(editor.state.value.completed)
        advanceUntilIdle()
        assertTrue(editor.state.value.completed)
        assertEquals(1, repo.creates)
        assertEquals("Friend", repo.records.values.single().note)
    }

    @Test fun committedCreateWithLostResultRecoversSameIdAndNewerDraft() = runTest(dispatcher) {
        val repo = TestPlaces().apply { failAfterCreate = true }
        val handle = SavedStateHandle()
        val editor = PlaceEditorViewModel(repo, null, handle)
        advanceUntilIdle(); editor.setName("First"); editor.save(); advanceUntilIdle()
        assertEquals(PlaceEditorError.SAVE, editor.state.value.error)
        editor.setName("Newer"); repo.failAfterCreate = false
        val restored = PlaceEditorViewModel(repo, null, handle)
        advanceUntilIdle()
        assertTrue(restored.state.value.editing)
        restored.save(); advanceUntilIdle()
        assertEquals(1, repo.creates)
        assertEquals("Newer", repo.records.values.single().name)
    }

    @Test fun failedLoadKeepsDirtyDraftAndRetryUnlocksIt() = runTest(dispatcher) {
        val repo = TestPlaces()
        val handle = SavedStateHandle()
        val editor = PlaceEditorViewModel(repo, null, handle)
        advanceUntilIdle(); editor.setName("Keep"); editor.setNote("Note")
        repo.failLoad = true
        val restored = PlaceEditorViewModel(repo, null, handle)
        advanceUntilIdle()
        assertFalse(restored.state.value.ready)
        assertTrue(restored.state.value.hasChanges)
        assertEquals("Keep", restored.state.value.draft.name)
        repo.failLoad = false; restored.load(); advanceUntilIdle()
        assertTrue(restored.state.value.ready)
        assertEquals("Note", restored.state.value.draft.note)
    }

    @Test fun missingEditCannotRecreateDeletedPlace() = runTest(dispatcher) {
        val repo = TestPlaces()
        val editor = PlaceEditorViewModel(repo, UUID.randomUUID(), SavedStateHandle())
        advanceUntilIdle(); editor.setName("Unexpected"); editor.save(); editor.delete(); advanceUntilIdle()
        assertFalse(editor.state.value.ready)
        assertTrue(repo.records.isEmpty())
    }

    @Test fun failedDeleteRetainsDraftAndLostCommittedDeleteRestoresCompletion() = runTest(dispatcher) {
        val repo = TestPlaces()
        val id = UUID.randomUUID()
        repo.create(id, PlaceInput("Ali"))
        val handle = SavedStateHandle()
        val editor = PlaceEditorViewModel(repo, id, handle)
        advanceUntilIdle(); editor.setNote("Keep")
        repo.failDelete = true; editor.delete(); advanceUntilIdle()
        assertEquals(PlaceEditorError.DELETE, editor.state.value.error)
        assertEquals("Keep", editor.state.value.draft.note)
        assertFalse(editor.state.value.completed)
        repo.failDelete = false; repo.failAfterDelete = true
        editor.delete(); advanceUntilIdle()
        assertNull(repo.records[id])
        val restored = PlaceEditorViewModel(repo, id, handle)
        advanceUntilIdle()
        assertTrue(restored.state.value.completed)
        assertTrue(restored.state.value.deleted)
    }

    @Test fun oversizedTextNeverReplacesRestorableDraftAndLegacyOversizeCannotLoad() = runTest(dispatcher) {
        val repo = TestPlaces()
        val handle = SavedStateHandle()
        val editor = PlaceEditorViewModel(repo, null, handle)
        advanceUntilIdle(); editor.setName("Keep"); editor.setNote("Note")
        editor.setName("x".repeat(201)); editor.setNote("x".repeat(10_001))
        assertEquals(PlaceEditorError.TEXT, editor.state.value.error)
        val restored = PlaceEditorViewModel(repo, null, handle)
        advanceUntilIdle()
        assertEquals(PlaceDraft("Keep", "Note"), restored.state.value.draft)
        val id = UUID.randomUUID()
        repo.records[id] = Place(id, "x".repeat(201))
        val legacy = PlaceEditorViewModel(repo, id, SavedStateHandle())
        advanceUntilIdle(); assertFalse(legacy.state.value.ready)
    }

    @Test fun rejectionIdentifiesFieldAndKeepsAcceptedText() = runTest(dispatcher) {
        val editor = PlaceEditorViewModel(TestPlaces(), null, SavedStateHandle())
        advanceUntilIdle(); editor.setName("Accepted"); editor.setNote("Note")
        editor.setName("x".repeat(201))
        assertEquals("Accepted", editor.state.value.draft.name)
        assertEquals(setOf(PlaceTextField.NAME), editor.state.value.textErrors)
    }

    @Test fun sameValueAndUnrelatedEditKeepRejection() = runTest(dispatcher) {
        val editor = PlaceEditorViewModel(TestPlaces(), null, SavedStateHandle())
        advanceUntilIdle(); editor.setName("Name"); editor.setNote("Note")
        editor.setName("x".repeat(201)); editor.setName("Name"); editor.setNote("Changed")
        assertEquals(setOf(PlaceTextField.NAME), editor.state.value.textErrors)
    }

    @Test fun twoRejectedFieldsClearIndependently() = runTest(dispatcher) {
        val editor = PlaceEditorViewModel(TestPlaces(), null, SavedStateHandle())
        advanceUntilIdle(); editor.setName("Name"); editor.setNote("Note")
        editor.setName("x".repeat(201)); editor.setNote("x".repeat(10_001))
        assertEquals(setOf(PlaceTextField.NAME, PlaceTextField.NOTE), editor.state.value.textErrors)
        editor.setName("Corrected")
        assertEquals(setOf(PlaceTextField.NOTE), editor.state.value.textErrors)
    }

    @Test fun recreationRestoresFieldFlags() = runTest(dispatcher) {
        val handle = SavedStateHandle(); val repo = TestPlaces()
        val editor = PlaceEditorViewModel(repo, null, handle)
        advanceUntilIdle(); editor.setNote("Kept note"); editor.setNote("x".repeat(10_001))
        val restored = PlaceEditorViewModel(repo, null, handle)
        assertEquals(setOf(PlaceTextField.NOTE), restored.state.value.textErrors)
        advanceUntilIdle()
        assertEquals("Kept note", restored.state.value.draft.note)
    }

    @Test fun unknownRestoredFieldNameIsIgnored() = runTest(dispatcher) {
        val handle = SavedStateHandle(mapOf("text_error_fields" to arrayListOf("FUTURE_FIELD")))
        val editor = PlaceEditorViewModel(TestPlaces(), null, handle)
        assertTrue(editor.state.value.textErrors.isEmpty())
    }

    private class TestPlaces : PlaceRepository {
        val records = mutableMapOf<UUID, Place>()
        var creates = 0
        var failLoad = false
        var failAfterCreate = false
        var failDelete = false
        var failAfterDelete = false
        override fun observePlaces() = flowOf(records.values.toList())
        override fun observeHistory(id: UUID): kotlinx.coroutines.flow.Flow<com.luma.lifeos.domain.model.PlaceHistory?> = flowOf(null)
        override fun observePlace(id: UUID) = flowOf(records[id])
        override suspend fun get(id: UUID): Place? { if (failLoad) error("Unavailable"); return records[id] }
        override suspend fun create(id: UUID, input: PlaceInput): UUID {
            creates++; require(id !in records)
            records[id] = Place(id, input.name, input.note)
            if (failAfterCreate) error("Lost result")
            return id
        }
        override suspend fun update(id: UUID, input: PlaceInput) {
            records[id] = records.getValue(id).copy(name = input.name, note = input.note)
        }
        override suspend fun delete(id: UUID) {
            if (failDelete) error("Unavailable")
            records.remove(id)
            if (failAfterDelete) error("Lost result")
        }
    }
}
