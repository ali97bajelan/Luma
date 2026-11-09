@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.luma.lifeos.ui.focus

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.mutableStateOf
import com.luma.lifeos.focus.AllowedFocusApps
import com.luma.lifeos.domain.focus.FocusInvitationConfig
import com.luma.lifeos.domain.focus.FocusInvitationRepository
import com.luma.lifeos.domain.focus.FocusInvitationSlot
import com.luma.lifeos.domain.focus.FocusProductContext
import com.luma.lifeos.domain.focus.FocusProductResult
import com.luma.lifeos.reminders.ReminderNotificationAccess
import com.luma.lifeos.ui.theme.LifeOsTheme
import java.time.DayOfWeek
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FocusAllowedAppsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun searchRetainsDuplicateLabelIdentityAndAppChoiceIsExplicit() {
        val apps = listOf(
            AllowedFocusApps.App("org.example.reader.alpha", "Reader"),
            AllowedFocusApps.App("net.example.reader.beta", "reader"),
            AllowedFocusApps.App("com.example.calendar", "Calendar"),
        )
        var changed: Pair<String, Boolean>? = null

        compose.setContent {
            LifeOsTheme {
                FocusSettingsScreen(
                    apps = apps,
                    selectedPackages = emptySet(),
                    isRunning = false,
                    loading = false,
                    discoveryFailed = false,
                    writeFailed = false,
                    onRefresh = {},
                    onToggle = { app, allowed -> changed = app.packageName to allowed },
                    onBack = {},
                )
            }
        }

        compose.onNodeWithTag("focus_settings_shortcuts_toggle").performClick()
        compose.onNodeWithTag("focus_settings_scope").assertIsDisplayed()
        compose.onNodeWithTag("focus_settings_empty_selection").assertIsDisplayed()
        compose.onNodeWithText("Reader · org.example.reader.alpha").assertIsDisplayed()
        compose.onNodeWithText("reader · net.example.reader.beta").assertIsDisplayed()
        compose.onNodeWithTag("focus_settings_search").performTextInput("com.example.calendar")
        compose.onNodeWithTag("focus_settings_app_com.example.calendar").assertIsDisplayed()
        compose.onNodeWithTag("focus_settings_app_org.example.reader.alpha").assertDoesNotExist()
        compose.onNodeWithTag("focus_settings_app_com.example.calendar").performClick()

        compose.runOnIdle { assertEquals("com.example.calendar" to true, changed) }
    }

    @Test fun staleSelectionsCanBeRemovedAndRunningSessionDisablesEdits() {
        val stalePackage = "org.example.removed"
        var changed: Pair<String, Boolean>? = null
        compose.setContent {
            LifeOsTheme {
                FocusSettingsScreen(
                    apps = listOf(AllowedFocusApps.App("com.example.calendar", "Calendar")),
                    selectedPackages = setOf("com.example.calendar", stalePackage),
                    isRunning = true,
                    loading = false,
                    discoveryFailed = false,
                    writeFailed = false,
                    onRefresh = {},
                    onToggle = { app, allowed -> changed = app.packageName to allowed },
                    onBack = {},
                )
            }
        }

        compose.onNodeWithTag("focus_settings_shortcuts_toggle")
            .assertTextContains("Changes paused while Focus is running")
            .performClick()
        compose.onNodeWithTag("focus_settings_running").assertIsDisplayed()
        compose.onNodeWithTag("focus_settings_app_com.example.calendar").assertIsNotEnabled()
        compose.onNodeWithTag("focus_settings_unavailable_$stalePackage").assertIsDisplayed()
        compose.onNodeWithTag("focus_settings_remove_$stalePackage").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(null, changed) }
    }

    @Test fun reminderScheduleDoesNotRequestPermissionUntilTheExplicitAction() {
        val repository = InvitationSettingsRepository(
            FocusInvitationConfig(enabled = false, weekdays = setOf(DayOfWeek.MONDAY)),
        )
        val permissionRequests = AtomicInteger()
        compose.setContent {
            LifeOsTheme {
                FocusInvitationSettings(
                    repository = repository,
                    preferences = null,
                    notificationAccessOverride = ReminderNotificationAccess(
                        granted = false,
                        request = { permissionRequests.incrementAndGet() },
                    ),
                )
            }
        }

        compose.onNodeWithTag("focus_settings_reminders_toggle")
            .assertTextContains("No reminder schedule")
            .assertTextContains("Notifications are blocked")
        compose.onNodeWithTag("focus_settings_reminders_toggle").performClick()
        compose.onNodeWithTag("focus_invitation_notification_status").assertIsDisplayed()
        compose.onNodeWithTag("focus_invitation_notification_access").assertIsDisplayed()
        compose.onNodeWithTag("focus_invitation_enable").performClick()
        compose.waitUntil(5_000) {
            repository.saveCount.get() > 0 &&
                repository.config.value.enabled &&
                compose.onAllNodesWithTag("focus_invitation_save_pending").fetchSemanticsNodes().isEmpty()
        }
        compose.runOnIdle { assertEquals(0, permissionRequests.get()) }

        compose.onNodeWithTag("focus_invitation_notification_access").performClick()
        compose.runOnIdle { assertEquals(1, permissionRequests.get()) }
    }

    @Test fun reminderSummaryReflectsRevokedAccessWithoutDisablingItsSchedule() {
        val repository = InvitationSettingsRepository(
            FocusInvitationConfig(enabled = true, weekdays = setOf(DayOfWeek.MONDAY)),
        )
        val permissionRequests = AtomicInteger()
        val access = mutableStateOf(
            ReminderNotificationAccess(granted = true) { permissionRequests.incrementAndGet() },
        )
        compose.setContent {
            LifeOsTheme {
                FocusInvitationSettings(
                    repository = repository,
                    preferences = null,
                    notificationAccessOverride = access.value,
                )
            }
        }

        compose.onNodeWithTag("focus_settings_reminders_toggle")
            .assertTextContains("Reminder schedule on")
            .assertTextContains("Notifications are allowed")
            .performClick()
        compose.onNodeWithTag("focus_invitation_notification_status")
            .assertTextContains("Notifications are allowed")
        compose.runOnIdle {
            access.value = ReminderNotificationAccess(granted = false) { permissionRequests.incrementAndGet() }
        }
        compose.onNodeWithTag("focus_invitation_notification_status")
            .assertTextContains("Notifications are blocked")
        compose.onNodeWithTag("focus_invitation_notification_access").assertIsDisplayed()
        compose.onNodeWithTag("focus_invitation_enable").assertIsDisplayed()
        compose.onNodeWithTag("focus_settings_reminders_toggle").performClick()
        compose.onNodeWithTag("focus_settings_reminders_toggle")
            .assertTextContains("Reminder schedule on")
            .assertTextContains("Notifications are blocked")
        compose.runOnIdle { assertEquals(0, permissionRequests.get()) }
    }

    @Test fun failedReminderSaveKeepsTheAuthoredWeekdayForRetry() {
        val repository = InvitationSettingsRepository(
            FocusInvitationConfig(enabled = false, weekdays = setOf(DayOfWeek.MONDAY)),
            saveResult = FocusProductResult.Unavailable(),
        )
        compose.setContent {
            LifeOsTheme {
                FocusInvitationSettings(
                    repository = repository,
                    preferences = null,
                    notificationAccessOverride = ReminderNotificationAccess(granted = false, request = {}),
                )
            }
        }

        compose.onNodeWithTag("focus_settings_reminders_toggle").performClick()
        compose.onNodeWithTag("focus_invitation_day_2").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("focus_invitation_save_error").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("focus_invitation_day_2").assertIsSelected()
    }

    @Test fun shortcutDiscoveryFailureIsDistinctFromAnEmptyCatalog() {
        compose.setContent {
            LifeOsTheme {
                FocusSettingsScreen(
                    apps = emptyList(),
                    selectedPackages = emptySet(),
                    isRunning = false,
                    loading = false,
                    discoveryFailed = true,
                    writeFailed = false,
                    onRefresh = {},
                    onToggle = { _, _ -> },
                    onBack = {},
                )
            }
        }

        compose.onNodeWithTag("focus_settings_shortcuts_toggle")
            .assertIsDisplayed()
            .assertTextContains("Apps could not be loaded")
        compose.onNodeWithTag("focus_settings_discovery_error").assertDoesNotExist()
        compose.onNodeWithTag("focus_settings_shortcuts_toggle").performClick()
        compose.onNodeWithTag("focus_settings_discovery_error").assertIsDisplayed()
        compose.onNodeWithTag("focus_settings_no_apps").assertDoesNotExist()
    }

    private class InvitationSettingsRepository(
        initial: FocusInvitationConfig,
        private val saveResult: FocusProductResult = FocusProductResult.Applied,
    ) : FocusInvitationRepository {
        val config = MutableStateFlow(initial)
        val saveCount = AtomicInteger()
        private val generation = UUID.randomUUID()

        override fun observeConfig(): Flow<FocusInvitationConfig> = config
        override fun observeGeneration(): Flow<UUID> = flowOf(generation)
        override suspend fun currentGeneration(): UUID = generation
        override suspend fun saveConfig(
            config: FocusInvitationConfig,
            expectedRevision: Long,
            context: FocusProductContext,
        ): FocusProductResult {
            saveCount.incrementAndGet()
            if (expectedRevision != this.config.value.revision) {
                return FocusProductResult.InvitationConflict(this.config.value)
            }
            if (saveResult != FocusProductResult.Applied) return saveResult
            this.config.value = config.copy(revision = expectedRevision + 1)
            return saveResult
        }
        override suspend fun ensureScheduled(slot: FocusInvitationSlot): Boolean = true
        override suspend fun skip(slot: FocusInvitationSlot, now: Instant): Boolean = true
        override suspend fun consume(slot: FocusInvitationSlot, now: Instant): Boolean = true
        override suspend fun actionToken(slot: FocusInvitationSlot): String? = null
        override suspend fun snoozedSlot(slot: FocusInvitationSlot): FocusInvitationSlot? = null
        override suspend fun snooze(slot: FocusInvitationSlot, now: Instant, token: String): Boolean = true
        override suspend fun disable(slot: FocusInvitationSlot, token: String): Boolean = true
    }
}
