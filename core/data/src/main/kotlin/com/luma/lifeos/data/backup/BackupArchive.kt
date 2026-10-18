package com.luma.lifeos.data.backup

import java.io.*
import java.security.MessageDigest
import java.util.Properties
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal val BACKUP_TABLES_V1 = listOf("life_event", "emotion", "person", "place", "tag",
    "mood_entry", "journal_entry", "memory", "activity_record", "app_usage_record",
    "media_attachment", "mood_emotion", "event_person", "event_place", "event_tag",
    "daily_summary", "integration_state")
internal val BACKUP_TABLES_V2 = BACKUP_TABLES_V1 + listOf("usage_session", "usage_session_window")
internal val HABIT_BACKUP_TABLES = listOf("habit_settings", "habit", "habit_schedule", "habit_log",
    "habit_day_choice", "habit_reminder", "habit_reminder_runtime")
internal val BACKUP_TABLES = BACKUP_TABLES_V2 + HABIT_BACKUP_TABLES
internal fun backupTables(version: Int): List<String> = when (version) {
    1 -> BACKUP_TABLES_V1
    2 -> BACKUP_TABLES_V2
    3 -> BACKUP_TABLES
    else -> error("Unsupported database schema version")
}
data class ArchiveManifest(val formatVersion: Int, val schemaVersion: Int, val createdAt: Long, val names: Set<String>)

/** Expanded-size limits and hashes are validated before any payload is used. */
object BackupArchive {
    private const val MANIFEST = "manifest.properties"
    const val MAX_TOTAL_BYTES = 2L * 1024 * 1024 * 1024
    private const val MAX_ENTRIES = 100_000
    private fun safeName(name: String): Boolean {
        if (name == MANIFEST) return true
        if (BACKUP_TABLES.any { name == "tables/$it.tsv" }) return true
        if (!name.startsWith("photos/") || !name.endsWith(".img")) return false
        val id = name.removePrefix("photos/").removeSuffix(".img")
        return runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)
    }
    fun write(output: OutputStream, files: Map<String, File>, schemaVersion: Int, createdAt: Long) {
        backupTables(schemaVersion)
        require(files.size <= MAX_ENTRIES && files.keys.all { it != MANIFEST && safeName(it) })
        val properties = Properties().apply {
            setProperty("format", "luma-backup"); setProperty("format_version", "1")
            setProperty("schema_version", schemaVersion.toString()); setProperty("created_at", createdAt.toString())
            setProperty("file_count", files.size.toString())
        }
        var total = 0L
        ZipOutputStream(object : FilterOutputStream(output) { override fun close() { flush() } }).use { zip ->
            files.toSortedMap().forEach { (name, file) ->
                zip.putNextEntry(ZipEntry(name))
                val digest = MessageDigest.getInstance("SHA-256")
                var size = 0L
                file.inputStream().use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = input.read(buffer); if (n < 0) break
                        size += n; total += n
                        require(size <= entryLimit(name) && total <= MAX_TOTAL_BYTES) { "Backup is too large" }
                        digest.update(buffer, 0, n); zip.write(buffer, 0, n)
                    }
                }
                zip.closeEntry()
                properties.setProperty("size.$name", size.toString())
                properties.setProperty("sha256.$name", hex(digest.digest()))
            }
            zip.putNextEntry(ZipEntry(MANIFEST)); properties.store(zip, "Luma portable backup v1"); zip.closeEntry()
        }
    }
    fun read(input: InputStream, directory: File, maxTotalBytes: Long = MAX_TOTAL_BYTES): ArchiveManifest {
        require(!directory.exists() || directory.list().isNullOrEmpty())
        check(directory.isDirectory || directory.mkdirs())
        val root = directory.canonicalFile
        val hashes = linkedMapOf<String, Pair<Long, String>>()
        var total = 0L
        ZipInputStream(object : FilterInputStream(input) { override fun close() {} }.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                require(!entry.isDirectory && safeName(name) && name !in hashes && hashes.size < MAX_ENTRIES + 1) { "Invalid archive entry" }
                val target = File(root, name)
                require(target.canonicalFile == target.absoluteFile && target.canonicalPath.startsWith(root.path + File.separator))
                check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs())
                val digest = MessageDigest.getInstance("SHA-256"); var size = 0L
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = zip.read(buffer); if (n < 0) break
                        size += n; total += n
                        require(size <= entryLimit(name) && total <= maxTotalBytes) { "Archive is too large" }
                        digest.update(buffer, 0, n); output.write(buffer, 0, n)
                    }
                }
                hashes[name] = size to hex(digest.digest()); zip.closeEntry()
            }
        }
        require(MANIFEST in hashes) { "Backup manifest is missing" }
        val properties = Properties().apply { File(root, MANIFEST).inputStream().use(::load) }
        require(properties.getProperty("format") == "luma-backup")
        val version = properties.getProperty("format_version").toInt()
        require(version == 1) { "Unsupported backup format" }
        val schema = properties.getProperty("schema_version").toInt(); backupTables(schema)
        val created = properties.getProperty("created_at").toLong()
        val payloads = hashes.filterKeys { it != MANIFEST }
        require(properties.getProperty("file_count").toInt() == payloads.size)
        val expectedKeys = mutableSetOf("format", "format_version", "schema_version", "created_at", "file_count")
        payloads.forEach { (name, info) ->
            require(properties.getProperty("size.$name") == info.first.toString() && properties.getProperty("sha256.$name") == info.second) { "Backup checksum mismatch" }
            expectedKeys += "size.$name"; expectedKeys += "sha256.$name"
        }
        require(properties.stringPropertyNames() == expectedKeys) { "Backup files are missing or unexpected" }
        return ArchiveManifest(version, schema, created, payloads.keys)
    }
    private fun entryLimit(name: String): Long = when {
        name == MANIFEST -> 32L * 1024 * 1024
        name.startsWith("photos/") -> 25L * 1024 * 1024
        else -> 512L * 1024 * 1024
    }
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
}
