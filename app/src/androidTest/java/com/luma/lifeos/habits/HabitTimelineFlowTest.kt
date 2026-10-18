package com.luma.lifeos.habits

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.repository.RoomHabitRepository
import com.luma.lifeos.data.repository.RoomMoodRepository
import com.luma.lifeos.data.repository.RoomTimelineRepository
import com.luma.lifeos.domain.habit.HabitLogInput
import com.luma.lifeos.domain.habit.HabitSaveInput
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.MoodInput
import com.luma.lifeos.ui.theme.LifeOsTheme
import com.luma.lifeos.ui.timeline.TimelineRoute
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class HabitTimelineFlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: LumaDatabase
    private lateinit var habits: RoomHabitRepository
    private lateinit var timeline: RoomTimelineRepository
    private val date = LocalDate.parse("2026-10-14")
    private val habitZone = ZoneId.of("Asia/Tehran")
    private val screenZone = ZoneOffset.UTC
    private val now = Instant.parse("2026-10-14T16:00:00Z")

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, LumaDatabase::class.java).build()
        habits = RoomHabitRepository(db, Clock.fixed(now, ZoneOffset.UTC))
        timeline = RoomTimelineRepository(db)
    }

    @After fun tearDown() = db.close()

    @Test fun dateOnlyLogHasItsOwnGroupAndOpensTheSourceLog() {
        val habitId = UUID.randomUUID()
        val dateOnly = UUID.randomUUID()
        val timed = UUID.randomUUID()
        val eventId = EventId.new()
        val happened = date.atTime(9, 0).atZone(habitZone).toInstant()
        runBlocking {
            habits.create(habitId, HabitSaveInput("Water", "glasses", date.minusDays(7), 8,
                DayOfWeek.entries.toSet(), habitZone))
            habits.log(dateOnly, HabitLogInput(habitId, date, habitZone, null))
            habits.log(timed, HabitLogInput(habitId, date, habitZone, happened))
            RoomMoodRepository(db, Clock.fixed(now, ZoneOffset.UTC)).create(
                eventId, MoodInput(4, Instant.parse("2026-10-14T11:00:00Z")))
        }
        var opened: Triple<UUID, LocalDate, UUID?>? = null
        compose.setContent { LifeOsTheme {
            TimelineRoute(timeline, date, screenZone, onSelectDate = {}, onToday = {}, onEvent = {}, onMemory = {},
                onHabit = { id, logDate, logId -> opened = Triple(id, logDate, logId) })
        } }

        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("habit_timeline_$dateOnly").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("timeline_date_only_group").assertIsDisplayed()
        compose.onNodeWithTag("habit_timeline_$dateOnly").assertTextContains("Time not recorded", substring = true)
        compose.onNodeWithTag("habit_timeline_zone_$dateOnly", useUnmergedTree = true).assertTextContains("Asia/Tehran")
        compose.onNodeWithTag("habit_timeline_$timed").assertTextContains("09:00")
        compose.onNodeWithTag("habit_timeline_zone_$timed", useUnmergedTree = true).assertTextContains("Asia/Tehran")
        compose.onNodeWithTag("timeline_habit_count").assertTextContains("2", substring = true)
        compose.onNodeWithTag("habit_timeline_$dateOnly").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(Triple(habitId, date, dateOnly), opened) }
    }
}
