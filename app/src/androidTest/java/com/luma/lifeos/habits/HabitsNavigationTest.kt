package com.luma.lifeos.habits

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.media.ManagedPhotoFiles
import com.luma.lifeos.data.repository.RoomHabitRepository
import com.luma.lifeos.data.repository.RoomJournalRepository
import com.luma.lifeos.data.repository.RoomLifeEventRepository
import com.luma.lifeos.data.repository.RoomMemoryRepository
import com.luma.lifeos.data.repository.RoomMoodRepository
import com.luma.lifeos.data.repository.RoomPersonRepository
import com.luma.lifeos.data.repository.RoomPhotoRepository
import com.luma.lifeos.data.repository.RoomPlaceRepository
import com.luma.lifeos.data.repository.RoomTimelineRepository
import com.luma.lifeos.domain.habit.HabitSaveInput
import com.luma.lifeos.language.localizedDate
import com.luma.lifeos.ui.LifeOsApp
import com.luma.lifeos.ui.theme.LifeOsTheme
import java.io.File
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.FormatStyle
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class HabitsNavigationTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var database: LumaDatabase
    private lateinit var habits: RoomHabitRepository
    private lateinit var photoDirectory: File
    private val zone = ZoneId.systemDefault()

    @Before fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, LumaDatabase::class.java).build()
        habits = RoomHabitRepository(database, Clock.system(zone))
        photoDirectory = File(context.cacheDir, "habit-navigation-${UUID.randomUUID()}").apply { mkdirs() }
    }

    @After fun tearDown() {
        database.close()
        photoDirectory.deleteRecursively()
    }

    @Test fun primaryHabitsDestinationCanCreateAndLogOneRepetition() {
        val name = "Navigation habit ${System.nanoTime()}"
        val photos = RoomPhotoRepository(database, ManagedPhotoFiles(photoDirectory)) {
            error("No photo picker is used by this test")
        }
        compose.setContent {
            LifeOsTheme {
                LifeOsApp(
                    repository = RoomLifeEventRepository(database),
                    moods = RoomMoodRepository(database),
                    journals = RoomJournalRepository(database),
                    memories = RoomMemoryRepository(database),
                    people = RoomPersonRepository(database),
                    places = RoomPlaceRepository(database),
                    photos = photos,
                    timeline = RoomTimelineRepository(database),
                    habits = habits,
                )
            }
        }

        compose.onNodeWithTag("nav_habits").performClick()
        compose.onNodeWithTag("screen_habits").assertIsDisplayed()
        compose.onNodeWithTag("quick_capture").assertDoesNotExist()
        compose.onNodeWithTag("habit_create").performClick()
        compose.onNodeWithTag("habit_name").performTextInput(name)
        compose.onNodeWithTag("habit_save").performClick()

        val date = LocalDate.now(zone)
        compose.waitUntil(10_000) {
            runBlocking { habits.observeDay(date).first().any { it.habit.name == name } }
        }
        val id = runBlocking {
            requireNotNull(habits.observeDay(date).first().singleOrNull { it.habit.name == name }).habit.id
        }
        compose.onNodeWithTag("habit_log_$id").performClick()
        compose.waitUntil(10_000) {
            runBlocking { habits.history(id, date, 0).logs.size == 1 }
        }
        assertEquals(1, runBlocking { habits.history(id, date, 0).logs.size })

        compose.onNodeWithTag("habit_open_$id").performClick()
        compose.onNodeWithTag("screen_habit_detail").assertIsDisplayed()
        compose.onNodeWithTag("habit_manage").performScrollTo().performClick()
        compose.onNodeWithTag("habit_manage_edit").assertIsDisplayed()
    }

    @Test fun quickCaptureHabitShortcutUsesTrackingTodayAndPreservesHistoricalDateWithoutLogging() {
        val deviceDate = LocalDate.now(zone)
        val trackingZone = ZoneId.of("Pacific/Kiritimati")
        val trackingDate = deviceDate.minusDays(1)
        val fixedInstant = trackingDate.atTime(1, 0).toInstant(ZoneOffset.UTC)
        habits = RoomHabitRepository(database, Clock.fixed(fixedInstant, trackingZone))
        val habitId = UUID.randomUUID()
        runBlocking {
            habits.create(
                habitId,
                HabitSaveInput(
                    name = "Capture shortcut habit",
                    unitLabel = null,
                    start = trackingDate.minusDays(30),
                    target = 1,
                    weekdays = DayOfWeek.values().toSet(),
                    zone = trackingZone,
                ),
            )
        }
        val photos = RoomPhotoRepository(database, ManagedPhotoFiles(photoDirectory)) {
            error("No photo picker is used by this test")
        }
        compose.setContent {
            LifeOsTheme {
                LifeOsApp(
                    repository = RoomLifeEventRepository(database),
                    moods = RoomMoodRepository(database),
                    journals = RoomJournalRepository(database),
                    memories = RoomMemoryRepository(database),
                    people = RoomPersonRepository(database),
                    places = RoomPlaceRepository(database),
                    photos = photos,
                    timeline = RoomTimelineRepository(database),
                    habits = habits,
                )
            }
        }

        compose.onNodeWithTag("nav_timeline").performClick()
        compose.onNodeWithTag("quick_capture").performClick()
        compose.onNodeWithTag("capture_habit").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("habits_scheduled_heading").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("habits_scheduled_heading").assertIsDisplayed()
        compose.onNodeWithTag("day_selected")
            .assertTextEquals(trackingDate.format(localizedDate(FormatStyle.MEDIUM)))
        assertEquals(0, runBlocking { habits.history(habitId, null, 0).total })

        compose.onNodeWithTag("nav_timeline").performClick()
        compose.onNodeWithTag("day_previous").performClick()
        compose.onNodeWithTag("day_previous").performClick()
        val historicalDate = deviceDate.minusDays(2)
        compose.onNodeWithTag("quick_capture").performClick()
        compose.onNodeWithTag("capture_on_browsed_date").performScrollTo().performClick()
        compose.onNodeWithTag("capture_habit").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("habits_scheduled_heading").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("habits_scheduled_heading").assertIsDisplayed()
        compose.onNodeWithTag("day_selected")
            .assertTextEquals(historicalDate.format(localizedDate(FormatStyle.MEDIUM)))
        assertEquals(0, runBlocking { habits.history(habitId, null, 0).total })
    }
}
