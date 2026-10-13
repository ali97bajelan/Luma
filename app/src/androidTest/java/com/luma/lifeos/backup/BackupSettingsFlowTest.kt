package com.luma.lifeos.backup

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.luma.lifeos.domain.backup.BackupPreview
import com.luma.lifeos.ui.backup.*
import com.luma.lifeos.ui.theme.LifeOsTheme
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

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
}
