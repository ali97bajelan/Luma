package com.luma.lifeos.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.data.backup.BackupArchive
import com.luma.lifeos.data.backup.BackupRows
import com.luma.lifeos.data.backup.RoomBackupService
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.media.ManagedPhotoFiles
import com.luma.lifeos.data.repository.RoomHabitRepository
import com.luma.lifeos.data.repository.RoomJournalRepository
import com.luma.lifeos.domain.habit.HabitLogInput
import com.luma.lifeos.domain.habit.HabitSaveInput
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.JournalInput
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HabitBackupTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val today = LocalDate.parse("2026-10-17")
    private val zone = ZoneId.of("Asia/Tehran")
    private val now = Instant.parse("2026-10-17T08:00:00Z")
    private lateinit var root: File
    private lateinit var database: LumaDatabase
    private lateinit var photos: ManagedPhotoFiles
    private lateinit var backup: RoomBackupService
    private lateinit var habits: RoomHabitRepository

    @Before fun setUp() {
        root = File(context.cacheDir, "habit-backup-${UUID.randomUUID()}").apply { mkdirs() }
        database = Room.inMemoryDatabaseBuilder(context, LumaDatabase::class.java).build()
        photos = ManagedPhotoFiles(File(root, "photos"))
        backup = RoomBackupService(context, database, photos, File(root, "work"), Mutex())
        habits = RoomHabitRepository(database, Clock.fixed(now, ZoneOffset.UTC))
    }

    @After fun tearDown() { database.close(); root.deleteRecursively() }

    private suspend fun export(): ByteArray = ByteArrayOutputStream().also { backup.export(it) }.toByteArray()

    private suspend fun createHabit(name: String = "Water"): UUID {
        val id = UUID.randomUUID()
        habits.create(id, HabitSaveInput(name, "glasses", today.minusDays(2), 2, DayOfWeek.entries.toSet(), zone))
        habits.log(UUID.randomUUID(), HabitLogInput(id, today, zone, null))
        return id
    }

    private fun archiveAs(bytes: ByteArray, version: Int): ByteArray {
        val folder = File(root, "decoded-${UUID.randomUUID()}")
        val manifest = BackupArchive.read(ByteArrayInputStream(bytes), folder)
        val files = com.luma.lifeos.data.backup.backupTables(version).associate { table ->
            "tables/$table.tsv" to File(folder, "tables/$table.tsv")
        } + manifest.names.filter { it.startsWith("photos/") }.associateWith { File(folder, it) }
        val result = ByteArrayOutputStream().also { BackupArchive.write(it, files, version, manifest.createdAt) }.toByteArray()
        folder.deleteRecursively()
        return result
    }

    @Test fun schemaThreeRoundTripKeepsDateOnlyLogsAndDisablesAndInvalidatesReminderState() = runBlocking {
        val habitId = createHabit()
        val oldRevision = database.openHelper.readableDatabase.query(
            "SELECT revision_token FROM habit_reminder WHERE habit_id = ?", arrayOf(habitId.toString()),
        ).use { assertTrue(it.moveToFirst()); it.getString(0) }
        val consumedAction = UUID.randomUUID().toString()
        database.openHelper.writableDatabase.execSQL(
            "UPDATE habit_reminder SET enabled = 1, times_json = '[540]' WHERE habit_id = ?", arrayOf(habitId.toString()),
        )
        database.openHelper.writableDatabase.execSQL(
            "UPDATE habit_reminder_runtime SET last_delivered_slot = '2026-10-17T09:00', consumed_action_token = ?, needs_reconcile = 0 WHERE habit_id = ?",
            arrayOf(consumedAction, habitId.toString()),
        )

        val bytes = export()
        backup.prepare(ByteArrayInputStream(bytes)).use { prepared ->
            assertEquals(1L, prepared.preview.habitCount)
            assertEquals(1L, prepared.preview.habitLogCount)
            assertEquals(0L, prepared.preview.eventCount)
            prepared.restore()
        }

        val log = requireNotNull(habits.history(habitId, today, 0, 10).logs.singleOrNull())
        assertNull(log.occurredAt)
        assertEquals(zone, log.zone)
        val reminder = database.openHelper.readableDatabase.query(
            "SELECT enabled, revision_token FROM habit_reminder WHERE habit_id = ?", arrayOf(habitId.toString()),
        ).use { assertTrue(it.moveToFirst()); it.getInt(0) to it.getString(1) }
        assertEquals(0, reminder.first)
        assertNotEquals(oldRevision, reminder.second)
        database.openHelper.readableDatabase.query(
            "SELECT revision_token, last_delivered_slot, consumed_action_token, snooze_slot, snooze_until, needs_reconcile FROM habit_reminder_runtime WHERE habit_id = ?",
            arrayOf(habitId.toString()),
        ).use {
            assertTrue(it.moveToFirst())
            assertEquals(reminder.second, it.getString(0))
            assertTrue(it.isNull(1)); assertTrue(it.isNull(2)); assertTrue(it.isNull(3)); assertTrue(it.isNull(4))
            assertEquals(1, it.getInt(5))
        }
    }

    @Test fun schemaOneAndTwoReplacementClearsLiveHabitsAndKeepsLegacyRecords() = runBlocking {
        val journal = RoomJournalRepository(database)
        val eventId = EventId.new()
        journal.create(eventId, JournalInput("Retained journal", now))
        val current = export()
        for (version in listOf(1, 2)) {
            val habitId = createHabit("Live $version")
            val archive = archiveAs(current, version)
            backup.prepare(ByteArrayInputStream(archive)).use { prepared ->
                assertEquals(0L, prepared.preview.habitCount)
                assertEquals(0L, prepared.preview.habitLogCount)
                prepared.restore()
            }
            assertEquals("Retained journal", journal.get(eventId)!!.journal!!.body)
            assertNull(habits.get(habitId))
            database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM habit_log").use {
                assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0))
            }
        }
    }

    @Test fun invalidHabitZoneIsRejectedBeforeReplacingCurrentRows() = runBlocking {
        val journal = RoomJournalRepository(database)
        val eventId = EventId.new()
        journal.create(eventId, JournalInput("Keep current", now))
        val habitId = createHabit()
        val original = export()
        val folder = File(root, "tamper-${UUID.randomUUID()}")
        val manifest = BackupArchive.read(ByteArrayInputStream(original), folder)
        val file = File(folder, "tables/habit_log.tsv")
        val header = file.readLines().first().split('\t')
        val rows = mutableListOf<List<Any?>>()
        BackupRows.read(file, header) { rows += it }
        val changed = rows.single().toMutableList().also { it[header.indexOf("zone_id")] = "UTC" }
        file.outputStream().use { BackupRows.write(it, header, sequenceOf(changed)) }
        val payloads = com.luma.lifeos.data.backup.backupTables(3).associate { "tables/$it.tsv" to File(folder, "tables/$it.tsv") }
        val invalid = ByteArrayOutputStream().also { BackupArchive.write(it, payloads, 3, manifest.createdAt) }.toByteArray()

        assertTrue(runCatching { backup.prepare(ByteArrayInputStream(invalid)) }.isFailure)
        assertEquals("Keep current", journal.get(eventId)!!.journal!!.body)
        assertEquals("Water", habits.get(habitId)!!.name)
    }

    @Test fun failedReplacementRollsBackBothHabitAndLegacyTables() = runBlocking {
        val journal = RoomJournalRepository(database)
        val eventId = EventId.new()
        journal.create(eventId, JournalInput("Archived copy", now))
        val archivedHabit = createHabit("Archived habit")
        val bytes = export()

        journal.update(eventId, JournalInput("Current journal", now))
        val currentHabit = createHabit("Current habit")
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_habit_restore BEFORE INSERT ON habit_log BEGIN SELECT RAISE(ABORT, 'injected restore failure'); END",
        )
        val prepared = backup.prepare(ByteArrayInputStream(bytes))
        assertTrue(runCatching { prepared.restore() }.isFailure)
        prepared.close()

        assertEquals("Current journal", journal.get(eventId)!!.journal!!.body)
        assertEquals("Current habit", habits.get(currentHabit)!!.name)
        assertEquals("Archived habit", habits.get(archivedHabit)!!.name)
    }
}
