package com.luma.lifeos

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import java.time.format.FormatStyle
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.repository.RoomLifeEventRepository
import com.luma.lifeos.data.repository.RoomMoodRepository
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.EventType
import com.luma.lifeos.domain.model.LifeEvent
import com.luma.lifeos.ui.LifeOsApp
import com.luma.lifeos.ui.theme.LifeOsTheme
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TodayPersistenceTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var database: LumaDatabase
    private lateinit var photoDirectory: java.io.File
    private lateinit var repository: RoomLifeEventRepository

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, LumaDatabase::class.java,
        ).build()
        repository = RoomLifeEventRepository(database)
        photoDirectory = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "photos-${java.util.UUID.randomUUID()}")
    }

    @After fun tearDown() { database.close(); photoDirectory.deleteRecursively() }

    @Test fun selectedDayLoadsStoredRecordsAndReactsToCommittedChanges() {
        val today = LocalDate.now()
        val current = event(today, "Current day record")
        val previous = event(today.minusDays(1), "Previous day record")
        runBlocking { repository.upsert(current); repository.upsert(previous) }
        showApp()
        awaitText("Current day record")
        compose.onNodeWithText("Previous day record").assertDoesNotExist()
        compose.onNodeWithTag("day_previous").performClick()
        awaitText("Previous day record")
        compose.onNodeWithText("Current day record").assertDoesNotExist()
        runBlocking { repository.upsert(previous.copy(title = "Edited day record")) }
        awaitText("Edited day record")
        compose.onNodeWithText("Previous day record").assertDoesNotExist()
        runBlocking { repository.delete(previous.id) }
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag("today_empty")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("today_empty").assertIsDisplayed()
        compose.onNodeWithTag("day_today").performClick()
        awaitText("Current day record")
    }

    @Test fun inlineCaptureOpensEachEditorAndPassesTheExplicitDateChoice() {
        showApp()
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        compose.onNodeWithTag("day_previous").performClick()
        listOf("mood", "journal", "memory").forEach { type ->
            compose.onNodeWithTag("today_capture_$type").performClick()
            compose.onNodeWithTag("today_capture_selected_date").performClick()
            compose.onNodeWithTag("screen_$type").assertIsDisplayed()
            compose.onNodeWithTag("mood_date").assertTextEquals(
                yesterday.format(com.luma.lifeos.language.localizedDate(FormatStyle.MEDIUM)))
            compose.onNodeWithTag("${type}_cancel").performClick()
            compose.onNodeWithTag("screen_today").assertIsDisplayed()
        }
        compose.onNodeWithTag("day_today").performClick()
        listOf("mood", "journal", "memory").forEach { type ->
            compose.onNodeWithTag("today_capture_$type").performClick()
            compose.onNodeWithTag("today_capture_date_dialog").assertDoesNotExist()
            compose.onNodeWithTag("screen_$type").assertIsDisplayed()
            compose.onNodeWithTag("mood_date").assertTextEquals(
                today.format(com.luma.lifeos.language.localizedDate(FormatStyle.MEDIUM)))
            compose.onNodeWithTag("${type}_cancel").performClick()
        }
    }

    private fun showApp() {
        compose.setContent {
            LifeOsTheme {
                LifeOsApp(repository, RoomMoodRepository(database),
                    com.luma.lifeos.data.repository.RoomJournalRepository(database),
                    com.luma.lifeos.data.repository.RoomMemoryRepository(database),
                    com.luma.lifeos.data.repository.RoomPersonRepository(database),
                    com.luma.lifeos.data.repository.RoomPlaceRepository(database),
                    com.luma.lifeos.data.repository.RoomPhotoRepository(database,
                        com.luma.lifeos.data.media.ManagedPhotoFiles(photoDirectory)) { error("No fixture source") },
                    com.luma.lifeos.data.repository.RoomTimelineRepository(database),
                    habits = com.luma.lifeos.data.repository.RoomHabitRepository(database))
            }
        }
    }

    private fun awaitText(text: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    private fun event(day: LocalDate, title: String): LifeEvent {
        val instant = day.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant()
        return LifeEvent(EventId.new(), EventType.JOURNAL, instant, instant, title = title)
    }
}
