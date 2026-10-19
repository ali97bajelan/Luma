package com.luma.lifeos.data.backup

import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.media.ManagedPhotoFiles
import com.luma.lifeos.data.repository.*
import com.luma.lifeos.domain.model.*
import com.luma.lifeos.domain.todo.TodoInput
import java.io.*
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import org.junit.*
import org.junit.Assert.*

class BackupPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var root: File
    private lateinit var db: LumaDatabase
    private lateinit var files: ManagedPhotoFiles
    private lateinit var backup: RoomBackupService
    private lateinit var photoRepository: RoomPhotoRepository
    private val now = Instant.parse("2026-10-11T08:00:00Z")
    @Before fun setup() {
        root = File(context.cacheDir, "backup-test-${UUID.randomUUID()}").apply { mkdirs() }
        db = Room.inMemoryDatabaseBuilder(context, LumaDatabase::class.java).build()
        files = ManagedPhotoFiles(File(root, "photos"))
        val mutex = Mutex()
        backup = RoomBackupService(context, db, files, File(root, "work"), mutex)
        val bitmap = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888)
        val png = ByteArrayOutputStream().apply { bitmap.compress(Bitmap.CompressFormat.PNG, 100, this) }.toByteArray()
        bitmap.recycle()
        photoRepository = RoomPhotoRepository(db, files, mutex) { ByteArrayInputStream(png) }
    }
    @After fun teardown() { db.close(); root.deleteRecursively() }
    private suspend fun export(): ByteArray = ByteArrayOutputStream().also { backup.export(it) }.toByteArray()

    @Test fun roundTripPreservesRelationsMetadataPhotosAndStableIds() = runBlocking {
        val person = RoomPersonRepository(db).create(UUID.randomUUID(), PersonInput("فارسی", "private"))
        val place = RoomPlaceRepository(db).create(UUID.randomUUID(), PlaceInput("Paris", "note"))
        val owner = UUID.randomUUID()
        val photo = photoRepository.import(owner, UUID.randomUUID(), "content://photo")
        val id = EventId.new()
        val journals = RoomJournalRepository(db)
        journals.create(id, JournalInput("Multiline\nFrançais", now, "Title", setOf("Travel"), personIds = setOf(person), placeSelection = PlaceSelection(place), photos = listOf(photo)))
        RoomMemoryRepository(db).saveMarker(id, MemoryMarkerInput(5, "keep"))
        photoRepository.releaseDraft(owner)
        val original = db.lifeEvents().getDetails(id.toString())!!
        val image = files.resolve(photo.relativePath)!!.readBytes()
        val bytes = export()
        journals.delete(id)
        val extra = EventId.new(); journals.create(extra, JournalInput("Replace me", now))
        backup.prepare(ByteArrayInputStream(bytes)).use { prepared ->
            assertEquals(1L, prepared.preview.eventCount); assertEquals(1, prepared.preview.photoCount)
            prepared.restore()
        }
        val restored = db.lifeEvents().getDetails(id.toString())!!
        assertEquals(original.copy(attachments = restored.attachments), restored)
        assertEquals(original.attachments.single().copy(relativePath = restored.attachments.single().relativePath), restored.attachments.single())
        assertArrayEquals(image, files.resolve(restored.attachments.single().relativePath)!!.readBytes())
        assertNull(journals.get(extra))
        assertFalse(File(root, "work").walkTopDown().any { it.name == "stage.db" })
    }
    @Test fun failedCommitPreservesCurrentRecordsAndPhotos() = runBlocking {
        val owner = UUID.randomUUID(); val photo = photoRepository.import(owner, UUID.randomUUID(), "content://photo")
        val id = EventId.new(); val journals = RoomJournalRepository(db)
        journals.create(id, JournalInput("Before", now, photos = listOf(photo)))
        photoRepository.releaseDraft(owner)
        val bytes = export()
        journals.update(id, JournalInput("Still current", now))
        val original = db.lifeEvents().getDetails(id.toString())!!
        val image = files.resolve(photo.relativePath)!!.readBytes()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_restore BEFORE INSERT ON journal_entry BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        val prepared = backup.prepare(ByteArrayInputStream(bytes))
        assertTrue(runCatching { prepared.restore() }.isFailure)
        assertEquals(original, db.lifeEvents().getDetails(id.toString()))
        assertArrayEquals(image, files.resolve(photo.relativePath)!!.readBytes())
        assertEquals(1, File(root, "photos").listFiles()!!.count { it.extension == "img" })
        prepared.close()
    }
    @Test fun invalidBackupAndCancelledPreviewLeaveLocalDataUntouched() = runBlocking {
        val id = EventId.new(); val journals = RoomJournalRepository(db)
        journals.create(id, JournalInput("Keep", now))
        val bytes = export()
        backup.prepare(ByteArrayInputStream(bytes)).close()
        assertEquals("Keep", journals.get(id)!!.journal!!.body)
        assertTrue(runCatching { backup.prepare(ByteArrayInputStream("bad".toByteArray())) }.isFailure)
        assertEquals("Keep", journals.get(id)!!.journal!!.body)
    }
    @Test fun moodEmotionsSessionsAndDerivedSummarySurviveRoundTrip() = runBlocking {
        val emotion = UUID.randomUUID()
        db.catalog().upsertEmotion(com.luma.lifeos.data.entity.EmotionEntity(emotion.toString(), "Content"))
        val id = EventId.new()
        RoomMoodRepository(db).create(id, MoodInput(4, now, setOf(emotion), energy = 2, stress = 3, note = "Keep note"))
        val original = db.lifeEvents().getDetails(id.toString())!!
        val session = com.luma.lifeos.data.entity.UsageSessionEntity("example.app", now.minusSeconds(60).toEpochMilli(), now.toEpochMilli(), "Example", false)
        val window = com.luma.lifeos.data.entity.UsageSessionWindowEntity(now.minusSeconds(3600).toEpochMilli(), now.toEpochMilli(), true)
        db.usageSessions().upsert(listOf(session)); db.usageSessions().upsertWindow(window)
        val summary = com.luma.lifeos.data.entity.DailySummaryEntity("2026-10-11", "UTC", "{}", 3, now.toEpochMilli())
        db.derived().upsertSummary(summary)
        val bytes = export()
        backup.prepare(ByteArrayInputStream(bytes)).use { it.restore() }
        assertEquals(original, db.lifeEvents().getDetails(id.toString()))
        assertEquals(listOf(session), db.usageSessions().forDay(window.queryStart, window.queryEnd))
        assertEquals(listOf(window), db.usageSessions().windows(window.queryStart, window.queryEnd))
        assertEquals(summary.copy(freshness = com.luma.lifeos.data.entity.SummaryFreshness.STALE), db.derived().summary("2026-10-11", "UTC"))
    }
    @Test fun restoreInvalidatesAnAlreadyObservedCatalog() = runBlocking {
        val people = RoomPersonRepository(db); val id = UUID.randomUUID()
        people.create(id, PersonInput("Original")); val bytes = export()
        people.update(id, PersonInput("Changed"))
        val observedChanged = kotlinx.coroutines.CompletableDeferred<Unit>()
        val result = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            kotlinx.coroutines.withTimeout(5000) {
                people.observePeople().onEach { rows -> if (rows.singleOrNull()?.name == "Changed") observedChanged.complete(Unit) }
                    .first { rows -> rows.singleOrNull()?.name == "Original" }
            }
        }
        kotlinx.coroutines.withTimeout(5000) { observedChanged.await() }
        backup.prepare(ByteArrayInputStream(bytes)).use { it.restore() }
        assertEquals("Original", result.await().single().name)
    }
    @Test fun nextOperationRemovesOnlyAbandonedPrivateStaging() = runBlocking {
        val bytes = export()
        val abandoned = File(root, "work/${UUID.randomUUID()}").apply { mkdirs() }
        File(abandoned, "private.tsv").writeText("abandoned backup")
        backup = RoomBackupService(context, db, files, File(root, "work"), Mutex())
        backup.prepare(ByteArrayInputStream(bytes)).use { assertFalse(abandoned.exists()) }
    }
    @Test fun previouslyRestoredPhotosCanBeExportedAndValidatedAgain() = runBlocking {
        val owner = UUID.randomUUID(); val photo = photoRepository.import(owner, UUID.randomUUID(), "content://photo")
        RoomJournalRepository(db).create(EventId.new(), JournalInput("Body", now, photos = listOf(photo)))
        val first = export()
        backup.prepare(ByteArrayInputStream(first)).use { it.restore() }
        backup.prepare(ByteArrayInputStream(export())).use { it.restore() }
    }
    @Test fun schemaOneBackupRetainsContentWithoutInventingSessionHistory() = runBlocking {
        val id = EventId.new(); RoomJournalRepository(db).create(id, JournalInput("Version one", now))
        val taskId = UUID.randomUUID()
        RoomTodoRepository(db).create(taskId, UUID.randomUUID(), TodoInput("Removed by old archive"))
        val payload = File(root, "v1-payload")
        val manifest = BackupArchive.read(ByteArrayInputStream(export()), payload)
        val v1Tables = backupTables(1).map { "tables/$it.tsv" }.toSet()
        val files = manifest.names.filter { it.startsWith("photos/") || it in v1Tables }
            .associateWith { File(payload, it) }
        val v1 = ByteArrayOutputStream().also { BackupArchive.write(it, files, 1, 1234) }.toByteArray()
        RoomJournalRepository(db).delete(id)
        backup.prepare(ByteArrayInputStream(v1)).use {
            assertEquals(0L, it.preview.todoCount)
            it.restore()
        }
        assertEquals("Version one", RoomJournalRepository(db).get(id)!!.journal!!.body)
        assertNull(RoomTodoRepository(db).get(taskId))
        assertEquals(0, db.openHelper.readableDatabase.query("SELECT * FROM usage_session").use { it.count })
    }

    @Test fun schemaFourTaskRoundTripPreservesRowsAndRotatesRevisions() = runBlocking {
        val clock = Clock.fixed(now, ZoneOffset.UTC)
        val todos = RoomTodoRepository(db, clock)
        val openId = UUID.randomUUID()
        val openRevision = UUID.randomUUID()
        val open = todos.create(
            openId,
            openRevision,
            TodoInput("Open task", "Keep this note", LocalDate.parse("2026-10-20")),
        )
        val completedId = UUID.randomUUID()
        val completedRevision = UUID.randomUUID()
        val createdCompleted = todos.create(
            completedId,
            completedRevision,
            TodoInput("Completed task", dueDate = LocalDate.parse("2026-10-18")),
        )
        val completed = todos.setCompleted(completedId, completedRevision, UUID.randomUUID(), true)
        val bytes = export()
        val extraId = UUID.randomUUID()
        todos.create(extraId, UUID.randomUUID(), TodoInput("Must be replaced"))

        backup.prepare(ByteArrayInputStream(bytes)).use {
            assertEquals(2L, it.preview.todoCount)
            it.restore()
        }

        val restoredOpen = requireNotNull(todos.get(openId))
        assertEquals(open.title, restoredOpen.title)
        assertEquals(open.note, restoredOpen.note)
        assertEquals(open.dueDate, restoredOpen.dueDate)
        assertEquals(open.createdAt, restoredOpen.createdAt)
        assertNull(restoredOpen.completedAt)
        assertNotEquals(open.revision, restoredOpen.revision)

        val restoredCompleted = requireNotNull(todos.get(completedId))
        assertEquals(createdCompleted.title, restoredCompleted.title)
        assertEquals(completed.dueDate, restoredCompleted.dueDate)
        assertEquals(completed.completedAt, restoredCompleted.completedAt)
        assertEquals(completed.createdAt, restoredCompleted.createdAt)
        assertNotEquals(completed.revision, restoredCompleted.revision)
        assertNull(todos.get(extraId))
    }
    @Test fun checksummedInvalidDomainRowsAndReferencesAreRejectedBeforeReplacement() = runBlocking {
        val id = EventId.new(); RoomJournalRepository(db).create(id, JournalInput("Keep", now))
        val bytes = export()
        for (variant in listOf("body", "reference", "enum", "mismatched_type", "missing_table")) {
            val payload = File(root, "invalid-$variant")
            val manifest = BackupArchive.read(ByteArrayInputStream(bytes), payload)
            val names = manifest.names.toMutableSet()
            val table = if (variant in setOf("enum", "mismatched_type")) "life_event" else "journal_entry"
            val file = File(payload, "tables/$table.tsv")
            val columns = file.readLines().first().split('\t')
            val rows = mutableListOf<List<Any?>>()
            BackupRows.read(file, columns) { rows += it }
            if (variant == "missing_table") names.remove("tables/emotion.tsv")
            else {
                val index = columns.indexOf(when (variant) { "body" -> "body"; "enum", "mismatched_type" -> "type"; else -> "life_event_id" })
                val changed = rows.single().toMutableList()
                changed[index] = when (variant) { "body" -> " "; "enum" -> "UNKNOWN"; "mismatched_type" -> "MOOD"; else -> UUID.randomUUID().toString() }
                file.outputStream().use { BackupRows.write(it, columns, sequenceOf(changed)) }
            }
            val changed = ByteArrayOutputStream().also { out -> BackupArchive.write(out, names.associateWith { File(payload, it) }, 2, 1234) }.toByteArray()
            assertTrue(runCatching { backup.prepare(ByteArrayInputStream(changed)) }.isFailure)
            assertEquals("Keep", RoomJournalRepository(db).get(id)!!.journal!!.body)
        }
    }
    @Test fun healthRestoreRetainsImportZoneAndRejectsOldRequestsAfterReenable() = runBlocking {
        val health = RoomHealthRepository(db)
        val zone = java.time.ZoneId.of("Asia/Tehran")
        health.setEnabled(true, zone)
        val old = health.state()
        val rows = listOf(com.luma.lifeos.domain.health.StepObservation(com.luma.lifeos.domain.health.stepWindows(now, zone).last(), 12))
        health.commit(rows, now, old)
        val bytes = export()
        backup.prepare(ByteArrayInputStream(bytes)).use { it.restore() }
        assertFalse(health.state().enabled)
        assertEquals(zone, health.state().importZone)
        assertTrue(health.state().revision > old.revision)
        health.setEnabled(true, java.time.ZoneId.of("UTC"))
        assertEquals(zone, health.state().importZone)
        assertTrue(runCatching { health.commit(rows, now, old) }.exceptionOrNull() is com.luma.lifeos.domain.health.HealthImportChangedException)
    }
    @Test fun missingReferencedPhotoAbortsExportWithoutClaimingSuccess() = runBlocking {
        val owner = UUID.randomUUID(); val photo = photoRepository.import(owner, UUID.randomUUID(), "content://photo")
        RoomJournalRepository(db).create(EventId.new(), JournalInput("Body", now, photos = listOf(photo)))
        files.resolve(photo.relativePath)!!.delete()
        assertTrue(runCatching { export() }.isFailure)
    }
    @Test fun displacedUsageParentAndNativeAggregateBoundsRemainDistinct() = runBlocking {
        val usage = RoomUsageRepository(db); usage.setEnabled(true)
        val bucket = com.luma.lifeos.domain.usage.UsageBucket("example.app", now.minusSeconds(3600), now, 500)
        usage.commit(listOf(bucket), now, now, null)
        val parent = db.lifeEvents().findBySource(EventSource.USAGE_STATS, bucket.sourceReference)!!
        val shifted = parent.copy(occurredAt = parent.occurredAt - 3600000)
        db.lifeEvents().update(shifted)
        val bytes = export()
        backup.prepare(ByteArrayInputStream(bytes)).use { it.restore() }
        assertEquals(shifted, db.lifeEvents().get(parent.id))
        assertEquals(bucket.start.toEpochMilli(), db.lifeEvents().getDetails(parent.id)!!.appUsage!!.bucketStart)
    }
    @Test fun restoreDisablesImportsAndRetainsSavedUsageHistory() = runBlocking {
        val usage = RoomUsageRepository(db)
        usage.setEnabled(true)
        usage.commit(listOf(com.luma.lifeos.domain.usage.UsageBucket("example.app", now.minusSeconds(3600), now, 500)), now, now, null)
        val bytes = export()
        backup.prepare(ByteArrayInputStream(bytes)).use { it.restore() }
        assertFalse(usage.state().enabled)
        assertNull(usage.state().checkpoint)
        assertEquals(now, usage.state().lastSync)
        assertEquals(1, db.openHelper.readableDatabase.query("SELECT * FROM app_usage_record").use { it.count })
    }
}
