@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.luma.lifeos

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.luma.lifeos.domain.search.SearchOption
import com.luma.lifeos.domain.search.SearchOptions
import com.luma.lifeos.ui.search.ActiveSearchFilters
import com.luma.lifeos.ui.search.SearchFilterKey
import com.luma.lifeos.ui.search.SearchFilterKind
import com.luma.lifeos.ui.search.SearchInput
import com.luma.lifeos.ui.search.SearchState
import com.luma.lifeos.ui.theme.LifeOsTheme
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ActiveSearchFiltersTest {
    @get:Rule val compose = createComposeRule()

    @Test fun allCategoriesHaveNamedRemovalActions() {
        val tag = UUID(3, 3); val person = UUID(1, 1); val place = UUID(4, 4)
        val state = SearchState(
            input = SearchInput(
                from = LocalDate.parse("2026-10-12"), through = LocalDate.parse("2026-10-13"),
                tagIds = setOf(tag), personIds = setOf(person), placeId = place,
            ),
            options = SearchOptions(
                tags = listOf(SearchOption(tag, "Work")),
                people = listOf(SearchOption(person, "Alex")),
                places = listOf(SearchOption(place, "Home")),
            ),
            optionsLoading = false,
        )
        val removed = mutableListOf<SearchFilterKey>()

        compose.setContent { LifeOsTheme { ActiveSearchFilters(state, removed::add) } }

        compose.onNodeWithTag("search_filter_from")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
        compose.onNodeWithTag("search_filter_through").assertExists()
        compose.onNodeWithTag("search_filter_tag_$tag").assertTextContains("Work", substring = true)
        compose.onNodeWithTag("search_filter_person_$person")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
            .performClick()
        compose.onNodeWithTag("search_filter_place_$place").assertTextContains("Home", substring = true)
        compose.runOnIdle { assertEquals(listOf(SearchFilterKey(SearchFilterKind.PERSON, person)), removed) }
    }

    @Test fun duplicateAndUnavailableNamesKeepIdentity() {
        val first = UUID(1, 1); val second = UUID(2, 2); val missing = UUID(3, 3)
        val state = SearchState(
            input = SearchInput(tagIds = setOf(missing), personIds = setOf(first, second)),
            options = SearchOptions(people = listOf(SearchOption(first, "Alex"), SearchOption(second, "Alex"))),
            optionsLoading = false,
        )

        compose.setContent { LifeOsTheme { ActiveSearchFilters(state) {} } }

        compose.onNodeWithTag("search_filter_person_$first").assertTextContains("Alex", substring = true)
            .assertTextContains("00000001", substring = true)
        compose.onNodeWithTag("search_filter_person_$second").assertTextContains("00000002", substring = true)
        compose.onNodeWithTag("search_filter_tag_$missing")
            .assertTextContains("Unavailable selection", substring = true)
        compose.onNodeWithTag("search_filter_person_$first")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
    }

    @Test fun unavailableSelectionRemainsRemovableDuringLoadingAndCatalogFailure() {
        val missing = UUID(3, 3)
        var state by mutableStateOf(SearchState(
            input = SearchInput(tagIds = setOf(missing)),
            optionsLoading = true,
        ))
        val removed = mutableListOf<SearchFilterKey>()

        compose.setContent { LifeOsTheme { ActiveSearchFilters(state, removed::add) } }

        compose.onNodeWithTag("search_filter_tag_$missing").assertTextContains("Selection loading", substring = true)
            .performClick()
        compose.runOnIdle { assertEquals(SearchFilterKey(SearchFilterKind.TAG, missing), removed.single()) }

        compose.runOnIdle {
            removed.clear()
            state = state.copy(optionsLoading = false, optionsError = true)
        }
        compose.onNodeWithTag("search_filter_tag_$missing").assertTextContains("Selection unavailable", substring = true)
            .performClick()
        compose.runOnIdle { assertEquals(SearchFilterKey(SearchFilterKind.TAG, missing), removed.single()) }
    }

    @Test fun manyFiltersScrollWithoutHidingToolbar() {
        val tags = (1..100).map { UUID(0x1000L + it, it.toLong()) }
        val people = (1..100).map { UUID(0x2000L + it, it.toLong()) }
        val place = UUID(0x3000L, 1)
        val initial = SearchState(
            input = SearchInput(
                from = LocalDate.parse("2026-10-12"), through = LocalDate.parse("2026-10-13"),
                tagIds = tags.toSet(), personIds = people.toSet(), placeId = place,
            ),
            options = SearchOptions(
                tags = tags.mapIndexed { index, id -> SearchOption(id, "Tag $index") },
                people = people.mapIndexed { index, id -> SearchOption(id, "Person $index") },
                places = listOf(SearchOption(place, "Home")),
            ),
            optionsLoading = false,
        )
        var state by mutableStateOf(initial)

        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
                LocalLayoutDirection provides LayoutDirection.Rtl,
            ) {
                LifeOsTheme {
                    Column(Modifier.width(320.dp).fillMaxHeight().padding(8.dp)) {
                        TextButton(onClick = {}, modifier = Modifier.testTag("search_test_toolbar")) { Text("Filters") }
                        TextButton(onClick = {}, modifier = Modifier.testTag("search_reset_filters")) { Text("Reset filters") }
                        val listState = rememberLazyListState()
                        LazyColumn(state = listState, modifier = Modifier.weight(1f).testTag("search_test_list")) {
                            item {
                                ActiveSearchFilters(state) { key ->
                                    key.id?.let { id ->
                                        state = state.copy(input = state.input.copy(personIds = state.input.personIds - id))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        val lastPerson = "search_filter_person_${people.last()}"
        compose.onNodeWithTag("search_test_list").performScrollToNode(hasTestTag(lastPerson))
        compose.onNodeWithTag(lastPerson).assertIsDisplayed().performClick()
        compose.onNodeWithTag(lastPerson).assertDoesNotExist()
        compose.onNodeWithTag("search_test_toolbar").assertIsDisplayed()
        compose.onNodeWithTag("search_reset_filters").assertIsDisplayed()
        compose.runOnIdle { assertTrue(state.input.personIds.size == 99) }
    }
}
