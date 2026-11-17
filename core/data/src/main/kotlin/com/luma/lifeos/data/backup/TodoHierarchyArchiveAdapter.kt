package com.luma.lifeos.data.backup

import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File
import java.util.UUID

/** Portable task-family adaptation; device-local family guards and command records are not archived. */
internal object TodoHierarchyArchiveAdapter {
    internal enum class ImportPass { ROOTS, CHILDREN }

    private const val TASK_TABLE = "todo_task"
    private const val FAMILY_REVISION = "family_revision_token"
    private const val PARENT_ID = "parent_id"
    private const val SIBLING_POSITION = "sibling_position"
    private const val COMPLETED_AT = "completed_at"

    fun importColumns(
        table: String,
        schemaVersion: Int,
        currentColumns: List<String>,
        archiveHeader: List<String>? = null,
    ): List<String> {
        if (table != TASK_TABLE) return currentColumns
        require(schemaVersion in 4..14) { "Unsupported task archive schema" }
        val omitted = buildSet {
            add(FAMILY_REVISION)
            if (schemaVersion < 14) {
                add(PARENT_ID)
                add(SIBLING_POSITION)
            }
        }
        val legacyColumns = currentColumns.filterNot { it in omitted }
        // Some pre-I08 schema-13 builds wrote the then-current Room 17 columns under
        // schema 13. Accept that exact header, but discard its device-local family token.
        val transitionalSchema13 = schemaVersion == 13 && archiveHeader == currentColumns
        val expectedHeader = if (transitionalSchema13) currentColumns else legacyColumns
        archiveHeader?.let {
            require(it == expectedHeader) { "Task columns do not match the archive schema" }
        }
        return expectedHeader
    }

    /** Build current staging rows, supplying safe defaults for every legacy task archive. */
    fun insertColumns(
        table: String,
        schemaVersion: Int,
        sourceColumns: List<String>,
        currentColumns: List<String>,
    ): List<String> {
        if (table != TASK_TABLE) return currentColumns
        require(schemaVersion in 4..14) { "Unsupported task archive schema" }
        require("id" in sourceColumns && "revision_token" in sourceColumns) {
            "Task archive is missing its stable identity"
        }
        return currentColumns
    }

    fun adaptRow(
        table: String,
        schemaVersion: Int,
        sourceColumns: List<String>,
        sourceValues: List<Any?>,
        insertColumns: List<String>,
    ): List<Any?> {
        if (table != TASK_TABLE) return sourceValues
        require(sourceColumns.size == sourceValues.size)
        require(schemaVersion in 4..14) { "Unsupported task archive schema" }
        val hasParentColumn = PARENT_ID in sourceColumns
        val hasSiblingColumn = SIBLING_POSITION in sourceColumns
        val hasFamilyRevision = FAMILY_REVISION in sourceColumns
        require(hasParentColumn == hasSiblingColumn) {
            "Task hierarchy columns are incomplete"
        }
        val hasHierarchy = hasParentColumn
        val validHierarchyHeader = when (schemaVersion) {
            14 -> hasHierarchy && !hasFamilyRevision
            13 -> hasHierarchy == hasFamilyRevision
            else -> !hasHierarchy && !hasFamilyRevision
        }
        require(validHierarchyHeader) { "Task hierarchy columns do not match the archive version" }
        require((schemaVersion >= 13) == ("importance_code" in sourceColumns)) {
            "Task importance column does not match the archive version"
        }

        val source = sourceColumns.zip(sourceValues).toMap()
        val values = source.toMutableMap().apply {
            put("due_time_minutes", source["due_time_minutes"])
            put("importance_code", source["importance_code"] ?: 0L)
            put(PARENT_ID, source[PARENT_ID])
            put(SIBLING_POSITION, source[SIBLING_POSITION])
            put(FAMILY_REVISION, UUID.randomUUID().toString())
        }
        return insertColumns.map { column ->
            require(column in values) { "Unsupported current task column: $column" }
            values.getValue(column)
        }
    }

    fun matchesImportPass(
        sourceColumns: List<String>,
        sourceValues: List<Any?>,
        pass: ImportPass?,
    ): Boolean {
        if (pass == null) return true
        val parentIndex = sourceColumns.indexOf(PARENT_ID)
        val isRoot = parentIndex < 0 || sourceValues[parentIndex] == null
        return when (pass) {
            ImportPass.ROOTS -> isRoot
            ImportPass.CHILDREN -> !isRoot
        }
    }

