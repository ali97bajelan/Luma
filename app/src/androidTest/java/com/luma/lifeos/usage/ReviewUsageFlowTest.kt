package com.luma.lifeos.usage

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.repository.RoomUsageRepository
import com.luma.lifeos.domain.time.TimeRange
import com.luma.lifeos.domain.usage.*
import com.luma.lifeos.ui.review.ReviewUsageRoute
import com.luma.lifeos.ui.theme.LifeOsTheme
import java.time.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*

class ReviewUsageFlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: LumaDatabase
    private lateinit var repository: RoomUsageRepository
    private lateinit var integration: UsageIntegration
    private val date = LocalDate.parse("2026-10-12")
    private val zone = ZoneOffset.UTC
    private val start = date.atStartOfDay(zone).toInstant()
    private val now = date.atTime(12, 0).toInstant(zone)
    private var sourceQueries = 0
    private var accessChecks = 0
    private var imports = 0
    private var refreshes = 0
    private var configChanges = 0

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, LumaDatabase::class.java).build()
        repository = RoomUsageRepository(db)
        val syncer = UsageSyncer(object : UsageSource {
            override suspend fun access(): UsageAccess { accessChecks++; return UsageAccess.REQUIRED }
            override suspend fun query(range: TimeRange): List<UsageBucket> { sourceQueries++; return emptyList() }
            override suspend fun querySessions(range: TimeRange): UsageSessionBatch? { sourceQueries++; return null }
        }, repository, object : UsageScheduler { override fun schedule() {}; override fun cancel() {} }, Clock.fixed(now, zone))
        integration = object : UsageIntegration by syncer {
            override suspend fun importNow(): UsageSyncOutcome { imports++; return syncer.importNow() }
            override suspend fun refreshAccess() { refreshes++; syncer.refreshAccess() }
            override suspend fun setEnabled(enabled: Boolean) { configChanges++; syncer.setEnabled(enabled) }
        }
        runBlocking { repository.setEnabled(true) }
    }
    @After fun close() { db.close() }

    @Test fun savedUsageDetailsAreReadOnly() {
        runBlocking {
            repository.commitImport(listOf(UsageBucket("org.example.unused", start, now, 0, "Unused app")),
                UsageSessionBatch(TimeRange(start.minusSeconds(60), now), listOf(
                    UsageSession("org.example.app", start.minusSeconds(30), start.plusSeconds(60), "Example app"),
                    UsageSession("org.example.app", start.plusSeconds(120), start.plusSeconds(240), "Example app"))), now, now)
            repository.setEnabled(false)
        }
        val restoration = StateRestorationTester(compose)
        var selected by mutableStateOf(date)
        restoration.setContent { LifeOsTheme { ReviewUsageRoute(integration, selected, zone, {}) } }
        scrollText("0 h 3 min 0 s")
        compose.onAllNodesWithText("0 h 3 min 0 s").assertCountEquals(2)
        scrollText("0 h 1 min 0 s")
        compose.onNodeWithText("0 h 1 min 0 s").assertIsDisplayed()
        scrollText("0 h 2 min 0 s")
        compose.onNodeWithText("0 h 2 min 0 s").assertIsDisplayed()
        compose.onNodeWithText("Unused app").assertDoesNotExist()
        restoration.emulateSavedInstanceStateRestore()
        scrollText("0 h 3 min 0 s")
        compose.runOnIdle { selected = date.minusDays(1) }
        scrollText("0 h 0 min 30 s")
        compose.onAllNodesWithText("0 h 0 min 30 s").assertCountEquals(3)
        listOf("usage_enabled", "usage_access", "usage_import", "usage_refresh", "day_previous", "usage_view_timeline", "usage_view_totals")
            .forEach { compose.onNodeWithTag(it).assertDoesNotExist() }
        compose.runOnIdle {
            assertEquals(0, sourceQueries); assertEquals(0, accessChecks)
            assertEquals(0, imports); assertEquals(0, refreshes); assertEquals(0, configChanges)
        }
        assertFalse(runBlocking { repository.state().enabled })
        assertEquals(180_000L, runBlocking { repository.observeDaily(date, zone).first() }!!.totalMillis)
    }

    @Test fun legacyTotalsRemainVisibleWithoutInventedSessions() {
        runBlocking { repository.commit(listOf(UsageBucket("org.example.app", start, now, 5_125, "Example app")), now, now) }
        compose.setContent { LifeOsTheme { ReviewUsageRoute(integration, date, zone, {}) } }
        scrollText("0 h 0 min 5.125 s")
        compose.onAllNodesWithText("0 h 0 min 5.125 s").assertCountEquals(2)
        compose.onAllNodes(SemanticsMatcher("Saved usage session") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("review_usage_session_") == true
        }).assertCountEquals(0)
        compose.runOnIdle { assertEquals(0, sourceQueries); assertEquals(0, imports) }
    }

    private fun scrollText(text: String) {
        compose.waitUntil(5000) {
            try {
                compose.onNodeWithTag("screen_review_usage").performScrollToNode(hasText(text))
                true
            } catch (_: AssertionError) { false }
        }
    }
}
