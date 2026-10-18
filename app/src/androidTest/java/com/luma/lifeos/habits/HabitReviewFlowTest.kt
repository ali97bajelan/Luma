package com.luma.lifeos.habits

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.repository.RoomHabitRepository
import com.luma.lifeos.data.repository.RoomWeeklyReviewRepository
import com.luma.lifeos.domain.habit.HabitLogInput
import com.luma.lifeos.domain.habit.HabitSaveInput
import com.luma.lifeos.domain.habit.HabitScheduleInput
import com.luma.lifeos.domain.habit.HabitLifecycle
import com.luma.lifeos.ui.review.WeeklyReviewRoute
import com.luma.lifeos.ui.theme.LifeOsTheme
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

class HabitReviewFlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: LumaDatabase
    private lateinit var habitRepository: RoomHabitRepository
    private lateinit var reviewRepository: RoomWeeklyReviewRepository
    private val today = LocalDate.parse("2026-10-14")
    private val zone = ZoneOffset.UTC
    private val now = Instant.parse("2026-10-14T12:00:00Z")

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, LumaDatabase::class.java).build()
        habitRepository = RoomHabitRepository(db, Clock.fixed(now, zone))
        reviewRepository = RoomWeeklyReviewRepository(db)
    }

    @After fun tearDown() = db.close()

    @Test fun habitsOnlyWeekHasItsOwnEvidenceAndOpensTheSelectedHabitDate() {
        val id = UUID.randomUUID()
        runBlocking {
            habitRepository.create(id, HabitSaveInput("Water", "glasses", today.minusDays(9), 8,
                DayOfWeek.entries.toSet(), zone))
            repeat(3) { habitRepository.log(UUID.randomUUID(), HabitLogInput(id, today, zone, null)) }
            habitRepository.setSkipRemaining(id, today, true)
            habitRepository.schedule(id, HabitScheduleInput(10, DayOfWeek.entries.toSet(), HabitLifecycle.ACTIVE))
        }
        var opened: Pair<UUID, LocalDate>? = null
        compose.setContent { LifeOsTheme {
            WeeklyReviewRoute(reviewRepository, today, zone, onSources = { _, _ -> }, onEvent = {},
                habits = habitRepository, onHabit = { habitId, date -> opened = habitId to date })
        } }

        val currentTotal = "review_habits_current_$id"
        compose.onNodeWithTag("memory_review_list").performScrollToNode(hasTestTag("review_heading_habits"))
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag(currentTotal)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("memory_review_list")
            .performScrollToNode(hasTestTag("review_overview_habits_summary"))
        compose.onNodeWithTag("review_overview_habits_summary").assertTextContains("3", substring = true)
        compose.onNodeWithTag("review_overview_habits_open").performClick()
        compose.waitUntil(10_000) {
            try { compose.onNodeWithTag("review_heading_habits").assertIsDisplayed(); true }
            catch (_: AssertionError) { false }
        }
        compose.onNodeWithTag("review_empty").assertDoesNotExist()
        compose.onNodeWithTag("review_habits_current_$id").assertTextContains("3", substring = true)
        compose.onNodeWithTag("review_habits_previous_$id").assertTextContains("0", substring = true)
        val dayTag = "review_habit_day_${id}_$today"
        compose.onNodeWithTag("review_habit_details_$id").assertExists()
        compose.onNodeWithTag(dayTag).assertDoesNotExist()
        compose.onNodeWithTag("review_habit_open_${id}_$today").assertDoesNotExist()
        compose.onNodeWithTag("review_habit_details_$id").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag(dayTag)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("memory_review_list").performScrollToNode(hasTestTag(dayTag))
        compose.onNodeWithTag(dayTag).assertIsDisplayed()
        compose.onNodeWithText("Remaining skipped").assertExists()
        compose.onNodeWithTag("review_habit_open_${id}_$today").performClick()
        compose.runOnIdle { assertEquals(id to today, opened) }
    }
}
