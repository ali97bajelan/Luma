package com.luma.lifeos.data.backup

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.media.ManagedPhotoFiles
import com.luma.lifeos.data.repository.RoomTodoRepository
import com.luma.lifeos.domain.todo.TodoChildProgress
import com.luma.lifeos.domain.todo.TodoImportance
import com.luma.lifeos.domain.todo.TodoInput
import com.luma.lifeos.domain.todo.TodoListQuery
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.flow.first
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

    @Test fun currentSchemaRoundTripPreservesTaskDataAndRotatesRevisionTokens() = runBlocking {
        val open = todos.create(
            UUID.randomUUID(),
            UUID.randomUUID(),
            TodoInput("Open task", "Persian فارسی", LocalDate.parse("2026-10-20"), LocalTime.of(9, 5), TodoImportance.HIGH),
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
        assertEquals(open.importance, restoredOpen.importance)
        assertNull(restoredOpen.parentId)
        assertNull(restoredOpen.siblingPosition)
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

    @Test fun currentSchemaRoundTripsNullMidnightAndMinuteBoundaries() = runBlocking {
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

    @Test fun currentSchemaRoundTripsFamilyOrderImportanceAndRootStatusWithChildrenBeforeParents() = runBlocking {
        val date = LocalDate.parse("2026-10-20")
        val parent = todos.create(
            UUID.nameUUIDFromBytes("archive-family-root".toByteArray()),
            UUID.randomUUID(),
            TodoInput("Archive root", dueDate = date, importance = TodoImportance.MEDIUM),
        )
        val first = todos.createChild(
            UUID.nameUUIDFromBytes("archive-family-first".toByteArray()),
            UUID.randomUUID(),
            parent.id,
            TodoInput("First child", importance = TodoImportance.HIGH),
        )
        val second = todos.createChild(
            UUID.nameUUIDFromBytes("archive-family-second".toByteArray()),
            UUID.randomUUID(),
            parent.id,
            TodoInput("Second child", dueDate = date.plusDays(1), importance = TodoImportance.LOW),
        )
        val completedFirst = todos.setCompleted(first.id, first.revision, UUID.randomUUID(), true)
        val completedSecond = todos.setCompleted(second.id, second.revision, UUID.randomUUID(), true)
        val completedParent = todos.setCompleted(parent.id, parent.revision, UUID.randomUUID(), true)
        val originalFamilyRevision = completedParent.familyRevision

        todos.prepareDelete(parent.id)
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM todo_family_command").use {
            assertTrue(it.moveToFirst())
            assertTrue(it.getInt(0) > 0)
        }

        val childFirstArchive = rewriteTaskArchive(export(), CURRENT_BACKUP_SCHEMA_VERSION) { columns, rows ->
            val parentIndex = columns.indexOf("parent_id")
            columns to rows.sortedBy { row -> if (row[parentIndex] != null) 0 else 1 }
        }
        val replaced = todos.create(UUID.randomUUID(), UUID.randomUUID(), TodoInput("Replace family"))

        backup.prepare(ByteArrayInputStream(childFirstArchive)).use {
            assertEquals(3L, it.preview.todoCount)
            it.restore()
        }

        val restoredParent = requireNotNull(todos.get(parent.id))
        assertEquals(TodoImportance.MEDIUM, restoredParent.importance)
        assertEquals(completedParent.completedAt, restoredParent.completedAt)
        assertNotEquals(originalFamilyRevision, restoredParent.familyRevision)
        assertNull(todos.get(replaced.id))

        val childPage = todos.observeChildren(parent.id, limit = 50).first()
        assertEquals(2, childPage.total)
        assertEquals(TodoChildProgress(completed = 2, total = 2), childPage.familyProgress)
        assertEquals(listOf(first.id, second.id), childPage.items.map { it.task.id })
        assertEquals(listOf(TodoImportance.HIGH, TodoImportance.LOW), childPage.items.map { it.task.importance })
        assertEquals(listOf(completedFirst.completedAt, completedSecond.completedAt), childPage.items.map { it.task.completedAt })
        assertTrue(childPage.items.all { it.task.parentId == parent.id })
        assertTrue(childPage.items.all { it.task.familyRevision == restoredParent.familyRevision })
        assertEquals(1, todos.observeRows(TodoListQuery(completed = true), limit = 50).first().total)
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM todo_family_command").use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }
    }

    @Test fun legacyTaskArchivesImportAsRootsAndDefaultImportanceByVersion() = runBlocking {
        val parent = todos.create(
            UUID.nameUUIDFromBytes("legacy-archive-root".toByteArray()),
            UUID.randomUUID(),
            TodoInput("Legacy root", dueDate = LocalDate.parse("2026-10-20"), dueTime = LocalTime.of(9, 5), importance = TodoImportance.HIGH),
        )
        val child = todos.createChild(
            UUID.nameUUIDFromBytes("legacy-archive-child".toByteArray()),
            UUID.randomUUID(),
            parent.id,
            TodoInput("Legacy child", dueDate = LocalDate.parse("2026-10-21"), importance = TodoImportance.LOW),
        )
        val sourceArchive = export()

        for (version in 4..13) {
            val legacy = rewriteTaskArchive(sourceArchive, version) { columns, rows -> columns to rows }
            backup.prepare(ByteArrayInputStream(legacy)).use {
                assertEquals(2L, it.preview.todoCount)
                it.restore()
            }

            val roots = todos.observeRows(TodoListQuery(completed = false), limit = 50).first()
            assertEquals(2, roots.total)
            assertTrue(roots.items.all { it.task.parentId == null && it.task.siblingPosition == null })
            val restoredParent = requireNotNull(todos.get(parent.id))
            val restoredChild = requireNotNull(todos.get(child.id))
            assertEquals(if (version >= 13) TodoImportance.HIGH else TodoImportance.NONE, restoredParent.importance)
            assertEquals(if (version >= 13) TodoImportance.LOW else TodoImportance.NONE, restoredChild.importance)
            assertEquals(if (version == 4) null else LocalTime.of(9, 5), restoredParent.dueTime)
        }
    }

    @Test fun invalidFamilyArchiveIsRejectedBeforeReplacingLiveTasks() = runBlocking {
        val live = todos.create(UUID.randomUUID(), UUID.randomUUID(), TodoInput("Keep live task"))
        val parent = todos.create(UUID.randomUUID(), UUID.randomUUID(), TodoInput("Archive parent"))
        todos.createChild(UUID.randomUUID(), UUID.randomUUID(), parent.id, TodoInput("Archive child"))
        val valid = export()
        val malformed = rewriteTaskArchive(valid, CURRENT_BACKUP_SCHEMA_VERSION) { columns, rows ->
            val titleIndex = columns.indexOf("title")
            val parentIndex = columns.indexOf("parent_id")
            columns to rows.map { row ->
                row.toMutableList().apply {
                    if (get(titleIndex) == "Archive child") set(parentIndex, "00000000-0000-0000-0000-999999999999")
                }
            }
        }

        assertTrue(runCatching {
            backup.prepare(ByteArrayInputStream(malformed)).use { }
        }.isFailure)
        assertEquals(live, todos.get(live.id))
        assertTrue(todos.get(parent.id) != null)
    }

    @Test fun malformedFamilyGraphsAndImportanceAreRejectedBeforeLiveReplacement() = runBlocking {
        val live = todos.create(UUID.randomUUID(), UUID.randomUUID(), TodoInput("Keep live task"))
        val parent = todos.create(
            UUID.nameUUIDFromBytes("malformed-family-root".toByteArray()),
            UUID.randomUUID(),
            TodoInput("Archive parent"),
        )
        val first = todos.createChild(
            UUID.nameUUIDFromBytes("malformed-family-first".toByteArray()),
            UUID.randomUUID(),
            parent.id,
            TodoInput("Archive first child"),
        )
        val second = todos.createChild(
            UUID.nameUUIDFromBytes("malformed-family-second".toByteArray()),
            UUID.randomUUID(),
            parent.id,
            TodoInput("Archive second child"),
        )
        val valid = export()
        fun malformed(change: (List<String>, MutableList<MutableList<Any?>>) -> Unit): ByteArray =
            rewriteTaskArchive(valid, CURRENT_BACKUP_SCHEMA_VERSION) { columns, rows ->
                val changedRows = rows.map { it.toMutableList() }.toMutableList()
                change(columns, changedRows)
                columns to changedRows
            }

        val invalidArchives = listOf(
            malformed { columns, rows ->
                rows.single { it[columns.indexOf("title")] == "Archive first child" }[
                    columns.indexOf("importance_code")
                ] = 99
            },
            malformed { columns, rows ->
                rows.single { it[columns.indexOf("title")] == "Archive second child" }[
                    columns.indexOf("sibling_position")
                ] = 0L
            },
            malformed { columns, rows ->
                rows.single { it[columns.indexOf("title")] == "Archive first child" }[
                    columns.indexOf("parent_id")
                ] = "00000000-0000-0000-0000-999999999999"
            },
            malformed { columns, rows ->
                val child = rows.single { it[columns.indexOf("title")] == "Archive first child" }
                child[columns.indexOf("parent_id")] = child[columns.indexOf("id")]
            },
            malformed { columns, rows ->
                rows.single { it[columns.indexOf("title")] == "Archive second child" }[
                    columns.indexOf("parent_id")
                ] = first.id.toString()
            },
            malformed { columns, rows ->
                val root = rows.single { it[columns.indexOf("title")] == "Archive parent" }
                root[columns.indexOf("parent_id")] = first.id.toString()
                root[columns.indexOf("sibling_position")] = 0L
            },
            malformed { columns, rows ->
                rows.single { it[columns.indexOf("title")] == "Archive parent" }[
                    columns.indexOf("completed_at")
                ] = now.toEpochMilli()
            },
        )

        invalidArchives.forEach { archive ->
            assertTrue(runCatching { backup.prepare(ByteArrayInputStream(archive)).use { } }.isFailure)
            assertEquals(live, todos.get(live.id))
            assertTrue(todos.get(parent.id) != null)
            assertTrue(todos.get(second.id) != null)
        }
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
        val (transformedColumns, transformedRows) = transform(columns, rows)
        val omittedColumns = buildSet {
            if (schemaVersion < 5) add("due_time_minutes")
            if (schemaVersion < 13) add("importance_code")
            if (schemaVersion < 14) {
                add("parent_id")
                add("sibling_position")
            }
        }
        val newColumns = transformedColumns.filterNot { it in omittedColumns }
        val newRows = transformedRows.map { row ->
            row.filterIndexed { index, _ -> transformedColumns[index] !in omittedColumns }
        }
        taskFile.outputStream().use { BackupRows.write(it, newColumns, newRows.asSequence()) }
        val supportedTables = backupTables(schemaVersion).map { "tables/$it.tsv" }.toSet()
        val members = manifest.names
            .filter { it in supportedTables || it.startsWith("photos/") }
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
