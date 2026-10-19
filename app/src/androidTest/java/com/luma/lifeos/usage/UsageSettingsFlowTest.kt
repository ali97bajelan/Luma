package com.luma.lifeos.usage

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.repository.RoomUsageRepository
import com.luma.lifeos.domain.time.TimeRange
import com.luma.lifeos.domain.usage.*
import com.luma.lifeos.ui.theme.LifeOsTheme
import com.luma.lifeos.ui.settings.IntegrationsSettingsRoute
import java.time.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*

class UsageSettingsFlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: LumaDatabase
    private lateinit var repository: RoomUsageRepository
    private lateinit var integration: UsageSyncer
    private lateinit var restoration: StateRestorationTester
    private val source = Source()
    private val today = LocalDate.now()
    private val zone = ZoneId.systemDefault()
    private val now = today.atTime(12, 0).atZone(zone).toInstant()
    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, LumaDatabase::class.java).build()
        repository = RoomUsageRepository(db)
        integration = UsageSyncer(source, repository, object : UsageScheduler {
            override fun schedule() { }; override fun cancel() { }
        }, Clock.fixed(now, zone))
        restoration = StateRestorationTester(compose)
        restoration.setContent {
            LifeOsTheme { IntegrationsSettingsRoute(integration, null, zone, {}) }
        }
        compose.waitUntil(5000) {
            try { compose.onNodeWithTag("usage_enabled").assertIsEnabled(); true }
            catch (_: AssertionError) { false }
        }
    }
    @After fun tearDown() { db.close() }
    @Test fun accessManagementIsAvailableWhileImportsAreOffAndExplanationCanBeCancelled() {
        source.permission = UsageAccess.GRANTED
        refreshUiAccess()
        awaitStatus("Android access: Allowed")
        awaitPreference("Automatic app usage imports: Off")
        scrollToTag("usage_manage_primary")
        compose.onNodeWithTag("usage_manage_primary").assertIsEnabled().performClick()
        compose.onNodeWithTag("permission_continue").assertExists()
        compose.onNodeWithTag("permission_cancel").performClick()
        compose.onNodeWithTag("permission_continue").assertDoesNotExist()
        assertFalse(runBlocking { repository.state().enabled })
    }
    @Test fun optionalAccessImportAndOptOutKeepDurableDailyValues() {
        awaitPreference("Automatic app usage imports: Off")
        compose.onNodeWithTag("usage_enabled").performClick()
        awaitStatus("Android access: Not allowed")
        compose.onNodeWithTag("usage_import").assertDoesNotExist()
        source.permission = UsageAccess.GRANTED
        source.rows = listOf(UsageBucket("org.example.app", today.atStartOfDay(zone).toInstant(), now, 5_125, "Example app"))
        runBlocking { integration.refreshAccess() }
        refreshUiAccess()
        awaitStatus("Android access: Allowed")
        compose.onNodeWithTag("usage_import").performScrollTo()
        compose.onNodeWithTag("usage_import").performClick()
        compose.waitUntil(5_000) { runBlocking { repository.state().lastSync != null } }
        assertNoSavedUsageBrowser()
        assertEquals(5_125L, runBlocking { repository.observeDaily(today, zone).firstReport() }!!.totalMillis)
        compose.onNodeWithTag("usage_enabled").performScrollTo()
        compose.onNodeWithTag("usage_enabled").performClick()
        awaitPreference("Automatic app usage imports: Off")
        assertFalse(runBlocking { RoomUsageRepository(db).state().enabled })
        assertEquals(now, runBlocking { repository.state().lastSync })
        assertEquals(5_125L, runBlocking { RoomUsageRepository(db).observeDaily(today, zone).firstReport() }!!.totalMillis)
        assertNoSavedUsageBrowser()
        restoration.emulateSavedInstanceStateRestore()
        awaitPreference("Automatic app usage imports: Off")
        assertEquals(5_125L, runBlocking { repository.observeDaily(today, zone).firstReport() }!!.totalMillis)
    }
    @Test fun emptyAndRevokedAccessDoNotProduceZeroUsageOrDiscardSavedData() {
        compose.waitUntil(5_000) { compose.onNodeWithTag("usage_enabled").fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsProperties.ToggleableState) }
        source.permission = UsageAccess.GRANTED
        runBlocking { integration.setEnabled(true); integration.importNow() }
        refreshUiAccess()
        awaitStatus("Android access: Allowed")
        awaitTaggedText("usage_no_data", "No new usage data was returned. Saved history is unchanged.")
        compose.onNodeWithTag("usage_daily_total").assertDoesNotExist()
        assertNull(runBlocking { repository.state().checkpoint })
        assertNull(runBlocking { repository.observeDaily(today, zone).firstReport() })
        source.rows = listOf(UsageBucket("org.example.app", today.atStartOfDay(zone).toInstant(), now, 5_125, "Example app"))
        runBlocking { integration.importNow() }
        assertEquals(5_125L, runBlocking { repository.observeDaily(today, zone).firstReport() }!!.totalMillis)
        source.permission = UsageAccess.REQUIRED
        runBlocking { integration.refreshAccess() }
        refreshUiAccess()
        awaitStatus("Android access: Not allowed")
        assertEquals(now, runBlocking { repository.state().checkpoint })
        scrollToTag("usage_grant"); compose.onNodeWithTag("usage_grant").assertIsEnabled()
        scrollToTag("usage_refresh"); compose.onNodeWithTag("usage_refresh").assertIsEnabled()
        assertEquals(5_125L, runBlocking { RoomUsageRepository(db).observeDaily(today, zone).firstReport() }!!.totalMillis)
        assertNoSavedUsageBrowser()
    }
    @Test fun importFailuresKeepAccessRefreshAndRetentionControlsUsable() {
        source.permission = UsageAccess.GRANTED; source.fail = true
        runBlocking { integration.setEnabled(true) }
        refreshUiAccess()
        awaitStatus("Android access: Allowed")
        scrollToTag("usage_import"); compose.onNodeWithTag("usage_import").performClick()
        awaitText("Import failed. Saved usage is unchanged; try again.")
        scrollToTag("usage_import"); compose.onNodeWithTag("usage_import").assertIsEnabled()
        scrollToTag("usage_refresh"); compose.onNodeWithTag("usage_refresh").assertIsEnabled().performClick()
        scrollToTag("usage_details"); compose.onNodeWithTag("usage_details").performClick()
        scrollToTag("usage_retention"); compose.onNodeWithTag("usage_retention").assertIsDisplayed()
        scrollToTag("usage_access"); compose.onNodeWithTag("usage_access").assertIsEnabled()
        assertNoSavedUsageBrowser()
    }
    private fun assertNoSavedUsageBrowser() {
        listOf("day_previous", "usage_view_timeline", "usage_view_totals", "usage_daily_total", "usage_daily_empty", "settings_places")
            .forEach { compose.onNodeWithTag(it).assertDoesNotExist() }
    }
    private fun scrollToTag(tag: String) {
        compose.waitUntil(5_000) {
            try {
                compose.onNodeWithTag(tag).performScrollTo()
                compose.onNodeWithTag(tag).assertIsDisplayed(); true
            } catch (_: AssertionError) { false }
        }
    }
    private fun awaitText(text: String) { compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    private fun refreshUiAccess() {
        compose.waitUntil(5_000) {
            try { compose.onNodeWithTag("usage_refresh").assertIsEnabled(); true }
            catch (_: AssertionError) { false }
        }
        scrollToTag("usage_refresh"); compose.onNodeWithTag("usage_refresh").performClick()
    }
    private fun awaitStatus(text: String) = awaitTaggedText("usage_status", text)
    private fun awaitPreference(text: String) = awaitTaggedText("usage_import_preference", text)
    private fun awaitTaggedText(tag: String, text: String) {
        compose.waitUntil(5_000) {
            try { compose.onNodeWithTag(tag).assertTextContains(text, substring = true); true }
            catch (_: AssertionError) { false }
        }
    }
    private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.firstReport(): T = first()
    private class Source : UsageSource {
        var permission = UsageAccess.REQUIRED; var rows = emptyList<UsageBucket>(); var fail = false
        override suspend fun access() = permission
        override suspend fun query(range: TimeRange): List<UsageBucket> { if (fail) error("Provider failure"); return rows }
    }
}
