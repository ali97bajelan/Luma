package com.luma.lifeos

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.media.ManagedPhotoFiles
import com.luma.lifeos.data.repository.RoomJournalRepository
import com.luma.lifeos.data.repository.RoomPhotoRepository
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.JournalInput
import com.luma.lifeos.ui.journal.JournalEditorScreen
import com.luma.lifeos.ui.journal.JournalEditorViewModel
import com.luma.lifeos.ui.photos.PhotoEditor
import com.luma.lifeos.ui.theme.LifeOsTheme
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Exercises the editor and private storage; native picker/provider behavior remains a device check. */
class PhotoCaptureTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var database: LumaDatabase
    private lateinit var directory: File
    private lateinit var journals: RoomJournalRepository
    private lateinit var photos: RoomPhotoRepository

    @Before fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, LumaDatabase::class.java).build()
        directory = File(context.cacheDir, "photo-ui-${UUID.randomUUID()}")
        journals = RoomJournalRepository(database)
        val bitmap = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888)
        val image = ByteArrayOutputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); out.toByteArray() }
        bitmap.recycle()
        photos = RoomPhotoRepository(database, ManagedPhotoFiles(directory)) { ByteArrayInputStream(image) }
    }
    @After fun tearDown() { database.close(); directory.deleteRecursively() }

    @Test fun attachedImageRendersAndRemovingItKeepsBodyAndTags() {
        val handle = SavedStateHandle()
        var editor by mutableStateOf(JournalEditorViewModel(journals, null, handle, photoRepository = photos))
        show { editor }
        compose.waitUntil(5_000) { editor.state.value.ready }
        compose.runOnIdle { editor.setBody("Keep this entry"); editor.addTag("family"); editor.importPhoto("content://fixture/photo") }
        compose.waitUntil(5_000) { editor.state.value.draft.photos.size == 1 && !editor.state.value.busy }
        val photo = editor.state.value.draft.photos.single()
        compose.onNodeWithTag("photo_add").performScrollTo().assertIsDisplayed()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("photo_preview_${photo.id}").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("journal_save").performClick()
        compose.waitUntil(5_000) { editor.state.value.completed }
        val id = EventId.parse(requireNotNull(handle.get<String>("capture_id")))
        val stored = runBlocking { requireNotNull(journals.get(id)) }
        assertEquals(photo, stored.photos.single())
        assertNotNull(runBlocking { photos.preview(photo) })
        compose.runOnIdle { editor = JournalEditorViewModel(journals, id, SavedStateHandle(), photoRepository = photos) }
        compose.waitUntil(5_000) { editor.state.value.ready }
        compose.onNodeWithTag("photo_remove_${photo.id}").performScrollTo().performClick()
        compose.waitUntil(5_000) { editor.state.value.draft.photos.isEmpty() && !editor.state.value.busy }
        compose.onNodeWithTag("journal_save").performClick()
        compose.waitUntil(5_000) { editor.state.value.completed }
        val updated = runBlocking { requireNotNull(journals.get(id)) }
        assertEquals("Keep this entry", updated.journal?.body)
        assertEquals(listOf("family"), updated.journal?.tags?.map { it.name })
        assertTrue(updated.photos.isEmpty())
        assertNull(runBlocking { photos.preview(photo) })
    }

    @Test fun missingPhotoShowsUnavailableAndCanBeRemovedFromSavedEntry() {
        val owner = UUID.randomUUID()
        val photo = runBlocking { photos.import(owner, UUID.randomUUID(), "content://fixture/photo") }
        val id = EventId.new()
        runBlocking { journals.create(id, JournalInput("Body stays", Instant.now(), photos = listOf(photo))); photos.releaseDraft(owner) }
        assertTrue(File(directory, "${photo.id}.img").delete())
        val editor = JournalEditorViewModel(journals, id, SavedStateHandle(), photoRepository = photos)
        show { editor }
        compose.waitUntil(5_000) { editor.state.value.ready }
        compose.onNodeWithTag("photo_remove_${photo.id}").performScrollTo()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("photo_unavailable_${photo.id}").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("photo_unavailable_${photo.id}").assertExists()
        compose.onNodeWithTag("photo_remove_${photo.id}").performClick()
        compose.waitUntil(5_000) { !editor.state.value.busy }
        compose.onNodeWithTag("journal_save").performClick()
        compose.waitUntil(5_000) { editor.state.value.completed }
        assertEquals("Body stays", runBlocking { journals.get(id) }?.journal?.body)
        assertTrue(runBlocking { journals.get(id) }!!.photos.isEmpty())
    }

    private fun show(editor: () -> JournalEditorViewModel) {
        compose.setContent {
            LifeOsTheme {
                val viewModel = editor()
                key(viewModel) {
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    JournalEditorScreen(state, {}, viewModel::save, viewModel::delete, viewModel::load,
                        viewModel::setTitle, viewModel::setBody, viewModel::addTag, viewModel::removeTag, viewModel::setLocalTime,
                        photosContent = { PhotoEditor(photos, state.draft.photos, state.ready && !state.busy && !state.completed,
                            viewModel::importPhoto, viewModel::removePhoto, viewModel::retryPhoto, false) })
                }
            }
        }
    }
}
