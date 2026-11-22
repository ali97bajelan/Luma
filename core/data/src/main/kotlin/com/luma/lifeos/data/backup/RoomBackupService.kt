package com.luma.lifeos.data.backup

import android.content.Context
import android.database.Cursor
import androidx.room.Room
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.db.normalizeLegacyJournals
import com.luma.lifeos.data.media.AndroidPhotoCodec
import com.luma.lifeos.data.media.ManagedPhotoFiles
import com.luma.lifeos.domain.backup.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.*
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class RoomBackupService(private val context: Context, private val database: LumaDatabase,
    private val photos: ManagedPhotoFiles, private val workRoot: File, private val photoMutex: Mutex) : BackupService {
    // Capture only directories abandoned by an earlier process, before this instance creates work.
    private val abandoned = workRoot.listFiles().orEmpty().filter { file ->
        file.isDirectory && file.canonicalFile == file.absoluteFile &&
            runCatching { UUID.fromString(file.name).toString() == file.name }.getOrDefault(false)
    }
    private var abandonedCleaned = false
    override suspend fun export(output: OutputStream) = export(output, null, null)

    override suspend fun export(output: OutputStream, focusChoice: BackupFocusExportChoice?) =
        export(output, focusChoice, null)

    override suspend fun export(
        output: OutputStream,
        focusChoice: BackupFocusExportChoice?,
        encryption: BackupEncryptionOptions?,
    ) {
        try {
            exportInternal(output, focusChoice, encryption)
        } finally {
            encryption?.close()
        }
    }

    private suspend fun exportInternal(
        output: OutputStream,
        focusChoice: BackupFocusExportChoice?,
        encryption: BackupEncryptionOptions?,
    ) = withContext(Dispatchers.IO) {
        val work = newWork()
        try {
            val payloads = linkedMapOf<String, File>()
            photoMutex.withLock {
                database.withTransaction {
                    val sqlite = database.openHelper.writableDatabase
                    sqlite.execSQL("INSERT OR IGNORE INTO focus_goal_settings(singleton_id, enabled, settings_revision) VALUES (1, 0, 0)")
                    validateFocusGoalsArchive(sqlite, Instant.ofEpochMilli(System.currentTimeMillis()))
                    val excludedSessionId = (focusChoice as? BackupFocusExportChoice.EXCLUDE_PAUSED)
                        ?.sessionId?.toString()
                    sqlite.query("SELECT id, state FROM focus_session WHERE open_slot = 1 LIMIT 1").use { cursor ->
                        if (cursor.moveToFirst()) {
                            check(cursor.getString(1) == "PAUSED" && cursor.getString(0) == excludedSessionId) {
                                "Pause or stop focus before exporting its history"
                            }
                        }
                    }
                    for (table in BACKUP_TABLES) {
                        currentCoroutineContext().ensureActive()
                        val file = File(work, "$table.tsv")
                        if (table == "focus_tag") {
                            val sessionQuery = FocusMetadataArchiveAdapter.focusSessionExportQuery(excludedSessionId != null)
                            val exportedTagIds = FocusMetadataArchiveAdapter.exportedTagIds(
                                (if (excludedSessionId == null) sqlite.query(sessionQuery)
                                else sqlite.query(sessionQuery, arrayOf(excludedSessionId))).use { cursor ->
                                    val tagIndex = cursor.getColumnIndexOrThrow("tag_id")
                                    buildList { while (cursor.moveToNext()) add(if (cursor.isNull(tagIndex)) null else cursor.getString(tagIndex)) }
                                },
                            )
                            sqlite.query("SELECT * FROM focus_tag ORDER BY id").use { cursor ->
                                file.outputStream().use { out ->
                                    BackupRows.write(
                                        out,
                                        cursor.columnNames.toList(),
                                        FocusMetadataArchiveAdapter.referencedTagRows(cursorRows(cursor), exportedTagIds),
                                    )
                                }
                            }
                            payloads["tables/$table.tsv"] = file
                            continue
                        }
                        val query = exportQuery(table, excludedSessionId != null)
                        val cursor = if (excludedSessionId != null && table in setOf("focus_session", "focus_run")) {
                            sqlite.query(query, arrayOf(excludedSessionId))
                        } else {
                            sqlite.query(query)
                        }
                        cursor.use {
                            file.outputStream().use { out ->
                                val currentColumns = cursor.columnNames.toList()
                                val archiveColumns = TodoHierarchyArchiveAdapter.exportColumns(table, currentColumns)
                                val archiveRows = cursorRows(cursor).map { row ->
                                    TodoHierarchyArchiveAdapter.exportRow(table, currentColumns, row, archiveColumns)
                                }
                                BackupRows.write(out, archiveColumns, archiveRows)
                            }
                        }
                        payloads["tables/$table.tsv"] = file
                    }
                    for (path in referencedPhotos(sqlite)) {
                        val file = requireNotNull(photos.resolve(path)) { "Referenced photo is unavailable" }
                        AndroidPhotoCodec.inspect(file)
                        val copy = File(work, File(path).name)
                        file.copyTo(copy)
                        payloads[path] = copy
                    }
                }
            }
            val archiveWriter: (OutputStream) -> Unit = { archiveOutput ->
                BackupArchive.write(archiveOutput, payloads, CURRENT_BACKUP_SCHEMA_VERSION, System.currentTimeMillis())
            }
            if (encryption == null) {
                archiveWriter(output)
            } else {
                encryption.usePassword { password ->
                    EncryptedBackupEnvelope.encrypt(output, password, archiveWriter)
                }
            }
        } finally { work.deleteRecursively() }
    }

    override suspend fun prepare(input: InputStream): PreparedBackup = prepare(input, null)

    override suspend fun prepare(
        input: InputStream,
        encryption: BackupEncryptionOptions?,
    ): PreparedBackup {
        try {
            return prepareInternal(input, encryption)
        } finally {
            encryption?.close()
        }
    }

    private suspend fun prepareInternal(
        input: InputStream,
        encryption: BackupEncryptionOptions?,
    ): PreparedBackup = withContext(Dispatchers.IO) {
        val work = newWork()
        var staged: LumaDatabase? = null
        try {
            val bufferedInput = input.buffered()
            bufferedInput.mark(BackupFormatMarker.ENCRYPTED_SIGNATURE.length)
            val prefix = ByteArray(BackupFormatMarker.ENCRYPTED_SIGNATURE.length)
            var prefixLength = 0
            while (prefixLength < prefix.size) {
                val count = bufferedInput.read(prefix, prefixLength, prefix.size - prefixLength)
                if (count <= 0) break
                prefixLength += count
            }
            bufferedInput.reset()
            val isEncrypted = BackupFormatMarker.isEncryptedPrefix(prefix, prefixLength)
            val payloadDirectory = File(work, "payload")
            val (manifest, archiveFormat) = if (isEncrypted) {
                val password = encryption ?: throw BackupPasswordRequiredException()
                val authenticatedArchive = File(work, "authenticated.zip")
                try {
                    password.usePassword { secret ->
                        EncryptedBackupEnvelope.decryptToFile(bufferedInput, secret, authenticatedArchive)
                    }
                    authenticatedArchive.inputStream().use { archive ->
                        BackupArchive.read(archive, payloadDirectory)
                    } to BackupArchiveFormat.ENCRYPTED
                } finally {
                    authenticatedArchive.delete()
                }
            } else {
                BackupArchive.read(bufferedInput, payloadDirectory) to BackupArchiveFormat.PLAINTEXT_ZIP
            }
            val archiveTables = backupTables(manifest.schemaVersion)
            require(manifest.names.filter { it.startsWith("tables/") }.toSet() == archiveTables.map { "tables/$it.tsv" }.toSet()) { "Backup tables do not match its schema version" }
            val stage = Room.databaseBuilder(context.applicationContext, LumaDatabase::class.java, File(work, "stage.db").path)
                .setJournalMode(androidx.room.RoomDatabase.JournalMode.TRUNCATE).build()
            staged = stage
            stage.withTransaction {
                val sql = stage.openHelper.writableDatabase
                for (table in backupTables(manifest.schemaVersion)) {
                    currentCoroutineContext().ensureActive()
                    val currentColumns = columns(sql, table)
                    val currentNames = currentColumns.map { it.name }
                    val archiveHeader = if (table == "todo_task") {
                        BackupRows.readHeader(File(work, "payload/tables/todo_task.tsv"))
                    } else {
                        null
                    }
                    val names = backupImportColumns(
                        table,
                        manifest.schemaVersion,
                        currentNames,
                        archiveHeader,
                    )
                    val focusInsertNames = FocusMetadataArchiveAdapter.insertColumns(
                        table,
                        manifest.schemaVersion,
                        names,
                        currentNames,
                    )
                    val insertNames = TodoHierarchyArchiveAdapter.insertColumns(
                        table,
                        manifest.schemaVersion,
                        names,
                        focusInsertNames,
                    )
                    val importColumns = currentColumns.filter { it.name in names }
                    val statement = sql.compileStatement(BackupSql.insert(table, insertNames))
                    statement.use {
                        val passes: List<TodoHierarchyArchiveAdapter.ImportPass?> =
                            if (table == "todo_task") {
                                listOf(
                                    TodoHierarchyArchiveAdapter.ImportPass.ROOTS,
                                    TodoHierarchyArchiveAdapter.ImportPass.CHILDREN,
                                )
                            } else {
                                listOf(null)
                            }
                        passes.forEach { pass ->
                            BackupRows.read(File(work, "payload/tables/$table.tsv"), names) { row ->
                                if (TodoHierarchyArchiveAdapter.matchesImportPass(names, row, pass)) {
                                    validateTypes(importColumns, row)
                                    val focusAdaptedRow = FocusMetadataArchiveAdapter.adaptRow(
                                        table,
                                        manifest.schemaVersion,
                                        names,
                                        row,
                                        insertNames,
                                    )
                                    val insertRow = TodoHierarchyArchiveAdapter.adaptRow(
                                        table,
                                        manifest.schemaVersion,
                                        names,
                                        focusAdaptedRow,
                                        insertNames,
                                    )
                                    val validationRow = insertNames.zip(insertRow).toMap().toMutableMap()
                                    if (table == "todo_task" && manifest.schemaVersion == 4) {
                                        validationRow["due_time_minutes"] = null
                                    }
                                    validateBackupRow(table, validationRow)
                                    val stagedRow = TodoHierarchyArchiveAdapter.prepareStagingRow(
                                        table,
                                        pass,
                                        insertNames,
                                        insertRow,
                                    )
                                    bind(statement, stagedRow)
                                    statement.executeInsert()
                                }
                            }
                        }
                    }
                }
                if (manifest.schemaVersion >= 4) {
                    val taskColumns = backupImportColumns(
                        "todo_task",
                        manifest.schemaVersion,
                        columns(sql, "todo_task").map { it.name },
                        BackupRows.readHeader(File(work, "payload/tables/todo_task.tsv")),
                    )
                    TodoHierarchyArchiveAdapter.restoreRootStatusesFromArchive(
                        sql,
                        File(work, "payload/tables/todo_task.tsv"),
                        taskColumns,
                    )
                }
                normalizeLegacyJournals(sql)
                sql.execSQL("INSERT OR IGNORE INTO focus_goal_settings(singleton_id, enabled, settings_revision) VALUES (1, 0, 0)")
                validateFocusArchive(sql)
                validateFocusMetadataArchive(sql)
                validateFocusGoalsArchive(sql, Instant.ofEpochMilli(manifest.createdAt))
                TodoHierarchyArchiveAdapter.validateStaged(sql)
                sql.query("PRAGMA foreign_key_check").use { require(!it.moveToFirst()) { "Backup contains broken references" } }
                sql.query("PRAGMA integrity_check").use { require(it.moveToFirst() && it.getString(0) == "ok") }
                BackupSql.invalidGraphQueries.forEach { query ->
                    sql.query(query).use { require(!it.moveToFirst()) { "Backup content does not match its event" } }
                }
                val paths = referencedPhotos(sql)
                require(paths == manifest.names.filter { it.startsWith("photos/") }.toSet()) { "Backup photo coverage does not match" }
                paths.forEach { path -> AndroidPhotoCodec.inspect(File(work, "payload/$path")) }
            }
            val count = stage.openHelper.readableDatabase.query("SELECT COUNT(*) FROM life_event").use { it.moveToFirst(); it.getLong(0) }
            val habitCount = stage.openHelper.readableDatabase.query("SELECT COUNT(*) FROM habit").use { it.moveToFirst(); it.getLong(0) }
            val habitLogCount = stage.openHelper.readableDatabase.query("SELECT COUNT(*) FROM habit_log").use { it.moveToFirst(); it.getLong(0) }
            val todoCount = stage.openHelper.readableDatabase.query("SELECT COUNT(*) FROM todo_task").use { it.moveToFirst(); it.getLong(0) }
            val focusSessionCount = stage.openHelper.readableDatabase.query("SELECT COUNT(*) FROM focus_session").use { it.moveToFirst(); it.getLong(0) }
            val paths = referencedPhotos(stage.openHelper.readableDatabase)
            // Validate timestamp before handing ownership to the retained preview.
            val preview = BackupPreview(count, paths.size, Instant.ofEpochMilli(manifest.createdAt),
                habitCount, habitLogCount, todoCount, focusSessionCount, archiveFormat)
            StagedBackup(stage, work, paths, manifest.schemaVersion, preview)
        } catch (failure: Throwable) {
            staged?.close(); work.deleteRecursively(); throw failure
        }
    }
    private inner class StagedBackup(private val stage: LumaDatabase, private val work: File,
        private val paths: Set<String>, private val schemaVersion: Int, override val preview: BackupPreview) : PreparedBackup {
        private val claimed = AtomicBoolean(false)
        override fun close() { if (claimed.compareAndSet(false, true)) dispose() }
        private fun dispose() { stage.close(); work.deleteRecursively() }
        override suspend fun restore() {
            check(claimed.compareAndSet(false, true)) { "Backup preview has already been used" }
            // Once confirmed, publication + DB commit must finish even if the screen disappears.
            withContext(Dispatchers.IO + NonCancellable) {
                try {
                    photoMutex.withLock {
                        val published = mutableListOf<String>()
                        val mapping = linkedMapOf<String, String>()
                        var committed = false
                        try {
                            paths.forEach { path ->
                                val photo = photos.publishBackup(UUID.randomUUID(), { File(work, "payload/$path").inputStream() }, AndroidPhotoCodec::inspect)
                                mapping[path] = photo.relativePath; published += photo.relativePath
                            }
                            // A file fsync alone does not make its renamed directory entry durable.
                            published.firstOrNull()?.let { path ->
                                val directory = requireNotNull(photos.resolve(path)).parentFile!!
                                val fd = android.system.Os.open(directory.path, android.system.OsConstants.O_RDONLY, 0)
                                try { android.system.Os.fsync(fd) } finally { android.system.Os.close(fd) }
                            }
                            database.withTransaction {
                                val target = database.openHelper.writableDatabase
                                target.query("SELECT id FROM focus_session WHERE open_slot = 1 LIMIT 1").use {
                                    check(!it.moveToFirst()) { "Stop or discard the active focus session before restoring" }
                                }
                                val liveHealthRevision = healthCheckpoint(target).optLong("revision", 0).coerceAtLeast(0)
                                prepareFocusReplacement(target)
                                BackupSql.deleteStatements.forEach(target::execSQL)
                                // Widget commands and feedback describe this device, not the archive.
                                // Revoke them only inside the committed replacement transaction.
                                database.widgets().deleteAllCommands()
                                database.widgets().resetInstancesAfterRestore()
                                // Family command handles and receipts belong to the pre-restore task graph.
                                database.todoCommands().clearAll()
                                val stagedSql = stage.openHelper.readableDatabase
                                for (table in backupTables(schemaVersion)) {
                                    if (table == "habit_reminder_runtime") continue
                                    if (table == "todo_task") {
                                        val passes = listOf(
                                            TodoHierarchyArchiveAdapter.ImportPass.ROOTS,
                                            TodoHierarchyArchiveAdapter.ImportPass.CHILDREN,
                                        )
                                        passes.forEach { pass ->
                                            stagedSql.query(TodoHierarchyArchiveAdapter.restoreQuery(pass)).use { cursor ->
                                                val names = cursor.columnNames.toList()
                                                target.compileStatement(BackupSql.insert(table, names)).use { statement ->
                                                    cursorRows(cursor).forEach { original ->
                                                        val row = original.toMutableList()
                                                        if (pass == TodoHierarchyArchiveAdapter.ImportPass.ROOTS) {
                                                            row[names.indexOf("completed_at")] = null
                                                        }
                                                        row[names.indexOf("revision_token")] = UUID.randomUUID().toString()
                                                        val familyRevisionIndex = names.indexOf("family_revision_token")
                                                        row[familyRevisionIndex] = if (pass == TodoHierarchyArchiveAdapter.ImportPass.ROOTS) {
                                                            UUID.randomUUID().toString()
                                                        } else {
                                                            val parentId = row[names.indexOf("parent_id")] as? String
                                                                ?: error("Restored child task is missing its parent")
                                                            target.query(
                                                                "SELECT family_revision_token FROM todo_task WHERE id = ?",
                                                                arrayOf(parentId),
                                                            ).use { parentCursor ->
                                                                check(parentCursor.moveToFirst()) { "Restored parent task is missing" }
                                                                parentCursor.getString(0)
                                                            }
                                                        }
                                                        bind(statement, row)
                                                        statement.executeInsert()
                                                    }
                                                }
                                            }
                                        }
                                        TodoHierarchyArchiveAdapter.restoreRootStatusesFromDatabase(stagedSql, target)
                                        continue
                                    }
                                    stagedSql.query("SELECT * FROM `$table`").use { cursor ->
                                        val names = cursor.columnNames.toList()
                                        target.compileStatement(BackupSql.insert(table, names)).use { statement ->
                                            cursorRows(cursor).forEach { original ->
                                                val row = original.toMutableList()
                                                if (table == "media_attachment") {
                                                    val index = names.indexOf("relative_path"); row[index] = mapping.getValue(row[index] as String)
                                                }
                                                if (table == "habit_reminder") {
                                                    row[names.indexOf("enabled")] = 0L
                                                    row[names.indexOf("revision_token")] = UUID.randomUUID().toString()
                                                }
                                                if (table == "task_reminder") {
                                                    val taskId = row[names.indexOf("task_id")] as String
                                                    val restoredTaskRevision = target.query(
                                                        "SELECT revision_token FROM todo_task WHERE id = ?", arrayOf(taskId),
                                                    ).use { cursor ->
                                                        check(cursor.moveToFirst()) { "Restored reminder task is missing" }
                                                        cursor.getString(0)
                                                    }
                                                    row[names.indexOf("enabled")] = 0L
                                                    row[names.indexOf("task_revision")] = restoredTaskRevision
                                                    row[names.indexOf("revision_token")] = UUID.randomUUID().toString()
                                                    row[names.indexOf("consumed_action_token")] = null
                                                    row[names.indexOf("last_delivered_slot")] = null
                                                    row[names.indexOf("snooze_slot")] = null
                                                    row[names.indexOf("snooze_until")] = null
                                                    row[names.indexOf("needs_reconcile")] = 1L
                                                }
                                                bind(statement, row); statement.executeInsert()
                                            }
                                        }
                                    }
                                }
                                normalizeLegacyJournals(target)
                                target.execSQL("INSERT OR IGNORE INTO focus_goal_settings(singleton_id, enabled, settings_revision) VALUES (1, 0, 0)")
                                target.execSQL("""INSERT INTO habit_reminder_runtime
                                    (habit_id, revision_token, last_delivered_slot, consumed_action_token, snooze_slot, snooze_until, needs_reconcile)
                                    SELECT habit_id, revision_token, NULL, NULL, NULL, NULL, 1 FROM habit_reminder""")
                                target.execSQL("UPDATE daily_summary SET freshness = 'STALE'")
                                // Retain saved observations/last sync, but cross-device consent/cursors are not portable.
                                val health = healthCheckpoint(target)
                                val restoredRevision = health.optLong("revision", 0)
                                require(restoredRevision >= 0)
                                val disabled = org.json.JSONObject().put("v", 1).put("enabled", false).put("status", "DISABLED")
                                val disabledHealth = org.json.JSONObject(disabled.toString())
                                    .put("revision", Math.addExact(maxOf(liveHealthRevision, restoredRevision), 1))
                                health.optString("zone").takeIf { it.isNotBlank() }?.let { zone ->
                                    java.time.ZoneId.of(zone); disabledHealth.put("zone", zone)
                                }
                                target.execSQL("UPDATE integration_state SET status = 'NOT_CONFIGURED', checkpoint = ? WHERE source = 'USAGE_STATS'", arrayOf(disabled.toString()))
                                target.execSQL("UPDATE integration_state SET status = 'NOT_CONFIGURED', checkpoint = ? WHERE source = 'HEALTH_CONNECT'", arrayOf(disabledHealth.toString()))
                            }
                            committed = true
                        } finally {
                            if (!committed) published.forEach { photos.resolve(it)?.delete() }
                            // Cleanup is best-effort after commit; its failure cannot misreport a successful replacement.
                            runCatching { photos.clean(referencedPhotos(database.openHelper.readableDatabase)) }
                        }
                    }
                } finally { dispose() }
            }
        }
    }
    private fun healthCheckpoint(sqlite: SupportSQLiteDatabase): org.json.JSONObject =
        sqlite.query("SELECT checkpoint FROM integration_state WHERE source = 'HEALTH_CONNECT'").use { cursor ->
            if (!cursor.moveToFirst() || cursor.isNull(0)) org.json.JSONObject()
            else { val text = cursor.getString(0); requireSafeJsonDepth(text); org.json.JSONObject(text) }
        }
    private fun exportQuery(table: String, excludePausedSession: Boolean): String = when (table) {
        "todo_task" -> TodoHierarchyArchiveAdapter.exportQuery()
        "focus_session" -> FocusMetadataArchiveAdapter.focusSessionExportQuery(excludePausedSession) + " ORDER BY id"
        "focus_run" -> """SELECT r.* FROM focus_run AS r
            INNER JOIN focus_session AS s ON s.id = r.session_id
            WHERE s.state IN ('FINISHED', 'INTERRUPTED') AND s.open_slot IS NULL
              AND r.open_session_id IS NULL${if (excludePausedSession) " AND s.id != ?" else ""}
            ORDER BY r.session_id, r.ordinal""".trimIndent()
        else -> "SELECT * FROM `$table`"
    }

    private fun prepareFocusReplacement(sqlite: SupportSQLiteDatabase) {
        sqlite.execSQL("DELETE FROM focus_runtime")
        sqlite.execSQL("DELETE FROM focus_break_runtime")
        sqlite.execSQL("DELETE FROM focus_command_receipt")
        sqlite.execSQL("DELETE FROM focus_product_command_receipt")
        sqlite.execSQL("DELETE FROM focus_invitation_delivery")
        sqlite.execSQL(
            """UPDATE focus_invitation_settings
               SET enabled = 0,
                   revision = CASE WHEN revision = 9223372036854775807 THEN 0 ELSE revision + 1 END,
                   weekday_mask = 0,
                   minute_of_day = 540
               WHERE singleton_id = 1""",
        )
        sqlite.execSQL("DELETE FROM focus_tag_alias")
        sqlite.execSQL("UPDATE focus_tag_catalog_state SET revision = 0 WHERE singleton_id = 1")
        sqlite.execSQL("DELETE FROM focus_deleted_session")
        sqlite.execSQL(
            "INSERT OR REPLACE INTO focus_storage_state(singleton_id, generation_id) VALUES (1, ?)",
            arrayOf(UUID.randomUUID().toString()),
        )
    }

    @Synchronized private fun newWork(): File {
        check(workRoot.isDirectory || workRoot.mkdirs())
        if (!abandonedCleaned) {
            abandoned.forEach { check(!it.exists() || it.deleteRecursively()) { "Could not clear abandoned backup staging" } }
            abandonedCleaned = true
        }
        return File(workRoot, UUID.randomUUID().toString()).apply { check(mkdir()) }
    }
    private fun referencedPhotos(sqlite: SupportSQLiteDatabase): Set<String> = sqlite.query("SELECT relative_path FROM media_attachment").use { cursor ->
        buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
    }
    private data class Column(val name: String, val type: String, val required: Boolean)
    private fun columns(sqlite: SupportSQLiteDatabase, table: String): List<Column> = sqlite.query("PRAGMA table_info(`$table`)").use { cursor ->
        buildList { while (cursor.moveToNext()) add(Column(cursor.getString(1), cursor.getString(2), cursor.getInt(3) != 0)) }
    }
    private fun validateTypes(columns: List<Column>, row: List<Any?>) {
        columns.zip(row).forEach { (column, value) ->
            if (value == null) require(!column.required) { "Missing required backup value" }
            else require(when (column.type) { "TEXT" -> value is String; "INTEGER" -> value is Long; "REAL" -> value is Double; else -> false }) { "Invalid backup value type" }
        }
    }
    private fun cursorRows(cursor: Cursor): Sequence<List<Any?>> = sequence {
        while (cursor.moveToNext()) yield((0 until cursor.columnCount).map { index -> when (cursor.getType(index)) {
            Cursor.FIELD_TYPE_NULL -> null
            Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
            Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
            Cursor.FIELD_TYPE_STRING -> cursor.getString(index)
            else -> error("Unsupported database value")
        } })
    }
    private fun bind(statement: androidx.sqlite.db.SupportSQLiteStatement, row: List<Any?>) {
        statement.clearBindings()
        row.forEachIndexed { index, value -> when (value) {
            null -> statement.bindNull(index + 1)
            is String -> statement.bindString(index + 1, value)
            is Long -> statement.bindLong(index + 1, value)
            is Double -> statement.bindDouble(index + 1, value)
            else -> error("Unsupported backup value")
        } }
    }
}
