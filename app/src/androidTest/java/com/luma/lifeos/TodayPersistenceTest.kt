package com.luma.lifeos

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
    private lateinit var repository: RoomLifeEventRepository

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, LumaDatabase::class.java,
        ).build()
        repository = RoomLifeEventRepository(database)
    }

    @After fun tearDown() { database.close() }

    @Test fun selectedDayLoadsStoredRecordsAndReactsToCommittedChanges() {
        val today = LocalDate.now()
        val current = event(today, "Current day record")
        val previous = event(today.minusDays(1), "Previous day record")
        runBlocking { repository.upsert(current); repository.upsert(previous) }
        compose.setContent { LifeOsTheme { LifeOsApp(repository, RoomMoodRepository(database), com.luma.lifeos.data.repository.RoomJournalRepository(database)) } }
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
            compose.onAllNodesWithText("No events for this day yet").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("today_empty").assertIsDisplayed()
        compose.onNodeWithTag("day_today").performClick()
        awaitText("Current day record")
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