    /** Temporarily keep roots open while children are staged; their archived status is applied last. */
    fun prepareStagingRow(
        table: String,
        pass: ImportPass?,
        insertColumns: List<String>,
        values: List<Any?>,
    ): List<Any?> {
        if (table != TASK_TABLE || pass != ImportPass.ROOTS) return values
        val completedIndex = insertColumns.indexOf(COMPLETED_AT)
        require(completedIndex >= 0)
        return values.toMutableList().also { it[completedIndex] = null }
    }

    fun restoreRootStatusesFromArchive(
        database: SupportSQLiteDatabase,
        archive: File,
        sourceColumns: List<String>,
    ) {
        val idIndex = sourceColumns.indexOf("id")
        val completedIndex = sourceColumns.indexOf(COMPLETED_AT)
        val parentIndex = sourceColumns.indexOf(PARENT_ID)
        require(idIndex >= 0 && completedIndex >= 0)
        BackupRows.read(archive, sourceColumns) { row ->
            val isRoot = parentIndex < 0 || row[parentIndex] == null
            val completedAt = row[completedIndex]
            if (isRoot && completedAt != null) {
                require(completedAt is Long && row[idIndex] is String)
                database.execSQL(
                    "UPDATE todo_task SET completed_at = ? WHERE id = ?",
                    arrayOf<Any?>(completedAt, row[idIndex]),
                )
            }
        }
    }

    fun restoreRootStatusesFromDatabase(
        source: SupportSQLiteDatabase,
        target: SupportSQLiteDatabase,
    ) {
        source.query(
            "SELECT id, completed_at FROM todo_task WHERE parent_id IS NULL AND completed_at IS NOT NULL ORDER BY id",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                target.execSQL(
                    "UPDATE todo_task SET completed_at = ? WHERE id = ?",
                    arrayOf<Any?>(cursor.getLong(1), cursor.getString(0)),
                )
            }
        }
    }

    fun exportColumns(table: String, currentColumns: List<String>): List<String> =
        if (table == TASK_TABLE) currentColumns.filterNot { it == FAMILY_REVISION } else currentColumns

    fun exportRow(
        table: String,
        currentColumns: List<String>,
        values: List<Any?>,
        exportedColumns: List<String>,
    ): List<Any?> {
        if (table != TASK_TABLE) return values
        require(currentColumns.size == values.size)
        return buildList(exportedColumns.size) {
            exportedColumns.forEach { column ->
                val index = currentColumns.indexOf(column)
                require(index >= 0) { "Unsupported portable task column: $column" }
                add(values[index])
            }
        }
    }

    fun exportQuery(): String =
        """SELECT * FROM todo_task
            ORDER BY CASE WHEN parent_id IS NULL THEN 0 ELSE 1 END,
                     COALESCE(parent_id, id), sibling_position, id""".trimIndent()

    fun restoreQuery(pass: ImportPass): String = when (pass) {
        ImportPass.ROOTS -> "SELECT * FROM todo_task WHERE parent_id IS NULL ORDER BY id"
        ImportPass.CHILDREN -> "SELECT * FROM todo_task WHERE parent_id IS NOT NULL ORDER BY parent_id, sibling_position, id"
    }

    /** Final staged validation runs before preview and before the live replacement transaction. */
    fun validateStaged(database: SupportSQLiteDatabase) {
        database.execSQL(
            """UPDATE todo_task SET family_revision_token = (
                SELECT parent.family_revision_token FROM todo_task AS parent WHERE parent.id = todo_task.parent_id
            ) WHERE parent_id IS NOT NULL""".trimIndent(),
        )
        val invalidHierarchyQueries = listOf(
            """SELECT child.id FROM todo_task AS child
               LEFT JOIN todo_task AS parent ON parent.id = child.parent_id
               WHERE (child.parent_id IS NULL) != (child.sibling_position IS NULL)
                  OR (child.sibling_position IS NOT NULL AND child.sibling_position < 0)
                  OR (child.parent_id IS NOT NULL AND (parent.id IS NULL OR parent.parent_id IS NOT NULL))
               LIMIT 1""",
            """SELECT child.id FROM todo_task AS child
               JOIN todo_task AS parent ON parent.id = child.parent_id
               WHERE parent.completed_at IS NOT NULL AND child.completed_at IS NULL
               LIMIT 1""",
            """SELECT parent_id FROM todo_task
               WHERE parent_id IS NOT NULL
               GROUP BY parent_id, sibling_position
               HAVING COUNT(*) > 1
               LIMIT 1""",
        )
        invalidHierarchyQueries.forEach { query ->
            database.query(query).use {
                require(!it.moveToFirst()) { "Backup contains an invalid task hierarchy" }
            }
        }
    }
}
