package com.luma.lifeos.settings

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.media.ManagedPhotoFiles
import com.luma.lifeos.data.repository.*
import com.luma.lifeos.domain.backup.*
import com.luma.lifeos.domain.health.*
import com.luma.lifeos.domain.usage.*
import com.luma.lifeos.ui.LifeOsApp
import com.luma.lifeos.ui.privacy.AppDataReset
import com.luma.lifeos.ui.settings.SettingsRoute
import com.luma.lifeos.ui.theme.LifeOsTheme
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.flowOf
import org.junit.*
import org.junit.Assert.*

class SettingsHubFlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: LumaDatabase
    private lateinit var photoDirectory: File
    private var resets = 0
    private var backupActions = 0

    @Before fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, LumaDatabase::class.java).build()
        photoDirectory = File(context.cacheDir, "settings-hub-${UUID.randomUUID()}").apply { mkdirs() }
    }
    @After fun close() { db.close(); photoDirectory.deleteRecursively() }

    @Test fun hubShowsOnlyAvailableSections() {
        var flags by mutableStateOf(Triple(false, false, false))
        var clicked: String? = null
        compose.setContent { LifeOsTheme {
            SettingsRoute(flags.first, flags.second, flags.third,
                { clicked = "language" }, { clicked = "backup" }, { clicked = "privacy" }, { clicked = "integrations" })
        } }
        for (backup in listOf(false, true)) for (privacy in listOf(false, true)) for (integrations in listOf(false, true)) {
            compose.runOnIdle { flags = Triple(backup, privacy, integrations) }
            compose.onNodeWithTag("settings_language").performScrollTo().assertHasClickAction().performClick()
            compose.runOnIdle { assertEquals("language", clicked) }
            listOf("backup" to backup, "privacy" to privacy, "integrations" to integrations).forEach { (section, available) ->
                if (available) {
                    compose.onNodeWithTag("settings_$section").performScrollTo().assertHasClickAction().performClick()
                    compose.runOnIdle { assertEquals(section, clicked) }
                } else compose.onNodeWithTag("settings_$section").assertDoesNotExist()
            }
            assertNoSavedUsageBrowser()
        }
    }

    @Test fun focusedPageBackReturnsToHub() {
        val restoration = StateRestorationTester(compose)
        show(restoration, available = true)
        compose.onNodeWithTag("day_previous").performClick()
        openSettings()
        listOf("language", "backup", "privacy", "integrations").forEachIndexed { index, section ->
            compose.onNodeWithTag("settings_$section").performScrollTo().performClick()
            awaitTag(if (section == "privacy") "screen_privacy" else "screen_settings_$section")
            listOf("today", "timeline", "habits", "review", "more").forEach {
                compose.onNodeWithTag("nav_$it").assertDoesNotExist()
            }
            compose.onNodeWithTag("quick_capture").assertDoesNotExist()
            if (section == "language") {
                restoration.emulateSavedInstanceStateRestore()
                awaitTag("screen_settings_language")
                compose.onNodeWithTag("nav_more").assertDoesNotExist()
            }
            if (section == "integrations") assertNoSavedUsageBrowser()
            compose.onNodeWithTag(if (section == "privacy") "privacy_back" else "settings_back").performClick()
            awaitTag("screen_settings")
            compose.onNodeWithTag("settings_hub_back").assertIsDisplayed()
            if (index < 3) {
                compose.onNodeWithTag("settings_hub_back").performClick()
                awaitTag("screen_more")
                compose.onNodeWithTag("nav_more").assertIsSelected()
                compose.onNodeWithTag("more_settings").performClick()
            }
        }
        compose.onNodeWithTag("settings_hub_back").performClick()
        compose.onNodeWithTag("nav_today").performClick()
        compose.onNodeWithTag("day_selected", useUnmergedTree = true)
            .assertTextEquals(LocalDate.now().minusDays(1).format(com.luma.lifeos.language.localizedDate(java.time.format.FormatStyle.MEDIUM)))
        assertEquals(0, resets)
        assertEquals(0, backupActions)
    }

    @Test fun healthWithoutUsageStillHasAnIntegrationPage() {
        show(StateRestorationTester(compose), available = false, withHealth = true)
        openSettings()
        compose.onNodeWithTag("settings_backup").assertDoesNotExist()
        compose.onNodeWithTag("settings_privacy").assertDoesNotExist()
        compose.onNodeWithTag("settings_integrations").performClick()
        awaitTag("screen_settings_integrations")
        compose.onNodeWithTag("health_card").assertExists()
        compose.onNodeWithTag("usage_enabled").assertDoesNotExist()
        assertNoSavedUsageBrowser()
    }

    @Test fun appWithoutOptionalServicesKeepsLanguageAvailable() {
        show(StateRestorationTester(compose), available = false)
        openSettings()
        listOf("backup", "privacy", "integrations").forEach { compose.onNodeWithTag("settings_$it").assertDoesNotExist() }
        compose.onNodeWithTag("settings_language").performClick()
        awaitTag("screen_settings_language")
        compose.onNodeWithTag("language_en").assertExists()
        compose.onNodeWithTag("settings_back").performClick()
        awaitTag("screen_settings")
        compose.onNodeWithTag("settings_hub_back").performClick()
        awaitTag("screen_more")
    }

    private fun show(restoration: StateRestorationTester, available: Boolean, withHealth: Boolean = false) {
        val backup = object : BackupService {
            override suspend fun export(output: OutputStream) { backupActions++ }
            override suspend fun prepare(input: InputStream): PreparedBackup { backupActions++; error("No picker in this test") }
        }
        val health = object : HealthIntegration {
            override fun observeState() = flowOf(HealthState())
            override suspend fun setEnabled(enabled: Boolean) {}
            override suspend fun refreshAccess() {}
            override suspend fun importNow() = HealthStatus.DISABLED
        }
        val usage = object : UsageIntegration {
            override fun observeState() = flowOf(UsageImportState())
            override fun observeDaily(date: LocalDate, zone: ZoneId) = flowOf<DailyUsageReport?>(null)
            override suspend fun refreshAccess() {}
            override suspend fun setEnabled(enabled: Boolean) {}
            override suspend fun importNow() = UsageSyncOutcome.DISABLED
        }
        val photos = RoomPhotoRepository(db, ManagedPhotoFiles(photoDirectory)) { error("No photo capture in this test") }
        restoration.setContent { LifeOsTheme {
            LifeOsApp(RoomLifeEventRepository(db), RoomMoodRepository(db), RoomJournalRepository(db),
                RoomMemoryRepository(db), RoomPersonRepository(db), RoomPlaceRepository(db), photos,
                RoomTimelineRepository(db), usage = usage.takeIf { available }, backup = backup.takeIf { available },
                reset = if (available) AppDataReset { resets++; true } else null, health = health.takeIf { withHealth },
                habits = RoomHabitRepository(db))
        } }
    }
    private fun assertNoSavedUsageBrowser() {
        listOf("day_previous", "usage_view_timeline", "usage_view_totals", "usage_daily_total", "settings_places")
            .forEach { compose.onNodeWithTag(it).assertDoesNotExist() }
    }
    private fun openSettings() {
        compose.onNodeWithTag("nav_more").performClick()
        compose.onNodeWithTag("more_settings").performClick()
    }
    private fun awaitTag(tag: String) {
        compose.waitUntil(5000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }
}
