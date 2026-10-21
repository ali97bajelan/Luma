package com.luma.lifeos

import androidx.compose.runtime.mutableStateOf
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
import com.luma.lifeos.ui.people.PersonDetailRoute
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

    @Test fun bothCatalogsSearchNamesAndExistingNotes() {
        val firstPerson = UUID.randomUUID()
        val secondPerson = UUID.randomUUID()
        val place = UUID.randomUUID()
        runBlocking {
            people.create(firstPerson, PersonInput("Sam", "North coast"))
            people.create(secondPerson, PersonInput("Alex", "South coast"))
            places.create(place, PlaceInput("Harbor studio", "North coast"))
        }
        compose.setContent { LifeOsTheme {
            PeopleRoute(people, places, onCreatePerson = {}, onPerson = {}, onCreatePlace = {}, onPlace = {})
        } }
        awaitTag("people_ready")
        compose.onNodeWithTag("people_search").performTextInput("north")
        compose.onNodeWithTag("person_$firstPerson").assertIsDisplayed()
        compose.onNodeWithTag("person_$secondPerson").assertDoesNotExist()

        compose.onNodeWithTag("catalog_places").performClick()
        awaitTag("places_ready")
        compose.onNodeWithTag("places_search").performTextInput("harbor")
        compose.onNodeWithTag("place_$place").assertIsDisplayed()
        compose.onNodeWithTag("catalog_people").performClick()
        compose.onNodeWithTag("people_search").assertTextContains("north")
        compose.onNodeWithTag("person_$firstPerson").assertIsDisplayed()
        compose.onNodeWithTag("person_$secondPerson").assertDoesNotExist()
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
        compose.onNodeWithTag("place_history_order").assertTextEquals("Oldest first")
        val earlyTop = compose.onNodeWithTag("place_event_$early").fetchSemanticsNode().boundsInRoot.top
        val lateTop = compose.onNodeWithTag("place_event_$late").fetchSemanticsNode().boundsInRoot.top
        assertTrue("Place history sorts by occurrence time, not insertion order", earlyTop < lateTop)
        compose.onNodeWithTag("place_detail_content").performScrollToNode(hasTestTag("place_event_$early"))
        compose.onNodeWithTag("event_$early").assertIsDisplayed()
        compose.onNodeWithTag("place_detail_content").performScrollToNode(hasTestTag("place_event_$late"))
        compose.onNodeWithTag("event_$late").assertIsDisplayed()
        compose.onNodeWithTag("place_detail_back").assertIsDisplayed()
        compose.onNodeWithTag("place_edit").assertIsDisplayed()
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
        compose.onNodeWithTag("place_detail_back").assertIsDisplayed()
    }

    @Test fun deletedPersonKeepsBackAndRemovesEditAction() {
        val person = UUID.randomUUID()
        runBlocking { people.create(person, PersonInput("Temporary person")) }
        compose.setContent { LifeOsTheme {
            PersonDetailRoute(people, person, onBack = {}, onEdit = {}, onEvent = {})
        } }
        awaitTag("person_detail_ready")
        runBlocking { people.delete(person) }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("This person is no longer available.").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("person_detail_back").assertIsDisplayed()
        compose.onNodeWithTag("person_edit").assertDoesNotExist()
    }

    @Test fun personAndPlaceActionsStayVisibleAtTheEndOfLongHistories() {
        val person = UUID.randomUUID()
        val place = UUID.randomUUID()
        val time = Instant.parse("2026-10-13T10:00:00Z")
        val eventIds = mutableListOf<EventId>()
        runBlocking {
            people.create(person, PersonInput("History person"))
            places.create(place, PlaceInput("History place"))
            val journals = RoomJournalRepository(db)
            repeat(60) { index ->
                val id = EventId.new()
                eventIds += id
                journals.create(id, JournalInput("Entry $index", time.plusSeconds((59 - index).toLong()),
                    personIds = setOf(person), placeSelection = PlaceSelection(place)))
            }
        }

        val showingPlace = mutableStateOf(false)
        compose.setContent { LifeOsTheme {
            if (showingPlace.value) PlaceDetailRoute(places, place, onBack = {}, onEdit = {}, onEvent = {})
            else PersonDetailRoute(people, person, onBack = {}, onEdit = {}, onEvent = {})
        } }
        awaitTag("person_detail_ready")
        compose.onNodeWithTag("person_history_order").assertTextEquals("Oldest first")
        val personOldest = "person_event_${eventIds.last()}"
        val personNext = "person_event_${eventIds[eventIds.lastIndex - 1]}"
        compose.onNodeWithTag(personOldest).assertIsDisplayed()
        compose.onNodeWithTag(personNext).assertIsDisplayed()
        assertTrue("Person history sorts by occurrence time, not insertion order",
            compose.onNodeWithTag(personOldest).fetchSemanticsNode().boundsInRoot.top <
                compose.onNodeWithTag(personNext).fetchSemanticsNode().boundsInRoot.top)
        compose.onNodeWithTag("person_detail_content").performScrollToNode(hasTestTag("person_event_${eventIds.last()}"))
        compose.onNodeWithTag("person_detail_content").performScrollToNode(hasTestTag("person_event_${eventIds.first()}"))
        compose.onNodeWithTag("person_detail_back").assertIsDisplayed()
        compose.onNodeWithTag("person_edit").assertIsDisplayed()

        compose.runOnIdle { showingPlace.value = true }
        awaitTag("place_detail_ready")
        compose.onNodeWithTag("place_history_order").assertTextEquals("Oldest first")
        val placeOldest = "place_event_${eventIds.last()}"
        val placeNext = "place_event_${eventIds[eventIds.lastIndex - 1]}"
        compose.onNodeWithTag(placeOldest).assertIsDisplayed()
        compose.onNodeWithTag(placeNext).assertIsDisplayed()
        assertTrue("Place history sorts by occurrence time, not insertion order",
            compose.onNodeWithTag(placeOldest).fetchSemanticsNode().boundsInRoot.top <
                compose.onNodeWithTag(placeNext).fetchSemanticsNode().boundsInRoot.top)
        compose.onNodeWithTag("place_detail_content").performScrollToNode(hasTestTag("place_event_${eventIds.first()}"))
        compose.onNodeWithTag("place_detail_back").assertIsDisplayed()
        compose.onNodeWithTag("place_edit").assertIsDisplayed()
    }

    private fun awaitTag(tag: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }
}
