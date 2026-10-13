package com.luma.lifeos.privacy

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.luma.lifeos.R
import com.luma.lifeos.ui.privacy.*
import com.luma.lifeos.ui.theme.LifeOsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

/** The test boundary never invokes Android's real destructive reset. */
class PrivacySettingsFlowTest {
    @get:Rule val compose = createComposeRule()
    @Test fun deleteRequiresConfirmationAndCancelKeepsScreenUsable() {
        var deletionRequests = 0
        compose.setContent {
            var state by remember { mutableStateOf(PrivacyUiState()) }
            LifeOsTheme { PrivacySettingsContent(state, {}, { state = PrivacyUiState(confirming = true) },
                { deletionRequests++ }, { state = PrivacyUiState() }, {}) }
        }
        compose.onNodeWithTag("privacy_local_storage").assertExists()
        compose.onNodeWithTag("privacy_delete").performScrollTo().performClick()
        compose.onNodeWithTag("privacy_confirm").assertExists()
        assertEquals(0, deletionRequests)
        compose.onNodeWithTag("privacy_cancel").performClick()
        compose.onNodeWithTag("privacy_confirm").assertDoesNotExist()
        compose.onNodeWithTag("privacy_delete").assertIsEnabled()
        assertEquals(0, deletionRequests)
    }
    @Test fun acceptedRequestDisablesDeletionAndShowsRequestStatus() {
        compose.setContent { LifeOsTheme { PrivacySettingsContent(
            PrivacyUiState(busy = true, requestAccepted = true), {}, {}, {}, {}, {}) } }
        compose.onNodeWithTag("privacy_delete").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("privacy_requested").assertExists()
        compose.onNodeWithTag("privacy_back").assertIsNotEnabled()
    }
    @Test fun permissionExplanationContinueRequiresAnExplicitClick() {
        var launches = 0
        compose.setContent {
            var showing by remember { mutableStateOf(true) }
            LifeOsTheme { if (showing) PermissionExplanationDialog(R.string.health_title, R.string.health_explanation,
                { launches++; showing = false }, { showing = false }) }
        }
        assertEquals(0, launches)
        compose.onNodeWithTag("permission_continue").performClick()
        assertEquals(1, launches)
        compose.onNodeWithTag("permission_continue").assertDoesNotExist()
    }
    @Test fun permissionExplanationCancelDoesNotLaunch() {
        var launches = 0
        compose.setContent {
            var showing by remember { mutableStateOf(true) }
            LifeOsTheme { if (showing) PermissionExplanationDialog(R.string.health_title, R.string.health_explanation,
                { launches++; showing = false }, { showing = false }) }
        }
        assertEquals(0, launches)
        compose.onNodeWithTag("permission_cancel").performClick()
        compose.onNodeWithTag("permission_continue").assertDoesNotExist()
        assertEquals(0, launches)
    }
}
