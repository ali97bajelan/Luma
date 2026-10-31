package com.luma.lifeos.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.data.db.LumaDatabase
import org.junit.After
import org.junit.Assert.assertEquals
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
                6,
                true,
                *LumaDatabase.MIGRATIONS.toTypedArray(),
            )
            try {
                migrated.query("SELECT body FROM journal_entry WHERE id = '$JOURNAL_ID'").use {
                    assertTrue(it.moveToFirst())
                    assertEquals("Keep this journal", it.getString(0))
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

            // Opening through the app builder validates the exported schema identity at version 6.
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
            6,
            true,
            *LumaDatabase.MIGRATIONS.toTypedArray(),
        )
        try {
            migrated.query("SELECT id, title, note, due_date, completed_at, created_at, updated_at, revision_token, due_time_minutes FROM todo_task ORDER BY id").use { cursor ->
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
            }
            val indexes = buildSet {
                migrated.query("PRAGMA index_list('todo_task')").use { cursor ->
                    while (cursor.moveToNext()) add(cursor.getString(1))
                }
            }
            assertTrue("index_todo_task_completed_at_due_date_due_time_minutes_created_at_id" in indexes)
            assertTrue("index_todo_task_completed_at_id" in indexes)
            assertTrue("index_todo_task_completed_at_due_date_created_at_id" !in indexes)
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
    }
}
