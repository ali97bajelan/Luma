package com.luma.lifeos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class PlaceCaptureTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun journalDraftSurvivesPlaceCreationAndSelectionCanBeRenamedAndCleared() {
        val suffix = System.nanoTime()
        val name = "Journal cafe $suffix"
        val body = "Place journal $suffix"
        compose.onNodeWithTag("quick_capture").performClick()
        compose.onNodeWithTag("capture_journal").performClick()
        awaitTag("journal_body")
        awaitTag("journal_more")
        compose.onNodeWithTag("journal_body").performTextInput(body)
        compose.onNodeWithTag("journal_more").performScrollTo().performClick()
        compose.onNodeWithTag("place_picker_create").performScrollTo().performClick()
        fillPlace(name)
        awaitTag("journal_body")
        compose.onNodeWithTag("journal_body").assertTextContains(body)
        compose.onNodeWithTag("place_picker").performScrollTo().performClick()
        awaitText(name)
        compose.onNodeWithText(name).performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        awaitTag("journal_body")
        compose.onNodeWithTag("place_selected").performScrollTo().assertTextContains(name, substring = true)
        compose.onNodeWithTag("journal_save").performClick()
        awaitTag("screen_today")
        openPlace(name)
        compose.onNodeWithTag("place_edit").performClick()
        awaitTag("place_name")
        compose.onNodeWithTag("place_name").performTextReplacement("$name edited")
        compose.onNodeWithTag("place_save").performClick()
        awaitText("$name edited")
        compose.onNodeWithTag("place_detail_back").performClick()
        awaitTag("places_ready")
        compose.onNodeWithTag("people_back").performClick()
        compose.onNodeWithTag("nav_today").performClick()
        awaitTag("screen_today")
        compose.onNodeWithTag("screen_today").performScrollToNode(hasText(body))
        compose.onNodeWithText("Place: $name edited").assertIsDisplayed()
        compose.onNodeWithText(body).performClick()
        awaitTag("journal_detail_body")
        compose.onNodeWithTag("journal_edit").performClick()
        awaitTag("journal_body")
        compose.onNodeWithTag("place_selected").performScrollTo().performClick()
        compose.onNodeWithTag("journal_save").performClick()
        awaitTag("journal_detail_body")
        compose.onNodeWithText("Place: $name edited").assertDoesNotExist()
        compose.onNodeWithTag("journal_edit").performClick()
        awaitTag("journal_delete")
        compose.onNodeWithTag("journal_delete").performClick()
        compose.onNodeWithTag("journal_confirm_delete").performClick()
        awaitTag("screen_today")
        openPlace("$name edited")
        deletePlace()
    }

    @Test fun existingPlaceIsReusableAndDeletingItKeepsLinkedMemory() {
        val name = "Memory cafe ${System.nanoTime()}"
        val note = "Memory at cafe ${System.nanoTime()}"
        openPlaces()
        compose.onNodeWithTag("places_create").performClick()
        fillPlace(name)
        awaitTag("places_ready")
        compose.onNodeWithTag("people_back").performClick()
        compose.onNodeWithTag("nav_today").performClick()
        compose.onNodeWithTag("quick_capture").performClick()
        compose.onNodeWithTag("capture_memory").performClick()
        awaitTag("memory_note")
        awaitTag("memory_more")
        compose.onNodeWithTag("memory_note").performTextInput(note)
        compose.onNodeWithTag("memory_more").performScrollTo().performClick()
        compose.onNodeWithTag("place_picker").performScrollTo().performClick()
        awaitText(name)
        compose.onNodeWithText(name).performScrollTo().performClick()
        compose.onNodeWithTag("memory_save").performClick()
        awaitTag("screen_today")
        openPlace(name)
        deletePlace()
        compose.onNodeWithTag("people_back").performClick()
        compose.onNodeWithTag("nav_today").performClick()
        awaitTag("screen_today")
        compose.onNodeWithTag("screen_today").performScrollToNode(hasText(note))
        compose.onNodeWithText(note).performClick()
        awaitTag("memory_note")
        compose.onNodeWithTag("memory_note").assertTextContains(note)
        compose.onNodeWithTag("place_selected").assertDoesNotExist()
        compose.onNodeWithTag("memory_remove").performClick()
        compose.onNodeWithTag("memory_confirm_remove").performClick()
        awaitTag("screen_today")
    }
    private fun fillPlace(name: String) {
        awaitTag("place_name")
        compose.onNodeWithTag("place_name").performTextInput(name)
        compose.onNodeWithTag("place_note").performTextInput("Near home")
        compose.activityRule.scenario.recreate()
        awaitTag("place_name")
        compose.onNodeWithTag("place_name").assertTextContains(name)
        compose.onNodeWithTag("place_save").performClick()
    }
    private fun openPlaces() {
        if (compose.onAllNodesWithTag("screen_people").fetchSemanticsNodes().isEmpty()) {
            compose.onNodeWithTag("nav_more").performClick()
            compose.onNodeWithTag("more_people_places").performClick()
        }
        compose.onNodeWithTag("catalog_places").performClick()
        awaitTag("places_ready")
    }
    private fun openPlace(name: String) {
        openPlaces()
        compose.onNodeWithTag("screen_places").performScrollToNode(hasText(name))
        compose.onNodeWithText(name).performClick()
        awaitTag("place_detail_name")
    }
    private fun deletePlace() {
        compose.onNodeWithTag("place_edit").performClick()
        awaitTag("place_delete")
        compose.onNodeWithTag("place_delete").performClick()
        compose.onNodeWithTag("place_confirm_delete").performClick()
        awaitTag("places_ready")
    }
    private fun awaitText(text: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun awaitTag(tag: String) {
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag(tag) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
    }
}
