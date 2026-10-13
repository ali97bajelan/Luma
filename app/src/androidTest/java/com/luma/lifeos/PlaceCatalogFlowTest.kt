package com.luma.lifeos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.luma.lifeos.data.db.LumaDatabase
import com.luma.lifeos.data.repository.*
import com.luma.lifeos.domain.model.*
import com.luma.lifeos.ui.people.PeopleRoute
import com.luma.lifeos.ui.places.PlaceDetailRoute
import com.luma.lifeos.ui.places.PlaceEditorRoute
import com.luma.lifeos.ui.theme.LifeOsTheme
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class PlaceCatalogFlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: LumaDatabase
    private lateinit var places: RoomPlaceRepository
    private lateinit var people: RoomPersonRepository
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            LumaDatabase::class.java).build()
        places = RoomPlaceRepository(db)
        people = RoomPersonRepository(db)
    }
    @After fun close() { db.close() }

    @Test fun selectorShowsEachCatalogAndItsCreateAndEmptyState() {
        var personCreates = 0; var placeCreates = 0
        compose.setContent { LifeOsTheme {
            PeopleRoute(people, places, onCreatePerson = { personCreates++ }, onPerson = {},
                onCreatePlace = { placeCreates++ }, onPlace = {})
        } }
        awaitTag("people_empty")
        compose.onNodeWithTag("catalog_people").assertIsSelected()
        compose.onNodeWithTag("people_create").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, personCreates); assertEquals(0, placeCreates) }
        compose.onNodeWithTag("catalog_places").performClick()
        awaitTag("places_empty")
        compose.onNodeWithTag("catalog_places").assertIsSelected()
        compose.onNodeWithTag("people_empty").assertDoesNotExist()
        compose.onNodeWithTag("places_back").assertDoesNotExist()
        compose.onNodeWithTag("places_create").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, personCreates); assertEquals(1, placeCreates) }
        compose.onNodeWithTag("catalog_people").performClick()
        awaitTag("people_empty")
    }

    @Test fun placesSelectionSurvivesCreationAndRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { LifeOsTheme {
            val nav = rememberNavController()
            NavHost(nav, startDestination = "people") {
                composable("people") {
                    PeopleRoute(people, places, onCreatePerson = {}, onPerson = {},
                        onCreatePlace = { nav.navigate("place") }, onPlace = {})
                }
                composable("place") {
                    PlaceEditorRoute(places, null, onCancel = { nav.popBackStack() },
                        onFinished = { nav.popBackStack() })
                }
            }
        } }
        awaitTag("people_ready")
        compose.onNodeWithTag("catalog_places").performClick()
        awaitTag("places_ready")
        restoration.emulateSavedInstanceStateRestore()
        awaitTag("places_ready")
        compose.onNodeWithTag("catalog_places").assertIsSelected()
        compose.onNodeWithTag("places_create").performScrollTo().performClick()
        awaitTag("place_name")
        restoration.emulateSavedInstanceStateRestore()
        awaitTag("place_name")
        compose.onNodeWithTag("place_cancel").performClick()
        awaitTag("places_ready")
        compose.onNodeWithTag("catalog_places").assertIsSelected()
        compose.onNodeWithTag("places_create").performScrollTo().performClick()
        awaitTag("place_name")
        compose.onNodeWithTag("place_name").performTextInput("Restored cafe")
        compose.onNodeWithTag("place_save").performClick()
        awaitTag("places_ready")
        compose.onNodeWithTag("catalog_places").assertIsSelected()
        compose.onNodeWithTag("screen_places").performScrollToNode(hasText("Restored cafe"))
        compose.onNodeWithText("Restored cafe").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        awaitTag("places_ready")
        compose.onNodeWithTag("catalog_places").assertIsSelected()
    }

    @Test fun historyIsChronologicalAndOpeningEntryInvokesEventAction() {
        val place = UUID.randomUUID()
        val early = EventId.new(); val late = EventId.new()
        val time = Instant.parse("2026-10-13T10:00:00Z")
        runBlocking {
            places.create(place, PlaceInput("Cafe", "Near home"))
            val journals = RoomJournalRepository(db)
            journals.create(late, JournalInput("Later entry", time, placeSelection = PlaceSelection(place)))
            journals.create(early, JournalInput("Earlier entry", time.minusSeconds(60), placeSelection = PlaceSelection(place)))
        }
        var opened: EventId? = null
        compose.setContent { LifeOsTheme {
            PlaceDetailRoute(places, place, onBack = {}, onEdit = {}, onEvent = { opened = it.event.id })
        } }
        awaitTag("place_detail_ready")
        compose.onNodeWithTag("screen_place_detail").performScrollToIndex(2)
        compose.onNodeWithTag("event_$early").assertIsDisplayed()
        compose.onNodeWithTag("screen_place_detail").performScrollToIndex(3)
        compose.onNodeWithTag("event_$late").assertIsDisplayed()
        compose.onNodeWithTag("event_$late").performClick()
        compose.runOnIdle { assertEquals(late, opened) }
    }

    @Test fun emptyHistoryBecomesMissingWhenPlaceIsDeleted() {
        val place = UUID.randomUUID()
        runBlocking { places.create(place, PlaceInput("Empty cafe")) }
        compose.setContent { LifeOsTheme {
            PlaceDetailRoute(places, place, onBack = {}, onEdit = {}, onEvent = {})
        } }
        awaitTag("place_history_empty")
        runBlocking { places.delete(place) }
        awaitTag("place_detail_missing")
        compose.onNodeWithTag("place_detail_name").assertDoesNotExist()
        compose.onNodeWithTag("place_edit").assertDoesNotExist()
    }

    private fun awaitTag(tag: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }
}
