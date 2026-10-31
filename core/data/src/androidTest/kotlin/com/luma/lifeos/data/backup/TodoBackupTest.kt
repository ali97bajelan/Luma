package com.luma.lifeos.data.backup

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.media.ManagedPhotoFiles
import com.luma.lifeos.data.repository.RoomTodoRepository
import com.luma.lifeos.domain.todo.TodoInput
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TodoBackupTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var root: File
    private lateinit var database: LumaDatabase
    private lateinit var backup: RoomBackupService
    private lateinit var todos: RoomTodoRepository
    private val now = Instant.parse("2026-10-11T08:00:00Z")

    @Before fun setUp() {
        root = File(context.cacheDir, "todo-backup-${UUID.randomUUID()}").apply { mkdirs() }
        database = Room.inMemoryDatabaseBuilder(context, LumaDatabase::class.java).build()
        backup = RoomBackupService(
            context,
            database,
            ManagedPhotoFiles(File(root, "photos")),
            File(root, "work"),
            Mutex(),
        )
        todos = RoomTodoRepository(database, Clock.fixed(now, ZoneOffset.UTC))
    }

    @After fun tearDown() {
        database.close()
        root.deleteRecursively()
    }

    private suspend fun export(): ByteArray = ByteArrayOutputStream().also { backup.export(it) }.toByteArray()

    @Test fun schemaFiveRoundTripPreservesTaskDataAndRotatesRevisionTokens() = runBlocking {
        val open = todos.create(
            UUID.randomUUID(),
            UUID.randomUUID(),
            TodoInput("Open task", "Persian فارسی", LocalDate.parse("2026-10-20"), LocalTime.of(9, 5)),
        )
        val undated = todos.create(UUID.randomUUID(), UUID.randomUUID(), TodoInput("Undated task"))
        val createdCompleted = todos.create(
            UUID.randomUUID(),
            UUID.randomUUID(),
            TodoInput("Completed task", dueDate = LocalDate.parse("2026-10-18"), dueTime = LocalTime.of(23, 59)),
        )
        val completed = todos.setCompleted(
            createdCompleted.id,
            createdCompleted.revision,
            UUID.randomUUID(),
            true,
        )
        val archive = export()
        val replaced = todos.create(UUID.randomUUID(), UUID.randomUUID(), TodoInput("Replace this"))

        backup.prepare(ByteArrayInputStream(archive)).use {
            assertEquals(3L, it.preview.todoCount)
            it.restore()
        }

        val restoredOpen = requireNotNull(todos.get(open.id))
        assertEquals(open.title, restoredOpen.title)
        assertEquals(open.note, restoredOpen.note)
        assertEquals(open.dueDate, restoredOpen.dueDate)
        assertEquals(open.dueTime, restoredOpen.dueTime)
        assertEquals(open.createdAt, restoredOpen.createdAt)
        assertEquals(open.updatedAt, restoredOpen.updatedAt)
        assertNull(restoredOpen.completedAt)
        assertNotEquals(open.revision, restoredOpen.revision)

        val restoredUndated = requireNotNull(todos.get(undated.id))
        assertNull(restoredUndated.dueDate)
        assertEquals(undated.createdAt, restoredUndated.createdAt)

        val restoredCompleted = requireNotNull(todos.get(completed.id))
        assertEquals(completed.title, restoredCompleted.title)
        assertEquals(completed.dueDate, restoredCompleted.dueDate)
        assertEquals(completed.dueTime, restoredCompleted.dueTime)
        assertEquals(completed.completedAt, restoredCompleted.completedAt)
        assertEquals(completed.createdAt, restoredCompleted.createdAt)
        assertEquals(completed.updatedAt, restoredCompleted.updatedAt)
        assertNotEquals(completed.revision, restoredCompleted.revision)
        assertNull(todos.get(replaced.id))
    }

    @Test fun schemaFiveRoundTripsNullMidnightAndMinuteBoundaries() = runBlocking {
        val date = LocalDate.parse("2026-10-20")
        val expected = listOf(null, LocalTime.MIDNIGHT, LocalTime.of(9, 5), LocalTime.of(23, 59)).mapIndexed { index, time ->
            todos.create(
                UUID.nameUUIDFromBytes("archive-time-$index".toByteArray()),
                UUID.randomUUID(),
                TodoInput("Time $index", dueDate = date, dueTime = time),
            )
        }
        val archive = export()
        expected.forEach { todos.delete(it.id, it.revision) }

        backup.prepare(ByteArrayInputStream(archive)).use { it.restore() }

        assertEquals(expected.map { it.dueTime }, expected.map { requireNotNull(todos.get(it.id)).dueTime })
    }

    @Test fun schemaFourExactLegacyTaskHeaderImportsWithNullDueTime() = runBlocking {
        val original = todos.create(
            UUID.randomUUID(), UUID.randomUUID(),
            TodoInput("Legacy time is dropped", dueDate = LocalDate.parse("2026-10-20"), dueTime = LocalTime.of(9, 5)),
        )
        val legacy = rewriteTaskArchive(export(), schemaVersion = 4) { columns, rows ->
            val timeIndex = columns.indexOf("due_time_minutes")
            val legacyColumns = columns.filterIndexed { index, _ -> index != timeIndex }
            legacyColumns to rows.map { row -> row.filterIndexed { index, _ -> index != timeIndex } }
        }
        todos.delete(original.id, original.revision)

        backup.prepare(ByteArrayInputStream(legacy)).use { it.restore() }

        assertNull(requireNotNull(todos.get(original.id)).dueTime)
        val payload = File(root, "schema-four-payload")
        val manifest = BackupArchive.read(ByteArrayInputStream(legacy), payload)
        val header = File(payload, "tables/todo_task.tsv").readLines().first()
        assertEquals(
            "id\ttitle\tnote\tdue_date\tcompleted_at\tcreated_at\tupdated_at\trevision_token",
            header,
        )
        assertEquals(4, manifest.schemaVersion)
    }

    @Test fun incompatibleHeadersAndInvalidMinutesFailBeforeReplacingLiveTask() = runBlocking {
        val live = todos.create(UUID.randomUUID(), UUID.randomUUID(), TodoInput("Keep live task"))
        todos.create(
            UUID.randomUUID(), UUID.randomUUID(),
            TodoInput("Archive validation source", dueDate = LocalDate.parse("2026-10-20"), dueTime = LocalTime.of(9, 5)),
        )
        val valid = export()
        val malformed = listOf(
            rewriteTaskArchive(valid, 5) { columns, rows ->
                val index = columns.indexOf("due_time_minutes")
                columns.filterIndexed { i, _ -> i != index } to rows.map { row -> row.filterIndexed { i, _ -> i != index } }
            },
            rewriteTaskArchive(valid, 4) { columns, rows -> columns to rows },
            rewriteTaskArchive(valid, 5) { columns, rows ->
                val index = columns.indexOf("due_time_minutes")
                columns to rows.map { row -> row.toMutableList().apply {
                    if (get(columns.indexOf("title")) == "Archive validation source") set(index, -1L)
                } }
            },
            rewriteTaskArchive(valid, 5) { columns, rows ->
                val index = columns.indexOf("due_time_minutes")
                columns to rows.map { row -> row.toMutableList().apply {
                    if (get(columns.indexOf("title")) == "Archive validation source") set(index, 1440L)
                } }
            },
            rewriteTaskArchive(valid, 5) { columns, rows ->
                val index = columns.indexOf("due_time_minutes")
                columns to rows.map { row -> row.toMutableList().apply {
                    if (get(columns.indexOf("title")) == "Archive validation source") set(index, "545")
                } }
            },
            rewriteTaskArchive(valid, 5) { columns, rows ->
                val dateIndex = columns.indexOf("due_date")
                columns to rows.map { row -> row.toMutableList().apply {
                    if (get(columns.indexOf("title")) == "Archive validation source") set(dateIndex, null)
                } }
            },
        )

        malformed.forEach { archive ->
            assertTrue(runCatching { runBlocking { backup.prepare(ByteArrayInputStream(archive)) } }.isFailure)
            assertEquals(live, todos.get(live.id))
        }
    }

    @Test fun schemaOneThroughThreeReplacementsReportZeroTasksAndClearExistingTasks() = runBlocking {
        for (version in 1..3) {
            val live = todos.create(UUID.randomUUID(), UUID.randomUUID(), TodoInput("Live before schema $version"))
            val fullArchive = export()
            val payload = File(root, "legacy-$version-payload")
            val manifest = BackupArchive.read(ByteArrayInputStream(fullArchive), payload)
            val legacyTableNames = backupTables(version).map { "tables/$it.tsv" }.toSet()
            val includedNames = manifest.names.filter { it in legacyTableNames || it.startsWith("photos/") }.toSet()
            val files = includedNames.associateWith { File(payload, it) }
            val legacyArchive = ByteArrayOutputStream().also {
                BackupArchive.write(it, files, version, manifest.createdAt)
            }.toByteArray()

            backup.prepare(ByteArrayInputStream(legacyArchive)).use {
                assertEquals(0L, it.preview.todoCount)
                it.restore()
            }
            assertNull("schema $version must replace the task table with zero rows", todos.get(live.id))
        }
    }

    @Test fun cancellationAndCorruptTaskRowsLeaveCurrentTaskUntouched() = runBlocking {
        val live = todos.create(UUID.randomUUID(), UUID.randomUUID(), TodoInput("Keep current task"))
        val archive = export()
        backup.prepare(ByteArrayInputStream(archive)).close()
        assertEquals(live, todos.get(live.id))

        val payload = File(root, "corrupt-payload")
        val manifest = BackupArchive.read(ByteArrayInputStream(archive), payload)
        val taskFile = File(payload, "tables/todo_task.tsv")
        val columns = taskFile.readLines().first().split('\t')
        val rows = mutableListOf<List<Any?>>()
        BackupRows.read(taskFile, columns) { rows += it }
        val invalidRow = rows.single().toMutableList().apply { set(columns.indexOf("title"), " ") }
        taskFile.outputStream().use { BackupRows.write(it, columns, sequenceOf(invalidRow)) }
        val corruptArchive = ByteArrayOutputStream().also {
            BackupArchive.write(it, manifest.names.associateWith { name -> File(payload, name) }, CURRENT_BACKUP_SCHEMA_VERSION, manifest.createdAt)
        }.toByteArray()

        assertTrue(runCatching { backup.prepare(ByteArrayInputStream(corruptArchive)) }.isFailure)
        assertEquals(live, todos.get(live.id))
    }

    private fun rewriteTaskArchive(
        archive: ByteArray,
        schemaVersion: Int,
        transform: (List<String>, List<List<Any?>>) -> Pair<List<String>, List<List<Any?>>>,
    ): ByteArray {
        val payload = File(root, "rewrite-${UUID.randomUUID()}")
        val manifest = BackupArchive.read(ByteArrayInputStream(archive), payload)
        val taskFile = File(payload, "tables/todo_task.tsv")
        val columns = taskFile.readLines().first().split('\t')
        val rows = mutableListOf<List<Any?>>()
        BackupRows.read(taskFile, columns) { rows += it }
        val (newColumns, newRows) = transform(columns, rows)
        taskFile.outputStream().use { BackupRows.write(it, newColumns, newRows.asSequence()) }
        val tablesAddedAfterSchemaFive = setOf(
            "tables/task_reminder.tsv",
            "tables/focus_session.tsv",
            "tables/focus_run.tsv",
        )
        val members = manifest.names
            .filterNot { schemaVersion <= 5 && it in tablesAddedAfterSchemaFive }
            .associateWith { name -> File(payload, name) }
        return ByteArrayOutputStream().also { output ->
            BackupArchive.write(
                output,
                members,
                schemaVersion,
                manifest.createdAt,
            )
        }.toByteArray()
    }
}
