package com.luma.lifeos.habits

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.repository.RoomHabitRepository
import com.luma.lifeos.data.repository.RoomLifeEventRepository
import com.luma.lifeos.domain.habit.HabitDay
import com.luma.lifeos.domain.habit.HabitRepository
import com.luma.lifeos.domain.habit.HabitSaveInput
import com.luma.lifeos.ui.habits.HabitsRoute
import com.luma.lifeos.ui.habits.HabitDetailRoute
import com.luma.lifeos.ui.theme.LifeOsTheme
import com.luma.lifeos.ui.today.TodayRoute
import java.time.Clock
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class HabitDateSelectionFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var database: LumaDatabase
    private lateinit var stored: RoomHabitRepository
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.now(zone)
    private val first = today.minusDays(2)
    private val second = today.minusDays(1)
    private val id = UUID.randomUUID()

    @Before fun setup() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, LumaDatabase::class.java,
        ).build()
        stored = RoomHabitRepository(database, Clock.system(zone))
        stored.create(id, HabitSaveInput("Date-bound habit", "pages", first.minusDays(7), 2,
            DayOfWeek.entries.toSet(), zone))
        Unit
    }

    @After fun teardown() {
        compose.activityRule.scenario.close()
        database.close()
    }

    @Test fun habitsRejectsStaleClicksDuringDelayFailureAndReturnToSameDate() = checkTransition(false)
    @Test fun todayRejectsStaleClicksDuringDelayFailureAndReturnToSameDate() = checkTransition(true)

    @Test fun detailOffersRetryWhenDayObservationFailsAfterItsFirstSnapshot() {
        val failure = CompletableDeferred<Unit>()
        var fail = true
        val repository = object : HabitRepository by stored {
            override fun observeDay(date: LocalDate): Flow<List<HabitDay>> = flow {
                emit(stored.observeDay(date).first())
                if (fail) {
                    failure.await()
                    error("day observation failed after initial snapshot")
                }
                emitAll(stored.observeDay(date))
            }
        }
        compose.setContent {
            LifeOsTheme { HabitDetailRoute(repository, id, first, onEdit = {}, onBack = {}, deviceZone = zone) }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("habit_detail_log").fetchSemanticsNodes().isNotEmpty()
        }
        failure.complete(Unit)
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("habit_detail_log").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("Try again").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { fail = false }
        compose.onNodeWithText("Try again").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("habit_detail_log").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("habit_detail_log").performScrollTo().performClick()
        compose.waitUntil(10_000) { runBlocking { stored.history(id, first, 0).total == 1 } }
    }

    private fun checkTransition(preview: Boolean) {
        val selected = mutableStateOf(first)
        val gate = CompletableDeferred<Unit>()
        var fail = true
        val opened = mutableListOf<LocalDate>()
        val repository = object : HabitRepository by stored {
            override fun observeDay(date: LocalDate): Flow<List<HabitDay>> = flow {
                if (date == second) {
                    gate.await()
                    if (fail) error("deliberate next-date read failure")
                }
                emitAll(stored.observeDay(date))
            }
        }
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                LifeOsTheme {
                    Box(Modifier.width(320.dp)) {
                        val date by selected
                        if (preview) TodayRoute(
                            repository = RoomLifeEventRepository(database), date = date, zone = zone,
                            onSelectDate = { selected.value = it }, onToday = { selected.value = today },
                            habits = repository, habitsDate = date, deviceDate = today,
                            onHabit = { _, rowDate -> opened += rowDate },
                        ) else HabitsRoute(repository, date, { selected.value = it },
                            { selected.value = today }, {}, { _, rowDate -> opened += rowDate }, today, zone)
                    }
                }
            }
        }
        fun waitForRow() = compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("habit_log_$id").fetchSemanticsNodes().isNotEmpty()
        }
        waitForRow()
        compose.onNodeWithTag("habit_log_$id").performScrollTo().assertIsDisplayed()
        val staleLog = compose.onNodeWithTag("habit_log_$id").fetchSemanticsNode()
            .config[SemanticsActions.OnClick].action!!
        val staleOpen = compose.onNodeWithTag("habit_open_$id").fetchSemanticsNode()
            .config[SemanticsActions.OnClick].action!!

        compose.runOnIdle { selected.value = second }
        compose.onNodeWithTag(if (preview) "today_habits_loading" else "habits_loading")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Loading habits for this date…").performScrollTo().assertIsDisplayed()
        screenshot(preview, "loading")
        compose.onNodeWithTag("habit_log_$id").assertDoesNotExist()
        compose.onNodeWithTag("habit_open_$id").assertDoesNotExist()
        compose.runOnIdle { staleLog(); staleOpen() }
        assertEquals(0, runBlocking { stored.history(id, null, 0).total })
        assertTrue(opened.isEmpty())

        gate.complete(Unit)
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Habits could not be loaded.").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Habits could not be loaded.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Try again").performScrollTo().assertIsDisplayed()
        screenshot(preview, "error")
        compose.onNodeWithTag("habit_log_$id").assertDoesNotExist()
        compose.runOnIdle { fail = false }
        compose.onNodeWithText("Try again").performClick()
        waitForRow()
        compose.onNodeWithTag("habit_log_$id").performScrollTo().performClick()
        compose.waitUntil(10_000) { runBlocking { stored.history(id, second, 0).total == 1 } }

        compose.runOnIdle { selected.value = first }
        waitForRow()
        compose.runOnIdle { staleLog(); staleOpen() }
        assertEquals(0, runBlocking { stored.history(id, first, 0).total })
        assertEquals(1, runBlocking { stored.history(id, second, 0).total })
        assertTrue(opened.isEmpty())
        compose.onNodeWithTag("habit_open_$id").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(first), opened) }

        compose.runOnIdle { selected.value = today.plusDays(1) }
        waitForRow()
        compose.onNodeWithTag("habit_log_$id").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("You cannot log a future date.").performScrollTo().assertIsDisplayed()
        screenshot(preview, "future")
        assertEquals(1, runBlocking { stored.history(id, null, 0).total })
    }

    private fun screenshot(preview: Boolean, state: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "ux01-${if (preview) "today" else "habits"}-$state.png")
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
