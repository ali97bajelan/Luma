package com.luma.lifeos

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import com.luma.lifeos.domain.model.*
import com.luma.lifeos.domain.repository.SearchRepository
import com.luma.lifeos.domain.search.*
import com.luma.lifeos.ui.search.SearchRoute
import com.luma.lifeos.ui.theme.LifeOsTheme
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test

class SearchStatesTest {
    @get:Rule val compose = createComposeRule()

    private fun show(repository: Fixture) {
        compose.setContent { LifeOsTheme { SearchRoute(repository, ZoneId.of("UTC"), {}, {}) } }
    }

    private fun awaitTag(tag: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun emptyLibraryNeverSuggestsClearingNonexistentCriteria() {
        val repository = Fixture(emptyList())
        show(repository)
        awaitTag("search_empty_library")
        compose.onNodeWithTag("search_empty_library").assertTextEquals("Your saved entries will appear here")
        compose.onNodeWithTag("search_empty").assertDoesNotExist()
        compose.onNodeWithTag("search_result_count").assertDoesNotExist()
        compose.onNodeWithTag("search_reset_filters").assertIsNotEnabled()
        compose.onNodeWithTag("search_clear").assertIsNotEnabled()

        compose.onNodeWithTag("search_text").performTextReplacement("missing")
        awaitTag("search_empty")
        compose.onNodeWithTag("search_empty_library").assertDoesNotExist()
        compose.onNodeWithTag("search_clear").assertIsEnabled().performClick()
        awaitTag("search_empty_library")

        // Blank input has the same query meaning as no input in the repository.
        compose.onNodeWithTag("search_text").performTextReplacement("   ")
        awaitTag("search_empty_library")
        compose.onNodeWithTag("search_empty").assertDoesNotExist()
    }

    @Test fun initialPromptKeepsSavedEntriesAndReturnsAfterClearingAQuery() {
        val repository = Fixture()
        show(repository)
        awaitTag("search_prompt")
        compose.onNodeWithTag("search_prompt").assertTextEquals("Search entry titles, tags, people or places")
        compose.onNodeWithTag("event_${repository.entry.event.id}").assertExists()
        compose.onNodeWithTag("search_result_count").assertDoesNotExist()

        compose.onNodeWithTag("search_text").performTextReplacement("missing")
        awaitTag("search_empty")
        compose.onNodeWithTag("search_prompt").assertDoesNotExist()
        compose.onNodeWithTag("search_empty").assertTextEquals("No entries match your search.")
        compose.onNodeWithTag("search_reset_filters").assertIsNotEnabled()
        compose.onNodeWithTag("search_clear").assertIsEnabled().performClick()
        awaitTag("search_prompt")

        compose.runOnIdle { repository.records.value = emptyList() }
        awaitTag("search_empty_library")
        compose.onNodeWithTag("search_prompt").assertDoesNotExist()
    }

    @Test fun filterResetPreservesQueryAndClearAllRemovesBoth() {
        val repository = Fixture()
        show(repository)
        awaitTag("search_prompt")
        selectUnusedTag()
        awaitTag("search_empty")
        compose.onNodeWithTag("search_reset_filters").assertIsEnabled()
        compose.onNodeWithTag("search_filter_tag_${repository.tag}").assertTextContains("Unused", substring = true)

        compose.onNodeWithTag("search_text").performTextReplacement("Saved")
        awaitTag("search_empty")
        compose.onNodeWithTag("search_reset_filters").performClick()
        awaitTag("event_${repository.entry.event.id}")
        compose.onNodeWithTag("search_text").assertTextContains("Saved")
        compose.onNodeWithTag("search_reset_filters").assertIsNotEnabled()

        selectUnusedTag()
        awaitTag("search_empty")
        compose.onNodeWithTag("search_clear").performClick()
        awaitTag("search_prompt")
        compose.onNodeWithTag("search_text").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithTag("search_filter_tag_${repository.tag}").assertDoesNotExist()
        compose.onNodeWithTag("search_reset_filters").assertIsNotEnabled()
        compose.onNodeWithTag("search_clear").assertIsNotEnabled()
    }

    @Test fun characterLimitAppearsOnlyNearLimitAndInputStaysBounded() {
        show(Fixture())
        awaitTag("search_prompt")
        compose.onNodeWithTag("search_text_limit", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("search_text").performTextReplacement("a".repeat(179))
        compose.onNodeWithTag("search_text_limit", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("search_text").performTextReplacement("a".repeat(180))
        compose.onNodeWithTag("search_text_limit", useUnmergedTree = true).assertTextEquals("180/200 characters")
        compose.onNodeWithTag("search_text").performTextReplacement("a".repeat(201))
        compose.onNodeWithTag("search_text_limit", useUnmergedTree = true).assertTextEquals("200/200 characters")
        compose.onNodeWithTag("search_text").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("a".repeat(200))))
        compose.onNodeWithTag("search_clear").performClick()
        compose.onNodeWithTag("search_text_limit", useUnmergedTree = true).assertDoesNotExist()
    }

    private fun selectUnusedTag() {
        compose.onNodeWithTag("search_filters").performClick()
        compose.onNodeWithTag("search_filter_panel").performScrollToNode(hasTestTag("search_tags"))
        compose.onNodeWithTag("search_tags").performClick()
        compose.onNodeWithText("Unused").performClick()
        compose.onNodeWithTag("search_options_done").performClick()
        compose.onNodeWithTag("search_filters_done").performClick()
    }

    private class Fixture(initial: List<LifeEventRecord>? = null) : SearchRepository {
        val tag = UUID(1, 1)
        val entry = LifeEventRecord(LifeEvent(EventId(UUID(2, 2)), EventType.JOURNAL,
            Instant.parse("2026-10-12T12:00:00Z"), Instant.parse("2026-10-12T12:00:00Z"), title = "Saved entry"))
        val records = MutableStateFlow(initial ?: listOf(entry))
        override fun observeChanges() = records.map { Unit }
        override fun observeOptions() = flowOf(SearchOptions(tags = listOf(SearchOption(tag, "Unused"))))
        override suspend fun page(query: SearchQuery, zone: ZoneId, offset: Int, limit: Int): SearchPage {
            val hits = records.value.filter {
                query.tagIds.isEmpty() && it.event.title.orEmpty().contains(query.text.trim(), ignoreCase = true)
            }
            return SearchPage(hits.drop(offset).take(limit), offset, hits.size)
        }
    }
}
