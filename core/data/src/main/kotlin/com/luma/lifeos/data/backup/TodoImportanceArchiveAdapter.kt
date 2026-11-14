package com.luma.lifeos.data.backup

/**
 * Keeps the task importance field compatible with portable archives written before schema 13.
 *
 * Schema 13 archives contain importance_code. Older task rows omit it and rely on the current
 * entity's None default when inserted into the staging database.
 */
internal object TodoImportanceArchiveAdapter {
    fun importColumns(table: String, schemaVersion: Int, currentColumns: List<String>): List<String> =
        if (table == "todo_task" && schemaVersion in 4..12) {
            currentColumns.filterNot { it == "importance_code" }
        } else {
            currentColumns
        }
}
