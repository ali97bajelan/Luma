package com.luma.lifeos.data.db

import androidx.sqlite.db.SupportSQLiteDatabase

/** Converts old journal events in-place and keeps their event identity and linked rows. */
internal fun normalizeLegacyJournals(db: SupportSQLiteDatabase) {
    db.execSQL(
        """INSERT INTO memory (id, life_event_id, importance, note)
            SELECT event.id, event.id, NULL, NULL
            FROM life_event AS event
            JOIN journal_entry ON journal_entry.life_event_id = event.id
            LEFT JOIN memory ON memory.life_event_id = event.id
            WHERE event.type = 'JOURNAL' AND memory.id IS NULL""".trimIndent(),
    )
    db.execSQL(
        """UPDATE life_event
            SET note = (SELECT journal_entry.body FROM journal_entry WHERE journal_entry.life_event_id = life_event.id)
            WHERE type = 'JOURNAL' AND EXISTS (
                SELECT 1 FROM journal_entry WHERE journal_entry.life_event_id = life_event.id
            )""".trimIndent(),
    )
    db.execSQL(
        """DELETE FROM journal_entry
            WHERE life_event_id IN (SELECT id FROM life_event WHERE type = 'JOURNAL')""".trimIndent(),
    )
    db.execSQL(
        """UPDATE life_event SET type = 'MEMORY'
            WHERE type = 'JOURNAL' AND EXISTS (
                SELECT 1 FROM memory WHERE memory.life_event_id = life_event.id
            )""".trimIndent(),
    )
    db.execSQL("UPDATE daily_summary SET freshness = 'STALE'")
}
