package com.luma.lifeos.data.backup

import com.luma.lifeos.domain.backup.BackupFormatMarker
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptedBackupEnvelopeTest {
    @Test
    fun rejectedShortPasswordsAreCleared() {
        val shortExportPassword = "     🔐🔐🔐🔐🔐🔐".toCharArray()
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                EncryptedBackupEnvelope.encrypt(ByteArrayOutputStream(), shortExportPassword) { }
            }
        }
        assertArrayEquals(CharArray(shortExportPassword.size), shortExportPassword)

        val shortImportPassword = "     🔐🔐🔐🔐🔐🔐".toCharArray()
        val target = File.createTempFile("luma-encrypted-short-", ".tmp")
        try {
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    EncryptedBackupEnvelope.decryptToFile(
                        ByteArrayInputStream(byteArrayOf()),
                        shortImportPassword,
                        target,
                    )
                }
            }
            assertArrayEquals(CharArray(shortImportPassword.size), shortImportPassword)
        } finally {
            target.delete()
        }
    }

    @Test
    fun roundTripPreservesPassphraseAndTamperingIsRejectedWithoutStagingOutput() {
        val contents = ("private multilingual backup payload فارسی 日本語 " + "x".repeat(2048))
            .toByteArray(StandardCharsets.UTF_8)
        val passphrase = "      🔐🔐🔐🔐🔐 "
        val encrypted = ByteArrayOutputStream()
        val exportPassword = passphrase.toCharArray()

        runBlocking {
            EncryptedBackupEnvelope.encrypt(encrypted, exportPassword) { output ->
                output.write(contents)
            }
        }

        assertArrayEquals(CharArray(exportPassword.size), exportPassword)
        val envelope = encrypted.toByteArray()
        assertTrue(
            envelope.copyOfRange(0, BackupFormatMarker.ENCRYPTED_SIGNATURE.length)
                .contentEquals(BackupFormatMarker.ENCRYPTED_SIGNATURE.toByteArray(StandardCharsets.US_ASCII)),
        )
        assertFalse(envelope.contains(contents))

        val restored = File.createTempFile("luma-encrypted-restored-", ".tmp")
        val importPassword = passphrase.toCharArray()
        try {
            runBlocking {
                EncryptedBackupEnvelope.decryptToFile(
                    ByteArrayInputStream(envelope),
                    importPassword,
                    restored,
                )
            }
            assertArrayEquals(contents, restored.readBytes())
            assertArrayEquals(CharArray(importPassword.size), importPassword)
        } finally {
            restored.delete()
        }

        val tampered = envelope.copyOf()
        tampered[tampered.lastIndex] = (tampered.last().toInt() xor 1).toByte()
        val rejected = File.createTempFile("luma-encrypted-rejected-", ".tmp")
        val tamperedPassword = passphrase.toCharArray()
        try {
            assertThrows(Exception::class.java) {
                runBlocking {
                    EncryptedBackupEnvelope.decryptToFile(
                        ByteArrayInputStream(tampered),
                        tamperedPassword,
                        rejected,
                    )
                }
            }
            assertFalse(rejected.exists())
            assertArrayEquals(CharArray(tamperedPassword.size), tamperedPassword)
        } finally {
            rejected.delete()
        }
    }

    private fun ByteArray.contains(other: ByteArray): Boolean =
        if (other.isEmpty() || other.size > size) false
        else (0..size - other.size).any { start ->
            other.indices.all { index -> this[start + index] == other[index] }
        }
}
