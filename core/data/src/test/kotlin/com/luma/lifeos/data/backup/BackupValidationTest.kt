package com.luma.lifeos.data.backup

import org.junit.Test
import org.junit.Assert.assertThrows
import java.util.UUID

class BackupValidationTest {
    @Test fun focusArchiveAcceptsOnlyCanonicalTerminalSessionsAndClosedRuns() {
        val sessionId = UUID.randomUUID().toString()
        val session = mapOf<String, Any?>(
            "id" to sessionId,
            "state" to "FINISHED",
            "revision" to 2L,
            "started_at_seconds" to 100L,
            "started_at_nanos" to 0L,
            "ended_at_seconds" to 110L,
            "ended_at_nanos" to 0L,
            "created_at_seconds" to 99L,
            "created_at_nanos" to 0L,
            "updated_at_seconds" to 110L,
            "updated_at_nanos" to 0L,
            "terminal_reason" to "USER_STOP",
            "recovered_or_uncertain" to 0L,
            "open_slot" to null,
        )
        validateBackupRow("focus_session", session)
        assertThrows(Exception::class.java) { validateBackupRow("focus_session", session + ("state" to "RUNNING")) }
        assertThrows(Exception::class.java) { validateBackupRow("focus_session", session + ("open_slot" to 1L)) }
        assertThrows(Exception::class.java) { validateBackupRow("focus_session", session + ("terminal_reason" to "PROCESS_RESTART_GAP")) }

        val run = mapOf<String, Any?>(
            "id" to UUID.randomUUID().toString(),
            "session_id" to sessionId,
            "ordinal" to 1L,
            "started_at_seconds" to 100L,
            "started_at_nanos" to 0L,
            "ended_at_seconds" to 110L,
            "ended_at_nanos" to 0L,
            "credited_millis" to 10_000L,
            "raw_started_at_seconds" to 100L,
            "raw_started_at_nanos" to 0L,
            "raw_ended_at_seconds" to 110L,
            "raw_ended_at_nanos" to 0L,
            "open_session_id" to null,
        )
        validateBackupRow("focus_run", run)
        assertThrows(Exception::class.java) { validateBackupRow("focus_run", run + ("credited_millis" to -1L)) }
        assertThrows(Exception::class.java) { validateBackupRow("focus_run", run + ("ended_at_seconds" to 111L)) }
        assertThrows(Exception::class.java) { validateBackupRow("focus_run", run + ("open_session_id" to sessionId)) }
    }

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

        listOf(0, 545, 1439).forEach { minute ->
            validateBackupRow("todo_task", row + ("due_time_minutes" to minute.toLong()))
        }

        val invalidRows = listOf(
            row + ("id" to row.getValue("id").toString().uppercase()),
            row + ("revision_token" to "not-a-uuid"),
            row + ("title" to " "),
            row + ("title" to "x".repeat(201)),
            row + ("note" to "x".repeat(4001)),
            row + ("due_date" to "2026-2-03"),
            row + ("updated_at" to 0L),
            row + ("due_time_minutes" to -1L),
            row + ("due_time_minutes" to 1440L),
            row + ("due_time_minutes" to "545"),
            row + ("due_date" to null) + ("due_time_minutes" to 545L),
        )
        invalidRows.forEach { invalid ->
            assertThrows(Exception::class.java) { validateBackupRow("todo_task", invalid) }
        }
    }
}
