package com.luma.lifeos.data.backup

import org.junit.Test
import org.junit.Assert.*
import java.io.*
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupCodecTest {
    @Test fun archiveInventoriesRemainExplicitAndAdditiveAcrossSchemaVersions() {
        val original = listOf(
            "life_event", "emotion", "person", "place", "tag", "mood_entry", "journal_entry",
            "memory", "activity_record", "app_usage_record", "media_attachment", "mood_emotion",
            "event_person", "event_place", "event_tag", "daily_summary", "integration_state",
        )
        val usage = listOf("usage_session", "usage_session_window")
        val habits = listOf(
            "habit_settings", "habit", "habit_schedule", "habit_log", "habit_day_choice",
            "habit_reminder", "habit_reminder_runtime",
        )
        assertEquals(original, backupTables(1))
        assertEquals(original + usage, backupTables(2))
        assertEquals(original + usage + habits, backupTables(3))
        assertEquals(original + usage + habits + "todo_task", backupTables(4))
        assertEquals(backupTables(4), backupTables(5))
        assertEquals(backupTables(5) + "task_reminder", backupTables(6))
        assertEquals(backupTables(6) + listOf("focus_session", "focus_run"), backupTables(7))
        (1..6).forEach { version ->
            assertFalse("focus_session must remain device-local in schema $version", "focus_session" in backupTables(version))
            assertFalse("focus_run must remain device-local in schema $version", "focus_run" in backupTables(version))
        }
        assertEquals(7, CURRENT_BACKUP_SCHEMA_VERSION)
    }

    @Test fun schemaFourTodoImportKeepsItsExactLegacyHeader() {
        val current = listOf(
            "id", "title", "note", "due_date", "completed_at", "created_at", "updated_at",
            "revision_token", "due_time_minutes",
        )
        assertEquals(current.dropLast(1), backupImportColumns("todo_task", 4, current))
        assertEquals(current, backupImportColumns("todo_task", 5, current))
        assertEquals(current, backupImportColumns("journal_entry", 4, current))
    }

    @Test fun duplicateArchiveEntryNamesAreRejected() {
        val root = Files.createTempDirectory("backup-duplicate").toFile()
        try {
            val bytes = ByteArrayOutputStream().apply { ZipOutputStream(this).use { zip ->
                for (name in listOf("tables/event_tag.tsv", "tables/event_tag.foo")) {
                    zip.putNextEntry(ZipEntry(name)); zip.write("event_id\ttag_id\n".toByteArray()); zip.closeEntry()
                }
            } }.toByteArray()
            // ZIP APIs reject duplicate names at write time, so change only the name bytes.
            val from = "tables/event_tag.foo".toByteArray(); val to = "tables/event_tag.tsv".toByteArray()
            for (index in 0..bytes.size-from.size) if (from.indices.all { bytes[index + it] == from[it] })
                to.copyInto(bytes, index)
            assertThrows(Exception::class.java) { BackupArchive.read(ByteArrayInputStream(bytes), File(root, "read")) }
        } finally { root.deleteRecursively() }
    }
    @Test fun archiveDoesNotCloseCallerOwnedStreams() {
        val root = Files.createTempDirectory("backup-owner").toFile()
        try {
            var closes = 0
            val output = object : ByteArrayOutputStream() { override fun close() { closes++; super.close() } }
            BackupArchive.write(output, emptyMap(), 2, 100)
            assertEquals(0, closes)
            var inputCloses = 0
            val input = object : ByteArrayInputStream(output.toByteArray()) { override fun close() { inputCloses++; super.close() } }
            BackupArchive.read(input, File(root, "read"))
            assertEquals(0, inputCloses)
        } finally { root.deleteRecursively() }
    }
    @Test fun rowsPreserveNullEmptyUnicodeNewlinesAndNumericTypes() {
        val row = listOf(null, "", "فارسی\nFrançais\t\\", Long.MIN_VALUE, 1.25)
        val file = File.createTempFile("rows", ".tsv")
        try {
            file.outputStream().use { BackupRows.write(it, listOf("a", "b", "c", "d", "e"), sequenceOf(row)) }
            var result: List<Any?>? = null
            BackupRows.read(file, listOf("a", "b", "c", "d", "e")) { result = it }
            assertEquals(row, result)
        } finally { file.delete() }
    }
    @Test fun rowsRejectWrongHeadersMalformedTypesAndUnboundedLines() {
        val file = File.createTempFile("rows", ".tsv")
        try {
            for (body in listOf("other\nN\n", "a\nQ123\n", "a\nFNaN\n", "a\nS%%%\n", "a\nI9223372036854775808\n", "a\nN\tN\n", "a\n" + "S".repeat(100))) {
                file.writeText(body)
                assertThrows(Exception::class.java) { BackupRows.read(file, listOf("a"), maxLineBytes = 40) {} }
            }
        } finally { file.delete() }
    }
    @Test fun versionedArchiveRoundTripVerifiesEveryPayload() {
        val root = Files.createTempDirectory("backup-test").toFile()
        try {
            val payload = File(root, "rows").apply { writeText("body\nS\n") }
            val bytes = ByteArrayOutputStream().apply { BackupArchive.write(this, mapOf("tables/journal_entry.tsv" to payload), 2, 1234) }.toByteArray()
            val target = File(root, "restored")
            val manifest = BackupArchive.read(ByteArrayInputStream(bytes), target)
            assertEquals(1, manifest.formatVersion)
            assertEquals(2, manifest.schemaVersion)
            assertEquals(1234L, manifest.createdAt)
            assertEquals(payload.readText(), File(target, "tables/journal_entry.tsv").readText())
        } finally { root.deleteRecursively() }
    }
    @Test fun archiveRejectsTraversalUnknownEntriesMissingManifestAndExpandedSize() {
        for (name in listOf("../outside", "/absolute", "tables/unknown.tsv", "photos/not-a-uuid.img", "tables/life_event.tsv")) {
            val root = Files.createTempDirectory("backup-bad").toFile()
            try {
                val bytes = ByteArrayOutputStream().apply { ZipOutputStream(this).use { zip ->
                    zip.putNextEntry(ZipEntry(name)); zip.write(ByteArray(100)); zip.closeEntry()
                } }.toByteArray()
                assertThrows(Exception::class.java) { BackupArchive.read(ByteArrayInputStream(bytes), File(root, "staged"), maxTotalBytes = 20) }
                assertFalse(File(root, "outside").exists())
            } finally { root.deleteRecursively() }
        }
    }
    @Test fun rejectsChangedPayloadUnsupportedVersionsAndMissingIndexedFile() {
        val root = Files.createTempDirectory("backup-tamper").toFile()
        try {
            val file = File(root, "rows").apply { writeText("id\nS\n") }
            val archive = ByteArrayOutputStream().apply { BackupArchive.write(this, mapOf("tables/life_event.tsv" to file), 2, 100) }.toByteArray()
            for (variant in listOf("tamper", "version", "schema", "missing")) {
                val entries = linkedMapOf<String, ByteArray>()
                java.util.zip.ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
                    while (true) { val entry = zip.nextEntry ?: break; entries[entry.name] = zip.readBytes() }
                }
                when (variant) {
                    "tamper" -> entries["tables/life_event.tsv"] = "id\nSbmV3\n".toByteArray()
                    "missing" -> entries.remove("tables/life_event.tsv")
                    else -> { val p = java.util.Properties().apply { load(ByteArrayInputStream(entries.getValue("manifest.properties"))) }
                        p.setProperty(if (variant == "version") "format_version" else "schema_version", "99")
                        entries["manifest.properties"] = ByteArrayOutputStream().apply { p.store(this, null) }.toByteArray()
                    }
                }
                val changed = ByteArrayOutputStream().apply { ZipOutputStream(this).use { zip -> entries.forEach { (name, content) ->
                    zip.putNextEntry(ZipEntry(name)); zip.write(content); zip.closeEntry()
                } } }.toByteArray()
                assertThrows(Exception::class.java) { BackupArchive.read(ByteArrayInputStream(changed), File(root, variant)) }
            }
        } finally { root.deleteRecursively() }
    }
}
