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
                execSQL("INSERT INTO person (id, name) VALUES ('00000000-0000-0000-0000-000000000006', 'Keep this person')")
                execSQL("INSERT INTO event_person (event_id, person_id) VALUES ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000006')")
                execSQL("INSERT INTO place (id, name) VALUES ('00000000-0000-0000-0000-000000000007', 'Keep this place')")
                execSQL("INSERT INTO event_place (event_id, place_id) VALUES ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000007')")
                execSQL("INSERT INTO tag (id, name, normalized_name) VALUES ('00000000-0000-0000-0000-000000000008', 'Keep this tag', 'keep this tag')")
                execSQL("INSERT INTO event_tag (event_id, tag_id) VALUES ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000008')")
                execSQL("INSERT INTO media_attachment (id, life_event_id, relative_path, type) VALUES ('00000000-0000-0000-0000-000000000009', '00000000-0000-0000-0000-000000000001', 'photos/00000000-0000-0000-0000-00000000000a.img', 'IMAGE')")
                execSQL("INSERT INTO life_event (id, type, occurred_at, created_at, updated_at, source) VALUES ('00000000-0000-0000-0000-000000000003', 'MOOD', 1100, 1100, 1100, 'MANUAL')")
                execSQL("INSERT INTO mood_entry (id, life_event_id, mood_score) VALUES ('00000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000003', 4)")
                execSQL("INSERT INTO emotion (id, name) VALUES ('00000000-0000-0000-0000-000000000005', 'Keep this emotion')")
                execSQL("INSERT INTO mood_emotion (mood_id, emotion_id) VALUES ('00000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000005')")
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
                sql.query("SELECT name FROM event_person JOIN person ON person.id = event_person.person_id WHERE event_id = '00000000-0000-0000-0000-000000000001'").use {
                    assertTrue(it.moveToFirst()); assertEquals("Keep this person", it.getString(0))
                }
                sql.query("SELECT name FROM event_place JOIN place ON place.id = event_place.place_id WHERE event_id = '00000000-0000-0000-0000-000000000001'").use {
                    assertTrue(it.moveToFirst()); assertEquals("Keep this place", it.getString(0))
                }
                sql.query("SELECT name FROM event_tag JOIN tag ON tag.id = event_tag.tag_id WHERE event_id = '00000000-0000-0000-0000-000000000001'").use {
                    assertTrue(it.moveToFirst()); assertEquals("Keep this tag", it.getString(0))
                }
                sql.query("SELECT relative_path FROM media_attachment WHERE life_event_id = '00000000-0000-0000-0000-000000000001'").use {
                    assertTrue(it.moveToFirst()); assertEquals("photos/00000000-0000-0000-0000-00000000000a.img", it.getString(0))
                }
                sql.query("SELECT emotion.name FROM mood_emotion JOIN emotion ON emotion.id = mood_emotion.emotion_id WHERE mood_id = '00000000-0000-0000-0000-000000000004'").use {
                    assertTrue(it.moveToFirst()); assertEquals("Keep this emotion", it.getString(0))
                }
                for (table in listOf("habit_settings", "habit", "habit_schedule", "habit_log", "habit_day_choice", "habit_reminder", "habit_reminder_runtime")) {
                    sql.query("SELECT COUNT(*) FROM `$table`").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
                }
                val expectedUsage = if (version == 2) 1 else 0
                sql.query("SELECT COUNT(*) FROM usage_session").use { assertTrue(it.moveToFirst()); assertEquals(expectedUsage, it.getInt(0)) }
                sql.query("SELECT COUNT(*) FROM usage_session_window").use { assertTrue(it.moveToFirst()); assertEquals(expectedUsage, it.getInt(0)) }
                sql.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
            } finally { database.close() }
        }
    }
}
