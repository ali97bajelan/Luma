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
import com.luma.lifeos.domain.repository.WeeklyReviewRepository
import com.luma.lifeos.domain.usage.*
import com.luma.lifeos.domain.summary.MoodSample
import androidx.compose.ui.semantics.SemanticsProperties
import kotlinx.coroutines.flow.flowOf
import com.luma.lifeos.ui.review.*
import com.luma.lifeos.ui.theme.LifeOsTheme
import java.time.*
import java.util.Locale
import java.util.UUID
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
    @Test fun overviewShowsNewestMemoryFromPagedSources() {
        val newest = EventId.new()
        runBlocking {
            val journals = RoomJournalRepository(db, clock)
            val memories = RoomMemoryRepository(db, clock)
            repeat(6) { index ->
                val id = if (index == 5) newest else EventId.new()
                journals.create(id, JournalInput("Memory $index", now.plusSeconds(index + 1L)))
                memories.saveMarker(id, MemoryMarkerInput())
            }
        }
        var opened: EventId? = null
        compose.setContent { LifeOsTheme { WeeklyReviewRoute(repo, today, zone,
            onSources = { _, _ -> }, onEvent = { opened = it.event.id }) } }
        scroll("review_overview_memory_open")
        compose.onNodeWithTag("review_overview_memory_open").performClick()
        compose.runOnIdle { assertEquals(newest, opened) }
    }

    @Test fun overviewKeepsMissingAndRecordedZeroDistinct() {
        val period = reviewPeriod(today, today, zone)
        val report = calculateWeeklyReview(period, emptyList(), listOf(
            DailyUsageReport(today, zone, emptyList(), 0L, history = UsageHistory.COMPLETE)))
        val repository = object : WeeklyReviewRepository by repo {
            override fun observe(period: ReviewPeriod) = flowOf(report)
        }
        compose.setContent { LifeOsTheme { WeeklyReviewRoute(repository, today, zone,
            onSources = { _, _ -> }, onEvent = {}) } }
        scroll("review_overview_mood")
        compose.onNodeWithTag("review_overview_mood").assertTextContains("Unavailable", substring = true)
        scroll("review_overview_usage")
        compose.onNodeWithTag("review_overview_usage").assertTextContains("0", substring = true)
        scroll("review_overview_usage_coverage")
        compose.onNodeWithTag("review_overview_usage_coverage").assertTextContains("1", substring = true)
        compose.onNodeWithTag("review_overview_usage_coverage").assertTextContains("Incomplete", substring = true)
        scroll("review_overview_activity")
        compose.onNodeWithTag("review_overview_activity").assertTextContains("Unavailable", substring = true)
        scroll("review_overview_memory_empty")
        compose.onNodeWithTag("review_overview_memory_empty").assertExists()
        scroll("review_overview_catalog")
        compose.onNodeWithTag("review_overview_catalog").assertExists()
    }

    @Test fun overviewShowsSavedSamplesActivitySourceAndLinkedCatalogCounts() {
        val period = reviewPeriod(today, today, zone)
        val person = ReviewName(UUID.randomUUID(), "Alex")
        val place = ReviewName(UUID.randomUUID(), "Park")
        val report = calculateWeeklyReview(period, listOf(
            ReviewRecord(mood, now, mood = MoodSample(1), people = listOf(person), place = place,
                activities = listOf(ActivityDetails(UUID.randomUUID(), "distance", 1.0, "km", EventSource.MANUAL))),
            ReviewRecord(journal, now.plusSeconds(1), mood = MoodSample(5), memory = true, people = listOf(person),
                activities = listOf(ActivityDetails(UUID.randomUUID(), "distance", 2.0, "km", EventSource.MANUAL),
                    ActivityDetails(UUID.randomUUID(), "steps", 9999.0, "count", EventSource.HEALTH_CONNECT)))
        ), emptyList())
        val repository = object : WeeklyReviewRepository by repo {
            override fun observe(period: ReviewPeriod) = flowOf(report)
        }
        var opened: Pair<ReviewDates, ReviewMetric>? = null
        compose.setContent { LifeOsTheme { WeeklyReviewRoute(repository, today, zone,
            onSources = { dates, metric -> opened = dates to metric }, onEvent = {}) } }
        scroll("review_overview_mood")
        compose.onNodeWithTag("review_overview_mood").assertTextContains("3 / 5", substring = true)
        compose.onNodeWithTag("review_overview_mood").assertTextContains("Samples: 2", substring = true)
        scroll("review_overview_usage")
        compose.onNodeWithTag("review_overview_usage").assertTextContains("Unavailable", substring = true)
        scroll("review_overview_activity")
        compose.onNodeWithTag("review_overview_activity").assertTextContains("3 km", substring = true)
        compose.onNodeWithTag("review_overview_activity").assertTextContains("Samples: 2", substring = true)
        scroll("review_overview_activity_sources")
        compose.onNodeWithTag("review_overview_activity_sources").performClick()
        compose.runOnIdle { assertEquals(period.current to ReviewMetric(ReviewMetricKind.ACTIVITY,
            activity = ReviewActivityKey("distance", "km", EventSource.MANUAL)), opened) }
        scroll("review_overview_catalog")
        compose.onNodeWithTag("review_overview_catalog").assertTextContains("Linked people: 1", substring = true)
        compose.onNodeWithTag("review_overview_catalog").assertTextContains("Linked places: 1", substring = true)
    }

    @Test fun sectionControlsScrollToTheirHeadings() {
        compose.setContent { LifeOsTheme { WeeklyReviewRoute(repo, today, zone,
            onSources = { _, _ -> }, onEvent = {}) } }
        listOf("mood", "memories", "catalog", "usage", "activity").forEach { section ->
            scroll("review_section_$section")
            compose.onNodeWithTag("review_section_$section").performClick()
            compose.waitUntil(5000) {
                try { compose.onNodeWithTag("review_heading_$section").assertIsDisplayed(); true }
                catch (_: AssertionError) { false }
            }
        }
    }

    @Test fun sectionSelectionRestoresWithoutChangingTheSelectedWeek() {
        val restoration = StateRestorationTester(compose)
        var opened: ReviewDates? = null
        restoration.setContent { LifeOsTheme { WeeklyReviewRoute(repo, today, zone,
            onSources = { dates, _ -> opened = dates }, onEvent = {}) } }
        scroll("review_previous")
        compose.onNodeWithTag("review_previous").performClick()
        scroll("review_section_mood")
        compose.onNodeWithTag("review_section_mood").performClick()
        restoration.emulateSavedInstanceStateRestore()
        scroll("review_section_mood")
        compose.onNodeWithTag("review_section_mood").assertIsSelected()
        compose.onNodeWithTag("review_section_mood").performClick()
        scroll("review_sources_menu_entries")
        compose.onNodeWithTag("review_sources_menu_entries").performClick()
        compose.onNodeWithTag("review_sources_entries").performClick()
        compose.runOnIdle { assertEquals(ReviewDates(LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-11")), opened) }
    }

    @Test fun sourceMenuOpensExactCurrentAndPreviousRows() {
        val previousMood = EventId.new()
        runBlocking { RoomMoodRepository(db, clock).create(previousMood, MoodInput(2, now.minusSeconds(7 * 86400))) }
        var opened: Pair<ReviewDates, ReviewMetric>? = null
        compose.setContent { LifeOsTheme { WeeklyReviewRoute(repo, today, zone,
            onSources = { dates, metric -> opened = dates to metric }, onEvent = {}) } }
        scroll("review_sources_menu_mood")
        compose.onNodeWithTag("review_sources_menu_mood").performClick()
        compose.onNodeWithTag("review_sources_mood").performClick()
        compose.runOnIdle { assertEquals(ReviewDates(LocalDate.parse("2026-10-12"), today) to ReviewMetric(ReviewMetricKind.MOOD), opened) }
        assertEquals(listOf(mood), runBlocking { repo.sources(opened!!.first, zone, opened!!.second, 0, 50).records.map { it.event.id } })
        compose.onNodeWithTag("review_sources_menu_mood").performClick()
        compose.onNodeWithTag("review_previous_sources_mood").performClick()
        compose.runOnIdle { assertEquals(ReviewDates(LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-07")) to ReviewMetric(ReviewMetricKind.MOOD), opened) }
        assertEquals(listOf(previousMood), runBlocking { repo.sources(opened!!.first, zone, opened!!.second, 0, 50).records.map { it.event.id } })
        scroll("review_sources_energy")
        compose.onNodeWithTag("review_sources_menu_energy").assertDoesNotExist()
        compose.onNodeWithTag("review_previous_sources_energy").assertDoesNotExist()
        compose.onNodeWithTag("review_sources_energy").performClick()
        compose.runOnIdle { assertEquals(ReviewDates(LocalDate.parse("2026-10-12"), today) to ReviewMetric(ReviewMetricKind.ENERGY), opened) }
    }

    @Test fun secondaryEvidenceStartsCollapsed() {
        val period = reviewPeriod(today, today, zone)
        val report = calculateWeeklyReview(period, emptyList(), listOf(
            DailyUsageReport(today, zone, emptyList(), 0L, history = UsageHistory.COMPLETE)))
        val repository = object : WeeklyReviewRepository by repo {
            override fun observe(period: ReviewPeriod) = flowOf(report)
        }
        var opened: LocalDate? = null
        compose.setContent { LifeOsTheme { WeeklyReviewRoute(repository, today, zone,
            onSources = { _, _ -> }, onEvent = {}, onUsage = { opened = it }) } }
        scroll("review_usage_methodology")
        compose.onNodeWithTag("review_usage_methodology")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
        compose.onNodeWithTag("review_usage_methodology_text").assertDoesNotExist()
        compose.onNodeWithTag("review_usage_methodology").performClick()
        compose.onNodeWithTag("review_usage_methodology")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
        compose.onNodeWithTag("review_usage_methodology_text").assertExists()
        scroll("review_usage_coverage_details")
        compose.onNodeWithTag("review_usage_coverage_text").assertDoesNotExist()
        compose.onNodeWithTag("review_usage_coverage_details").performClick()
        compose.onNodeWithTag("review_usage_coverage_text").assertExists()
        scroll("review_usage_daily_details")
        compose.onNodeWithTag("review_usage_date_$today").assertDoesNotExist()
        compose.onNodeWithTag("review_usage_daily_details").performClick()
        scroll("review_usage_date_$today")
        compose.onNodeWithTag("review_usage_date_$today").performClick()
        compose.runOnIdle { assertEquals(today, opened) }
    }
    private fun scroll(tag: String) {
        compose.waitUntil(15000) {
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
