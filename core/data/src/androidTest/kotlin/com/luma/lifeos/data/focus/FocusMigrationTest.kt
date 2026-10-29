package com.luma.lifeos.data.focus

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.data.db.LumaDatabase
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FocusMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val fileName = "focus-migration-${UUID.randomUUID()}.db"

    @get:Rule val helper = MigrationTestHelper(instrumentation, LumaDatabase::class.java)

    @After fun cleanUp() {
        instrumentation.targetContext.deleteDatabase(fileName)
    }

    @Test fun populatedVersionSevenDatabaseMigratesToEightWithoutChangingExistingContent() {
        helper.createDatabase(fileName, 7).apply {
            execSQL(
                "INSERT INTO life_event (id, type, occurred_at, created_at, updated_at, source) VALUES (?, 'JOURNAL', 1000, 1000, 1000, 'MANUAL')",
                arrayOf(EVENT_ID),
            )
            execSQL(
                "INSERT INTO journal_entry (id, life_event_id, body) VALUES (?, ?, 'Keep journal through focus migration')",
                arrayOf(JOURNAL_ID, EVENT_ID),
            )
            execSQL(
                "INSERT INTO media_attachment (id, life_event_id, relative_path, type) VALUES (?, ?, 'photos/focus-migration.img', 'IMAGE')",
                arrayOf(PHOTO_ID, EVENT_ID),
            )
            execSQL(
                "INSERT INTO todo_task (id, title, note, due_date, completed_at, created_at, updated_at, revision_token, due_time_minutes) VALUES (?, 'Keep task', NULL, NULL, NULL, 1000, 1000, ?, NULL)",
                arrayOf(TASK_ID, "00000000-0000-0000-0000-000000000005"),
            )
        }.close()

        val migrated = helper.runMigrationsAndValidate(
            fileName,
            LumaDatabase.VERSION,
            true,
            *LumaDatabase.MIGRATIONS.toTypedArray(),
        )
        try {
            migrated.query("SELECT body FROM journal_entry WHERE id = '$JOURNAL_ID'").use {
                assertTrue(it.moveToFirst())
                assertEquals("Keep journal through focus migration", it.getString(0))
            }
            migrated.query("SELECT relative_path FROM media_attachment WHERE id = '$PHOTO_ID'").use {
                assertTrue(it.moveToFirst())
                assertEquals("photos/focus-migration.img", it.getString(0))
            }
            migrated.query("SELECT title FROM todo_task WHERE id = '$TASK_ID'").use {
                assertTrue(it.moveToFirst())
                assertEquals("Keep task", it.getString(0))
            }
            for (table in listOf(
                "focus_session", "focus_run", "focus_runtime", "focus_command_receipt", "focus_deleted_session",
            )) {
                migrated.query("SELECT COUNT(*) FROM `$table`").use {
                    assertTrue(it.moveToFirst())
                    assertEquals("Expected empty $table after the additive migration", 0, it.getInt(0))
                }
            }
            migrated.query("SELECT generation_id FROM focus_storage_state WHERE singleton_id = 1").use {
                assertTrue(it.moveToFirst())
                assertTrue(runCatching { UUID.fromString(it.getString(0)) }.isSuccess)
            }
            val focusIndexes = buildSet {
                migrated.query("PRAGMA index_list('focus_session')").use { cursor ->
                    while (cursor.moveToNext()) add(cursor.getString(1))
                }
            }
            assertTrue("index_focus_session_open_slot" in focusIndexes)
            assertTrue("index_focus_session_state_started_at_seconds_started_at_nanos" in focusIndexes)
            migrated.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
            assertEquals(LumaDatabase.VERSION, migrated.version)
        } finally {
            migrated.close()
        }

        val database = LumaDatabase.open(instrumentation.targetContext, fileName)
        try {
            database.openHelper.readableDatabase.query(
                "SELECT generation_id FROM focus_storage_state WHERE singleton_id = 1",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(runCatching { UUID.fromString(cursor.getString(0)) }.isSuccess)
            }
            database.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use {
                assertEquals(0, it.count)
            }
        } finally {
            database.close()
        }
    }

    @Test fun versionSevenMigrationCreatesRequiredCompositeRunForeignKeyAndUniqueIndexes() {
        helper.createDatabase(fileName, 7).close()
        val migrated = helper.runMigrationsAndValidate(
            fileName,
            LumaDatabase.VERSION,
            true,
            *LumaDatabase.MIGRATIONS.toTypedArray(),
        )
        try {
            val foreignKeyPairs = buildSet {
                migrated.query("PRAGMA foreign_key_list('focus_runtime')").use { cursor ->
                    while (cursor.moveToNext()) {
                        if (cursor.getString(2) == "focus_run") {
                            add(cursor.getString(3) to cursor.getString(4))
                        }
                    }
                }
            }
            assertEquals(setOf("run_id" to "id", "session_id" to "session_id"), foreignKeyPairs)

            val runIndexes = buildMap<String, Boolean> {
                migrated.query("PRAGMA index_list('focus_run')").use { cursor ->
                    while (cursor.moveToNext()) put(cursor.getString(1), cursor.getInt(2) == 1)
                }
            }
            assertTrue(runIndexes["index_focus_run_session_id_ordinal"] == true)
            assertTrue(runIndexes["index_focus_run_id_session_id"] == true)
            assertTrue(runIndexes["index_focus_run_open_session_id"] == true)

            val tables = buildSet {
                migrated.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name LIKE 'focus_%'").use { cursor ->
                    while (cursor.moveToNext()) add(cursor.getString(0))
                }
            }
            assertTrue("focus_storage_state" in tables)
            assertEquals(
                setOf(
                    "focus_session", "focus_run", "focus_runtime", "focus_command_receipt",
                    "focus_deleted_session", "focus_storage_state",
                ),
                tables,
            )
        } finally {
            migrated.close()
        }
    }

    private companion object {
        const val EVENT_ID = "00000000-0000-0000-0000-000000000001"
        const val JOURNAL_ID = "00000000-0000-0000-0000-000000000002"
        const val PHOTO_ID = "00000000-0000-0000-0000-000000000003"
        const val TASK_ID = "00000000-0000-0000-0000-000000000004"
    }
}
