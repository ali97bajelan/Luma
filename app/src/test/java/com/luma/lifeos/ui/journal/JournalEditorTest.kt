package com.luma.lifeos.ui.journal

import com.luma.lifeos.ui.photos.TestPhotoRepository
import androidx.lifecycle.SavedStateHandle
import com.luma.lifeos.domain.model.*
import com.luma.lifeos.domain.repository.JournalRepository
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
class JournalEditorTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = Clock.fixed(Instant.parse("2026-10-11T08:00:00Z"), ZoneOffset.UTC)
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun addPersonIsIdempotentAndStopsAtSelectionLimit() = runTest(dispatcher) {
        val editor = JournalEditorViewModel(TestJournalRepository(), null, SavedStateHandle(), clock)
        advanceUntilIdle()
        val selected = (0 until PersonTextLimits.SELECTION).map { UUID.randomUUID() }
        selected.forEach { assertTrue(editor.addPerson(it)) }
        assertFalse(editor.addPerson(UUID.randomUUID()))
        assertTrue(editor.addPerson(selected.first()))
        assertEquals(selected.toSet(), editor.state.value.draft.personIds)
    }

    @Test fun blankBodyCannotSaveAndRepeatedSubmitCommitsOnce() = runTest(dispatcher) {
        val repo = TestJournalRepository()
        val editor = JournalEditorViewModel(repo, null, SavedStateHandle(), clock)
        advanceUntilIdle()
        editor.setBody(" \n "); editor.save(); advanceUntilIdle()
        assertFalse(editor.state.value.completed)
        editor.setBody("  First\n\nSecond  "); editor.setTitle("Title")
        editor.save(); editor.save()
        assertTrue(editor.state.value.busy)
        assertFalse(editor.state.value.completed)
        advanceUntilIdle()
        assertTrue(editor.state.value.completed)
        assertEquals(1, repo.creates)
        assertEquals("  First\n\nSecond  ", repo.records.values.single().journal!!.body)
    }

    @Test fun restoredDraftRetainsTextTimestampAndTags() = runTest(dispatcher) {
        val repo = TestJournalRepository()
        val handle = SavedStateHandle()
        val editor = JournalEditorViewModel(repo, null, handle, clock)
        advanceUntilIdle()
        editor.setBody("Unsaved"); editor.setTitle("Draft title")
        editor.addTag(" Ideas "); editor.addTag("IDEAS"); editor.addTag("Work")
        editor.setLocalTime(LocalDateTime.parse("2026-10-10T12:30:00"), ZoneOffset.UTC)
        val restored = JournalEditorViewModel(repo, null, handle, clock)
        assertTrue(restored.state.value.hasChanges)
        advanceUntilIdle()
        assertEquals("Unsaved", restored.state.value.draft.body)
        assertEquals("Draft title", restored.state.value.draft.title)
        assertEquals(setOf("Ideas", "Work"), restored.state.value.draft.tagNames)
        assertEquals(Instant.parse("2026-10-10T12:30:00Z").toEpochMilli(), restored.state.value.draft.occurredAtMillis)
    }

    @Test fun rejectedTagInputKeepsItsErrorUntilTheTextChanges() {
        val rejected = reduceJournalTagInput("", hasError = false, proposed = "x".repeat(JournalTextLimits.TAG + 1))
        assertEquals("" to true, rejected)

        val unchangedCallback = reduceJournalTagInput(rejected.first, rejected.second, rejected.first)
        assertEquals("" to true, unchangedCallback)

        val corrected = reduceJournalTagInput(unchangedCallback.first, unchangedCallback.second, "Tag")
        assertEquals("Tag" to false, corrected)
    }

    @Test fun ambiguousCommittedSaveRestoresAsEditAndPreservesNewerText() = runTest(dispatcher) {
        val repo = TestJournalRepository().apply { failAfterCreate = true }
        val handle = SavedStateHandle()
        val editor = JournalEditorViewModel(repo, null, handle, clock)
        advanceUntilIdle()
        editor.setBody("First"); editor.save(); advanceUntilIdle()
        assertEquals(JournalEditorError.SAVE, editor.state.value.error)
        editor.setBody("Newer draft")
        repo.failAfterCreate = false
        val restored = JournalEditorViewModel(repo, null, handle, clock)
        advanceUntilIdle()
        assertTrue(restored.state.value.editing)
        restored.save(); advanceUntilIdle()
        assertEquals(1, repo.records.size)
        assertEquals("Newer draft", repo.records.values.single().journal!!.body)
    }

    @Test fun loadFailureKeepsDirtyDraftAndRetryUnlocksIt() = runTest(dispatcher) {
        val repo = TestJournalRepository()
        val handle = SavedStateHandle()
        val editor = JournalEditorViewModel(repo, null, handle, clock)
        advanceUntilIdle(); editor.setBody("Keep me")
        repo.failLoading = true
        val restored = JournalEditorViewModel(repo, null, handle, clock)
        advanceUntilIdle()
        assertEquals(JournalEditorError.LOAD, restored.state.value.error)
        assertTrue(restored.state.value.hasChanges)
        assertEquals("Keep me", restored.state.value.draft.body)
        repo.failLoading = false
        restored.load(); advanceUntilIdle()
        assertTrue(restored.state.value.ready)
        assertEquals("Keep me", restored.state.value.draft.body)
    }

    @Test fun missingAndImportedRecordsCannotBeEditedOrRecreated() = runTest(dispatcher) {
        val repo = TestJournalRepository()
        val missing = JournalEditorViewModel(repo, EventId.new(), SavedStateHandle(), clock)
        advanceUntilIdle(); missing.setBody("Unexpected"); missing.save(); advanceUntilIdle()
        assertFalse(missing.state.value.ready)
        assertTrue(repo.records.isEmpty())
        val id = EventId.new()
        repo.records[id] = LifeEventRecord(LifeEvent(id, EventType.JOURNAL, clock.instant(), clock.instant(),
            source = EventSource.HEALTH_CONNECT, sourceReference = "imported"), journal = JournalDetails(null, "Keep"))
        val imported = JournalEditorViewModel(repo, id, SavedStateHandle(), clock)
        advanceUntilIdle(); imported.delete(); imported.save(); advanceUntilIdle()
        assertFalse(imported.state.value.ready)
        assertEquals("Keep", repo.records[id]!!.journal!!.body)
    }

    @Test fun failedDeleteRetainsDraftAndCompletesOnlyAfterSuccessfulRetry() = runTest(dispatcher) {
        val repo = TestJournalRepository()
        val id = EventId.new()
        repo.create(id, JournalInput("Body", clock.instant()))
        val editor = JournalEditorViewModel(repo, id, SavedStateHandle(), clock)
        advanceUntilIdle(); editor.setBody("Unsaved edit")
        repo.failDeleting = true
        editor.delete(); advanceUntilIdle()
        assertFalse(editor.state.value.completed)
        assertEquals("Unsaved edit", editor.state.value.draft.body)
        assertEquals(JournalEditorError.DELETE, editor.state.value.error)
        repo.failDeleting = false
        editor.delete()
        assertFalse(editor.state.value.completed)
        advanceUntilIdle()
        assertTrue(editor.state.value.deleted)
        assertTrue(editor.state.value.completed)
    }

    @Test fun invalidDstTimeAndBlankTagRetainExistingDraftValues() = runTest(dispatcher) {
        val editor = JournalEditorViewModel(TestJournalRepository(), null, SavedStateHandle(), clock)
        advanceUntilIdle(); editor.setBody("Body"); editor.addTag("Work")
        assertFalse(editor.addTag(" "))
        assertEquals(setOf("Work"), editor.state.value.draft.tagNames)
        assertFalse(editor.setLocalTime(LocalDateTime.parse("2026-03-08T02:30:00"), ZoneId.of("America/New_York")))
        assertEquals(clock.millis(), editor.state.value.draft.occurredAtMillis)
        assertEquals(JournalEditorError.TIME, editor.state.value.error)
    }

    @Test fun redundantTextUpdatesKeepRejectionVisibleUntilTheDraftChanges() = runTest(dispatcher) {
        val editor = JournalEditorViewModel(TestJournalRepository(), null, SavedStateHandle(), clock)
        advanceUntilIdle()
        editor.setBody("Unsaved")
        editor.setBody("x".repeat(50_001))
        editor.setBody("Unsaved")
        editor.setTitle("")
        assertEquals("Unsaved", editor.state.value.draft.body)
        assertEquals(JournalEditorError.TEXT, editor.state.value.error)
        editor.setBody("Corrected")
        assertNull(editor.state.value.error)
    }

    @Test fun oversizedInputIsRejectedBeforeReplacingTheRestorableDraft() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val repo = TestJournalRepository()
        val editor = JournalEditorViewModel(repo, null, handle, clock)
        advanceUntilIdle(); editor.setBody("Keep this"); editor.setTitle("Keep title")
        editor.setBody("x".repeat(50_001))
        assertEquals("Keep this", editor.state.value.draft.body)
        assertEquals(JournalEditorError.TEXT, editor.state.value.error)
        editor.setTitle("x".repeat(201))
        assertEquals("Keep title", editor.state.value.draft.title)
        assertFalse(editor.addTag("x".repeat(51)))
        assertTrue(editor.state.value.draft.tagNames.isEmpty())
        val restored = JournalEditorViewModel(repo, null, handle, clock)
        advanceUntilIdle()
        assertEquals("Keep this", restored.state.value.draft.body)
        assertEquals("Keep title", restored.state.value.draft.title)
        (1..50).forEach { assertTrue(restored.addTag("tag$it")) }
        assertFalse(restored.addTag("Another"))
        assertEquals(50, restored.state.value.draft.tagNames.size)
    }

    @Test fun committedDeleteWithLostResultCompletesAfterRestoration() = runTest(dispatcher) {
        val repo = TestJournalRepository()
        val id = EventId.new()
        repo.create(id, JournalInput("Body", clock.instant()))
        val handle = SavedStateHandle()
        val editor = JournalEditorViewModel(repo, id, handle, clock)
        advanceUntilIdle()
        repo.failAfterDelete = true
        editor.delete(); advanceUntilIdle()
        assertEquals(JournalEditorError.DELETE, editor.state.value.error)
        assertNull(repo.records[id])
        val restored = JournalEditorViewModel(repo, id, handle, clock)
        advanceUntilIdle()
        assertTrue(restored.state.value.completed)
        assertTrue(restored.state.value.deleted)
        assertNull(restored.state.value.error)
    }

    @Test fun peopleDraftRestoresAndSubmitsExplicitSelectionIncludingClear() = runTest(dispatcher) {
        val repo = TestJournalRepository()
        val handle = SavedStateHandle()
        val person = java.util.UUID.randomUUID()
        val editor = JournalEditorViewModel(repo, null, handle, clock)
        advanceUntilIdle(); editor.setBody("Body"); editor.togglePerson(person)
        val restored = JournalEditorViewModel(repo, null, handle, clock)
        advanceUntilIdle()
        assertEquals(setOf(person), restored.state.value.draft.personIds)
        restored.save(); advanceUntilIdle()
        assertEquals(setOf(person), repo.submittedPeople)
        val editing = JournalEditorViewModel(repo, repo.records.keys.single(), SavedStateHandle(), clock)
        advanceUntilIdle()
        assertEquals(setOf(person), editing.state.value.draft.personIds)
        editing.togglePerson(person); editing.save(); advanceUntilIdle()
        assertEquals(emptySet<java.util.UUID>(), repo.submittedPeople)
    }

    @Test fun placeSelectionRestoresLoadsAndExplicitlyClearsWhileNoneIsOptional() = runTest(dispatcher) {
        val repo = TestJournalRepository()
        val handle = SavedStateHandle()
        val place = java.util.UUID.randomUUID()
        val editor = JournalEditorViewModel(repo, null, handle, clock)
        advanceUntilIdle(); editor.setBody("Body")
        assertNull(editor.state.value.draft.placeId)
        editor.setPlace(place)
        val restored = JournalEditorViewModel(repo, null, handle, clock)
        advanceUntilIdle()
        assertEquals(place, restored.state.value.draft.placeId)
        restored.save(); advanceUntilIdle()
        assertEquals(PlaceSelection(place), repo.submittedPlace)
        val editing = JournalEditorViewModel(repo, repo.records.keys.single(), SavedStateHandle(), clock)
        advanceUntilIdle()
        assertEquals(place, editing.state.value.draft.placeId)
        editing.setPlace(null); editing.save(); advanceUntilIdle()
        assertEquals(PlaceSelection(null), repo.submittedPlace)
        assertNull(repo.records.values.single().place)
    }

    @Test fun photosSurviveRestorationAndExistingPhotoCanBeRemovedWithoutChangingText() = runTest(dispatcher) {
        val repo = TestJournalRepository(); val files = TestPhotoRepository(); val handle = SavedStateHandle()
        val editor = JournalEditorViewModel(repo, null, handle, clock, files)
        advanceUntilIdle(); editor.setBody("Keep"); editor.importPhoto("content://selected/image"); advanceUntilIdle()
        val photo = editor.state.value.draft.photos.single()
        val restored = JournalEditorViewModel(repo, null, handle, clock, files)
        advanceUntilIdle()
        assertEquals(listOf(photo), restored.state.value.draft.photos)
        restored.save(); advanceUntilIdle()
        assertEquals(listOf(photo), repo.submittedPhotos)
        val editing = JournalEditorViewModel(repo, repo.records.keys.single(), SavedStateHandle(), clock, files)
        advanceUntilIdle(); assertEquals(listOf(photo), editing.state.value.draft.photos)
        editing.removePhoto(photo.id); advanceUntilIdle(); editing.save(); advanceUntilIdle()
        assertEquals(emptyList<PhotoAttachment>(), repo.submittedPhotos)
        assertTrue(repo.records.values.single().photos.isEmpty())
    }

    @Test fun finishedCopyWithLostResultRecoversRetainedPhotoIdAndDraft() = runTest(dispatcher) {
        val repo = TestJournalRepository(); val files = TestPhotoRepository().apply { failAfterCopy = true }
        val handle = SavedStateHandle()
        val editor = JournalEditorViewModel(repo, null, handle, clock, files)
        advanceUntilIdle(); editor.setBody("Keep"); editor.importPhoto("content://selected/image"); advanceUntilIdle()
        assertEquals(JournalEditorError.PHOTO, editor.state.value.error)
        assertTrue(editor.state.value.draft.photos.isEmpty())
        files.failAfterCopy = false
        val restored = JournalEditorViewModel(repo, null, handle, clock, files)
        advanceUntilIdle()
        assertEquals(1, files.files.size)
        assertEquals(files.files.values.single(), restored.state.value.draft.photos.single())
        files.failRelease = true
        restored.save(); advanceUntilIdle()
        assertTrue(restored.state.value.completed)
        assertEquals(listOf(files.files.values.single()), repo.submittedPhotos)
    }

    @Test fun failedImportKeepsPreviousSelectionAndDiscardDoesNotWriteEntry() = runTest(dispatcher) {
        val repo = TestJournalRepository(); val files = TestPhotoRepository()
        val editor = JournalEditorViewModel(repo, null, SavedStateHandle(), clock, files)
        advanceUntilIdle(); editor.setBody("Keep"); editor.importPhoto("content://first"); advanceUntilIdle()
        val draft = editor.state.value.draft
        files.failImport = true; editor.importPhoto("content://missing"); advanceUntilIdle()
        assertEquals(draft, editor.state.value.draft)
        assertEquals(JournalEditorError.PHOTO, editor.state.value.error)
        var closed = false
        editor.discardPhotos { closed = true }; advanceUntilIdle()
        assertTrue(closed)
        assertTrue(repo.records.isEmpty())
        assertTrue(files.leases.isEmpty())
    }

    @Test fun photoChangesAfterAmbiguousDeleteStayVisibleInsteadOfFinishingRestoredEditor() = runTest(dispatcher) {
        val repo = TestJournalRepository().apply { failAfterDelete = true }
        val files = TestPhotoRepository()
        val id = EventId.new()
        repo.create(id, JournalInput("Body", clock.instant()))
        val handle = SavedStateHandle()
        val editor = JournalEditorViewModel(repo, id, handle, clock, files)
        advanceUntilIdle(); editor.delete(); advanceUntilIdle()
        editor.importPhoto("content://new-draft"); advanceUntilIdle()
        val photo = editor.state.value.draft.photos.single()
        val restored = JournalEditorViewModel(repo, id, handle, clock, files)
        advanceUntilIdle()
        assertFalse(restored.state.value.completed)
        assertEquals(JournalEditorError.LOAD, restored.state.value.error)
        assertEquals(listOf(photo), restored.state.value.draft.photos)
        assertTrue(files.leases.values.any { photo in it })
    }

    @Test fun rejectionIdentifiesFieldAndKeepsAcceptedText() = runTest(dispatcher) {
        val editor = JournalEditorViewModel(TestJournalRepository(), null, SavedStateHandle(), clock)
        advanceUntilIdle(); editor.setTitle("Accepted"); editor.setBody("Body")
        editor.setTitle("x".repeat(201))
        assertEquals("Accepted", editor.state.value.draft.title)
        assertEquals(setOf(JournalTextField.TITLE), editor.state.value.textErrors)
    }

    @Test fun sameValueAndUnrelatedEditKeepRejection() = runTest(dispatcher) {
        val editor = JournalEditorViewModel(TestJournalRepository(), null, SavedStateHandle(), clock)
        advanceUntilIdle(); editor.setTitle("Title"); editor.setBody("Body")
        editor.setTitle("x".repeat(201)); editor.setTitle("Title"); editor.setBody("Changed")
        assertEquals(setOf(JournalTextField.TITLE), editor.state.value.textErrors)
    }

    @Test fun twoRejectedFieldsClearIndependently() = runTest(dispatcher) {
        val editor = JournalEditorViewModel(TestJournalRepository(), null, SavedStateHandle(), clock)
        advanceUntilIdle(); editor.setTitle("Title"); editor.setBody("Body")
        editor.setTitle("x".repeat(201)); editor.setBody("x".repeat(50_001))
        assertEquals(setOf(JournalTextField.TITLE, JournalTextField.BODY), editor.state.value.textErrors)
        editor.setTitle("Corrected")
        assertEquals(setOf(JournalTextField.BODY), editor.state.value.textErrors)
    }

    @Test fun recreationRestoresFieldFlags() = runTest(dispatcher) {
        val handle = SavedStateHandle(); val repo = TestJournalRepository()
        val editor = JournalEditorViewModel(repo, null, handle, clock)
        advanceUntilIdle(); editor.setBody("Kept body"); editor.setBody("x".repeat(50_001))
        val restored = JournalEditorViewModel(repo, null, handle, clock)
        assertEquals(setOf(JournalTextField.BODY), restored.state.value.textErrors)
        advanceUntilIdle()
        assertEquals("Kept body", restored.state.value.draft.body)
    }

    @Test fun unknownRestoredFieldNameIsIgnored() = runTest(dispatcher) {
        val handle = SavedStateHandle(mapOf("text_error_fields" to arrayListOf("FUTURE_FIELD")))
        val editor = JournalEditorViewModel(TestJournalRepository(), null, handle, clock)
        assertTrue(editor.state.value.textErrors.isEmpty())
    }

    @Test fun acceptedTagClearsOnlyTagFlag() = runTest(dispatcher) {
        val editor = JournalEditorViewModel(TestJournalRepository(), null, SavedStateHandle(), clock)
        advanceUntilIdle(); editor.setBody("x".repeat(50_001)); editor.addTag("Work")
        assertFalse(editor.addTag("x".repeat(51)))
        assertEquals(setOf(JournalTextField.BODY, JournalTextField.TAG), editor.state.value.textErrors)
        assertTrue(editor.addTag(" work "))
        assertEquals(setOf(JournalTextField.BODY), editor.state.value.textErrors)
    }

    private class TestJournalRepository : JournalRepository {
        val records = mutableMapOf<EventId, LifeEventRecord>()
        var creates = 0
        var submittedPeople: Set<java.util.UUID>? = null
        var submittedPlace: PlaceSelection? = null
        var submittedPhotos: List<PhotoAttachment>? = null
        var failAfterCreate = false
        var failLoading = false
        var failDeleting = false
        var failAfterDelete = false
        override fun observeTags() = flowOf(emptyList<Tag>())
        override suspend fun get(id: EventId): LifeEventRecord? {
            if (failLoading) error("Unavailable")
            return records[id]
        }
        override suspend fun create(id: EventId, input: JournalInput): EventId {
            submittedPeople = input.personIds
            submittedPlace = input.placeSelection
            submittedPhotos = input.photos
            creates++
            require(id !in records)
            records[id] = record(id, input)
            if (failAfterCreate) error("Committed result unavailable")
            return id
        }
        override suspend fun update(id: EventId, input: JournalInput) {
            submittedPeople = input.personIds
            submittedPlace = input.placeSelection
            submittedPhotos = input.photos
            require(id in records)
            records[id] = record(id, input)
        }
        override suspend fun delete(id: EventId) {
            if (failDeleting) error("Unavailable")
            records.remove(id)
            if (failAfterDelete) error("Committed delete result unavailable")
        }
        private fun record(id: EventId, input: JournalInput) = LifeEventRecord(
            LifeEvent(id, EventType.JOURNAL, input.occurredAt, input.occurredAt, title = input.title),
            journal = JournalDetails(input.title, input.body),
            people = input.personIds?.map { Person(it, "Friend") } ?: records[id]?.people.orEmpty(),
            place = if (input.placeSelection == null) records[id]?.place else input.placeSelection?.id?.let { Place(it, "Cafe") },
            photos = input.photos ?: records[id]?.photos.orEmpty())
    }
}
