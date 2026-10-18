package com.luma.lifeos.habits

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.repository.*
import com.luma.lifeos.domain.habit.*
import com.luma.lifeos.domain.model.*
import com.luma.lifeos.ui.review.WeeklyReviewRoute
import com.luma.lifeos.ui.theme.LifeOsTheme
import com.luma.lifeos.ui.timeline.TimelineRoute
import com.luma.lifeos.ui.today.TodayRoute
import java.time.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.*

/** Counts must describe saved logs, including repetitions beyond a reached daily target. */
class EntryHabitCountsFlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: LumaDatabase
    private lateinit var habits: RoomHabitRepository
    private val date = LocalDate.now(ZoneOffset.UTC).minusDays(7)
    private val zone = ZoneOffset.UTC
    private val now = date.atTime(12, 0).toInstant(zone)
    private val clock = Clock.fixed(now, zone)
    private val habitId = UUID.randomUUID()
    private var surface by mutableStateOf("today")

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, LumaDatabase::class.java).build()
        habits = RoomHabitRepository(db, clock)
        runBlocking {
            habits.create(habitId, HabitSaveInput("Water", "glasses", date.minusWeeks(2), 1,
                DayOfWeek.entries.toSet(), zone))
        }
    }
    @After fun tearDown() = db.close()

    private fun show(habitsDate: LocalDate = date) {
        compose.setContent { LifeOsTheme { key(surface) {
            when (surface) {
                "today" -> TodayRoute(RoomLifeEventRepository(db), date, zone, {}, {},
                    summaries = RoomDailySummaryRepository(db, clock), habits = habits, habitsDate = habitsDate)
                "timeline" -> TimelineRoute(RoomTimelineRepository(db), date, zone, {}, {}, {}, {})
                else -> WeeklyReviewRoute(RoomWeeklyReviewRepository(db), date, zone, { _, _ -> }, {}, habits = habits)
            }
        } } }
    }

    private fun awaitCounts(tag: String, expected: String) {
        compose.waitUntil(10_000) {
            try {
                val list = when (surface) { "today" -> "screen_today"; "timeline" -> "screen_timeline"; else -> "memory_review_list" }
                compose.onNodeWithTag(list).performScrollToNode(hasTestTag(tag))
                compose.onNodeWithTag(tag).assertTextContains(expected, substring = true)
                true
            } catch (_: AssertionError) { false }
            catch (failure: IllegalStateException) {
                if (failure.message?.startsWith("No compose hierarchies found") == true) false else throw failure
            }
        }
    }

    private fun assertEverySurface(expected: String) {
        listOf("today" to "today_summary", "timeline" to "timeline_counts", "review" to "review_overview_counts").forEach { (screen, tag) ->
            compose.runOnIdle { surface = screen }
            awaitCounts(tag, expected)
        }
    }

    @Test fun habitOnlyDayAcknowledgesOneLogOnEverySurface() {
        runBlocking { habits.log(UUID.randomUUID(), HabitLogInput(habitId, date, zone, null)) }
        show()
        awaitCounts("today_summary", "0 entries · 1 habit log")
        compose.onNodeWithTag("today_empty").assertTextContains("No mood, journal or memory entries yet")
        compose.onNodeWithTag("screen_today").performScrollToNode(hasTestTag("today_daily_summary"))
        compose.onNodeWithTag("today_daily_summary").performClick()
        compose.onNodeWithTag("screen_today").performScrollToNode(hasTestTag("summary_records"))
        awaitCounts("summary_records", "0 entries · 1 habit log")
        assertEverySurface("0 entries · 1 habit log")
    }

    @Test fun journalAndRepeatedLogsStaySeparateAndRefreshAfterDeletion() {
        val first = UUID.randomUUID(); val second = UUID.randomUUID()
        runBlocking {
            RoomJournalRepository(db, clock).create(EventId.new(), JournalInput("A short walk", now))
            habits.log(first, HabitLogInput(habitId, date, zone, null))
            habits.log(second, HabitLogInput(habitId, date, zone, now))
        }
        show()
        assertEverySurface("1 entry · 2 habit logs")
        // Review is still visible: its overview must update without navigating away.
        runBlocking { habits.deleteLog(second) }
        awaitCounts("review_overview_counts", "1 entry · 1 habit log")
        assertEverySurface("1 entry · 1 habit log")
        runBlocking { habits.deleteLog(first) }
        awaitCounts("review_overview_counts", "1 entry · 0 habit logs")
        assertEverySurface("1 entry · 0 habit logs")
    }

    @Test fun todayCountsTheSelectedDateEvenWhenHabitPreviewUsesAnotherDate() {
        runBlocking {
            habits.log(UUID.randomUUID(), HabitLogInput(habitId, date, zone, null))
            repeat(2) { habits.log(UUID.randomUUID(), HabitLogInput(habitId, date.minusDays(1), zone, null)) }
        }
        show(habitsDate = date.minusDays(1))
        awaitCounts("today_summary", "0 entries · 1 habit log")
    }
}
