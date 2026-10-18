package com.luma.lifeos.habits

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
import com.luma.lifeos.ui.LifeOsApp
import com.luma.lifeos.ui.theme.LifeOsTheme
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
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
        compose.onNodeWithTag("quick_capture").assertIsDisplayed()
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
    }
}
