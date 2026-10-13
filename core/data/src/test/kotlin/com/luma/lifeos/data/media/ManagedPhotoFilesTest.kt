package com.luma.lifeos.data.media

import com.luma.lifeos.domain.model.PhotoMetadata
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class ManagedPhotoFilesTest {
    private val root = Files.createTempDirectory("luma-photos-test").toFile()
    private val owner = UUID.randomUUID()
    private val info = PhotoMetadata("image/png", 4, 2, 2)
    @Test fun backupPublicationHasNoDraftLeaseAndNeverOverwritesExistingFile() {
        val files = ManagedPhotoFiles(root)
        val id = UUID.randomUUID()
        val original = files.import(owner, id, { ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)) }) { info }
        assertThrows(Exception::class.java) { files.publishBackup(id, { ByteArrayInputStream(byteArrayOf(5)) }) { info } }
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), files.resolve(original.relativePath)!!.readBytes())
        val restored = files.publishBackup(UUID.randomUUID(), { ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)) }) { info }
        files.clean(emptySet())
        assertNull(files.resolve(restored.relativePath))
        assertNotNull(files.resolve(original.relativePath))
    }
    @After fun cleanup() { root.deleteRecursively() }
    @Test fun privateCopySurvivesReopeningAndDoesNotDependOnSource() {
        val files = ManagedPhotoFiles(root)
        val id = UUID.randomUUID()
        val photo = files.import(owner, id, { ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)) }) { info }
        assertEquals("photos/$id.img", photo.relativePath)
        val reopened = ManagedPhotoFiles(root)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), reopened.resolve(photo.relativePath)!!.readBytes())
        val retried = reopened.import(owner, id, { error("Original source is gone") }) { info }
        assertEquals(photo, retried)
    }
    @Test fun failedAndOversizedImportsPublishNothingAndKeepEarlierDraft() {
        val files = ManagedPhotoFiles(root, maxBytes = 4)
        val good = files.import(owner, UUID.randomUUID(), { ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)) }) { info }
        val bad = UUID.randomUUID()
        assertTrue(runCatching { files.import(owner, bad, { ByteArrayInputStream(ByteArray(5)) }) { info } }.isFailure)
        assertFalse(File(root, "$bad.img").exists())
        assertFalse(File(root, "$bad.part").exists())
        assertTrue(runCatching { files.import(owner, bad, { object : InputStream() {
            override fun read(): Int = throw IOException("Read failed")
        } }) { info } }.isFailure)
        files.clean(emptySet())
        assertNotNull(files.resolve(good.relativePath))
        assertFalse(File(root, "$bad.img").exists())
    }
    @Test fun rejectedImageNeverBecomesPublishedFile() {
        val files = ManagedPhotoFiles(root)
        val id = UUID.randomUUID()
        assertTrue(runCatching { files.import(owner, id, { ByteArrayInputStream(byteArrayOf(1)) }) {
            throw IOException("Not an image")
        } }.isFailure)
        assertFalse(File(root, "$id.img").exists())
        assertFalse(File(root, "$id.part").exists())
    }
    @Test fun cleanupProtectsCurrentReferencesAndDurableLeasesThenRemovesOnlyOrphans() {
        val files = ManagedPhotoFiles(root)
        val photo = files.import(owner, UUID.randomUUID(), { ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)) }) { info }
        val unrelated = File(root, "unrelated.txt").apply { writeText("Keep") }
        val reopened = ManagedPhotoFiles(root)
        reopened.clean(emptySet())
        assertNotNull(reopened.resolve(photo.relativePath))
        reopened.release(owner)
        reopened.clean(setOf(photo.relativePath))
        assertNotNull(reopened.resolve(photo.relativePath))
        reopened.clean(emptySet())
        assertNull(reopened.resolve(photo.relativePath))
        assertEquals("Keep", unrelated.readText())
    }
    @Test fun anotherDraftLeaseKeepsPhotoAfterFirstEditorRemovesIt() {
        val files = ManagedPhotoFiles(root)
        val photo = files.import(owner, UUID.randomUUID(), { ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)) }) { info }
        val other = UUID.randomUUID()
        files.retain(other, listOf(photo))
        files.release(owner); files.clean(emptySet())
        assertNotNull(files.resolve(photo.relativePath))
        files.release(other); files.clean(emptySet())
        assertNull(files.resolve(photo.relativePath))
    }
    @Test fun legacyAndEscapingPathsCannotExposeOrDeleteArbitraryFiles() {
        val files = ManagedPhotoFiles(root)
        assertNull(files.resolve("../luma.db"))
        assertNull(files.resolve("photos/test.jpg"))
        assertNull(files.resolve("/etc/passwd"))
        val private = File(root, "luma.db").apply { writeText("Keep") }
        files.clean(emptySet())
        assertEquals("Keep", private.readText())
    }
    @Test fun managedSymlinkNeverReadsOrDeletesAnOutsideFile() {
        val outside = File(root.parentFile, "outside-${UUID.randomUUID()}").apply { writeText("Keep") }
        try {
            val id = UUID.randomUUID()
            Files.createSymbolicLink(File(root, "$id.img").toPath(), outside.toPath())
            val files = ManagedPhotoFiles(root)
            assertNull(files.resolve("photos/$id.img"))
            files.clean(emptySet())
            assertEquals("Keep", outside.readText())
        } finally { outside.delete() }
    }
}
