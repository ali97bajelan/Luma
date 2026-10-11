package com.luma.lifeos.ui.people

import androidx.lifecycle.SavedStateHandle
import com.luma.lifeos.domain.model.*
import com.luma.lifeos.domain.repository.PersonRepository
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
class PersonEditorTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun requiredNameAndRepeatedSubmitCommitOnce() = runTest(dispatcher) {
        val repo = TestPeople()
        val editor = PersonEditorViewModel(repo, null, SavedStateHandle())
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
        val repo = TestPeople().apply { failAfterCreate = true }
        val handle = SavedStateHandle()
        val editor = PersonEditorViewModel(repo, null, handle)
        advanceUntilIdle(); editor.setName("First"); editor.save(); advanceUntilIdle()
        assertEquals(PersonEditorError.SAVE, editor.state.value.error)
        editor.setName("Newer"); repo.failAfterCreate = false
        val restored = PersonEditorViewModel(repo, null, handle)
        advanceUntilIdle()
        assertTrue(restored.state.value.editing)
        restored.save(); advanceUntilIdle()
        assertEquals(1, repo.creates)
        assertEquals("Newer", repo.records.values.single().name)
    }

    @Test fun failedLoadKeepsDirtyDraftAndRetryUnlocksIt() = runTest(dispatcher) {
        val repo = TestPeople()
        val handle = SavedStateHandle()
        val editor = PersonEditorViewModel(repo, null, handle)
        advanceUntilIdle(); editor.setName("Keep"); editor.setNote("Note")
        repo.failLoad = true
        val restored = PersonEditorViewModel(repo, null, handle)
        advanceUntilIdle()
        assertFalse(restored.state.value.ready)
        assertTrue(restored.state.value.hasChanges)
        assertEquals("Keep", restored.state.value.draft.name)
        repo.failLoad = false; restored.load(); advanceUntilIdle()
        assertTrue(restored.state.value.ready)
        assertEquals("Note", restored.state.value.draft.note)
    }

    @Test fun missingEditCannotRecreateDeletedPerson() = runTest(dispatcher) {
        val repo = TestPeople()
        val editor = PersonEditorViewModel(repo, UUID.randomUUID(), SavedStateHandle())
        advanceUntilIdle(); editor.setName("Unexpected"); editor.save(); editor.delete(); advanceUntilIdle()
        assertFalse(editor.state.value.ready)
        assertTrue(repo.records.isEmpty())
    }

    @Test fun failedDeleteRetainsDraftAndLostCommittedDeleteRestoresCompletion() = runTest(dispatcher) {
        val repo = TestPeople()
        val id = UUID.randomUUID()
        repo.create(id, PersonInput("Ali"))
        val handle = SavedStateHandle()
        val editor = PersonEditorViewModel(repo, id, handle)
        advanceUntilIdle(); editor.setNote("Keep")
        repo.failDelete = true; editor.delete(); advanceUntilIdle()
        assertEquals(PersonEditorError.DELETE, editor.state.value.error)
        assertEquals("Keep", editor.state.value.draft.note)
        assertFalse(editor.state.value.completed)
        repo.failDelete = false; repo.failAfterDelete = true
        editor.delete(); advanceUntilIdle()
        assertNull(repo.records[id])
        val restored = PersonEditorViewModel(repo, id, handle)
        advanceUntilIdle()
        assertTrue(restored.state.value.completed)
        assertTrue(restored.state.value.deleted)
    }

    @Test fun oversizedTextNeverReplacesRestorableDraftAndLegacyOversizeCannotLoad() = runTest(dispatcher) {
        val repo = TestPeople()
        val handle = SavedStateHandle()
        val editor = PersonEditorViewModel(repo, null, handle)
        advanceUntilIdle(); editor.setName("Keep"); editor.setNote("Note")
        editor.setName("x".repeat(201)); editor.setNote("x".repeat(10_001))
        assertEquals(PersonEditorError.TEXT, editor.state.value.error)
        val restored = PersonEditorViewModel(repo, null, handle)
        advanceUntilIdle()
        assertEquals(PersonDraft("Keep", "Note"), restored.state.value.draft)
        val id = UUID.randomUUID()
        repo.records[id] = Person(id, "x".repeat(201))
        val legacy = PersonEditorViewModel(repo, id, SavedStateHandle())
        advanceUntilIdle(); assertFalse(legacy.state.value.ready)
    }

    private class TestPeople : PersonRepository {
        val records = mutableMapOf<UUID, Person>()
        var creates = 0
        var failLoad = false
        var failAfterCreate = false
        var failDelete = false
        var failAfterDelete = false
        override fun observePeople() = flowOf(records.values.toList())
        override fun observeHistory(id: UUID) = flowOf(records[id]?.let { PersonHistory(it, emptyList()) })
        override suspend fun get(id: UUID): Person? { if (failLoad) error("Unavailable"); return records[id] }
        override suspend fun create(id: UUID, input: PersonInput): UUID {
            creates++; require(id !in records)
            records[id] = Person(id, input.name, input.note)
            if (failAfterCreate) error("Lost result")
            return id
        }
        override suspend fun update(id: UUID, input: PersonInput) {
            records[id] = records.getValue(id).copy(name = input.name, note = input.note)
        }
        override suspend fun delete(id: UUID) {
            if (failDelete) error("Unavailable")
            records.remove(id)
            if (failAfterDelete) error("Lost result")
        }
    }
}
