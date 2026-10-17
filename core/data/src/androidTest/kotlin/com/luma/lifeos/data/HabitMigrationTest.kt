package com.luma.lifeos.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.data.db.LumaDatabase
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HabitMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val names = mutableListOf<String>()
    @get:Rule val helper = MigrationTestHelper(instrumentation, LumaDatabase::class.java)

    @After fun cleanUp() { names.forEach(instrumentation.targetContext::deleteDatabase) }

    @Test fun schemaOneAndTwoUpgradeToThreeWithoutDroppingLegacyRows() {
        for (version in listOf(1, 2)) {
            val fileName = "habit-migration-v$version.db"
            names += fileName
            helper.createDatabase(fileName, version).apply {
                execSQL("INSERT INTO life_event (id, type, occurred_at, created_at, updated_at, source) VALUES ('00000000-0000-0000-0000-000000000001', 'JOURNAL', 1000, 1000, 1000, 'MANUAL')")
                execSQL("INSERT INTO journal_entry (id, life_event_id, body) VALUES ('00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'Keep this journal')")
                if (version == 2) {
                    execSQL("INSERT INTO usage_session (package_name, started_at, ended_at, app_label, ongoing) VALUES ('org.example.app', 2000, 3000, 'Example', 0)")
                    execSQL("INSERT INTO usage_session_window (query_start, query_end, complete) VALUES (1000, 5000, 1)")
                }
            }.close()

            helper.runMigrationsAndValidate(fileName, 3, true, *LumaDatabase.MIGRATIONS.toTypedArray()).close()
            val database = LumaDatabase.open(instrumentation.targetContext, fileName)
            try {
                val sql = database.openHelper.readableDatabase
                sql.query("SELECT body FROM journal_entry").use { assertTrue(it.moveToFirst()); assertEquals("Keep this journal", it.getString(0)) }
                for (table in listOf("habit_settings", "habit", "habit_schedule", "habit_log", "habit_day_choice", "habit_reminder", "habit_reminder_runtime")) {
                    sql.query("SELECT COUNT(*) FROM `$table`").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
                }
                val expectedUsage = if (version == 2) 1 else 0
                sql.query("SELECT COUNT(*) FROM usage_session").use { assertTrue(it.moveToFirst()); assertEquals(expectedUsage, it.getInt(0)) }
                sql.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
            } finally { database.close() }
        }
    }
}
