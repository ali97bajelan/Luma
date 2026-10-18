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

    @Test fun populatedSchemasOneThroughThreeUpgradeToFourWithoutLosingData() {
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
                4,
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

            // Opening through the app builder validates the exported schema identity at version 4.
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

    private companion object {
        const val EVENT_ID = "00000000-0000-0000-0000-000000000001"
        const val JOURNAL_ID = "00000000-0000-0000-0000-000000000002"
        const val PHOTO_ID = "00000000-0000-0000-0000-000000000003"
        const val HABIT_ID = "00000000-0000-0000-0000-000000000004"
        const val SCHEDULE_ID = "00000000-0000-0000-0000-000000000005"
    }
}
