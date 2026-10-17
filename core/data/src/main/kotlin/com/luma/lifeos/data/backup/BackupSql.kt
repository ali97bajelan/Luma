package com.luma.lifeos.data.backup

/** Fixed, application-owned SQL. Archive content supplies only bound values. */
internal object BackupSql {
    val deleteStatements = BACKUP_TABLES.asReversed().map { "DELETE FROM `$it`" }
    fun insert(table: String, names: List<String>) = "INSERT INTO `$table` (${names.joinToString(",") { "`$it`" }}) VALUES (${names.joinToString(",") { "?" }})"
    val invalidGraphQueries = listOf(
        "SELECT c.id FROM mood_entry c JOIN life_event e ON e.id = c.life_event_id WHERE e.type <> 'MOOD' LIMIT 1",
        "SELECT c.id FROM journal_entry c JOIN life_event e ON e.id = c.life_event_id WHERE e.type <> 'JOURNAL' LIMIT 1",
        "SELECT c.id FROM activity_record c JOIN life_event e ON e.id = c.life_event_id WHERE e.type <> 'ACTIVITY' OR c.source <> e.source LIMIT 1",
        "SELECT c.id FROM app_usage_record c JOIN life_event e ON e.id = c.life_event_id WHERE e.type <> 'APP_USAGE' OR c.source <> e.source LIMIT 1",
        "SELECT h.id FROM habit h WHERE NOT EXISTS (SELECT 1 FROM habit_settings WHERE setting_id = 1) LIMIT 1",
        "SELECT h.id FROM habit h WHERE NOT EXISTS (SELECT 1 FROM habit_schedule s WHERE s.habit_id = h.id AND s.effective_from = h.start_date) LIMIT 1",
        "SELECT s.id FROM habit_schedule s JOIN habit h ON h.id = s.habit_id WHERE s.effective_from < h.start_date LIMIT 1",
        "SELECT l.id FROM habit_log l JOIN habit h ON h.id = l.habit_id LEFT JOIN habit_settings z ON z.setting_id = 1 LEFT JOIN habit_schedule s ON s.habit_id = h.id AND s.effective_from = (SELECT MAX(previous.effective_from) FROM habit_schedule previous WHERE previous.habit_id = h.id AND previous.effective_from <= l.habit_date) WHERE l.habit_date < h.start_date OR l.zone_id <> z.tracking_zone_id OR s.id IS NULL OR s.state <> 'ACTIVE' OR (s.weekday_mask & (1 << ((CAST(strftime('%w', l.habit_date) AS INTEGER) + 6) % 7))) = 0 LIMIT 1",
        "SELECT c.habit_id FROM habit_day_choice c JOIN habit h ON h.id = c.habit_id LEFT JOIN habit_schedule s ON s.habit_id = c.habit_id AND s.effective_from = (SELECT MAX(previous.effective_from) FROM habit_schedule previous WHERE previous.habit_id = c.habit_id AND previous.effective_from <= c.habit_date) WHERE c.habit_date < h.start_date OR s.id IS NULL OR s.state <> 'ACTIVE' OR (s.weekday_mask & (1 << ((CAST(strftime('%w', c.habit_date) AS INTEGER) + 6) % 7))) = 0 LIMIT 1",
        "SELECT r.habit_id FROM habit_reminder r LEFT JOIN habit_reminder_runtime rt ON rt.habit_id = r.habit_id AND rt.revision_token = r.revision_token WHERE rt.habit_id IS NULL LIMIT 1",
    )
}
