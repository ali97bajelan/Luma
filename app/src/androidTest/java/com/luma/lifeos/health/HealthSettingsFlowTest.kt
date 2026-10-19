package com.luma.lifeos.health

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.repository.*
import com.luma.lifeos.domain.health.*
import com.luma.lifeos.domain.usage.*
import com.luma.lifeos.domain.time.TimeRange
import com.luma.lifeos.ui.theme.LifeOsTheme
import com.luma.lifeos.ui.settings.IntegrationsSettingsRoute
import java.time.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class HealthSettingsFlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: LumaDatabase
    private lateinit var health: HealthSyncer
    private lateinit var store: RoomHealthRepository
    private val source = Source()
    private val zone = ZoneId.of("UTC")
    private val now = Instant.parse("2026-10-12T12:00:00Z")
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, LumaDatabase::class.java).build()
        store = RoomHealthRepository(db); health = HealthSyncer(source, store, Clock.fixed(now, zone)) { zone }
        val usage = UsageSyncer(object : UsageSource {
            override suspend fun access() = UsageAccess.REQUIRED
            override suspend fun query(range: TimeRange) = emptyList<UsageBucket>()
        }, RoomUsageRepository(db), object : UsageScheduler { override fun schedule() {}; override fun cancel() {} })
        compose.setContent { LifeOsTheme {
            IntegrationsSettingsRoute(usage, health, zone, {})
        } }
        compose.waitUntil(5000) {
            try { compose.onNodeWithTag("health_enabled").assertIsEnabled(); true }
            catch (_: AssertionError) { false }
        }
    }
    @After fun close() { db.close() }
    @Test fun permissionStatusImportsCorrectionsAndOptOutPersistAcrossRecreation() {
        scroll("health_enabled"); compose.onNodeWithTag("health_enabled").performClick()
        awaitStatus("Android access: Not allowed")
        compose.onNodeWithTag("health_import").assertDoesNotExist()
        source.access = HealthAccess.GRANTED; runBlocking { health.refreshAccess() }
        refreshUiAccess()
        awaitStatus("Android access: Allowed")
        scroll("health_import"); compose.onNodeWithTag("health_import").performClick()
        compose.waitUntil(5000) { runBlocking { store.state().lastSync != null } }
        source.count = 80; runBlocking { health.importNow() }
        val window = stepWindows(now, zone).last()
        val saved = runBlocking { db.lifeEvents().findBySource(com.luma.lifeos.domain.model.EventSource.HEALTH_CONNECT, window.sourceReference)!! }
        assertEquals(80.0, runBlocking { db.lifeEvents().getDetails(saved.id)!!.activities.single().value }, 0.0)
        scroll("health_enabled"); compose.onNodeWithTag("health_enabled").performClick()
        awaitTaggedText("health_import_preference", "Step imports: Off")
        scroll("health_details"); compose.onNodeWithTag("health_details").performClick()
        scroll("health_retention"); compose.onNodeWithTag("health_retention").assertIsDisplayed()
        assertFalse(runBlocking { RoomHealthRepository(db).state().enabled })
        assertNotNull(runBlocking { db.lifeEvents().get(saved.id) })
    }
    @Test fun missingUnsupportedAndRevokedDataLeaveCoreSettingsUsable() {
        source.access = HealthAccess.UNAVAILABLE
        runBlocking { health.setEnabled(true) }; scroll("health_status")
        refreshUiAccess()
        awaitStatus("Android access: Unavailable on this device")
        awaitText("Health Connect is unavailable on this device")
        source.access = HealthAccess.GRANTED; source.count = null
        runBlocking { health.refreshAccess() }
        refreshUiAccess()
        awaitStatus("Android access: Allowed")
        runBlocking { health.importNow() }
        awaitTaggedText("health_no_data", "Health Connect returned no step data for this window")
        assertEquals(0, runBlocking { db.lifeEvents().countDay(0, now.toEpochMilli()) })
        source.access = HealthAccess.REQUIRED; runBlocking { health.refreshAccess() }
        refreshUiAccess()
        awaitStatus("Android access: Not allowed")
        scroll("health_manage"); compose.onNodeWithTag("health_manage").assertIsEnabled()
        scroll("health_refresh"); compose.onNodeWithTag("health_refresh").assertIsEnabled()
        compose.onNodeWithTag("day_previous").assertDoesNotExist()
        compose.onNodeWithTag("usage_view_totals").assertDoesNotExist()
        compose.onNodeWithTag("settings_places").assertDoesNotExist()
    }
    @Test fun revokedAccessKeepsSavedStepsAndManagementUsable() {
        source.access = HealthAccess.GRANTED
        runBlocking { health.setEnabled(true); health.importNow() }
        refreshUiAccess()
        awaitStatus("Android access: Allowed")
        val window = stepWindows(now, zone).last()
        val saved = runBlocking { db.lifeEvents().findBySource(com.luma.lifeos.domain.model.EventSource.HEALTH_CONNECT, window.sourceReference)!! }
        source.access = HealthAccess.REQUIRED
        runBlocking { health.refreshAccess() }
        refreshUiAccess()
        awaitStatus("Android access: Not allowed")
        compose.onNodeWithTag("health_import").assertDoesNotExist()
        scroll("health_manage"); compose.onNodeWithTag("health_manage").assertIsEnabled()
        scroll("health_refresh"); compose.onNodeWithTag("health_refresh").assertIsEnabled()
        assertEquals(100.0, runBlocking { db.lifeEvents().getDetails(saved.id)!!.activities.single().value }, 0.0)
    }
    private fun scroll(tag: String) { compose.onNodeWithTag(tag).performScrollTo() }
    private fun awaitText(text: String) { compose.waitUntil(5000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    private fun refreshUiAccess() {
        compose.waitUntil(5_000) {
            try { compose.onNodeWithTag("health_refresh").assertIsEnabled(); true }
            catch (_: AssertionError) { false }
        }
        scroll("health_refresh"); compose.onNodeWithTag("health_refresh").performClick()
    }
    private fun awaitStatus(text: String) = awaitTaggedText("health_status", text)
    private fun awaitTaggedText(tag: String, text: String) {
        compose.waitUntil(5_000) {
            try { compose.onNodeWithTag(tag).assertTextContains(text, substring = true); true }
            catch (_: AssertionError) { false }
        }
    }
    private class Source : HealthSource {
        var access = HealthAccess.REQUIRED; var count: Long? = 100
        override suspend fun access() = access
        override suspend fun query(windows: List<StepWindow>) = windows.map { StepObservation(it, count) }
    }
}
