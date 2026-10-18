package com.luma.lifeos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class PeopleCaptureTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun journalKeepsDraftAcrossNewPeopleAndHistoryReflectsExplicitRemoval() {
        val suffix = System.nanoTime()
        val first = "First person $suffix"
        val second = "Second person $suffix"
        val body = "People journal $suffix"
        compose.onNodeWithTag("quick_capture").performClick()
        compose.onNodeWithTag("capture_journal").performClick()
        awaitTag("journal_body")
        awaitTag("journal_more")
        compose.onNodeWithTag("journal_body").performTextInput(body)
        compose.onNodeWithTag("journal_more").performScrollTo().performClick()
        createFromPicker(first)
        compose.onNodeWithTag("journal_body").assertTextContains(body)
        createFromPicker(second)
        compose.onNodeWithTag("people_picker").performScrollTo().performClick()
        awaitText(first)
        compose.onNodeWithText(first).performScrollTo().performClick()
        compose.onNodeWithText(second).performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        awaitTag("journal_body")
        compose.onNodeWithTag("journal_body").assertTextContains(body)
        compose.onNodeWithText("$first · Remove").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("$second · Remove").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("journal_save").performClick()
        awaitTag("screen_today")
        openPerson(first)
        compose.onNodeWithTag("person_history_order").assertTextEquals("Oldest first")
        compose.onNodeWithTag("person_detail_content").performScrollToNode(hasText(body))
        compose.onNodeWithTag("person_detail_back").assertIsDisplayed()
        compose.onNodeWithTag("person_edit").assertIsDisplayed()
        compose.onNodeWithText(body).assertIsDisplayed()
        compose.onNodeWithText("Open entry").performClick()
        awaitTag("journal_detail_body")
        compose.onNodeWithTag("journal_edit").performClick()
        awaitTag("journal_body")
        compose.onNodeWithText("$first · Remove").performScrollTo().performClick()
        compose.onNodeWithTag("journal_save").performClick()
        awaitTag("journal_detail_body")
        compose.onNodeWithTag("journal_detail_back").performClick()
        awaitTag("person_detail_ready")
        awaitTag("person_history_empty")
        deleteCurrentPerson()
        openPerson(second)
        compose.onNodeWithTag("person_detail_content").performScrollToNode(hasText(body))
        compose.onNodeWithTag("person_detail_back").assertIsDisplayed()
        compose.onNodeWithTag("person_edit").assertIsDisplayed()
        compose.onNodeWithText("Open entry").performClick()
        awaitTag("journal_detail_body")
        compose.onNodeWithTag("journal_edit").performClick()
        awaitTag("journal_delete")
        compose.onNodeWithTag("journal_delete").performClick()
        compose.onNodeWithTag("journal_confirm_delete").performClick()
        awaitTag("person_detail_ready")
        deleteCurrentPerson()
    }

    @Test fun deletingLinkedPersonKeepsStandaloneMemory() {
        val name = "Memory person ${System.nanoTime()}"
        val note = "Retained memory ${System.nanoTime()}"
        openPeople()
        compose.onNodeWithTag("people_create").performClick()
        fillPerson(name)
        awaitTag("screen_people")
        compose.onNodeWithTag("people_back").performClick()
        compose.onNodeWithTag("nav_today").performClick()
        compose.onNodeWithTag("quick_capture").performClick()
        compose.onNodeWithTag("capture_memory").performClick()
        awaitTag("memory_note")
        awaitTag("memory_more")
        compose.onNodeWithTag("memory_note").performTextInput(note)
        compose.onNodeWithTag("memory_more").performScrollTo().performClick()
        compose.onNodeWithTag("people_picker").performScrollTo().performClick()
        awaitText(name)
        compose.onNodeWithText(name).performScrollTo().performClick()
        compose.onNodeWithTag("memory_save").performClick()
        awaitTag("screen_today")
        openPerson(name)
        compose.onNodeWithTag("person_detail_content").performScrollToNode(hasText(note))
        compose.onNodeWithTag("person_detail_back").assertIsDisplayed()
        compose.onNodeWithTag("person_edit").assertIsDisplayed()
        compose.onNodeWithText(note).assertIsDisplayed()
        deleteCurrentPerson()
        compose.onNodeWithTag("people_back").performClick()
        compose.onNodeWithTag("nav_today").performClick()
        awaitTag("screen_today")
        compose.onNodeWithTag("screen_today").performScrollToNode(hasText(note))
        compose.onNodeWithText(note).performClick()
        awaitTag("memory_note")
        compose.onNodeWithTag("memory_note").assertTextContains(note)
        compose.onNodeWithTag("memory_remove").performClick()
        compose.onNodeWithTag("memory_confirm_remove").performClick()
        awaitTag("screen_today")
    }
    private fun createFromPicker(name: String) {
        compose.onNodeWithTag("people_picker_create").performScrollTo().performClick()
        fillPerson(name)
        awaitTag("journal_body")
    }
    private fun fillPerson(name: String) {
        awaitTag("person_name")
        compose.onNodeWithTag("person_name").performTextInput(name)
        compose.onNodeWithTag("person_note").performTextInput("Friend")
        compose.activityRule.scenario.recreate()
        awaitTag("person_name")
        compose.onNodeWithTag("person_name").assertTextContains(name)
        compose.onNodeWithTag("person_save").performClick()
    }
    private fun openPerson(name: String) {
        openPeople()
        awaitTag("people_ready")
        compose.onNodeWithTag("screen_people").performScrollToNode(hasText(name))
        compose.onNodeWithText(name).performClick()
        awaitTag("person_detail_ready")
    }
    private fun deleteCurrentPerson() {
        compose.onNodeWithTag("person_edit").performClick()
        awaitTag("person_delete")
        compose.onNodeWithTag("person_delete").performClick()
        compose.onNodeWithTag("person_confirm_delete").performClick()
        awaitTag("screen_people")
    }
    private fun openPeople() {
        if (compose.onAllNodesWithTag("screen_people").fetchSemanticsNodes().isEmpty()) {
            compose.onNodeWithTag("nav_more").performClick()
            compose.onNodeWithTag("more_people_places").performClick()
        }
    }
    private fun awaitText(text: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun awaitTag(tag: String) {
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag(tag) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
    }
}
