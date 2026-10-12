package com.luma.lifeos.review

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.repository.*
import com.luma.lifeos.domain.model.*
import com.luma.lifeos.domain.review.*
import com.luma.lifeos.ui.review.*
import com.luma.lifeos.ui.theme.LifeOsTheme
import java.time.*
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalTestApi::class)
class WeeklyReviewFlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: LumaDatabase
    private lateinit var repo: RoomWeeklyReviewRepository
    private lateinit var oldLocale: Locale
    private val today = LocalDate.parse("2026-10-14"); private val zone = ZoneOffset.UTC
    private val now = today.atTime(12, 0).toInstant(zone); private val clock = Clock.fixed(now, zone)
    private val mood = EventId.new(); private val journal = EventId.new()
    @Before fun setup() {
        oldLocale = Locale.getDefault(); Locale.setDefault(Locale.US)
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, LumaDatabase::class.java).build()
        repo = RoomWeeklyReviewRepository(db)
        runBlocking {
            RoomMoodRepository(db, clock).create(mood, MoodInput(4, now, energy = 2))
            RoomJournalRepository(db, clock).create(journal, JournalInput("Original remembered journal", now))
            RoomMemoryRepository(db, clock).saveMarker(journal, MemoryMarkerInput())
        }
    }
    @After fun close() { db.close(); Locale.setDefault(oldLocale) }
    @Test fun statisticsOpenExactSourcesAndThenStoredContent() {
        var opened: EventId? = null
        compose.setContent {
            var selected by remember { mutableStateOf<Pair<ReviewDates, ReviewMetric>?>(null) }
            LifeOsTheme {
                if (selected == null) WeeklyReviewRoute(repo, today, zone,
                    onSources = { dates, metric -> selected = dates to metric }, onEvent = { opened = it.event.id })
                else ReviewSourcesRoute(repo, selected!!.first, zone, selected!!.second,
                    onBack = { selected = null }, onEvent = { opened = it.event.id })
            }
        }
        scroll("review_metric_mood"); compose.onNodeWithTag("review_metric_mood").assertTextContains("4", substring = true)
        scroll("review_sources_mood"); compose.onNodeWithTag("review_sources_mood").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("event_$mood").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("event_$journal").assertDoesNotExist()
        compose.onNodeWithTag("event_$mood").performClick()
        compose.runOnIdle { assertEquals(mood, opened) }
    }
    @Test fun historicalWeekRestoresAndThisWeekReturnsToLiveMetrics() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { LifeOsTheme { WeeklyReviewRoute(repo, today, zone, onSources = { _, _ -> }, onEvent = {}) } }
        scroll("review_previous"); compose.onNodeWithTag("review_previous").performClick()
        awaitText("review_metric_entries", "0")
        restoration.emulateSavedInstanceStateRestore()
        awaitText("review_metric_entries", "0")
        scroll("review_this_week"); compose.onNodeWithTag("review_this_week").performClick()
        awaitText("review_metric_entries", "2")
        compose.onNodeWithTag("review_next").assertIsNotEnabled()
    }
    @Test fun memoriesKeepOriginalRoutesAndMissingSignalsAreUnavailable() {
        var opened: EventId? = null
        compose.setContent { LifeOsTheme { WeeklyReviewRoute(repo, today, zone, onSources = { _, _ -> }, onEvent = { opened = it.event.id }) } }
        scroll("event_$journal"); compose.onNodeWithTag("event_$journal").performClick()
        compose.runOnIdle { assertEquals(journal, opened) }
        scroll("review_usage_unavailable"); compose.onNodeWithTag("review_usage_unavailable").assertIsDisplayed()
        scroll("review_activity_unavailable"); compose.onNodeWithTag("review_activity_unavailable").assertIsDisplayed()
    }
    private fun scroll(tag: String) {
        compose.waitUntil(5000) {
            try { compose.onNodeWithTag("memory_review_list").performScrollToNode(hasTestTag(tag)); true }
            catch (_: AssertionError) { false }
        }
    }
    private fun awaitText(tag: String, value: String) {
        compose.waitUntil(5000) {
            try { scroll(tag); compose.onNodeWithTag(tag).assertTextContains(value, substring = true); true }
            catch (_: AssertionError) { false }
        }
    }
}
