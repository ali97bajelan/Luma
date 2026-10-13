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
    )
}
