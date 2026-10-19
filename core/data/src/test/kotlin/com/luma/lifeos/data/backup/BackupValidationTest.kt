package com.luma.lifeos.data.backup

import org.junit.Test
import org.junit.Assert.assertThrows
import java.util.UUID

class BackupValidationTest {
    @Test fun deepJsonIsRejectedWhileBracketsInQuotedTextDoNotCount() {
        assertThrows(Exception::class.java) { requireSafeJsonDepth("{\"x\":" + "[".repeat(1000) + "0" + "]".repeat(1000) + "}") }
        requireSafeJsonDepth("{\"text\":\"" + "[".repeat(1000) + "\"}")
    }
    @Test fun invalidNativeSessionLabelsAndPackagesAreRejectedBeforeRestore() {
        val row = mapOf("package_name" to "example.app", "started_at" to 1000L, "ended_at" to 2000L, "app_label" to "App", "ongoing" to 0L)
        validateBackupRow("usage_session", row)
        for (change in listOf("app_label" to "", "app_label" to "a".repeat(201), "package_name" to "p".repeat(501)))
            assertThrows(Exception::class.java) { validateBackupRow("usage_session", row + change) }
    }
    @Test fun oversizedNativeBucketPackageIsRejectedBeforeRestore() {
        val row = mapOf("id" to UUID.randomUUID().toString(), "life_event_id" to UUID.randomUUID().toString(),
            "package_name" to "p".repeat(501), "bucket_start" to 1000L, "bucket_end" to 2000L, "duration_ms" to 100L, "source" to "USAGE_STATS")
        assertThrows(Exception::class.java) { validateBackupRow("app_usage_record", row) }
    }
    @Test fun taskRowsRequireCanonicalIdentityValidTextDateAndAuditChronology() {
        val row = mapOf<String, Any?>(
            "id" to UUID.randomUUID().toString(),
            "title" to "Task",
            "note" to "Persian فارسی",
            "due_date" to "0001-01-01",
            "completed_at" to 0L,
            "created_at" to 1L,
            "updated_at" to 1L,
            "revision_token" to UUID.randomUUID().toString(),
        )
        validateBackupRow("todo_task", row)

        val invalidRows = listOf(
            row + ("id" to row.getValue("id").toString().uppercase()),
            row + ("revision_token" to "not-a-uuid"),
            row + ("title" to " "),
            row + ("title" to "x".repeat(201)),
            row + ("note" to "x".repeat(4001)),
            row + ("due_date" to "2026-2-03"),
            row + ("updated_at" to 0L),
        )
        invalidRows.forEach { invalid ->
            assertThrows(Exception::class.java) { validateBackupRow("todo_task", invalid) }
        }
    }
}
