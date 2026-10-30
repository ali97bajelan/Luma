package com.luma.lifeos.backup

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.luma.lifeos.domain.focus.FocusRun
import com.luma.lifeos.domain.focus.FocusSession
import com.luma.lifeos.domain.focus.FocusState
import com.luma.lifeos.domain.backup.BackupPreview
import com.luma.lifeos.ui.backup.*
import com.luma.lifeos.ui.theme.LifeOsTheme
import java.time.Instant
import java.util.UUID
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class BackupSettingsFlowTest {
    @get:Rule val compose = createComposeRule()
    @Test fun confirmationShowsReplacementWarningAndCancelDoesNotRestore() {
        var confirms = 0
        compose.setContent {
            var state by remember { mutableStateOf(BackupUiState(preview = BackupPreview(3, 1, Instant.EPOCH))) }
            LifeOsTheme { BackupCardContent(state, {}, {}, { confirms++ }, { state = BackupUiState() }) }
        }
        compose.onNodeWithText("Replace saved data?").assertIsDisplayed()
        compose.onNodeWithTag("backup_export").assertIsNotEnabled()
        compose.onNodeWithTag("backup_cancel").performClick()
        compose.onNodeWithTag("backup_confirm").assertDoesNotExist()
        compose.onNodeWithTag("backup_export").assertIsEnabled()
        assertEquals(0, confirms)
    }
    @Test fun busyStateBlocksDuplicateActionsAndSuccessIsVisible() {
        var state by mutableStateOf(BackupUiState(busy = true))
        compose.setContent { LifeOsTheme { BackupCardContent(state, {}, {}, {}, {}) } }
        compose.onNodeWithTag("backup_export").assertIsNotEnabled()
        compose.onNodeWithTag("backup_choose").assertIsNotEnabled()
        compose.onNodeWithTag("backup_busy").assertExists()
        compose.runOnIdle { state = BackupUiState(message = BackupMessage.EXPORTED) }
        compose.onNodeWithText("Backup exported.").assertExists()
    }
    @Test fun previewShowsTaskCountAndExplainsLegacyReplacement() {
        var state by mutableStateOf(BackupUiState(preview = BackupPreview(3, 1, Instant.EPOCH, 2, 7, 4)))
        compose.setContent { LifeOsTheme { BackupCardContent(state, {}, {}, {}, {}) } }
        compose.onNodeWithText("4 tasks", substring = true).assertExists()
        compose.onNodeWithText("This backup contains no tasks. Restoring it will remove any tasks currently stored on this device.").assertDoesNotExist()
        compose.runOnIdle { state = state.copy(preview = state.preview!!.copy(todoCount = 0)) }
        compose.onNodeWithText("This backup contains no tasks. Restoring it will remove any tasks currently stored on this device.").assertExists()
        compose.onNodeWithText("This replaces all saved life records, focus history, tasks, catalogs, and imported history with this backup.", substring = true).assertExists()
    }

    @Test fun activeFocusReplacementActionsDoNotOverlap() {
        val sessionId = UUID.fromString("00000000-0000-0000-0000-000000000010")
        val runStart = Instant.parse("2026-10-30T00:00:00Z")
        val runEnd = runStart.plusSeconds(60)
        val session = FocusSession(
            id = sessionId,
            state = FocusState.PAUSED,
            revision = 1,
            startedAt = runStart,
            endedAt = null,
            createdAt = runStart,
            updatedAt = runEnd,
            terminalReason = null,
            recoveredOrUncertain = false,
            runs = listOf(
                FocusRun(
                    id = UUID.fromString("00000000-0000-0000-0000-000000000011"),
                    sessionId = sessionId,
                    ordinal = 1,
                    startedAt = runStart,
                    endedAt = runEnd,
                    creditedMillis = 60_000,
                    rawStartedAt = runStart,
                    rawEndedAt = runEnd,
                ),
            ),
        )
        compose.setContent {
            LifeOsTheme {
                BackupCardContent(
                    state = BackupUiState(preview = BackupPreview(0, 0, Instant.EPOCH)),
                    onExport = {},
                    onRestore = {},
                    onConfirmRestore = {},
                    onCancelRestore = {},
                    openFocusSession = session,
                )
            }
        }

        val stop = compose.onNodeWithTag("backup_stop_focus_replace").assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val discard = compose.onNodeWithTag("backup_discard_focus_replace").assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val cancel = compose.onNodeWithTag("backup_cancel").assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Stop and Discard must have distinct action areas", stop.bottom <= discard.top || discard.bottom <= stop.top)
        assertFalse("Cancel overlaps the Discard action", discard.left < cancel.right && cancel.left < discard.right &&
            discard.top < cancel.bottom && cancel.top < discard.bottom)
        assertFalse("Cancel overlaps the Stop action", stop.left < cancel.right && cancel.left < stop.right &&
            stop.top < cancel.bottom && cancel.top < stop.bottom)
    }
}
