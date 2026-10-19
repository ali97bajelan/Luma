package com.luma.lifeos.summary

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.repository.*
import com.luma.lifeos.domain.model.*
import com.luma.lifeos.domain.repository.DailySummaryRepository
import com.luma.lifeos.domain.summary.DailySummary
import com.luma.lifeos.domain.usage.UsageBucket
import com.luma.lifeos.ui.theme.LifeOsTheme
import com.luma.lifeos.ui.today.TodayRoute
import com.luma.lifeos.ui.integrations.IntegrationAccessState
import com.luma.lifeos.ui.integrations.IntegrationAvailability
import com.luma.lifeos.ui.integrations.IntegrationAvailabilityState
import java.time.*
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class DailySummaryFlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: LumaDatabase
    private lateinit var summaries: RoomDailySummaryRepository
    private lateinit var originalLocale: Locale
    private val day = LocalDate.now()
    private val zone = ZoneId.systemDefault()
    private val now = day.atTime(12, 0).atZone(zone).toInstant()
    private val clock = Clock.fixed(now, zone)
    private val mood = EventId.new(); private val journal = EventId.new()
    private var opened: EventId? = null
    @Before fun setUp() {
        originalLocale = Locale.getDefault(); Locale.setDefault(Locale.US)
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, LumaDatabase::class.java).build()
        summaries = RoomDailySummaryRepository(db, clock)
        runBlocking {
            RoomMoodRepository(db, clock).create(mood, MoodInput(4, now, energy = 2))
            RoomJournalRepository(db, clock).create(journal, JournalInput("Source journal remains available", now))
            RoomMemoryRepository(db, clock).saveMarker(journal, MemoryMarkerInput())
        }
    }
    @After fun tearDown() { db.close(); Locale.setDefault(originalLocale) }
    private fun show(repository: DailySummaryRepository = summaries,
        integrationAvailability: IntegrationAvailabilityState = IntegrationAvailabilityState()) {
        compose.setContent {
            var date by remember { mutableStateOf(day) }
            LifeOsTheme { TodayRoute(RoomLifeEventRepository(db), date, zone, { date = it }, { date = day },
                onJournalClick = { opened = it }, summaries = repository,
                integrationAvailability = integrationAvailability) }
        }
    }
    @Test fun realMetricsRefreshAndOmitUnavailableMoodMeans() {
        show(integrationAvailability = IntegrationAvailabilityState(usage = IntegrationAvailability(
            access = IntegrationAccessState.UNAVAILABLE)))
        openSummary(); awaitText("summary_records", "2 entries", substring = true)
        compose.onNodeWithTag("summary_memories").assertDoesNotExist()
        awaitText("summary_mood", "4/5", substring = true)
        compose.onNodeWithTag("summary_energy").assertDoesNotExist()
        compose.onNodeWithTag("summary_stress").assertDoesNotExist()
        awaitText("summary_usage", "unavailable", substring = true)
        scrollToTag("summary_details"); compose.onNodeWithTag("summary_details").performClick()
        compose.onNodeWithTag("summary_memories").assertTextContains("1", substring = true)
        compose.onNodeWithTag("summary_energy").assertTextContains("2/5", substring = true)
        runBlocking { RoomMoodRepository(db, clock).update(mood, MoodInput(5, now, energy = 2)) }
        awaitText("summary_mood", "5/5", substring = true)
        compose.onNodeWithTag("screen_today").performScrollToNode(hasTestTag("day_previous"))
        compose.onNodeWithTag("day_previous").performClick()
        openSummary()
        awaitText("summary_records", "0 entries", substring = true)
        compose.onNodeWithTag("summary_mood").assertDoesNotExist()
        compose.onNodeWithTag("summary_energy").assertDoesNotExist()
        compose.onNodeWithTag("summary_stress").assertDoesNotExist()
        compose.onNodeWithTag("screen_today").performScrollToNode(hasTestTag("today_empty"))
        compose.onNodeWithTag("today_empty").assertIsDisplayed()
    }
    @Test fun summaryFailureDoesNotBlockSourceBrowsingAndCanRetry() {
        var failed = true
        val repository = object : DailySummaryRepository by summaries {
            override fun observe(date: LocalDate, zone: ZoneId): Flow<DailySummary> = flow {
                if (failed) error("Temporary summary failure")
                emitAll(summaries.observe(date, zone))
            }
        }
        show(repository)
        openSummary()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("daily_summary_error").fetchSemanticsNodes().isNotEmpty() }
        scrollToTag("event_$journal"); compose.onNodeWithTag("event_$journal").performClick()
        compose.runOnIdle { assertEquals(journal, opened) }
        failed = false; scrollToTag("daily_summary_retry")
        compose.onNodeWithTag("daily_summary_retry").performClick()
        awaitText("summary_records", "2 entries", substring = true)
        compose.onNodeWithTag("daily_summary_error").assertDoesNotExist()
    }
    @Test fun usageRowsShowLabelsPackagesAndTheirOwnDurationsByNativeDate() {
        val start = day.atStartOfDay(zone).toInstant()
        val labeled = UsageBucket("org.example.reader", start, now, 65_125, "Reader")
        val unlabeled = UsageBucket("org.example.empty", start, now, 0)
        runBlocking {
            val usage = RoomUsageRepository(db)
            usage.setEnabled(true)
            usage.commit(listOf(labeled, unlabeled), now, now)
            // The native interval belongs to today even if its timeline envelope does not.
            val events = RoomLifeEventRepository(db)
            val parent = events.findBySource(SourceIdentity(EventSource.USAGE_STATS, labeled.sourceReference))!!
            events.upsert(parent.copy(occurredAt = day.minusDays(1).atStartOfDay(zone).toInstant()))
        }
        show()
        openSummary()
        awaitText("summary_usage", "Saved foreground usage: 1 min · apps shown: 1")
        compose.onNodeWithTag("summary_usage_label_org.example.reader").assertDoesNotExist()
        scrollToTag("summary_details"); compose.onNodeWithTag("summary_details").performClick()
        compose.onNodeWithTag("summary_usage_label_org.example.reader").assertTextEquals("Reader")
        compose.onNodeWithTag("summary_usage_duration_org.example.reader").assertTextEquals("1 min")
        compose.onNodeWithTag("summary_usage_package_org.example.reader").assertTextEquals("org.example.reader")
        compose.onNodeWithTag("summary_usage_label_org.example.empty").assertDoesNotExist()
        compose.onNodeWithTag("summary_usage_duration_org.example.empty").assertDoesNotExist()
        compose.onNodeWithTag("summary_usage_package_org.example.empty").assertDoesNotExist()
    }
    @Test fun todayHidesUsageEventsAndItsCountsExcludeThem() {
        val start = day.atStartOfDay(zone).toInstant()
        val usage = com.luma.lifeos.domain.usage.UsageBucket("org.example.reader", start, now, 5_125, "Reader")
        runBlocking {
            val imports = RoomUsageRepository(db); imports.setEnabled(true); imports.commit(listOf(usage), now, now)
        }
        val id = runBlocking { db.lifeEvents().findBySource(EventSource.USAGE_STATS, usage.sourceReference)!!.id }
        show()
        openSummary()
        awaitText("summary_records", "2 entries", substring = true)
        scrollToTag("today_summary")
        compose.onNodeWithTag("today_summary").assertTextEquals("2 entries").assertIsDisplayed()
        compose.onNodeWithTag("event_$id").assertDoesNotExist()
        runBlocking { RoomLifeEventRepository(db).delete(mood); RoomLifeEventRepository(db).delete(journal) }
        awaitText("summary_records", "0 entries", substring = true)
        scrollToTag("today_empty")
        compose.onNodeWithTag("event_$id").assertDoesNotExist()
        assertNotNull(runBlocking { db.lifeEvents().get(id) })
    }
    private fun openSummary() {
        scrollToTag("today_daily_summary")
        compose.onNodeWithTag("today_daily_summary").performClick()
    }
    private fun scrollToTag(tag: String) {
        compose.waitUntil(5_000) {
            try { compose.onNodeWithTag("screen_today").performScrollToNode(hasTestTag(tag)); compose.onNodeWithTag(tag).assertIsDisplayed(); true }
            catch (_: AssertionError) { false }
        }
    }
    private fun awaitText(tag: String, text: String, substring: Boolean = false) {
        compose.waitUntil(5_000) {
            try {
                compose.onNodeWithTag("screen_today").performScrollToNode(hasTestTag(tag))
                compose.onNodeWithTag(tag).assertTextContains(text, substring = substring); true
            } catch (_: AssertionError) { false }
        }
    }
}
