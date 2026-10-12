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
import com.luma.lifeos.ui.usage.UsageSettingsRoute
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
            var date by remember { mutableStateOf(today) }
            LifeOsTheme { UsageSettingsRoute(integration, date, zone, { date = it }, { date = today }, {}) }
        }
    }
    @After fun tearDown() { db.close() }
    @Test fun optionalAccessImportAndOptOutKeepDurableDailyValues() {
        awaitText("Imports are off")
        compose.onNodeWithTag("usage_enabled").performClick()
        awaitText("Android Usage access is required")
        compose.onNodeWithTag("usage_import").assertIsNotEnabled()
        source.permission = UsageAccess.GRANTED
        source.rows = listOf(UsageBucket("org.example.app", today.atStartOfDay(zone).toInstant(), now, 5_125, "Example app"))
        runBlocking { integration.refreshAccess() }
        awaitText("Usage access is available")
        compose.onNodeWithTag("screen_settings").performScrollToNode(hasTestTag("usage_import"))
        compose.onNodeWithTag("usage_import").performClick()
        scrollToTag("usage_daily_total")
        compose.onNodeWithTag("screen_settings").performScrollToNode(hasTestTag("usage_app_org.example.app"))
        compose.onNodeWithText("Example app").assertIsDisplayed()
        runBlocking { integration.importNow() }
        assertEquals(5_125L, runBlocking { repository.observeDaily(today, zone).firstReport() }!!.totalMillis)
        compose.onNodeWithTag("screen_settings").performScrollToNode(hasTestTag("usage_enabled"))
        compose.onNodeWithTag("usage_enabled").performClick()
        awaitText("Imports are off")
        assertFalse(runBlocking { RoomUsageRepository(db).state().enabled })
        assertEquals(now, runBlocking { repository.state().lastSync })
        compose.onNodeWithTag("screen_settings").performScrollToNode(hasTestTag("day_previous"))
        compose.onNodeWithTag("day_previous").performClick(); scrollToTag("usage_daily_empty")
    }
    @Test fun emptyAndRevokedAccessDoNotProduceZeroUsageOrDiscardSavedData() {
        compose.waitUntil(5_000) { compose.onNodeWithTag("usage_enabled").fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsProperties.ToggleableState) }
        source.permission = UsageAccess.GRANTED
        runBlocking { integration.setEnabled(true); integration.importNow() }
        awaitText("Android has no reported usage data yet")
        compose.onNodeWithTag("usage_daily_total").assertDoesNotExist()
        source.permission = UsageAccess.REQUIRED
        runBlocking { integration.refreshAccess() }
        awaitText("Android Usage access is required")
        assertNull(runBlocking { repository.state().checkpoint })
        compose.onNodeWithTag("screen_settings").performScrollToNode(hasTestTag("settings_places"))
        compose.onNodeWithTag("settings_places").assertIsEnabled()
    }
    @Test fun switchesBetweenRecordedVisitsAndTotalsWithoutShowingUnusedApps() {
        val start = today.atStartOfDay(zone).toInstant()
        runBlocking {
            integration.setEnabled(true)
            repository.commitImport(listOf(UsageBucket("org.example.unused", start, now, 0, "Unused app")),
                UsageSessionBatch(TimeRange(start.minusSeconds(60), now), listOf(
                    UsageSession("org.example.app", start.minusSeconds(30), start.plusSeconds(60), "Example app"),
                    UsageSession("org.example.app", start.plusSeconds(120), start.plusSeconds(240), "Example app"))), now, now)
        }
        scrollToTag("usage_view_timeline")
        compose.onNodeWithTag("usage_view_timeline").performClick()
        scrollToTag("usage_session_org.example.app_${start.minusSeconds(30).toEpochMilli()}")
        compose.onNodeWithText("0 h 1 min 0 s").assertIsDisplayed()
        scrollToTag("usage_session_org.example.app_${start.plusSeconds(120).toEpochMilli()}")
        compose.onNodeWithText("0 h 2 min 0 s").assertIsDisplayed()
        compose.onNodeWithText("Unused app").assertDoesNotExist()
        scrollToTag("usage_view_totals")
        compose.onNodeWithTag("usage_view_totals").performClick()
        scrollToTag("usage_app_org.example.app")
        compose.onAllNodesWithText("0 h 3 min 0 s").assertCountEquals(2)
        compose.onNodeWithTag("usage_app_org.example.unused").assertDoesNotExist()
        scrollToTag("usage_history_partial")
        scrollToTag("day_previous"); compose.onNodeWithTag("day_previous").performClick()
        scrollToTag("usage_app_org.example.app")
        compose.onAllNodesWithText("0 h 0 min 30 s").assertCountEquals(2)
        scrollToTag("usage_view_timeline"); compose.onNodeWithTag("usage_view_timeline").performClick()
        scrollToTag("usage_session_org.example.app_${start.minusSeconds(30).toEpochMilli()}")
    }
    @Test fun legacyDailyTotalsDoNotInventTimelineSessions() {
        runBlocking {
            integration.setEnabled(true)
            repository.commit(listOf(UsageBucket("org.example.app", today.atStartOfDay(zone).toInstant(), now, 5_125)), now, now)
        }
        scrollToTag("usage_view_timeline"); compose.onNodeWithTag("usage_view_timeline").performClick()
        scrollToTag("usage_history_unavailable")
        restoration.emulateSavedInstanceStateRestore()
        scrollToTag("usage_view_timeline"); compose.onNodeWithTag("usage_view_timeline").assertIsSelected()
        scrollToTag("usage_history_unavailable")
        compose.onNodeWithTag("usage_daily_total").assertDoesNotExist()
        scrollToTag("usage_view_totals"); compose.onNodeWithTag("usage_view_totals").performClick()
        scrollToTag("usage_app_org.example.app")
        compose.onAllNodesWithText("0 h 0 min 5.125 s").assertCountEquals(2)
    }
    private fun scrollToTag(tag: String) {
        compose.waitUntil(5_000) {
            try {
                compose.onNodeWithTag("screen_settings").performScrollToNode(hasTestTag(tag))
                compose.onNodeWithTag(tag).assertIsDisplayed(); true
            } catch (_: AssertionError) { false }
        }
    }
    private fun awaitText(text: String) { compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.firstReport(): T = first()
    private class Source : UsageSource {
        var permission = UsageAccess.REQUIRED; var rows = emptyList<UsageBucket>()
        override suspend fun access() = permission
        override suspend fun query(range: TimeRange) = rows
    }
}
