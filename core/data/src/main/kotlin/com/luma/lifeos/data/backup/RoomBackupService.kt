package com.luma.lifeos.data.backup

import android.content.Context
import android.database.Cursor
import androidx.room.Room
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.luma.lifeos.data.db.LumaDatabase
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
    override suspend fun export(output: OutputStream) = withContext(Dispatchers.IO) {
        val work = newWork()
        try {
            val payloads = linkedMapOf<String, File>()
            photoMutex.withLock {
                database.withTransaction {
                    val sqlite = database.openHelper.writableDatabase
                    for (table in BACKUP_TABLES) {
                        currentCoroutineContext().ensureActive()
                        val file = File(work, "$table.tsv")
                        sqlite.query("SELECT * FROM `$table`").use { cursor ->
                            file.outputStream().use { out -> BackupRows.write(out, cursor.columnNames.toList(), cursorRows(cursor)) }
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
            BackupArchive.write(output, payloads, LumaDatabase.VERSION, System.currentTimeMillis())
        } finally { work.deleteRecursively() }
    }
    override suspend fun prepare(input: InputStream): PreparedBackup = withContext(Dispatchers.IO) {
        val work = newWork()
        var staged: LumaDatabase? = null
        try {
            val manifest = BackupArchive.read(input, File(work, "payload"))
            require(manifest.names.filter { it.startsWith("tables/") }.toSet() == backupTables(manifest.schemaVersion).map { "tables/$it.tsv" }.toSet()) { "Backup tables do not match its schema version" }
            val stage = Room.databaseBuilder(context.applicationContext, LumaDatabase::class.java, File(work, "stage.db").path)
                .setJournalMode(androidx.room.RoomDatabase.JournalMode.TRUNCATE).build()
            staged = stage
            stage.withTransaction {
                val sql = stage.openHelper.writableDatabase
                for (table in backupTables(manifest.schemaVersion)) {
                    currentCoroutineContext().ensureActive()
                    val columns = columns(sql, table)
                    val names = columns.map { it.name }
                    val statement = sql.compileStatement(BackupSql.insert(table, names))
                    statement.use {
                        BackupRows.read(File(work, "payload/tables/$table.tsv"), names) { row ->
                            validateTypes(columns, row)
                            validateBackupRow(table, names.zip(row).toMap())
                            bind(statement, row); statement.executeInsert()
                        }
                    }
                }
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
            val paths = referencedPhotos(stage.openHelper.readableDatabase)
            // Validate timestamp before handing ownership to the retained preview.
            val preview = BackupPreview(count, paths.size, Instant.ofEpochMilli(manifest.createdAt))
            StagedBackup(stage, work, paths, preview)
        } catch (failure: Throwable) {
            staged?.close(); work.deleteRecursively(); throw failure
        }
    }
    private inner class StagedBackup(private val stage: LumaDatabase, private val work: File,
        private val paths: Set<String>, override val preview: BackupPreview) : PreparedBackup {
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
                                val liveHealthRevision = healthCheckpoint(target).optLong("revision", 0).coerceAtLeast(0)
                                BackupSql.deleteStatements.forEach(target::execSQL)
                                for (table in BACKUP_TABLES) {
                                    stage.openHelper.readableDatabase.query("SELECT * FROM `$table`").use { cursor ->
                                        val names = cursor.columnNames.toList()
                                        target.compileStatement(BackupSql.insert(table, names)).use { statement ->
                                            cursorRows(cursor).forEach { original ->
                                                val row = original.toMutableList()
                                                if (table == "media_attachment") {
                                                    val index = names.indexOf("relative_path"); row[index] = mapping.getValue(row[index] as String)
                                                }
                                                bind(statement, row); statement.executeInsert()
                                            }
                                        }
                                    }
                                }
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
