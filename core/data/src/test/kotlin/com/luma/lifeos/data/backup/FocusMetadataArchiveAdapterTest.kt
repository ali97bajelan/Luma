package com.luma.lifeos.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertThrows
import java.io.File
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID

class FocusMetadataArchiveAdapterTest {
    private fun tag(name: String = "Work", normalized: String = name.trim().lowercase(), id: String = UUID.randomUUID().toString()) =
        FocusMetadataArchiveAdapter.TagRow(id, name, normalized)

    @Test fun schemaSevenSessionImportsWithNullMetadataAndSchemasOneToSixHaveNoFocusTables() {
        val currentColumns = listOf("id", "state", "revision", "open_slot", "name", "tag_id")
        val sourceColumns = backupImportColumns("focus_session", 7, currentColumns)
        assertEquals(listOf("id", "state", "revision", "open_slot"), sourceColumns)

        val targetColumns = FocusMetadataArchiveAdapter.insertColumns(
            "focus_session", 7, sourceColumns, currentColumns,
        )
        val inserted = FocusMetadataArchiveAdapter.adaptRow(
            "focus_session", 7, sourceColumns,
            listOf(UUID.randomUUID().toString(), "FINISHED", 3L, null), targetColumns,
        )
        val row = targetColumns.zip(inserted).toMap()
        assertEquals(null, row["name"])
        assertEquals(null, row["tag_id"])
        (1..6).forEach { version ->
            assertFalse("schema $version unexpectedly includes focus history", "focus_session" in backupTables(version))
            assertFalse("schema $version unexpectedly includes focus tags", "focus_tag" in backupTables(version))
        }
    }

    @Test fun schemaEightKeepsTheMetadataColumnsAndRoundTripsUnicodeLabels() {
        val tagId = UUID.randomUUID().toString()
        val tagColumns = listOf("id", "name", "normalized_name")
        assertEquals("focus_tag", backupTables(8)[backupTables(8).indexOf("focus_session") - 1])
        assertEquals(tagColumns, backupImportColumns("focus_tag", 8, tagColumns))
        assertEquals(listOf("id", "name", "tag_id"), backupImportColumns("focus_session", 8, listOf("id", "name", "tag_id")))

        val archive = File.createTempFile("focus-metadata", ".tsv")
        try {
            val original = listOf(tagId, "مطالعه 📚", "مطالعه 📚".lowercase())
            archive.outputStream().use { BackupRows.write(it, tagColumns, sequenceOf(original)) }
            var restored: List<Any?>? = null
            BackupRows.read(archive, tagColumns) { restored = it }
            assertEquals(original, restored)
        } finally {
            archive.delete()
        }
    }

    @Test fun schemaEightArchiveRoundTripsSessionNamesAndReferencedTags() {
        val root = kotlin.io.path.createTempDirectory("focus-schema-eight").toFile()
        try {
            val tagId = UUID.randomUUID().toString()
            val tagFile = File(root, "focus_tag.tsv")
            val sessionFile = File(root, "focus_session.tsv")
            val runFile = File(root, "focus_run.tsv")
            tagFile.outputStream().use {
                BackupRows.write(it, listOf("id", "name", "normalized_name"), sequenceOf(listOf(tagId, "مطالعه 📚", "مطالعه 📚")))
            }
            sessionFile.outputStream().use {
                BackupRows.write(it, listOf("id", "name", "tag_id"), sequenceOf(listOf(UUID.randomUUID().toString(), "Read chapter 4", tagId)))
            }
            runFile.outputStream().use {
                BackupRows.write(it, listOf("id", "session_id"), emptySequence())
            }
            val archive = ByteArrayOutputStream().apply {
                BackupArchive.write(this, mapOf(
                    "tables/focus_tag.tsv" to tagFile,
                    "tables/focus_session.tsv" to sessionFile,
                    "tables/focus_run.tsv" to runFile,
                ), schemaVersion = 8, createdAt = 1234L)
            }.toByteArray()
            val extracted = File(root, "extracted")
            val manifest = BackupArchive.read(ByteArrayInputStream(archive), extracted)
            assertEquals(8, manifest.schemaVersion)
            assertEquals(setOf("tables/focus_tag.tsv", "tables/focus_session.tsv", "tables/focus_run.tsv"), manifest.names)
            assertEquals(sessionFile.readText(), File(extracted, "tables/focus_session.tsv").readText())
            assertEquals(tagFile.readText(), File(extracted, "tables/focus_tag.tsv").readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun duplicateDanglingAndUnusedTagsAreRejected() {
        val first = tag()
        FocusMetadataArchiveAdapter.validateReferences(listOf(first), listOf(first.id, null))

        assertThrows(IllegalArgumentException::class.java) {
            FocusMetadataArchiveAdapter.validateReferences(listOf(first), listOf(UUID.randomUUID().toString()))
        }
        assertThrows(IllegalArgumentException::class.java) {
            FocusMetadataArchiveAdapter.validateReferences(listOf(first, first.copy()), listOf(first.id))
        }
        val duplicateName = tag(name = "work", normalized = "work")
        assertThrows(IllegalArgumentException::class.java) {
            FocusMetadataArchiveAdapter.validateReferences(listOf(first, duplicateName), listOf(first.id, duplicateName.id))
        }
        assertThrows(IllegalArgumentException::class.java) {
            FocusMetadataArchiveAdapter.validateReferences(listOf(first), listOf(null))
        }
    }

    @Test fun invalidTagAndSessionLabelsAndOpenSessionsAreRejected() {
        listOf("", " Work", "Work ", "a".repeat(51), "bad\u0000label", "one\ntwo").forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) {
                FocusMetadataArchiveAdapter.validateTag(tag(name = invalid, normalized = invalid.trim().lowercase()))
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            FocusMetadataArchiveAdapter.validateTag(tag(name = "Work", normalized = "different"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            FocusMetadataArchiveAdapter.validateSession("RUNNING", 1L, "Reading")
        }
        assertThrows(IllegalArgumentException::class.java) {
            FocusMetadataArchiveAdapter.validateSession("FINISHED", null, "   ")
        }
        assertTrue(runCatching { FocusMetadataArchiveAdapter.validateSession("INTERRUPTED", null, null) }.isSuccess)
    }

    @Test fun exportRowsOmitUnreferencedTags() {
        val used = tag()
        val unused = tag(name = "Old", normalized = "old")
        val ids = FocusMetadataArchiveAdapter.exportedTagIds(listOf(used.id, null))
        val rows = sequenceOf(
            listOf(used.id, used.name, used.normalizedName),
            listOf(unused.id, unused.name, unused.normalizedName),
        )
        assertEquals(listOf(listOf(used.id, used.name, used.normalizedName)),
            FocusMetadataArchiveAdapter.referencedTagRows(rows, ids).toList())
        assertTrue(FocusMetadataArchiveAdapter.exportedTagIds(listOf(null)).isEmpty())
    }

    @Test fun tagExportUsesTheSameTerminalSessionPredicateAndRetainedExclusionChoice() {
        assertEquals(FOCUS_SESSION_EXPORT_QUERY, FocusMetadataArchiveAdapter.focusSessionExportQuery(false))
        assertEquals(FOCUS_SESSION_EXPORT_EXCLUDING_QUERY, FocusMetadataArchiveAdapter.focusSessionExportQuery(true))
        assertTrue(FOCUS_SESSION_EXPORT_EXCLUDING_QUERY.endsWith("AND id != ?"))
    }
}
