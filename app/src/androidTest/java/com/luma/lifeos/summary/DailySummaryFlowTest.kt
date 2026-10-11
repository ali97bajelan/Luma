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
import com.luma.lifeos.ui.theme.LifeOsTheme
import com.luma.lifeos.ui.today.TodayRoute
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
    private fun show(repository: DailySummaryRepository = summaries) {
        compose.setContent {
            var date by remember { mutableStateOf(day) }
            LifeOsTheme { TodayRoute(RoomLifeEventRepository(db), date, zone, { date = it }, { date = day },
                onJournalClick = { opened = it }, summaries = repository) }
        }
    }
    @Test fun realMetricsRefreshAndEmptyDatesShowUnavailableSignals() {
        show(); awaitText("summary_records", "Saved entries: 2")
        awaitText("summary_memories", "Memories, including remembered entries: 1")
        awaitText("summary_mood", "4/5", substring = true)
        awaitText("summary_usage", "unavailable", substring = true)
        runBlocking { RoomMoodRepository(db, clock).update(mood, MoodInput(5, now, energy = 2)) }
        awaitText("summary_mood", "5/5", substring = true)
        compose.onNodeWithTag("screen_today").performScrollToNode(hasTestTag("day_previous"))
        compose.onNodeWithTag("day_previous").performClick()
        awaitText("summary_records", "Saved entries: 0")
        awaitText("summary_mood", "unavailable", substring = true)
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
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("daily_summary_error").fetchSemanticsNodes().isNotEmpty() }
        scrollToTag("event_$journal"); compose.onNodeWithTag("event_$journal").performClick()
        compose.runOnIdle { assertEquals(journal, opened) }
        failed = false; scrollToTag("daily_summary_retry")
        compose.onNodeWithTag("daily_summary_retry").performClick()
        awaitText("summary_records", "Saved entries: 2")
        compose.onNodeWithTag("daily_summary_error").assertDoesNotExist()
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
