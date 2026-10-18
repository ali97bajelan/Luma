package com.luma.lifeos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.luma.lifeos.domain.model.EventId
import com.luma.lifeos.domain.model.JournalInput
import com.luma.lifeos.domain.model.PersonInput
import com.luma.lifeos.domain.model.PlaceInput
import com.luma.lifeos.domain.model.PlaceSelection
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun launchesOnTodayAndNavigatesToEveryPrimaryRoute() {
        compose.onNodeWithTag("screen_today").assertIsDisplayed()
        listOf("timeline", "habits", "review", "more", "today").forEach { route ->
            compose.onNodeWithTag("nav_$route").performClick()
            compose.onNodeWithTag("screen_$route").assertIsDisplayed()
        }
        compose.onNodeWithTag("nav_habits").performClick()
        compose.onNodeWithTag("screen_habits").assertIsDisplayed()
        compose.onNodeWithTag("habits_back").assertDoesNotExist()
        compose.onNodeWithTag("nav_people").assertDoesNotExist()
        compose.onNodeWithTag("nav_settings").assertDoesNotExist()
    }

    @Test fun reviewCatalogDetailsReturnToReviewOrigin() {
        val storage = (compose.activity.application as LumaApplication).storage
        val personId = UUID.randomUUID()
        val placeId = UUID.randomUUID()
        val journalId = EventId.new()
        val personName = "Review person ${System.nanoTime()}"
        val placeName = "Review place ${System.nanoTime()}"
        runBlocking {
            storage.people.create(personId, PersonInput(personName))
            storage.places.create(placeId, PlaceInput(placeName))
            storage.journals.create(journalId, JournalInput("Review catalog origin", Instant.now(),
                personIds = setOf(personId), placeSelection = PlaceSelection(placeId)))
        }
        try {
            compose.onNodeWithTag("nav_review").performClick()
            awaitTag("screen_review")
            compose.onNodeWithTag("review_section_catalog").performClick()
            awaitTag("review_heading_catalog")
            val openDetails = compose.activity.getString(R.string.review_catalog_detail)
            compose.waitUntil(5_000) { compose.onAllNodesWithText(openDetails).fetchSemanticsNodes().size == 2 }

            compose.onAllNodesWithText(openDetails)[0].performScrollTo().performClick()
            awaitTag("screen_person_detail")
            compose.onNodeWithTag("person_detail_back").performClick()
            awaitTag("screen_review")

            compose.onAllNodesWithText(openDetails)[1].performScrollTo().performClick()
            awaitTag("screen_place_detail")
            compose.onNodeWithTag("place_detail_back").performClick()
            awaitTag("screen_review")
        } finally {
            runBlocking {
                storage.journals.delete(journalId)
                storage.people.delete(personId)
                storage.places.delete(placeId)
            }
        }
    }

    @Test fun moreOpensPeoplePlacesAndSettingsWithBackToMore() {
        compose.onNodeWithTag("nav_more").performClick()
        compose.onNodeWithTag("screen_more").assertIsDisplayed()
        compose.onNodeWithTag("more_people_places").performClick()
        compose.onNodeWithTag("screen_people").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("screen_people").assertIsDisplayed()
        compose.onNodeWithTag("catalog_people").assertIsSelected()
        compose.onNodeWithTag("people_back").assertIsDisplayed().performClick()
        compose.onNodeWithTag("screen_more").assertIsDisplayed()

        compose.onNodeWithTag("more_settings").performClick()
        compose.onNodeWithTag("screen_settings").assertIsDisplayed()
        compose.onNodeWithTag("settings_hub_back").assertIsDisplayed().performClick()
        compose.onNodeWithTag("screen_more").assertIsDisplayed()
    }

    @Test fun todayHabitPreviewOpensThePrimaryHabitsDestination() {
        compose.onNodeWithTag("today_habits_card").assertIsDisplayed()
        compose.onNodeWithTag("habits_view_all").performClick()
        compose.onNodeWithTag("screen_habits").assertIsDisplayed()
    }

    @Test fun recreationRestoresSelectedRoute() {
        compose.onNodeWithTag("nav_more").performClick()
        compose.onNodeWithTag("more_settings").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("screen_settings").assertIsDisplayed()
        compose.onNodeWithTag("settings_hub_back").performClick()
        compose.onNodeWithTag("screen_more").assertIsDisplayed()
    }

    @Test fun quickCaptureIsReachableFromEveryPrimaryRoute() {
        listOf("today", "timeline", "habits", "review", "more").forEach { route ->
            compose.onNodeWithTag("nav_$route").performClick()
            compose.onNodeWithTag("quick_capture").assertIsDisplayed().performClick()
            compose.onNodeWithTag("capture_sheet").assertIsDisplayed()
            compose.onNodeWithTag("capture_close").performClick()
        }
    }

    @Test fun selectedDaySurvivesNavigationAndRecreation() {
        val yesterday = LocalDate.now().minusDays(1)
            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        compose.onNodeWithTag("day_previous").performClick()
        compose.onNodeWithTag("nav_habits").performClick()
        compose.onNodeWithTag("day_selected", useUnmergedTree = true).assertTextEquals(yesterday)
        compose.onNodeWithTag("nav_more").performClick()
        compose.onNodeWithTag("more_settings").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("settings_hub_back").performClick()
        compose.onNodeWithTag("nav_more").assertIsSelected()
        compose.onNodeWithTag("nav_today").performClick()
        compose.onNodeWithTag("day_selected", useUnmergedTree = true).assertTextEquals(yesterday)
        compose.onNodeWithTag("day_today").performClick()
        compose.onNodeWithTag("day_selected", useUnmergedTree = true).assertTextEquals(
            LocalDate.now().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)))
    }

    private fun awaitTag(tag: String) {
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
