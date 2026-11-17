package com.luma.lifeos.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.data.db.LumaDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TodoMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val fileNames = mutableListOf<String>()

    @get:Rule
    val helper = MigrationTestHelper(instrumentation, LumaDatabase::class.java)

    @After fun cleanUp() {
        fileNames.forEach(instrumentation.targetContext::deleteDatabase)
    }

    @Test fun populatedSchemasOneThroughThreeUpgradeToLatestWithoutLosingData() {
        for (version in 1..3) {
            val fileName = "todo-migration-v$version.db"
            fileNames += fileName
            helper.createDatabase(fileName, version).apply {
                execSQL("INSERT INTO life_event (id, type, occurred_at, created_at, updated_at, source) VALUES ('$EVENT_ID', 'JOURNAL', 1000, 1000, 1000, 'MANUAL')")
                execSQL("INSERT INTO journal_entry (id, life_event_id, body) VALUES ('$JOURNAL_ID', '$EVENT_ID', 'Keep this journal')")
                execSQL("INSERT INTO media_attachment (id, life_event_id, relative_path, type) VALUES ('$PHOTO_ID', '$EVENT_ID', 'photos/kept.img', 'IMAGE')")
                if (version >= 2) {
                    execSQL("INSERT INTO usage_session (package_name, started_at, ended_at, app_label, ongoing) VALUES ('org.example.app', 2000, 3000, 'Example', 0)")
                }
                if (version >= 3) {
                    execSQL("INSERT INTO habit_settings (setting_id, tracking_zone_id) VALUES (1, 'UTC')")
                    execSQL("INSERT INTO habit (id, name, unit_label, start_date, created_at, updated_at) VALUES ('$HABIT_ID', 'Keep this habit', NULL, '2026-10-01', 1000, 1000)")
                    execSQL("INSERT INTO habit_schedule (id, habit_id, effective_from, target, weekday_mask, state) VALUES ('$SCHEDULE_ID', '$HABIT_ID', '2026-10-01', 1, 127, 'ACTIVE')")
                }
            }.close()

            val migrated = helper.runMigrationsAndValidate(
                fileName,
                LumaDatabase.VERSION,
                true,
                *LumaDatabase.MIGRATIONS.toTypedArray(),
            )
            try {
                migrated.query(
                    "SELECT life_event.type, life_event.note, memory.id FROM life_event " +
                        "JOIN memory ON memory.life_event_id = life_event.id WHERE life_event.id = '$EVENT_ID'",
                ).use {
                    assertTrue(it.moveToFirst())
                    assertEquals("MEMORY", it.getString(0))
                    assertEquals("Keep this journal", it.getString(1))
                    assertEquals(EVENT_ID, it.getString(2))
                }
                migrated.query("SELECT COUNT(*) FROM journal_entry WHERE life_event_id = '$EVENT_ID'").use {
                    assertTrue(it.moveToFirst())
                    assertEquals(0, it.getInt(0))
                }
                migrated.query("SELECT relative_path FROM media_attachment WHERE id = '$PHOTO_ID'").use {
                    assertTrue(it.moveToFirst())
                    assertEquals("photos/kept.img", it.getString(0))
                }
                migrated.query("SELECT COUNT(*) FROM todo_task").use {
                    assertTrue(it.moveToFirst())
                    assertEquals(0, it.getInt(0))
                }
                if (version >= 2) {
                    migrated.query("SELECT COUNT(*) FROM usage_session").use {
                        assertTrue(it.moveToFirst())
                        assertEquals(1, it.getInt(0))
                    }
                }
                if (version >= 3) {
                    migrated.query("SELECT name FROM habit WHERE id = '$HABIT_ID'").use {
                        assertTrue(it.moveToFirst())
                        assertEquals("Keep this habit", it.getString(0))
                    }
                }
                migrated.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
            } finally {
                migrated.close()
            }

            // Reopening through the app builder validates the latest exported schema identity.
            val database = LumaDatabase.open(instrumentation.targetContext, fileName)
            try {
                database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM todo_task").use {
                    assertTrue(it.moveToFirst())
                    assertEquals(0, it.getInt(0))
                }
            } finally {
                database.close()
            }
        }
    }

    @Test fun populatedVersionFiveTasksMigrateWithNullTimeAndStableIndexes() {
        val fileName = "todo-migration-v5-${System.nanoTime()}.db"
        fileNames += fileName
        helper.createDatabase(fileName, 5).apply {
            execSQL("INSERT INTO todo_task (id, title, note, due_date, completed_at, created_at, updated_at, revision_token) VALUES ('$OPEN_TASK_ID', 'Open task', 'Keep note', '2026-10-20', NULL, 1000, 2000, '$OPEN_REVISION')")
            execSQL("INSERT INTO todo_task (id, title, note, due_date, completed_at, created_at, updated_at, revision_token) VALUES ('$COMPLETED_TASK_ID', 'Done task', NULL, NULL, 3000, 1000, 3000, '$COMPLETED_REVISION')")
        }.close()

        val migrated = helper.runMigrationsAndValidate(
            fileName,
            LumaDatabase.VERSION,
            true,
            *LumaDatabase.MIGRATIONS.toTypedArray(),
        )
        try {
            migrated.query("SELECT id, title, note, due_date, completed_at, created_at, updated_at, revision_token, due_time_minutes, importance_code, parent_id, sibling_position, family_revision_token FROM todo_task ORDER BY id").use { cursor ->
                assertEquals(2, cursor.count)
                assertTrue(cursor.moveToFirst())
                assertEquals(OPEN_TASK_ID, cursor.getString(0))
                assertEquals("Open task", cursor.getString(1))
                assertEquals("Keep note", cursor.getString(2))
                assertEquals("2026-10-20", cursor.getString(3))
                assertTrue(cursor.isNull(4))
                assertEquals(1000, cursor.getLong(5))
                assertEquals(2000, cursor.getLong(6))
                assertEquals(OPEN_REVISION, cursor.getString(7))
                assertTrue(cursor.isNull(8))
                assertEquals(0, cursor.getInt(9))
                assertTrue(cursor.isNull(10))
                assertTrue(cursor.isNull(11))
                assertTrue(cursor.getString(12).isNotBlank())
                assertTrue(cursor.moveToNext())
                assertEquals(COMPLETED_TASK_ID, cursor.getString(0))
                assertEquals("Done task", cursor.getString(1))
                assertTrue(cursor.isNull(2))
                assertTrue(cursor.isNull(3))
                assertEquals(3000, cursor.getLong(4))
                assertEquals(1000, cursor.getLong(5))
                assertEquals(3000, cursor.getLong(6))
                assertEquals(COMPLETED_REVISION, cursor.getString(7))
                assertTrue(cursor.isNull(8))
                assertEquals(0, cursor.getInt(9))
                assertTrue(cursor.isNull(10))
                assertTrue(cursor.isNull(11))
                assertTrue(cursor.getString(12).isNotBlank())
            }
            val indexes = buildSet {
                migrated.query("PRAGMA index_list('todo_task')").use { cursor ->
                    while (cursor.moveToNext()) add(cursor.getString(1))
                }
            }
            assertTrue("index_todo_task_completed_at_due_date_due_time_minutes_created_at_id" in indexes)
            assertTrue("index_todo_task_completed_at_id" in indexes)
            assertTrue("index_todo_task_parent_id_sibling_position" in indexes)
            assertTrue("index_todo_task_parent_id_completed_at_sibling_position" in indexes)
            assertTrue("index_todo_task_completed_at_importance_code_due_date_due_time_minutes_created_at_id" in indexes)
            assertTrue("index_todo_task_completed_at_due_date_created_at_id" !in indexes)
        } finally {
            migrated.close()
        }
    }

    @Test fun everyExportedTaskSchemaKeepsPopulatedRowsThroughTheFamilyMigration() {
        val supportedTaskVersions = listOf(4, 5, 6, 7, 8, 9, 10, 12, 14, 15, 16)
        supportedTaskVersions.forEach { version ->
            val fileName = "todo-migration-populated-v$version-${System.nanoTime()}.db"
            fileNames += fileName
            helper.createDatabase(fileName, version).apply {
                val columns = buildList {
                    add("id")
                    add("title")
                    add("note")
                    add("due_date")
                    add("completed_at")
                    add("created_at")
                    add("updated_at")
                    add("revision_token")
                    if (version >= 6) add("due_time_minutes")
                    if (version >= 16) add("importance_code")
                }
                val values = mutableListOf<Any?>(
                    OPEN_TASK_ID,
                    "Preserved v$version task",
                    "Version $version note",
                    "2026-11-20",
                    null,
                    1000L,
                    2000L,
                    OPEN_REVISION,
                )
                if (version >= 6) values += 545
                if (version >= 16) values += 3
                val placeholders = List(columns.size) { "?" }.joinToString()
                execSQL(
                    "INSERT INTO todo_task (${columns.joinToString()}) VALUES ($placeholders)",
                    values.toTypedArray(),
                )
                if (version >= 7) {
                    execSQL(
                        "INSERT INTO task_reminder (task_id, enabled, task_revision, revision_token, needs_reconcile) VALUES (?, 1, ?, ?, 1)",
                        arrayOf(OPEN_TASK_ID, OPEN_REVISION, REMINDER_REVISION),
                    )
                }
            }.close()

            val migrated = helper.runMigrationsAndValidate(
                fileName,
                LumaDatabase.VERSION,
                true,
                *LumaDatabase.MIGRATIONS.toTypedArray(),
            )
            try {
                migrated.query(
                    "SELECT title, note, due_date, completed_at, created_at, updated_at, revision_token, due_time_minutes, importance_code, parent_id, sibling_position, family_revision_token FROM todo_task WHERE id = '$OPEN_TASK_ID'",
                ).use { cursor ->
                    assertTrue("Missing task migrated from v$version", cursor.moveToFirst())
                    assertEquals("Preserved v$version task", cursor.getString(0))
                    assertEquals("Version $version note", cursor.getString(1))
                    assertEquals("2026-11-20", cursor.getString(2))
                    assertTrue(cursor.isNull(3))
                    assertEquals(1000L, cursor.getLong(4))
                    assertEquals(2000L, cursor.getLong(5))
                    assertEquals(OPEN_REVISION, cursor.getString(6))
                    if (version >= 6) assertEquals(545, cursor.getInt(7)) else assertTrue(cursor.isNull(7))
                    assertEquals(if (version >= 16) 3 else 0, cursor.getInt(8))
                    assertTrue(cursor.isNull(9))
                    assertTrue(cursor.isNull(10))
                    assertTrue(cursor.getString(11).isNotBlank())
                }
                if (version >= 7) {
                    migrated.query("SELECT task_id, task_revision, revision_token FROM task_reminder").use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertEquals(OPEN_TASK_ID, cursor.getString(0))
                        assertEquals(OPEN_REVISION, cursor.getString(1))
                        assertEquals(REMINDER_REVISION, cursor.getString(2))
                    }
                }
                migrated.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
            } finally {
                migrated.close()
            }
        }
    }

    @Test fun populatedVersionSixteenTasksMigrateAsRootsAndKeepReminderAndFocusLinks() {
        val fileName = "todo-migration-v16-${System.nanoTime()}.db"
        fileNames += fileName
        helper.createDatabase(fileName, 16).apply {
            execSQL(
                "INSERT INTO todo_task (id, title, note, due_date, completed_at, created_at, updated_at, revision_token, due_time_minutes, importance_code) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(OPEN_TASK_ID, "Open family root", "Keep this note", "2026-11-20", null, 1000, 2000, OPEN_REVISION, 545, 3),
            )
            execSQL(
                "INSERT INTO todo_task (id, title, note, due_date, completed_at, created_at, updated_at, revision_token, due_time_minutes, importance_code) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(COMPLETED_TASK_ID, "Completed root", null, null, 3000, 1000, 3000, COMPLETED_REVISION, null, 1),
            )
            execSQL(
                "INSERT INTO task_reminder (task_id, enabled, task_revision, revision_token, consumed_action_token, last_delivered_slot, snooze_slot, snooze_until, needs_reconcile) VALUES (?, 1, ?, ?, NULL, NULL, NULL, NULL, 1)",
                arrayOf(OPEN_TASK_ID, OPEN_REVISION, REMINDER_REVISION),
            )
            execSQL(
                "INSERT INTO focus_session (id, state, revision, started_at_seconds, started_at_nanos, ended_at_seconds, ended_at_nanos, created_at_seconds, created_at_nanos, updated_at_seconds, updated_at_nanos, terminal_reason, recovered_or_uncertain, open_slot, name, tag_id, timer_mode, target_millis, outcome, next_step, task_id, task_origin) VALUES (?, 'COMPLETED', 1, 1000, 0, 2000, 0, 1000, 0, 2000, 0, NULL, 0, NULL, NULL, NULL, 'COUNT_UP', NULL, NULL, NULL, ?, 1)",
                arrayOf(FOCUS_SESSION_ID, OPEN_TASK_ID),
            )
        }.close()

        val migrated = helper.runMigrationsAndValidate(
            fileName,
            LumaDatabase.VERSION,
            true,
            *LumaDatabase.MIGRATIONS.toTypedArray(),
        )
        try {
            migrated.query(
                "SELECT id, title, note, due_date, completed_at, created_at, updated_at, revision_token, due_time_minutes, importance_code, parent_id, sibling_position, family_revision_token FROM todo_task ORDER BY id",
            ).use { cursor ->
                assertEquals(2, cursor.count)
                assertTrue(cursor.moveToFirst())
                assertEquals(OPEN_TASK_ID, cursor.getString(0))
                assertEquals("Open family root", cursor.getString(1))
                assertEquals("Keep this note", cursor.getString(2))
                assertEquals("2026-11-20", cursor.getString(3))
                assertTrue(cursor.isNull(4))
                assertEquals(1000L, cursor.getLong(5))
                assertEquals(2000L, cursor.getLong(6))
                assertEquals(OPEN_REVISION, cursor.getString(7))
                assertEquals(545, cursor.getInt(8))
                assertEquals(3, cursor.getInt(9))
                assertTrue(cursor.isNull(10))
                assertTrue(cursor.isNull(11))
                val familyRevision = cursor.getString(12)
                assertTrue(familyRevision.isNotBlank())
                assertTrue(familyRevision != "00000000-0000-0000-0000-000000000000")
                assertTrue(cursor.moveToNext())
                assertEquals(COMPLETED_TASK_ID, cursor.getString(0))
                assertEquals("Completed root", cursor.getString(1))
                assertNull(cursor.getString(2))
                assertTrue(cursor.isNull(3))
                assertEquals(3000L, cursor.getLong(4))
                assertEquals(1000L, cursor.getLong(5))
                assertEquals(3000L, cursor.getLong(6))
                assertEquals(COMPLETED_REVISION, cursor.getString(7))
                assertTrue(cursor.isNull(8))
                assertEquals(1, cursor.getInt(9))
                assertTrue(cursor.isNull(10))
                assertTrue(cursor.isNull(11))
                assertTrue(cursor.getString(12).isNotBlank())
            }
            migrated.query("SELECT task_id, enabled, task_revision, revision_token FROM task_reminder").use { cursor ->
                assertEquals(1, cursor.count)
                assertTrue(cursor.moveToFirst())
                assertEquals(OPEN_TASK_ID, cursor.getString(0))
                assertEquals(1, cursor.getInt(1))
                assertEquals(OPEN_REVISION, cursor.getString(2))
                assertEquals(REMINDER_REVISION, cursor.getString(3))
            }
            migrated.query("SELECT task_id, task_origin FROM focus_session WHERE id = '$FOCUS_SESSION_ID'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(OPEN_TASK_ID, cursor.getString(0))
                assertEquals(1, cursor.getInt(1))
            }
            migrated.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        } finally {
            migrated.close()
        }
    }

    private companion object {
        const val EVENT_ID = "00000000-0000-0000-0000-000000000001"
        const val JOURNAL_ID = "00000000-0000-0000-0000-000000000002"
        const val PHOTO_ID = "00000000-0000-0000-0000-000000000003"
        const val HABIT_ID = "00000000-0000-0000-0000-000000000004"
        const val SCHEDULE_ID = "00000000-0000-0000-0000-000000000005"
        const val OPEN_TASK_ID = "00000000-0000-0000-0000-000000000006"
        const val COMPLETED_TASK_ID = "00000000-0000-0000-0000-000000000007"
        const val OPEN_REVISION = "10000000-0000-0000-0000-000000000006"
        const val COMPLETED_REVISION = "10000000-0000-0000-0000-000000000007"
        const val REMINDER_REVISION = "20000000-0000-0000-0000-000000000008"
        const val FOCUS_SESSION_ID = "00000000-0000-0000-0000-000000000010"
    }
}
