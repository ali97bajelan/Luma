package com.luma.lifeos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.luma.lifeos.domain.model.*
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

/** Run using the disposable validation application ID, never against personal app data. */
class SearchFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun storage() = (compose.activity.application as LumaApplication).storage
    private fun awaitTag(tag: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun titleLookupOpensSourceRetainsQueryThroughRecreationAndReflectsDeletion() {
        val id = EventId.new(); val token = "Lookup ${UUID.randomUUID()}"
        val time = LocalDate.now(ZoneId.systemDefault()).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant()
        runBlocking { storage().journals.create(id, JournalInput("Private source text", time, token)) }
        try {
            compose.onNodeWithTag("nav_timeline").performClick()
            compose.onNodeWithTag("timeline_search").performClick(); awaitTag("screen_search")
            compose.onNodeWithTag("search_text").performTextReplacement(token); awaitTag("event_$id")
            compose.activityRule.scenario.recreate(); awaitTag("screen_search"); awaitTag("event_$id")
            compose.onNodeWithTag("search_text").assertTextContains(token)
            compose.onNodeWithTag("event_$id").performClick(); awaitTag("screen_journal_detail")
            compose.onNodeWithText("Private source text").assertExists()
            compose.onNodeWithText("Back").performClick(); awaitTag("screen_search"); awaitTag("event_$id")
            runBlocking { storage().lifeEvents.delete(id) }
            awaitTag("search_empty"); compose.onNodeWithTag("event_$id").assertDoesNotExist()
        } finally { runBlocking { storage().lifeEvents.delete(id) } }
    }
    @Test fun combinedCatalogFiltersClearAndBodyOnlyTextDoesNotMatch() {
        val id = EventId.new(); val person = UUID.randomUUID(); val place = UUID.randomUUID()
        val label = "Fixture ${UUID.randomUUID()}"; val tag = "Tag ${UUID.randomUUID()}"
        val time = LocalDate.now(ZoneId.systemDefault()).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant()
        runBlocking {
            storage().people.create(person, PersonInput(label)); storage().places.create(place, PlaceInput(label))
            storage().journals.create(id, JournalInput("Body only $label", time, "Simple title", setOf(tag), setOf(person), PlaceSelection(place)))
        }
        try {
            compose.onNodeWithTag("nav_timeline").performClick(); compose.onNodeWithTag("timeline_search").performClick()
            awaitTag("screen_search"); compose.onNodeWithTag("search_text").performTextReplacement("Body only $label")
            awaitTag("search_empty")
            compose.onNodeWithTag("search_clear").performClick()
            compose.onNodeWithTag("search_filters").performClick()
            for ((kind, name) in listOf("tags" to tag, "people" to label, "places" to label)) {
                compose.onNodeWithTag("search_filter_panel").performScrollToNode(hasTestTag("search_$kind"))
                compose.onNodeWithTag("search_$kind").performClick()
                compose.onNodeWithTag("search_option_text").performTextReplacement(name)
                val option = hasText(name) and hasAnyAncestor(hasTestTag("search_options_list"))
                compose.waitUntil(10_000) { compose.onAllNodes(option).fetchSemanticsNodes().isNotEmpty() }
                compose.onNode(option).performClick()
                compose.onNodeWithTag("search_options_done").performClick()
            }
            compose.onNodeWithTag("search_filters_done").performClick(); awaitTag("event_$id")
            compose.onNodeWithTag("search_clear").performClick()
            compose.onNodeWithTag("search_filters").assertTextEquals("Filters (0)")
        } finally {
            runBlocking { storage().lifeEvents.delete(id); storage().people.delete(person); storage().places.delete(place) }
        }
    }
}
