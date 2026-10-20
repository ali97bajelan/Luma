package com.luma.lifeos.ui.memory

import com.luma.lifeos.ui.photos.TestPhotoRepository
import androidx.lifecycle.SavedStateHandle
import com.luma.lifeos.domain.model.*
import com.luma.lifeos.domain.repository.MemoryRepository
import com.luma.lifeos.domain.time.TimeRange
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MemoryEditorTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = Clock.fixed(Instant.parse("2026-10-11T08:00:00Z"), ZoneOffset.UTC)
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun addPersonIsIdempotentAndHonorsSelectionLimit() = runTest(dispatcher) {
        val editor = MemoryEditorViewModel(TestMemoryRepository(), null, SavedStateHandle(), clock)
        advanceUntilIdle()
        val first = UUID.randomUUID()
        assertTrue(editor.addPerson(first))
        assertTrue(editor.addPerson(first))
        repeat(PersonTextLimits.SELECTION - 1) { assertTrue(editor.addPerson(UUID.randomUUID())) }
        assertFalse(editor.addPerson(UUID.randomUUID()))
        assertEquals(PersonTextLimits.SELECTION, editor.state.value.draft.personIds.size)
    }

    @Test fun directCaptureRequiresNoteAndRepeatedSubmitCommitsOnce() = runTest(dispatcher) {
        val repo = TestMemoryRepository()
        val editor = MemoryEditorViewModel(repo, null, SavedStateHandle(), clock)
        advanceUntilIdle(); editor.setNote(" "); editor.save(); advanceUntilIdle()
        assertFalse(editor.state.value.completed)
        assertTrue(repo.records.isEmpty())
        editor.setNote("A moment"); editor.setTitle("Title"); editor.setImportance(5)
        editor.save(); editor.save()
        assertTrue(editor.state.value.busy)
        assertFalse(editor.state.value.completed)
        advanceUntilIdle()
        assertTrue(editor.state.value.completed)
        assertEquals(1, repo.creates)
        assertEquals(5, repo.records.values.single().memory!!.importance)
        assertEquals("A moment", repo.records.values.single().memory!!.note)
    }

    @Test fun promotionAllowsBlankMarkerAndCannotChangeOriginalTimeOrTitle() = runTest(dispatcher) {
        val repo = TestMemoryRepository()
        val original = journal()
        repo.records[original.event.id] = original
        val editor = MemoryEditorViewModel(repo, original.event.id, SavedStateHandle(), clock)
        advanceUntilIdle()
        assertFalse(editor.state.value.direct)
        editor.setTitle("Unexpected")
        assertFalse(editor.setLocalTime(LocalDateTime.parse("2026-10-10T12:30:00"), ZoneOffset.UTC))
        editor.save(); advanceUntilIdle()
        val saved = repo.records[original.event.id]!!
        assertEquals(original, saved.copy(memory = null))
        assertEquals(MemoryDetails(), saved.memory)
        assertTrue(editor.state.value.completed)
    }

    @Test fun restoredDraftRetainsTextImportanceTimestampAndDirtyStateDuringLoadFailure() = runTest(dispatcher) {
        val repo = TestMemoryRepository()
        val handle = SavedStateHandle()
        val editor = MemoryEditorViewModel(repo, null, handle, clock)
        advanceUntilIdle(); editor.setNote("Keep"); editor.setTitle("Title"); editor.setImportance(2)
        editor.setLocalTime(LocalDateTime.parse("2026-10-10T12:30:00"), ZoneOffset.UTC)
        repo.failLoading = true
        val restored = MemoryEditorViewModel(repo, null, handle, clock)
        assertTrue(restored.state.value.hasChanges)
        advanceUntilIdle()
        assertEquals(MemoryEditorError.LOAD, restored.state.value.error)
        assertEquals("Keep", restored.state.value.draft.note)
        assertEquals(2, restored.state.value.draft.importance)
        repo.failLoading = false
        restored.load(); advanceUntilIdle()
        assertEquals(Instant.parse("2026-10-10T12:30:00Z").toEpochMilli(), restored.state.value.draft.occurredAtMillis)
        assertTrue(restored.state.value.ready)
    }

    @Test fun ambiguousCreateRestoresAsEditAndKeepsNewerDraft() = runTest(dispatcher) {
        val repo = TestMemoryRepository().apply { failAfterSave = true }
        val handle = SavedStateHandle()
        val editor = MemoryEditorViewModel(repo, null, handle, clock)
        advanceUntilIdle(); editor.setNote("First"); editor.save(); advanceUntilIdle()
        assertEquals(MemoryEditorError.SAVE, editor.state.value.error)
        editor.setNote("Newer")
        repo.failAfterSave = false
        val restored = MemoryEditorViewModel(repo, null, handle, clock)
        advanceUntilIdle()
        assertTrue(restored.state.value.editing)
        restored.save(); advanceUntilIdle()
        assertEquals(1, repo.records.size)
        assertEquals("Newer", repo.records.values.single().memory!!.note)
    }

    @Test fun ambiguousPromotionRetryKeepsOriginalAndNewerMarkerNote() = runTest(dispatcher) {
        val repo = TestMemoryRepository().apply { failAfterSave = true }
        val original = journal()
        repo.records[original.event.id] = original
        val handle = SavedStateHandle()
        val editor = MemoryEditorViewModel(repo, original.event.id, handle, clock)
        advanceUntilIdle(); editor.setNote("First marker"); editor.save(); advanceUntilIdle()
        editor.setNote("Newer marker")
        repo.failAfterSave = false
        val restored = MemoryEditorViewModel(repo, original.event.id, handle, clock)
        advanceUntilIdle(); restored.save(); advanceUntilIdle()
        assertEquals(original, repo.records[original.event.id]!!.copy(memory = null))
        assertEquals("Newer marker", repo.records[original.event.id]!!.memory!!.note)
    }

    @Test fun confirmedUnmarkLostResultReconcilesAfterRestorationWithoutDeletingOriginal() = runTest(dispatcher) {
        val repo = TestMemoryRepository().apply { failAfterRemoval = true }
        val original = journal().copy(memory = MemoryDetails(5))
        repo.records[original.event.id] = original
        val handle = SavedStateHandle()
        val editor = MemoryEditorViewModel(repo, original.event.id, handle, clock)
        advanceUntilIdle(); editor.remove(); advanceUntilIdle()
        assertEquals(MemoryEditorError.REMOVE, editor.state.value.error)
        assertEquals(original.copy(memory = null), repo.records[original.event.id])
        val restored = MemoryEditorViewModel(repo, original.event.id, handle, clock)
        advanceUntilIdle()
        assertTrue(restored.state.value.completed)
        assertTrue(restored.state.value.removed)
        assertFalse(restored.state.value.direct)
    }

    @Test fun confirmedDirectDeleteLostResultReconcilesAfterRestoration() = runTest(dispatcher) {
        val repo = TestMemoryRepository().apply { failAfterRemoval = true }
        val id = EventId.new()
        repo.create(id, MemoryInput("Direct", clock.instant()))
        val handle = SavedStateHandle()
        val editor = MemoryEditorViewModel(repo, id, handle, clock)
        advanceUntilIdle(); editor.remove(); advanceUntilIdle()
        val restored = MemoryEditorViewModel(repo, id, handle, clock)
        advanceUntilIdle()
        assertTrue(restored.state.value.completed)
        assertTrue(restored.state.value.removed)
        assertTrue(restored.state.value.direct)
        assertNull(repo.records[id])
    }

    @Test fun failedRemovalKeepsDraftUntilSuccessfulRetry() = runTest(dispatcher) {
        val repo = TestMemoryRepository().apply { failRemoval = true }
        val original = journal().copy(memory = MemoryDetails(4))
        repo.records[original.event.id] = original
        val editor = MemoryEditorViewModel(repo, original.event.id, SavedStateHandle(), clock)
        advanceUntilIdle(); editor.setNote("Unsaved"); editor.remove(); advanceUntilIdle()
        assertFalse(editor.state.value.completed)
        assertEquals("Unsaved", editor.state.value.draft.note)
        repo.failRemoval = false
        editor.remove(); advanceUntilIdle()
        assertTrue(editor.state.value.completed)
    }

    @Test fun missingImportedAndIncompleteRecordsStayProtected() = runTest(dispatcher) {
        val repo = TestMemoryRepository()
        val missing = MemoryEditorViewModel(repo, EventId.new(), SavedStateHandle(), clock)
        advanceUntilIdle(); missing.setNote("Unexpected"); missing.save(); advanceUntilIdle()
        assertFalse(missing.state.value.ready)
        val imported = journal().let { it.copy(event = it.event.copy(source = EventSource.HEALTH_CONNECT, sourceReference = "source")) }
        val incomplete = journal().copy(journal = null)
        listOf(imported, incomplete).forEach { record ->
            repo.records[record.event.id] = record
            val editor = MemoryEditorViewModel(repo, record.event.id, SavedStateHandle(), clock)
            advanceUntilIdle(); editor.save(); editor.remove(); advanceUntilIdle()
            assertFalse(editor.state.value.ready)
            assertEquals(record, repo.records[record.event.id])
        }
    }

    @Test fun oversizedTextAndDstGapsRetainPreviousDraft() = runTest(dispatcher) {
        val editor = MemoryEditorViewModel(TestMemoryRepository(), null, SavedStateHandle(), clock)
        advanceUntilIdle(); editor.setNote("Keep"); editor.setTitle("Title")
        editor.setNote("x".repeat(10_001)); editor.setTitle("x".repeat(201))
        assertEquals("Keep", editor.state.value.draft.note)
        assertEquals("Title", editor.state.value.draft.title)
        assertEquals(MemoryEditorError.TEXT, editor.state.value.error)
        assertFalse(editor.setLocalTime(LocalDateTime.parse("2026-03-08T02:30:00"), ZoneId.of("America/New_York")))
        assertEquals(clock.millis(), editor.state.value.draft.occurredAtMillis)
    }

    private fun journal() = LifeEventRecord(LifeEvent(EventId.new(), EventType.JOURNAL, clock.instant(), clock.instant(), title = "Source title"),
        journal = JournalDetails("Source title", "Source body"))

    @Test fun peopleDraftRestoresAndSubmitsExplicitSelectionIncludingClear() = runTest(dispatcher) {
        val repo = TestMemoryRepository()
        val handle = SavedStateHandle()
        val person = java.util.UUID.randomUUID()
        val editor = MemoryEditorViewModel(repo, null, handle, clock)
        advanceUntilIdle(); editor.setNote("Moment"); editor.togglePerson(person)
        val restored = MemoryEditorViewModel(repo, null, handle, clock)
        advanceUntilIdle()
        assertEquals(setOf(person), restored.state.value.draft.personIds)
        restored.save(); advanceUntilIdle()
        assertEquals(setOf(person), repo.submittedPeople)
        val editing = MemoryEditorViewModel(repo, repo.records.keys.single(), SavedStateHandle(), clock)
        advanceUntilIdle()
        assertEquals(setOf(person), editing.state.value.draft.personIds)
        editing.togglePerson(person); editing.save(); advanceUntilIdle()
        assertEquals(emptySet<java.util.UUID>(), repo.submittedPeople)
    }

    @Test fun promotionLoadsOriginalPeopleAndSubmitsChangedSelection() = runTest(dispatcher) {
        val repo = TestMemoryRepository()
        val person = Person(java.util.UUID.randomUUID(), "Friend")
        val original = journal().copy(people = listOf(person))
        repo.records[original.event.id] = original
        val editor = MemoryEditorViewModel(repo, original.event.id, SavedStateHandle(), clock)
        advanceUntilIdle()
        assertEquals(setOf(person.id), editor.state.value.draft.personIds)
        editor.togglePerson(person.id); editor.save(); advanceUntilIdle()
        assertEquals(emptySet<java.util.UUID>(), repo.submittedPeople)
        assertEquals(original.copy(people = emptyList()), repo.records[original.event.id]!!.copy(memory = null))
        assertTrue(repo.records[original.event.id]!!.people.isEmpty())
    }

    @Test fun placeSelectionRestoresLoadsAndExplicitlyClearsWhileNoneIsOptional() = runTest(dispatcher) {
        val repo = TestMemoryRepository()
        val handle = SavedStateHandle()
        val place = java.util.UUID.randomUUID()
        val editor = MemoryEditorViewModel(repo, null, handle, clock)
        advanceUntilIdle(); editor.setNote("Moment")
        assertNull(editor.state.value.draft.placeId)
        editor.setPlace(place)
        val restored = MemoryEditorViewModel(repo, null, handle, clock)
        advanceUntilIdle()
        assertEquals(place, restored.state.value.draft.placeId)
        restored.save(); advanceUntilIdle()
        assertEquals(PlaceSelection(place), repo.submittedPlace)
        val editing = MemoryEditorViewModel(repo, repo.records.keys.single(), SavedStateHandle(), clock)
        advanceUntilIdle()
        assertEquals(place, editing.state.value.draft.placeId)
        editing.setPlace(null); editing.save(); advanceUntilIdle()
        assertEquals(PlaceSelection(null), repo.submittedPlace)
        assertNull(repo.records.values.single().place)
    }

    @Test fun promotionRetainsSourcePlaceInDraftAndForwardsReplacementWithoutChangingContent() = runTest(dispatcher) {
        val repo = TestMemoryRepository()
        val first = Place(java.util.UUID.randomUUID(), "Home")
        val second = java.util.UUID.randomUUID()
        val original = journal().copy(place = first)
        repo.records[original.event.id] = original
        val editor = MemoryEditorViewModel(repo, original.event.id, SavedStateHandle(), clock)
        advanceUntilIdle()
        assertEquals(first.id, editor.state.value.draft.placeId)
        editor.setPlace(second); editor.save(); advanceUntilIdle()
        assertEquals(PlaceSelection(second), repo.submittedPlace)
        assertEquals(original, repo.records[original.event.id]!!.copy(memory = null, place = first))
    }

    @Test fun photosSurviveRestorationAndExistingPhotoCanBeRemovedWithoutChangingText() = runTest(dispatcher) {
        val repo = TestMemoryRepository(); val files = TestPhotoRepository(); val handle = SavedStateHandle()
        val editor = MemoryEditorViewModel(repo, null, handle, clock, files)
        advanceUntilIdle(); editor.setNote("Keep"); editor.importPhoto("content://selected/image"); advanceUntilIdle()
        val photo = editor.state.value.draft.photos.single()
        val restored = MemoryEditorViewModel(repo, null, handle, clock, files)
        advanceUntilIdle()
        assertEquals(listOf(photo), restored.state.value.draft.photos)
        restored.save(); advanceUntilIdle()
        assertEquals(listOf(photo), repo.submittedPhotos)
        val editing = MemoryEditorViewModel(repo, repo.records.keys.single(), SavedStateHandle(), clock, files)
        advanceUntilIdle(); assertEquals(listOf(photo), editing.state.value.draft.photos)
        editing.removePhoto(photo.id); advanceUntilIdle(); editing.save(); advanceUntilIdle()
        assertEquals(emptyList<PhotoAttachment>(), repo.submittedPhotos)
        assertTrue(repo.records.values.single().photos.isEmpty())
    }

    @Test fun finishedCopyWithLostResultRecoversRetainedPhotoIdAndDraft() = runTest(dispatcher) {
        val repo = TestMemoryRepository(); val files = TestPhotoRepository().apply { failAfterCopy = true }
        val handle = SavedStateHandle()
        val editor = MemoryEditorViewModel(repo, null, handle, clock, files)
        advanceUntilIdle(); editor.setNote("Keep"); editor.importPhoto("content://selected/image"); advanceUntilIdle()
        assertEquals(MemoryEditorError.PHOTO, editor.state.value.error)
        assertTrue(editor.state.value.draft.photos.isEmpty())
        files.failAfterCopy = false
        val restored = MemoryEditorViewModel(repo, null, handle, clock, files)
        advanceUntilIdle()
        assertEquals(1, files.files.size)
        assertEquals(files.files.values.single(), restored.state.value.draft.photos.single())
        files.failRelease = true
        restored.save(); advanceUntilIdle()
        assertTrue(restored.state.value.completed)
        assertEquals(listOf(files.files.values.single()), repo.submittedPhotos)
    }

    @Test fun failedImportKeepsPreviousSelectionAndDiscardDoesNotWriteEntry() = runTest(dispatcher) {
        val repo = TestMemoryRepository(); val files = TestPhotoRepository()
        val editor = MemoryEditorViewModel(repo, null, SavedStateHandle(), clock, files)
        advanceUntilIdle(); editor.setNote("Keep"); editor.importPhoto("content://first"); advanceUntilIdle()
        val draft = editor.state.value.draft
        files.failImport = true; editor.importPhoto("content://missing"); advanceUntilIdle()
        assertEquals(draft, editor.state.value.draft)
        assertEquals(MemoryEditorError.PHOTO, editor.state.value.error)
        var closed = false
        editor.discardPhotos { closed = true }; advanceUntilIdle()
        assertTrue(closed)
        assertTrue(repo.records.isEmpty())
        assertTrue(files.leases.isEmpty())
    }

    @Test fun photoChangesAfterAmbiguousUnmarkRetainNewDraftOnRestoration() = runTest(dispatcher) {
        val repo = TestMemoryRepository().apply { failAfterRemoval = true }
        val files = TestPhotoRepository()
        val original = journal().copy(memory = MemoryDetails(5))
        repo.records[original.event.id] = original
        val handle = SavedStateHandle()
        val editor = MemoryEditorViewModel(repo, original.event.id, handle, clock, files)
        advanceUntilIdle(); editor.remove(); advanceUntilIdle()
        assertEquals(MemoryEditorError.REMOVE, editor.state.value.error)
        editor.importPhoto("content://new-draft"); advanceUntilIdle()
        val photo = editor.state.value.draft.photos.single()
        val restored = MemoryEditorViewModel(repo, original.event.id, handle, clock, files)
        advanceUntilIdle()
        assertFalse(restored.state.value.completed)
        assertTrue(restored.state.value.ready)
        assertEquals(listOf(photo), restored.state.value.draft.photos)
        assertTrue(files.leases.values.any { photo in it })
        restored.save(); advanceUntilIdle()
        assertEquals(listOf(photo), repo.records.getValue(original.event.id).photos)
        assertEquals(original.journal, repo.records.getValue(original.event.id).journal)
    }

    @Test fun rejectionIdentifiesFieldAndKeepsAcceptedText() = runTest(dispatcher) {
        val editor = MemoryEditorViewModel(TestMemoryRepository(), null, SavedStateHandle(), clock)
        advanceUntilIdle(); editor.setTitle("Accepted"); editor.setNote("Note")
        editor.setTitle("x".repeat(201))
        assertEquals("Accepted", editor.state.value.draft.title)
        assertEquals(setOf(MemoryTextField.TITLE), editor.state.value.textErrors)
    }

    @Test fun sameValueAndUnrelatedEditKeepRejection() = runTest(dispatcher) {
        val editor = MemoryEditorViewModel(TestMemoryRepository(), null, SavedStateHandle(), clock)
        advanceUntilIdle(); editor.setTitle("Title"); editor.setNote("Note")
        editor.setTitle("x".repeat(201)); editor.setTitle("Title"); editor.setNote("Changed")
        assertEquals(setOf(MemoryTextField.TITLE), editor.state.value.textErrors)
    }

    @Test fun twoRejectedFieldsClearIndependently() = runTest(dispatcher) {
        val editor = MemoryEditorViewModel(TestMemoryRepository(), null, SavedStateHandle(), clock)
        advanceUntilIdle(); editor.setTitle("Title"); editor.setNote("Note")
        editor.setTitle("x".repeat(201)); editor.setNote("x".repeat(10_001))
        assertEquals(setOf(MemoryTextField.TITLE, MemoryTextField.NOTE), editor.state.value.textErrors)
        editor.setTitle("Corrected")
        assertEquals(setOf(MemoryTextField.NOTE), editor.state.value.textErrors)
    }

    @Test fun recreationRestoresFieldFlags() = runTest(dispatcher) {
        val handle = SavedStateHandle(); val repo = TestMemoryRepository()
        val editor = MemoryEditorViewModel(repo, null, handle, clock)
        advanceUntilIdle(); editor.setNote("Kept note"); editor.setNote("x".repeat(10_001))
        val restored = MemoryEditorViewModel(repo, null, handle, clock)
        assertEquals(setOf(MemoryTextField.NOTE), restored.state.value.textErrors)
        advanceUntilIdle()
        assertEquals("Kept note", restored.state.value.draft.note)
    }

    @Test fun unknownRestoredFieldNameIsIgnored() = runTest(dispatcher) {
        val handle = SavedStateHandle(mapOf("text_error_fields" to arrayListOf("FUTURE_FIELD")))
        val editor = MemoryEditorViewModel(TestMemoryRepository(), null, handle, clock)
        assertTrue(editor.state.value.textErrors.isEmpty())
    }

    private class TestMemoryRepository : MemoryRepository {
        val records = mutableMapOf<EventId, LifeEventRecord>()
        var creates = 0
        var submittedPeople: Set<java.util.UUID>? = null
        var submittedPlace: PlaceSelection? = null
        var submittedPhotos: List<PhotoAttachment>? = null
        var failLoading = false
        var failAfterSave = false
        var failRemoval = false
        var failAfterRemoval = false
        override suspend fun get(id: EventId): LifeEventRecord? { if (failLoading) error("Unavailable"); return records[id] }
        override fun observe(range: TimeRange) = flowOf(records.values.filter { it.memory != null })
        override suspend fun create(id: EventId, input: MemoryInput): EventId {
            submittedPeople = input.personIds
            submittedPlace = input.placeSelection
            submittedPhotos = input.photos
            creates++
            require(id !in records)
            records[id] = LifeEventRecord(LifeEvent(id, EventType.MEMORY, input.occurredAt, input.occurredAt, title = input.title, note = input.note),
                memory = MemoryDetails(input.importance, input.note),
                people = input.personIds?.map { Person(it, "Friend") } ?: records[id]?.people.orEmpty(),
            place = if (input.placeSelection == null) records[id]?.place else input.placeSelection?.id?.let { Place(it, "Cafe") },
            photos = input.photos ?: records[id]?.photos.orEmpty())
            if (failAfterSave) error("Committed result unavailable")
            return id
        }
        override suspend fun update(id: EventId, input: MemoryInput) {
            submittedPeople = input.personIds
            submittedPlace = input.placeSelection
            submittedPhotos = input.photos
            val previous = records.getValue(id)
            require(previous.event.type == EventType.MEMORY && previous.event.isEditable && previous.memory != null)
            records[id] = previous.copy(event = previous.event.copy(occurredAt = input.occurredAt, title = input.title, note = input.note),
                memory = MemoryDetails(input.importance, input.note),
                people = input.personIds?.map { Person(it, "Friend") } ?: records[id]?.people.orEmpty(),
            place = if (input.placeSelection == null) records[id]?.place else input.placeSelection?.id?.let { Place(it, "Cafe") },
            photos = input.photos ?: records[id]?.photos.orEmpty())
            if (failAfterSave) error("Committed result unavailable")
        }
        override suspend fun saveMarker(id: EventId, input: MemoryMarkerInput) {
            submittedPeople = input.personIds
            submittedPlace = input.placeSelection
            submittedPhotos = input.photos
            val original = records.getValue(id)
            require(original.canPromoteToMemory)
            records[id] = original.copy(memory = MemoryDetails(input.importance, input.note?.takeIf { it.isNotBlank() }),
                people = input.personIds?.map { Person(it, "Friend") } ?: original.people,
                place = if (input.placeSelection == null) original.place else input.placeSelection?.id?.let { Place(it, "Cafe") },
            photos = input.photos ?: records[id]?.photos.orEmpty())
            if (failAfterSave) error("Committed result unavailable")
        }
        override suspend fun removeMarker(id: EventId) {
            if (failRemoval) error("Unavailable")
            val previous = records[id] ?: return
            require(previous.canPromoteToMemory)
            records[id] = previous.copy(memory = null)
            if (failAfterRemoval) error("Committed result unavailable")
        }
        override suspend fun delete(id: EventId) {
            if (failRemoval) error("Unavailable")
            records.remove(id)
            if (failAfterRemoval) error("Committed result unavailable")
        }
    }
}
