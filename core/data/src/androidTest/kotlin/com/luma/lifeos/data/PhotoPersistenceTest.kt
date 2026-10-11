package com.luma.lifeos.data

import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.media.ManagedPhotoFiles
import com.luma.lifeos.data.repository.*
import com.luma.lifeos.domain.model.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Clock
import java.time.ZoneOffset
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PhotoPersistenceTest {
    private lateinit var db: LumaDatabase
    private lateinit var root: File
    private lateinit var files: ManagedPhotoFiles
    private lateinit var photos: RoomPhotoRepository
    private lateinit var png: ByteArray
    private val now = Instant.parse("2026-10-11T08:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    @Before fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, LumaDatabase::class.java).build()
        root = File(context.cacheDir, "photo-test-${UUID.randomUUID()}")
        files = ManagedPhotoFiles(root)
        val bitmap = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888)
        png = ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() }
        bitmap.recycle()
        photos = RoomPhotoRepository(db, files) { ByteArrayInputStream(png) }
    }
    @After fun tearDown() { db.close(); root.deleteRecursively() }

    @Test fun copiedImageAndMetadataSurviveReopeningWithoutOriginalSource() = runBlocking {
        val owner = UUID.randomUUID(); val id = UUID.randomUUID()
        val photo = photos.import(owner, id, "content://selected/image")
        assertEquals(PhotoMetadata("image/png", png.size.toLong(), 3, 2), photo.metadata)
        val event = EventId.new(); val journals = RoomJournalRepository(db, clock)
        val input = JournalInput("Body", now, photos = listOf(photo))
        journals.create(event, input); journals.create(event, input)
        photos.releaseDraft(owner)
        val reopened = RoomPhotoRepository(db, ManagedPhotoFiles(root)) { error("Source was removed") }
        assertEquals(photo, journals.get(event)!!.photos.single())
        assertNotNull(reopened.preview(journals.get(event)!!.photos.single()))
        assertEquals(photo, reopened.import(UUID.randomUUID(), id, "content://gone"))
        assertTrue(runCatching { journals.create(event, input.copy(photos = emptyList())) }.isFailure)
    }
    @Test fun committedPhotoSurvivesClosingAndReopeningTheDatabaseAndPrivateStore() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "photo-reopen-${UUID.randomUUID()}.db"
        var persistent = Room.databaseBuilder(context, LumaDatabase::class.java, name).build()
        try {
            val owner = UUID.randomUUID()
            val originalFiles = RoomPhotoRepository(persistent, ManagedPhotoFiles(root)) { ByteArrayInputStream(png) }
            val photo = originalFiles.import(owner, UUID.randomUUID(), "content://selected")
            val id = EventId.new()
            RoomJournalRepository(persistent, clock).create(id, JournalInput("Survives restart", now, photos = listOf(photo)))
            originalFiles.releaseDraft(owner)
            persistent.close()
            persistent = Room.databaseBuilder(context, LumaDatabase::class.java, name).build()
            val reopenedFiles = RoomPhotoRepository(persistent, ManagedPhotoFiles(root)) { error("Original source is unavailable") }
            val restored = requireNotNull(RoomJournalRepository(persistent, clock).get(id))
            assertEquals("Survives restart", restored.journal?.body)
            assertEquals(photo, restored.photos.single())
            reopenedFiles.cleanup()
            assertNotNull(reopenedFiles.preview(restored.photos.single()))
        } finally {
            persistent.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun failedReferenceWriteRetainsOriginalAndDraftFilesAndRollsBackBodyAndTags() = runBlocking {
        val owner = UUID.randomUUID()
        val original = photos.import(owner, UUID.randomUUID(), "content://first")
        val event = EventId.new(); val journals = RoomJournalRepository(db, clock)
        journals.create(event, JournalInput("Before", now, tagNames = setOf("Work"), photos = listOf(original)))
        photos.releaseDraft(owner)
        val replacement = photos.import(owner, UUID.randomUUID(), "content://second")
        val before = db.lifeEvents().getDetails(event.toString())!!
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_photo BEFORE INSERT ON media_attachment BEGIN SELECT RAISE(ABORT, 'test photo failure'); END")
        assertTrue(runCatching { journals.update(event, JournalInput("After", now, tagNames = setOf("New"), photos = listOf(replacement))) }.isFailure)
        assertEquals(before, db.lifeEvents().getDetails(event.toString()))
        assertEquals(1, db.catalog().observeTags().first().size)
        photos.cleanup()
        assertNotNull(files.resolve(original.relativePath)); assertNotNull(files.resolve(replacement.relativePath))
        photos.releaseDraft(owner)
        assertNotNull(files.resolve(original.relativePath)); assertNull(files.resolve(replacement.relativePath))
    }
    @Test fun promotionAndUnmarkShareSourcePhotosAndRemovalKeepsBodyAndAllOtherLinks() = runBlocking {
        val owner = UUID.randomUUID()
        val first = photos.import(owner, UUID.randomUUID(), "content://first")
        val event = EventId.new(); val journals = RoomJournalRepository(db, clock); val memories = RoomMemoryRepository(db, clock)
        journals.create(event, JournalInput("Keep", now, tagNames = setOf("Work"), photos = listOf(first)))
        val before = db.lifeEvents().getDetails(event.toString())!!
        memories.saveMarker(event, MemoryMarkerInput(5))
        assertEquals(listOf(first), memories.get(event)!!.photos)
        memories.removeMarker(event)
        assertEquals(before, db.lifeEvents().getDetails(event.toString()))
        journals.update(event, JournalInput("Keep", now, tagNames = setOf("Work"), photos = emptyList()))
        val remaining = db.lifeEvents().getDetails(event.toString())!!
        assertEquals(before.copy(attachments = emptyList()), remaining)
        photos.releaseDraft(owner)
        assertNull(files.resolve(first.relativePath))
    }
    @Test fun missingOrCorruptFilesRenderUnavailableWhileReferencesAndEntryRemainEditable() = runBlocking {
        val owner = UUID.randomUUID()
        val photo = photos.import(owner, UUID.randomUUID(), "content://selected")
        val event = EventId.new(); val memories = RoomMemoryRepository(db, clock)
        memories.create(event, MemoryInput("Keep", now, photos = listOf(photo)))
        files.resolve(photo.relativePath)!!.writeText("Not an image")
        assertNull(photos.preview(photo))
        files.resolve(photo.relativePath)!!.delete()
        assertNull(photos.preview(photo))
        memories.update(event, MemoryInput("Keep edited", now))
        assertEquals(listOf(photo), memories.get(event)!!.photos)
        memories.update(event, MemoryInput("Keep edited", now, photos = emptyList()))
        assertTrue(memories.get(event)!!.photos.isEmpty())
        assertEquals("Keep edited", memories.get(event)!!.memory!!.note)
        photos.releaseDraft(owner)
        val bad = RoomPhotoRepository(db, files) { ByteArrayInputStream("Not an image".toByteArray()) }
        assertTrue(runCatching { bad.import(owner, UUID.randomUUID(), "content://bad") }.isFailure)
    }
    @Test fun eventDeletionCommitsBeforeCleanupAndAnotherDraftCanProtectTheSameFile() = runBlocking {
        val owner = UUID.randomUUID(); val other = UUID.randomUUID()
        val photo = photos.import(owner, UUID.randomUUID(), "content://selected")
        val event = EventId.new(); val memories = RoomMemoryRepository(db, clock)
        memories.create(event, MemoryInput("Moment", now, photos = listOf(photo)))
        photos.releaseDraft(owner)
        photos.retainDraft(other, listOf(photo))
        memories.delete(event); memories.delete(event)
        assertNull(memories.get(event))
        photos.cleanup(); assertNotNull(files.resolve(photo.relativePath))
        photos.releaseDraft(other); assertNull(files.resolve(photo.relativePath))
    }
    @Test fun conflictingPhotoIdentityRollsBackNewCaptureAndExistingMetadataIsPreserved() = runBlocking {
        val owner = UUID.randomUUID()
        val photo = photos.import(owner, UUID.randomUUID(), "content://selected")
        val journal = EventId.new(); val journals = RoomJournalRepository(db, clock)
        journals.create(journal, JournalInput("Before", now, photos = listOf(photo)))
        db.openHelper.writableDatabase.execSQL("UPDATE media_attachment SET metadata_json = '{\"custom\":true}' WHERE life_event_id = ?", arrayOf(journal.toString()))
        journals.update(journal, JournalInput("After", now, photos = journals.get(journal)!!.photos))
        assertEquals("{\"custom\":true}", db.content().attachments(journal.toString()).single().metadataJson)
        val memory = EventId.new()
        assertTrue(runCatching { RoomMemoryRepository(db, clock).create(memory, MemoryInput("Must rollback", now, photos = listOf(photo))) }.isFailure)
        assertNull(db.lifeEvents().get(memory.toString()))
        assertEquals("After", journals.get(journal)!!.journal!!.body)
        photos.cleanup(); assertNotNull(files.resolve(photo.relativePath))
    }
}
