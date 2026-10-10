package com.luma.lifeos.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.repository.RoomEventStore
import com.luma.lifeos.data.repository.RoomLifeEventRepository
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.EventType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Uses compiler-exported schemas; no fabricated 1 -> 1 or destructive migration. */
@RunWith(AndroidJUnit4::class)
class SchemaMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val fileName = "luma-schema-test.db"
    private val eventId = "00000000-0000-0000-0000-000000000001"
    private val journalId = "00000000-0000-0000-0000-000000000002"

    @get:Rule val helper = MigrationTestHelper(instrumentation, LumaDatabase::class.java)

    @After fun cleanUp() { instrumentation.targetContext.deleteDatabase(fileName) }

    @Test fun exportedVersionOneSchemaOpensWithProductionDatabaseAndPreservesContent() = runBlocking {
        helper.createDatabase(fileName, 1).apply {
            execSQL(
                "INSERT INTO life_event (id, type, occurred_at, created_at, updated_at, source) VALUES (?, 'JOURNAL', 1000, 1000, 1000, 'MANUAL')",
                arrayOf(eventId),
            )
            execSQL(
                "INSERT INTO journal_entry (id, life_event_id, body) VALUES (?, ?, 'Survives reopening')",
                arrayOf(journalId, eventId),
            )
        }.close()

        // At v1 this validates the baseline. Once VERSION increases it traverses all real upgrades.
        helper.runMigrationsAndValidate(fileName, LumaDatabase.VERSION, true, *LumaDatabase.MIGRATIONS.toTypedArray()).close()
        val database = LumaDatabase.open(instrumentation.targetContext, fileName)
        try {
            val events = RoomLifeEventRepository(database)
            val stored = requireNotNull(events.get(EventId.parse(eventId)))
            assertEquals(EventType.JOURNAL, stored.type)
            assertEquals(1000L, stored.createdAt.toEpochMilli())
            assertEquals("Survives reopening", RoomEventStore(database).get(stored.id)?.journal?.body)
            events.upsert(stored.copy(title = "Updated after reopening", updatedAt = stored.updatedAt.plusMillis(1)))
        } finally {
            database.close()
        }
        val reopened = LumaDatabase.open(instrumentation.targetContext, fileName)
        try {
            val details = requireNotNull(RoomEventStore(reopened).get(EventId.parse(eventId)))
            assertEquals("Updated after reopening", details.event.title)
            assertEquals(journalId, details.journal?.id)
            assertEquals("Survives reopening", details.journal?.body)
            reopened.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        } finally {
            reopened.close()
        }
    }

    @Test fun unsupportedSchemaVersionFailsWithoutDestroyingUserRecords() {
        helper.createDatabase(fileName, 1).apply {
            execSQL(
                "INSERT INTO life_event (id, type, occurred_at, created_at, updated_at, source) VALUES (?, 'JOURNAL', 1000, 1000, 1000, 'MANUAL')",
                arrayOf(eventId),
            )
            // A version with no migration path must fail rather than recreate the database.
            version = LumaDatabase.VERSION + 1
        }.close()
        val database = LumaDatabase.open(instrumentation.targetContext, fileName)
        try {
            assertThrows(IllegalStateException::class.java) { database.openHelper.writableDatabase }
        } finally {
            database.close()
        }
        val file = instrumentation.targetContext.getDatabasePath(fileName)
        android.database.sqlite.SQLiteDatabase.openDatabase(file.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use { sqlite ->
            sqlite.rawQuery("SELECT id FROM life_event", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(eventId, cursor.getString(0))
            }
        }
    }
}
